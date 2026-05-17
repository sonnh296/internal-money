package com.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.account.dto.AccountStatus;
import com.account.dto.AccountSubType;
import com.account.dto.AccountType;
import com.account.dto.CreateHoldRequest;
import com.account.dto.HoldStatus;
import com.account.mapper.AccountMapper;
import com.account.mapper.TransactionMapper;
import com.account.model.Account;
import com.account.model.AccountHold;
import com.account.repository.AccountHoldRepository;
import com.account.repository.AccountRepository;
import com.commons.exception.InsufficientFundsException;
import com.commons.security.CurrentUser;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class AccountServiceHoldConcurrencyTest {

    private AccountRepository accountRepo;
    private AccountHoldRepository holdRepo;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountRepo = Mockito.mock(AccountRepository.class);
        holdRepo = Mockito.mock(AccountHoldRepository.class);
        TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });

        CurrentUser currentUser = Mockito.mock(CurrentUser.class);
        when(currentUser.hasScope("admin:accounts")).thenReturn(true);
        TransactionMapper transactionMapper = Mockito.mock(TransactionMapper.class);
        TransactionService transactionService = Mockito.mock(TransactionService.class);
        when(transactionMapper.toEntity(any())).thenReturn(com.account.model.Transaction.builder().build());
        when(transactionService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new AccountService(
                accountRepo,
                holdRepo,
                Mockito.mock(AccountMapper.class),
                transactionService,
                transactionMapper,
                Mockito.mock(com.account.client.CustomerServiceClient.class),
                Mockito.mock(EntityManager.class),
                transactionTemplate,
                Mockito.mock(TransactionLedgerService.class),
                currentUser);

        when(holdRepo.findByRequestFingerprint(any())).thenReturn(Optional.empty());
    }

    @Test
    void concurrentHoldsShouldNotExceedAvailableBalance() throws Exception {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .customerId("cust-1")
                .balance(new BigDecimal("100.00"))
                .currency("VND")
                .status(AccountStatus.ACTIVE)
                .accountType(AccountType.CHEQUING)
                .accountSubType(AccountSubType.PERSONAL)
                .build();

        AtomicInteger activeHolds = new AtomicInteger(0);
        when(accountRepo.findByIdForUpdate(accountId)).thenAnswer(inv -> {
            Account copy = Account.builder()
                    .id(accountId)
                    .customerId("cust-1")
                    .balance(new BigDecimal("100.00"))
                    .currency("VND")
                    .status(AccountStatus.ACTIVE)
                    .accountType(AccountType.CHEQUING)
                    .accountSubType(AccountSubType.PERSONAL)
                    .build();
            return Optional.of(copy);
        });
        when(holdRepo.findByAccountIdAndStatus(eq(accountId), eq(HoldStatus.ACTIVE)))
                .thenAnswer(inv -> {
                    int holds = activeHolds.get();
                    if (holds == 0) {
                        return List.of();
                    }
                    return List.of(AccountHold.builder()
                            .amount(new BigDecimal("80.00"))
                            .status(HoldStatus.ACTIVE)
                            .build());
                });
        when(holdRepo.save(any())).thenAnswer(inv -> {
            activeHolds.incrementAndGet();
            AccountHold h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        CreateHoldRequest req = new CreateHoldRequest(new BigDecimal("80.00"), "BILLPAY", null, "hold-1");
        service.createHold(accountId, req);

        assertThrows(InsufficientFundsException.class,
                () -> service.createHold(accountId, new CreateHoldRequest(
                        new BigDecimal("80.00"), "BILLPAY", null, "hold-2")));
    }
}
