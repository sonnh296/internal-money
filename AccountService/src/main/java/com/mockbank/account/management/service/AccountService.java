package com.mockbank.account.management.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.mockbank.account.client.CustomerServiceClient;
import com.mockbank.commons.dto.account.*;
import com.mockbank.account.management.mapper.AccountMapper;
import com.mockbank.account.management.model.*;
import com.mockbank.account.transaction.model.*;
import com.mockbank.account.transaction.service.*;
import com.mockbank.account.management.repository.AccountHoldRepository;
import com.mockbank.account.management.repository.AccountRepository;
import com.mockbank.commons.dto.exception.InsufficientFundsException;
import com.mockbank.commons.dto.exception.OwnerAccessDeniedException;
import com.mockbank.commons.security.CurrentUser;
import com.mockbank.commons.dto.account.TransactionRequest;
import com.mockbank.account.transaction.model.Transaction;
import com.mockbank.account.transaction.mapper.TransactionMapper;
import org.apache.commons.codec.digest.DigestUtils;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository accountRepo;
	private final AccountHoldRepository holdRepo;
	private final AccountMapper mapper;
	private final TransactionService transactionService;
	private final TransactionMapper transactionMapper;

	@Value("${account.transfer.max-amount:500000000}")
	private BigDecimal maxTransferAmount;

	private final CustomerServiceClient customerServiceClient;
	private final EntityManager entityManager;
	private final TransactionTemplate transactionTemplate;
	private final TransactionLedgerService ledgerService;

	private final CurrentUser currentUser;

	/** Tiền tố số tài khoản theo BIN nội bộ */
	private static final String ACCOUNT_NUMBER_PREFIX = "9";

	/** Tài khoản đối ứng nội bộ cho sổ cái double-entry (debit/credit đơn phía) */
	private static final UUID SYSTEM_SETTLEMENT_ACCOUNT_ID =
			UUID.fromString("00000000-0000-0000-0000-000000000099");

	private static final int MAX_OPTIMISTIC_RETRIES = 3;
	private static final long OPTIMISTIC_BACKOFF_MS = 50L;

	/* ---------------- Utility ---------------- */

	private static String fingerprintForCreate(AccountRequest r, String idempotencyKey) {
		if (idempotencyKey != null && !idempotencyKey.isBlank())
			return idempotencyKey.trim();
		// Stable fingerprint for idempotent account create
		String base = (r.customerId() + "|" + r.accountType() + "|" + r.accountSubType() + "|" + r.currency() + "|"
				+ r.nickname() + "|" + r.displayName()).toUpperCase();
		return Integer.toHexString(base.hashCode());
	}

	private BigDecimal activeHoldsTotal(UUID accountId) {
		return holdRepo.findByAccountIdAndStatus(accountId, HoldStatus.ACTIVE).stream().map(AccountHold::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

	}

	private AccountResponse withAvailability(Account account) {
		AccountResponse base = mapper.toDto(account);
		BigDecimal holds = activeHoldsTotal(account.getId());
		BigDecimal available = account.getBalance().subtract(holds);
		return new AccountResponse(base.id(), base.customerId(), base.accountNumber(), base.accountType(),
				base.accountSubType(), base.status(), base.currency(), base.nickname(), base.displayName(),
				base.balance(), base.maskedAccountNumber(), base.version(), available, holds);
	}

	private void ensureTransferableAmount(UUID accountId, BigDecimal balance, BigDecimal amount) {
		BigDecimal holds = activeHoldsTotal(accountId);
		BigDecimal available = balance.subtract(holds);
		if (amount.compareTo(available) > 0) {
			String detail = holds.signum() > 0
					? String.format(
							"Số dư: %s VND, đang giữ: %s VND, khả dụng: %s VND, yêu cầu: %s VND.",
							balance.toPlainString(), holds.toPlainString(), available.toPlainString(),
							amount.toPlainString())
					: String.format("Số khả dụng: %s VND, yêu cầu: %s VND.", available.toPlainString(),
							amount.toPlainString());
			throw new InsufficientFundsException(detail);
		}
	}

	private void ensureOwnerOrAdmin(Account a) {
		
		
		if (currentUser.hasScope("admin:accounts"))
			return;
		
		
		var me = currentUser.customerId().orElseThrow(() -> new OwnerAccessDeniedException());
		
		if (!a.getCustomerId().equals(me)) {
			throw new OwnerAccessDeniedException();
		}
	}

	private static String transferFingerprint(String transferId, String leg) {
		return "transfer:" + transferId + ":" + leg;
	}

	private void emitTransaction(Account acc, String type, BigDecimal amount, String reason, boolean posting,
			BigDecimal balanceAfterOrNull) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String fingerprint = DigestUtils
				.sha256Hex(acc.getId().toString() + type + amount.toPlainString() + reason + now.toString());
		emitTransaction(acc, type, amount, reason, posting, balanceAfterOrNull, fingerprint, null, now);
	}

	private void emitTransaction(Account acc, String type, BigDecimal amount, String reason, boolean posting,
			BigDecimal balanceAfterOrNull, String fingerprint, String referenceId, OffsetDateTime occurredAt) {
		String flow = inferFlowDirection(type);
		emitTransaction(acc, type, amount, reason, posting, balanceAfterOrNull, fingerprint, referenceId, occurredAt,
				flow, null, null);
	}

	private void emitTransaction(Account acc, String type, BigDecimal amount, String reason, boolean posting,
			BigDecimal balanceAfterOrNull, String fingerprint, String referenceId, OffsetDateTime occurredAt,
			String flowDirection, String counterpartyName, String counterpartyAccountNumber) {
		String currency = acc.getCurrency();
		TransactionRequest req = new TransactionRequest(acc.getId(), amount, currency, type, reason,
				posting ? balanceAfterOrNull : null, occurredAt);
		Transaction tx = transactionMapper.toEntity(req);
		tx.setRequestFingerprint(fingerprint);
		tx.setReferenceId(referenceId);
		tx.setFlowDirection(flowDirection);
		tx.setCounterpartyName(counterpartyName);
		tx.setCounterpartyAccountNumber(counterpartyAccountNumber);
		transactionService.save(tx);
	}

	private static String inferFlowDirection(String type) {
		if (type == null) return null;
		String t = type.toUpperCase();
		if (t.contains("CREDIT") || t.equals("CREDIT")) return "IN";
		if (t.contains("DEBIT") || t.equals("DEBIT") || t.contains("HOLD_PLACED")) return "OUT";
		if (t.contains("HOLD_RELEASED")) return "IN";
		return null;
	}

	/**
	 * Sinh số tài khoản duy nhất từ DB sequence, đảm bảo không collision kể cả dưới tải cao.
	 * Format: tiền tố BIN (1 chữ số) + 9 chữ số từ sequence = 10 chữ số tổng.
	 */
	private String generateAccountNumber() {
		Long nextVal = ((Number) entityManager
				.createNativeQuery("SELECT nextval('account_number_seq')")
				.getSingleResult()).longValue();
		return ACCOUNT_NUMBER_PREFIX + String.format("%09d", nextVal);
	}

	/* ---------------- Queries ---------------- */

	public List<AccountResponse> listAll() {
		return accountRepo.findAll().stream().map(mapper::toDto).toList();
	}

	public AccountResponse get(UUID id) {
		return mapper
				.toDto(accountRepo.findById(id).orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id)));
	}

	public AccountBalanceResponse getBalance(UUID id) {
		Account a = accountRepo.findById(id).orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id));
		ensureOwnerOrAdmin(a);
		BigDecimal holds = activeHoldsTotal(id);
		BigDecimal available = a.getBalance().subtract(holds);
		return new AccountBalanceResponse(a.getBalance(), holds, available);
	}

	/** NEW: used by GET /customer/{id}/accounts */
	public List<AccountResponse> findByCustomerId(String customerId) {
		return accountRepo.findByCustomerId(customerId).stream().map(mapper::toDto).toList();
	}

	public AccountResponse getMyAccount() {
		String customerId = currentUser.customerId()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Customer context required"));
		return accountRepo.findFirstByCustomerId(customerId)
				.map(this::withAvailability)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No bank account found for this customer. Please contact support."));
	}

	public AccountLookupResponse lookupByAccountNumber(String accountNumber) {
		String normalized = accountNumber == null ? "" : accountNumber.trim();
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Account number is required");
		}
		Account account = accountRepo.findByAccountNumber(normalized)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
		String displayName = resolveRecipientDisplayName(account);
		return new AccountLookupResponse(account.getAccountNumber(), displayName, account.getCurrency(), account.getStatus());
	}

	private String resolveRecipientDisplayName(Account account) {
		try {
			var profile = customerServiceClient.getByExternalId(account.getCustomerId());
			if (profile != null) {
				String first = profile.firstName() != null ? profile.firstName().trim() : "";
				String last = profile.lastName() != null ? profile.lastName().trim() : "";
				String fullName = (first + " " + last).trim();
				if (!fullName.isBlank()) {
					return fullName;
				}
			}
		} catch (Exception e) {
			log.debug("Could not resolve customer name for {}: {}", account.getCustomerId(), e.getMessage());
		}
		if (account.getDisplayName() != null && !account.getDisplayName().isBlank()
				&& !account.getDisplayName().toLowerCase().startsWith("tài khoản")) {
			return account.getDisplayName().trim();
		}
		if (account.getNickname() != null && !account.getNickname().isBlank()) {
			return account.getNickname().trim();
		}
		return "Người nhận";
	}

	private static String labelCounterparty(String reason) {
		if (reason == null || reason.isBlank()) {
			return "Giao dịch nội bộ";
		}
		return reason.trim();
	}

	@Transactional
	public AccountResponse provisionForCustomer(ProvisionAccountRequest request, String idempotencyKey) {
		Optional<Account> existing = accountRepo.findFirstByCustomerId(request.customerId());
		if (existing.isPresent()) {
			return mapper.toDto(existing.get());
		}
		String displayName = (request.displayName() != null && !request.displayName().isBlank())
				? request.displayName().trim()
				: "Tài khoản thanh toán";
		AccountRequest accountRequest = new AccountRequest(
				request.customerId(),
				AccountType.CHEQUING,
				AccountSubType.PERSONAL,
				AccountStatus.ACTIVE,
				"VND",
				"Main",
				displayName,
				BigDecimal.ZERO);
		return create(accountRequest, idempotencyKey);
	}

	@Transactional
	public InternalTransferResponse transferForCurrentUser(InternalTransferRequest request, String idempotencyKey) {
		AccountResponse mine = getMyAccount();
		InternalTransferRequest resolved = new InternalTransferRequest(
				mine.id(),
				request.toAccountNumber(),
				request.amount(),
				request.reason());
		return transfer(resolved, idempotencyKey);
	}

	/** NEW: used by PATCH /accounts/{id}/status */
	@Transactional
	public void updateStatus(UUID id, AccountStatus status) {
		Account a = accountRepo.findById(id).orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id));
		ensureOwnerOrAdmin(a);
		a.setStatus(status);
		accountRepo.save(a);
	}

	/** NEW: used by GET /accounts/{id}/owner */
	public String getCustomerIdForAccount(UUID id) {
		Account a = accountRepo.findById(id).orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id));
		ensureOwnerOrAdmin(a);
		return a.getCustomerId();
	}

	/* ---------------- Commands ---------------- */

	@Transactional
	public AccountResponse create(AccountRequest request, String idempotencyKey) {
		String fp = fingerprintForCreate(request, idempotencyKey);

		Optional<Account> existing = accountRepo.findByRequestFingerprint(fp);
		if (existing.isPresent()) {
			Account a = existing.get();
			return mapper.toDto(a);
		}
		if (accountRepo.findFirstByCustomerId(request.customerId()).isPresent()) {
			throw new IllegalArgumentException("Each customer can only own one account");
		}

		Account entity = mapper.toEntity(request);
		ensureOwnerOrAdmin(entity);
		entity.setRequestFingerprint(fp);
		entity.setBalance(request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance());

		// Sinh số tài khoản từ DB sequence để đảm bảo không collision dù dưới load cao
		entity.setAccountNumber(generateAccountNumber());

		return mapper.toDto(accountRepo.save(entity));
	}

	public AccountResponse credit(UUID id, PostingRequest r, Integer expectedVersion) {
		return credit(id, r, expectedVersion, null);
	}

	/**
	 * Cộng tiền với idempotency — tránh double-credit khi client/M2M retry.
	 */
	public AccountResponse credit(UUID id, PostingRequest r, Integer expectedVersion, String idempotencyKey) {
		return withOptimisticRetry(() -> transactionTemplate.execute(
				status -> creditInTransaction(id, r, expectedVersion, idempotencyKey)));
	}

	public AccountResponse debit(UUID id, PostingRequest r, Integer expectedVersion) {
		return debit(id, r, expectedVersion, null);
	}

	/**
	 * Trừ tiền khỏi tài khoản với hỗ trợ idempotency.
	 * Nếu idempotencyKey được cung cấp, kiểm tra xem giao dịch này đã được xử lý chưa.
	 * Đảm bảo Kafka consumer retry không gây double-debit.
	 */
	public AccountResponse debit(UUID id, PostingRequest r, Integer expectedVersion, String idempotencyKey) {
		return withOptimisticRetry(() -> transactionTemplate.execute(
				status -> debitInTransaction(id, r, expectedVersion, idempotencyKey)));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	AccountResponse creditInTransaction(UUID id, PostingRequest r, Integer expectedVersion, String idempotencyKey) {
		Account a = accountRepo.findByIdForUpdate(id)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id));
		ensureOwnerOrAdmin(a);

		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			String fp = idempotencyKey.trim();
			Optional<com.mockbank.account.transaction.model.Transaction> existing = transactionService.findByAccountAndFingerprint(id, fp);
			if (existing.isPresent()) {
				log.info("Credit idempotency replay: accountId={} fingerprint={}", id, fp);
				Account fresh = accountRepo.findById(id)
						.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id));
				return mapper.toDto(fresh);
			}
		}

		if (expectedVersion != null && !expectedVersion.equals(a.getVersion())) {
			throw new com.mockbank.commons.dto.exception.VersionMismatchException("ETag mismatch");
		}
		a.setBalance(a.getBalance().add(r.amount()));

		Account saved = accountRepo.saveAndFlush(a);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String fp = (idempotencyKey != null && !idempotencyKey.isBlank())
				? idempotencyKey.trim()
				: DigestUtils.sha256Hex(saved.getId() + "CREDIT" + r.amount() + r.reason() + now);
		UUID ledgerGroupId = ledgerGroupId(idempotencyKey);
		ledgerService.postTransferPair(ledgerGroupId, SYSTEM_SETTLEMENT_ACCOUNT_ID, saved.getId(),
				r.amount(), saved.getCurrency(), fp, currentActorId());
		emitTransaction(saved, "CREDIT", r.amount(), r.reason(), true, saved.getBalance(), fp, null, now,
				"IN", labelCounterparty(r.reason()), null);
		return mapper.toDto(saved);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	AccountResponse debitInTransaction(UUID id, PostingRequest r, Integer expectedVersion, String idempotencyKey) {
		Account a = accountRepo.findByIdForUpdate(id)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id));
		ensureOwnerOrAdmin(a);

		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			String fp = idempotencyKey.trim();
			Optional<com.mockbank.account.transaction.model.Transaction> existing = transactionService.findByAccountAndFingerprint(id, fp);
			if (existing.isPresent()) {
				log.info("Debit idempotency replay: accountId={} fingerprint={}", id, fp);
				Account fresh = accountRepo.findById(id)
						.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(id));
				return mapper.toDto(fresh);
			}
		}

		if (expectedVersion != null && !expectedVersion.equals(a.getVersion())) {
			throw new com.mockbank.commons.dto.exception.VersionMismatchException("ETag mismatch");
		}

		ensureTransferableAmount(id, a.getBalance(), r.amount());
		a.setBalance(a.getBalance().subtract(r.amount()));

		Account saved = accountRepo.saveAndFlush(a);

		String cpName = "BILLPAY".equalsIgnoreCase(r.reason()) ? "Thanh toán hóa đơn" : labelCounterparty(r.reason());
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String fp = (idempotencyKey != null && !idempotencyKey.isBlank())
				? idempotencyKey.trim()
				: DigestUtils.sha256Hex(saved.getId() + "DEBIT" + r.amount() + r.reason() + now);
		UUID ledgerGroupId = ledgerGroupId(idempotencyKey);
		ledgerService.postTransferPair(ledgerGroupId, saved.getId(), SYSTEM_SETTLEMENT_ACCOUNT_ID, r.amount(),
				saved.getCurrency(), fp, currentActorId());
		emitTransaction(saved, "DEBIT", r.amount(), r.reason(), true, saved.getBalance(), fp, null, now,
				"OUT", cpName, null);
		return mapper.toDto(saved);
	}

	public InternalTransferResponse transfer(InternalTransferRequest r, String idempotencyKey) {
		if (r.amount() == null || r.amount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Transfer amount must be positive");
		}
		if (r.amount().compareTo(maxTransferAmount) > 0) {
			throw new IllegalArgumentException("Transfer amount exceeds maximum limit of " + maxTransferAmount);
		}
		String targetAccountNumber = r.toAccountNumber().trim();
		Account targetPreview = accountRepo.findByAccountNumber(targetAccountNumber)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(
						"Destination account not found: " + targetAccountNumber));
		Account fromPreview = accountRepo.findById(r.fromAccountId())
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(r.fromAccountId()));
		String toLabel = resolveRecipientDisplayName(targetPreview);
		String fromLabel = resolveRecipientDisplayName(fromPreview);
		return withOptimisticRetry(() -> transactionTemplate.execute(
				status -> transferInTransaction(r, idempotencyKey, toLabel, fromLabel)));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	InternalTransferResponse transferInTransaction(InternalTransferRequest request, String idempotencyKey,
			String toLabel, String fromLabel) {
		if (request.fromAccountId() == null) {
			throw new com.mockbank.commons.dto.exception.BadRequestException("Source account id is required");
		}
		String targetAccountNumber = request.toAccountNumber().trim();
		Account target = accountRepo.findByAccountNumber(targetAccountNumber)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(
						"Destination account not found: " + targetAccountNumber));

		String transferId = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim()
				: UUID.randomUUID().toString();
		String debitFingerprint = transferFingerprint(transferId, "DEBIT");
		String creditFingerprint = transferFingerprint(transferId, "CREDIT");

		if (request.fromAccountId().equals(target.getId())) {
			throw new com.mockbank.commons.dto.exception.BadRequestException("Source and destination account must be different");
		}

		// Idempotency replay trước khi lock — tránh giữ lock không cần thiết
		Optional<Transaction> replay = transactionService.findByAccountAndFingerprint(request.fromAccountId(),
				debitFingerprint);
		if (replay.isPresent()) {
			Account from = accountRepo.findById(request.fromAccountId())
					.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(request.fromAccountId()));
			ensureOwnerOrAdmin(from);
			return new InternalTransferResponse(transferId, from.getId(), target.getId(), request.amount(),
					from.getCurrency(), from.getBalance(), target.getBalance(), replay.get().getOccurredAt());
		}

		// Acquire pessimistic locks theo thứ tự nhất định để tránh deadlock
		UUID firstLockId = request.fromAccountId().compareTo(target.getId()) <= 0 ? request.fromAccountId()
				: target.getId();
		UUID secondLockId = firstLockId.equals(request.fromAccountId()) ? target.getId() : request.fromAccountId();

		Account first = accountRepo.findByIdForUpdate(firstLockId)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(firstLockId));
		Account second = accountRepo.findByIdForUpdate(secondLockId)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(secondLockId));

		Account from = first.getId().equals(request.fromAccountId()) ? first : second;
		Account to = first.getId().equals(target.getId()) ? first : second;

		// Re-check fingerprint SAU KHI đã lock để tránh TOCTOU
		replay = transactionService.findByAccountAndFingerprint(request.fromAccountId(), debitFingerprint);
		if (replay.isPresent()) {
			ensureOwnerOrAdmin(from);
			return new InternalTransferResponse(transferId, from.getId(), to.getId(), request.amount(), from.getCurrency(),
					from.getBalance(), to.getBalance(), replay.get().getOccurredAt());
		}

		ensureOwnerOrAdmin(from);

		if (from.getStatus() != AccountStatus.ACTIVE || to.getStatus() != AccountStatus.ACTIVE) {
			throw new com.mockbank.commons.dto.exception.InvalidTransitionException(
					"Both accounts must be ACTIVE to transfer. from=" + from.getStatus() + " to=" + to.getStatus());
		}
		if (!from.getCurrency().equalsIgnoreCase(to.getCurrency())) {
			throw new com.mockbank.commons.dto.exception.BadRequestException(
					"Currency mismatch between source and destination account");
		}

		ensureTransferableAmount(from.getId(), from.getBalance(), request.amount());

		from.setBalance(from.getBalance().subtract(request.amount()));
		to.setBalance(to.getBalance().add(request.amount()));

		Account fromSaved = accountRepo.saveAndFlush(from);
		Account toSaved = accountRepo.saveAndFlush(to);
		OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);

		emitTransaction(fromSaved, "TRANSFER_DEBIT", request.amount(), request.reason(), true, fromSaved.getBalance(),
				debitFingerprint, transferId, occurredAt, "OUT", toLabel, to.getAccountNumber());
		emitTransaction(toSaved, "TRANSFER_CREDIT", request.amount(), request.reason(), true, toSaved.getBalance(),
				creditFingerprint, transferId, occurredAt, "IN", fromLabel, from.getAccountNumber());
		ledgerService.postTransferPair(ledgerGroupId(transferId), fromSaved.getId(), toSaved.getId(), request.amount(),
				fromSaved.getCurrency(), transferId, currentActorId());

		return new InternalTransferResponse(transferId, fromSaved.getId(), toSaved.getId(), request.amount(),
				fromSaved.getCurrency(), fromSaved.getBalance(), toSaved.getBalance(), occurredAt);
	}

	public HoldResponse createHold(UUID accountId, CreateHoldRequest r) {
		return withOptimisticRetry(() -> transactionTemplate.execute(
				status -> createHoldInTransaction(accountId, r)));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	HoldResponse createHoldInTransaction(UUID accountId, CreateHoldRequest r) {
		Account a = accountRepo.findByIdForUpdate(accountId)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(accountId));
		ensureOwnerOrAdmin(a);

		String fp = (r.idempotencyKey() != null && !r.idempotencyKey().isBlank()) ? r.idempotencyKey().trim() : null;
		if (fp != null) {
			Optional<AccountHold> ex = holdRepo.findByRequestFingerprint(fp);
			if (ex.isPresent()) {
				AccountHold h = ex.get();
				return new HoldResponse(h.getId(), h.getAmount(), h.getStatus(), h.getCreatedAt(), h.getReleaseAt());
			}
		}

		ensureTransferableAmount(accountId, a.getBalance(), r.amount());

		java.time.LocalDateTime releaseAt = r.releaseAt();
		if (releaseAt == null && r.reason() != null && r.reason().toUpperCase().contains("BILLPAY")) {
			releaseAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(15);
		}
		AccountHold h = AccountHold.builder().accountId(accountId).amount(r.amount()).status(HoldStatus.ACTIVE)
				.reason(r.reason()).releaseAt(releaseAt).requestFingerprint(fp).build();

		h = holdRepo.save(h);
		emitTransaction(a, "HOLD_PLACED", r.amount(), r.reason(), true, a.getBalance());

		return new HoldResponse(h.getId(), h.getAmount(), h.getStatus(), h.getCreatedAt(), h.getReleaseAt());
	}

	/**
	 * Chuyển hold thành debit trong cùng một transaction DB — tránh release rồi debit
	 * (cửa sổ race khiến số dư khả dụng bị tiêu bởi giao dịch khác).
	 */
	public AccountResponse captureHoldAndDebit(UUID accountId, UUID holdId, PostingRequest r,
			String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new com.mockbank.commons.dto.exception.BadRequestException("Idempotency-Key là bắt buộc khi capture hold");
		}
		return withOptimisticRetry(() -> transactionTemplate.execute(
				status -> captureHoldAndDebitInTransaction(accountId, holdId, r, idempotencyKey.trim())));
	}

	public HoldResponse releaseHold(UUID accountId, UUID holdId, String reason) {
		return withOptimisticRetry(() -> transactionTemplate.execute(
				status -> releaseHoldInTransaction(accountId, holdId, reason)));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	AccountResponse captureHoldAndDebitInTransaction(UUID accountId, UUID holdId, PostingRequest r,
			String idempotencyKey) {
		String fp = idempotencyKey;
		Optional<com.mockbank.account.transaction.model.Transaction> existing = transactionService.findByAccountAndFingerprint(accountId, fp);
		if (existing.isPresent()) {
			log.info("Capture-hold idempotency replay: accountId={} holdId={} fingerprint={}", accountId, holdId, fp);
			Account fresh = accountRepo.findById(accountId)
					.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(accountId));
			return mapper.toDto(fresh);
		}

		Account a = accountRepo.findByIdForUpdate(accountId)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(accountId));
		AccountHold h = holdRepo.findById(holdId)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.ResourceNotFoundException("hold", holdId));
		if (!h.getAccountId().equals(accountId)) {
			throw new com.mockbank.commons.dto.exception.BadRequestException("Hold does not belong to this account");
		}
		if (h.getStatus() == HoldStatus.CAPTURED) {
			Account fresh = accountRepo.findById(accountId)
					.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(accountId));
			return mapper.toDto(fresh);
		}
		if (h.getStatus() != HoldStatus.ACTIVE) {
			throw new com.mockbank.commons.dto.exception.InvalidTransitionException(
					"Hold must be ACTIVE to capture. status=" + h.getStatus());
		}
		if (r.amount().compareTo(h.getAmount()) > 0) {
			throw new com.mockbank.commons.dto.exception.BadRequestException(
					"Debit amount cannot exceed held amount");
		}
		if (a.getBalance().compareTo(r.amount()) < 0) {
			throw new InsufficientFundsException("Insufficient balance to capture hold");
		}

		// Đánh dấu hold đã tiêu thụ trước khi trừ balance — vẫn trong cùng lock account
		h.setStatus(HoldStatus.CAPTURED);
		h.setReason("captured");
		holdRepo.save(h);

		a.setBalance(a.getBalance().subtract(r.amount()));
		Account saved = accountRepo.saveAndFlush(a);

		String cpName = "BILLPAY".equalsIgnoreCase(r.reason()) ? "Thanh toán hóa đơn" : labelCounterparty(r.reason());
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID ledgerGroupId = ledgerGroupId(idempotencyKey);
		ledgerService.postTransferPair(ledgerGroupId, saved.getId(), SYSTEM_SETTLEMENT_ACCOUNT_ID, r.amount(),
				saved.getCurrency(), fp, currentActorId());
		emitTransaction(saved, "DEBIT", r.amount(), r.reason(), true, saved.getBalance(), fp, null, now,
				"OUT", cpName, null);
		return mapper.toDto(saved);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	HoldResponse releaseHoldInTransaction(UUID accountId, UUID holdId, String reason) {
		Account a = accountRepo.findByIdForUpdate(accountId)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.AccountNotFoundException(accountId));
		AccountHold h = holdRepo.findById(holdId)
				.orElseThrow(() -> new com.mockbank.commons.dto.exception.ResourceNotFoundException("hold", holdId));
		if (!h.getAccountId().equals(accountId)) {
			throw new com.mockbank.commons.dto.exception.BadRequestException("Hold does not belong to this account");
		}
		if (h.getStatus() != HoldStatus.ACTIVE) {
			return new HoldResponse(h.getId(), h.getAmount(), h.getStatus(), h.getCreatedAt(), h.getReleaseAt());
		}

		h.setStatus(HoldStatus.RELEASED);
		h.setReason(reason);
		h = holdRepo.save(h);

		emitTransaction(a, "HOLD_RELEASED", h.getAmount(), reason, true, a.getBalance());

		return new HoldResponse(h.getId(), h.getAmount(), h.getStatus(), h.getCreatedAt(), h.getReleaseAt());
	}

	/**
	 * Retry khi optimistic lock conflict — tránh lost update trên balance.
	 */
	private <T> T withOptimisticRetry(Supplier<T> action) {
		for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRIES; attempt++) {
			try {
				return action.get();
			} catch (OptimisticLockingFailureException ex) {
				if (attempt >= MAX_OPTIMISTIC_RETRIES) {
					throw new com.mockbank.commons.dto.exception.VersionMismatchException(
							"Giao dịch bị xung đột phiên bản. Vui lòng tải lại và thử lại.");
				}
				sleepBackoff(attempt);
			}
		}
		throw new IllegalStateException("unreachable");
	}

	private static void sleepBackoff(int attempt) {
		try {
			Thread.sleep(OPTIMISTIC_BACKOFF_MS * attempt);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new com.mockbank.commons.dto.exception.VersionMismatchException("Giao dịch bị gián đoạn");
		}
	}

	private static UUID ledgerGroupId(String idempotencyKey) {
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			return UUID.nameUUIDFromBytes(idempotencyKey.trim().getBytes(StandardCharsets.UTF_8));
		}
		return UUID.randomUUID();
	}

	private String currentActorId() {
		return currentUser.customerId().orElse("SYSTEM");
	}
}
