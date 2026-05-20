package com.mockbank.account.transaction.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mockbank.account.transaction.model.TransactionLedgerEntry;
import com.mockbank.account.transaction.model.TransactionLedgerEntry.EntryType;
import com.mockbank.account.transaction.repository.TransactionLedgerRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ghi sổ cái double-entry: mỗi nhóm luôn có ít nhất một DEBIT và một CREDIT cùng số tiền.
 */
@Service
@RequiredArgsConstructor
public class TransactionLedgerService {

	private final TransactionLedgerRepository ledgerRepo;

	@Transactional(propagation = Propagation.MANDATORY)
	public void postTransferPair(UUID groupId, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
			String currency, String referenceId, String createdBy) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		ledgerRepo.save(entry(groupId, fromAccountId, EntryType.DEBIT, amount, currency, "TRANSFER", referenceId,
				createdBy, now));
		ledgerRepo.save(entry(groupId, toAccountId, EntryType.CREDIT, amount, currency, "TRANSFER", referenceId,
				createdBy, now));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void postSingleSided(UUID groupId, UUID accountId, EntryType entryType, BigDecimal amount, String currency,
			String referenceType, String referenceId, String createdBy) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		ledgerRepo.save(entry(groupId, accountId, entryType, amount, currency, referenceType, referenceId, createdBy,
				now));
	}

	private static TransactionLedgerEntry entry(UUID groupId, UUID accountId, EntryType entryType, BigDecimal amount,
			String currency, String referenceType, String referenceId, String createdBy, OffsetDateTime createdAt) {
		return TransactionLedgerEntry.builder()
				.transactionGroupId(groupId)
				.accountId(accountId)
				.entryType(entryType)
				.amount(amount)
				.currency(currency)
				.referenceType(referenceType)
				.referenceId(referenceId)
				.createdBy(createdBy)
				.createdAt(createdAt)
				.build();
	}
}
