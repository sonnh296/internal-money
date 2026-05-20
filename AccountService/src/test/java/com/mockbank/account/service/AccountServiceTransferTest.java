package com.mockbank.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mockbank.account.client.CustomerServiceClient;
import com.mockbank.commons.dto.account.AccountStatus;
import com.mockbank.commons.dto.account.AccountSubType;
import com.mockbank.commons.dto.account.AccountType;
import com.mockbank.commons.dto.account.HoldStatus;
import com.mockbank.commons.dto.account.InternalTransferRequest;
import com.mockbank.commons.dto.account.AccountRequest;
import com.mockbank.account.mapper.AccountMapper;
import com.mockbank.account.mapper.TransactionMapper;
import com.mockbank.account.model.Account;
import com.mockbank.account.model.Transaction;
import com.mockbank.account.repository.AccountHoldRepository;
import com.mockbank.account.repository.AccountRepository;
import com.mockbank.commons.dto.exception.InsufficientFundsException;
import com.mockbank.commons.security.CurrentUser;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class AccountServiceTransferTest {

    private AccountRepository accountRepo;
    private AccountHoldRepository holdRepo;
    private AccountMapper accountMapper;
    private TransactionService transactionService;
    private TransactionMapper transactionMapper;
    private CurrentUser currentUser;
    private CustomerServiceClient customerServiceClient;
    private EntityManager entityManager;
    private TransactionTemplate transactionTemplate;
    private TransactionLedgerService ledgerService;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountRepo = Mockito.mock(AccountRepository.class);
        holdRepo = Mockito.mock(AccountHoldRepository.class);
        accountMapper = Mockito.mock(AccountMapper.class);
        transactionService = Mockito.mock(TransactionService.class);
        transactionMapper = Mockito.mock(TransactionMapper.class);
        currentUser = Mockito.mock(CurrentUser.class);
        customerServiceClient = Mockito.mock(CustomerServiceClient.class);
        entityManager = Mockito.mock(EntityManager.class);
        transactionTemplate = Mockito.mock(TransactionTemplate.class);
        ledgerService = Mockito.mock(TransactionLedgerService.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });

        service = new AccountService(accountRepo, holdRepo, accountMapper, transactionService, transactionMapper,
                customerServiceClient, entityManager, transactionTemplate, ledgerService, currentUser);
        when(currentUser.customerId()).thenReturn(Optional.of("cust-1"));
        when(transactionMapper.toEntity(any())).thenAnswer(invocation -> Transaction.builder().build());
        when(holdRepo.findByAccountIdAndStatus(any(), eq(HoldStatus.ACTIVE))).thenReturn(List.of());
        when(accountRepo.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void transferShouldMoveBalanceAtomicallyAndWriteTwoTransactions() {
        UUID fromId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID toId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String idempotencyKey = "transfer-001";

        Account from = Account.builder()
                .id(fromId)
                .customerId("cust-1")
                .accountNumber("900000001")
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("120.00"))
                .build();
        Account to = Account.builder()
                .id(toId)
                .customerId("cust-2")
                .accountNumber("900000002")
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("40.00"))
                .build();

        when(currentUser.hasScope("admin:accounts")).thenReturn(false);
        when(currentUser.customerId()).thenReturn(Optional.of("cust-1"));
        when(accountRepo.findByAccountNumber("900000002")).thenReturn(Optional.of(to));
        when(transactionService.findByAccountAndFingerprint(fromId, "transfer:transfer-001:DEBIT")).thenReturn(Optional.empty());
        when(accountRepo.findByIdForUpdate(fromId)).thenReturn(Optional.of(from));
        when(accountRepo.findByIdForUpdate(toId)).thenReturn(Optional.of(to));
        when(transactionService.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.transfer(
                new InternalTransferRequest(fromId, to.getAccountNumber(), new BigDecimal("20.00"), "p2p-test"),
                idempotencyKey);

        assertEquals("transfer-001", response.transferId());
        assertEquals(new BigDecimal("100.00"), response.fromBalanceAfter());
        assertEquals(new BigDecimal("60.00"), response.toBalanceAfter());
        assertEquals("CAD", response.currency());
        assertNotNull(response.occurredAt());
        verify(transactionService, Mockito.times(2)).save(any(Transaction.class));
    }

    @Test
    void transferShouldReturnReplayWhenIdempotencyKeyAlreadyProcessed() {
        UUID fromId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID toId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String idempotencyKey = "transfer-001";

        Account from = Account.builder()
                .id(fromId)
                .customerId("cust-1")
                .accountNumber("900000001")
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("100.00"))
                .build();
        Account to = Account.builder()
                .id(toId)
                .customerId("cust-2")
                .accountNumber("900000002")
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("60.00"))
                .build();

        Transaction existing = Transaction.builder()
                .referenceId("transfer-001")
                .occurredAt(OffsetDateTime.now())
                .build();

        when(currentUser.hasScope("admin:accounts")).thenReturn(false);
        when(currentUser.customerId()).thenReturn(Optional.of("cust-1"));
        when(accountRepo.findByAccountNumber("900000002")).thenReturn(Optional.of(to));
        when(transactionService.findByAccountAndFingerprint(fromId, "transfer:transfer-001:DEBIT"))
                .thenReturn(Optional.of(existing));
        when(accountRepo.findById(fromId)).thenReturn(Optional.of(from));

        var response = service.transfer(
                new InternalTransferRequest(fromId, to.getAccountNumber(), new BigDecimal("20.00"), "p2p-test"),
                idempotencyKey);

        assertEquals("transfer-001", response.transferId());
        assertEquals(new BigDecimal("100.00"), response.fromBalanceAfter());
        assertEquals(new BigDecimal("60.00"), response.toBalanceAfter());
        verify(accountRepo, never()).findByIdForUpdate(any());
        verify(transactionService, never()).save(any());
    }

    @Test
    void transferShouldFailWhenAvailableBalanceIsInsufficient() {
        UUID fromId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID toId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        Account from = Account.builder()
                .id(fromId)
                .customerId("cust-1")
                .accountNumber("900000011")
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10.00"))
                .build();
        Account to = Account.builder()
                .id(toId)
                .customerId("cust-2")
                .accountNumber("900000022")
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("50.00"))
                .build();

        when(currentUser.hasScope("admin:accounts")).thenReturn(false);
        when(currentUser.customerId()).thenReturn(Optional.of("cust-1"));
        when(accountRepo.findByAccountNumber("900000022")).thenReturn(Optional.of(to));
        when(transactionService.findByAccountAndFingerprint(any(), any())).thenReturn(Optional.empty());
        when(accountRepo.findByIdForUpdate(fromId)).thenReturn(Optional.of(from));
        when(accountRepo.findByIdForUpdate(toId)).thenReturn(Optional.of(to));

        assertThrows(InsufficientFundsException.class, () -> service.transfer(
                new InternalTransferRequest(fromId, "900000022", new BigDecimal("20.00"), "p2p-test"),
                "transfer-011"));
    }

    @Test
    void creditIdempotencyReplayShouldReturnCurrentBalanceFromDatabase() {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000088");
        Account account = Account.builder()
                .id(accountId)
                .customerId("cust-1")
                .balance(new BigDecimal("120.00"))
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .build();
        com.mockbank.account.model.Transaction existing = com.mockbank.account.model.Transaction.builder()
                .requestFingerprint("credit-idem-1")
                .build();

        when(currentUser.hasScope("admin:accounts")).thenReturn(true);
        when(accountRepo.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(transactionService.findByAccountAndFingerprint(accountId, "credit-idem-1"))
                .thenReturn(Optional.of(existing));
        when(accountRepo.findById(accountId)).thenReturn(Optional.of(
                Account.builder().id(accountId).customerId("cust-1").balance(new BigDecimal("120.00"))
                        .currency("CAD").status(AccountStatus.ACTIVE).version(2).build()));

        var response = service.credit(accountId, new com.mockbank.commons.dto.account.PostingRequest(new BigDecimal("20.00"), "test"),
                null, "credit-idem-1");

        assertEquals(new BigDecimal("120.00"), response.balance());
        verify(accountRepo, never()).saveAndFlush(any());
    }

    @Test
    void debitIdempotencyReplayShouldReturnCurrentBalanceFromDatabase() {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Account account = Account.builder()
                .id(accountId)
                .customerId("cust-1")
                .balance(new BigDecimal("80.00"))
                .currency("CAD")
                .status(AccountStatus.ACTIVE)
                .build();
        com.mockbank.account.model.Transaction existing = com.mockbank.account.model.Transaction.builder()
                .requestFingerprint("debit-idem-1")
                .build();

        when(currentUser.hasScope("admin:accounts")).thenReturn(true);
        when(accountRepo.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(transactionService.findByAccountAndFingerprint(accountId, "debit-idem-1"))
                .thenReturn(Optional.of(existing));
        when(accountRepo.findById(accountId)).thenReturn(Optional.of(
                Account.builder().id(accountId).customerId("cust-1").balance(new BigDecimal("80.00"))
                        .currency("CAD").status(AccountStatus.ACTIVE).version(2).build()));

        var response = service.debit(accountId, new com.mockbank.commons.dto.account.PostingRequest(new BigDecimal("20.00"), "test"),
                null, "debit-idem-1");

        assertEquals(new BigDecimal("80.00"), response.balance());
        verify(accountRepo, never()).saveAndFlush(any());
    }

    @Test
    void createShouldRejectWhenCustomerAlreadyHasAccount() {
        var request = new AccountRequest(
                "cust-1",
                AccountType.CHEQUING,
                AccountSubType.PERSONAL,
                AccountStatus.ACTIVE,
                "CAD",
                "Main",
                "Primary account",
                BigDecimal.ZERO);
        when(accountRepo.findByRequestFingerprint(any())).thenReturn(Optional.empty());
        when(accountRepo.findFirstByCustomerId("cust-1")).thenReturn(Optional.of(Account.builder().id(UUID.randomUUID()).build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(request, "idem-1"));
        assertEquals("Each customer can only own one account", ex.getMessage());
    }
}
