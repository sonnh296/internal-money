package com.mockbank.account.management.service;

import com.mockbank.commons.dto.account.InternalTransferRequest;
import com.mockbank.account.management.model.Account;
import com.mockbank.account.management.model.AccountStatus;
import com.mockbank.account.management.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
public class AccountServiceTransferConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("accountsdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private UUID accountA;
    private UUID accountB;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        Account a = Account.builder()
                .accountId(UUID.randomUUID())
                .customerId("CUST-1")
                .currency("VND")
                .balance(new BigDecimal("1000000.0000"))
                .status(AccountStatus.ACTIVE)
                .build();
        accountA = accountRepository.save(a).getAccountId();

        Account b = Account.builder()
                .accountId(UUID.randomUUID())
                .customerId("CUST-2")
                .currency("VND")
                .balance(new BigDecimal("1000000.0000"))
                .status(AccountStatus.ACTIVE)
                .build();
        accountB = accountRepository.save(b).getAccountId();
    }

    @Test
    void testConcurrentTransfers() throws InterruptedException {
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    // Each thread sends 1000 VND
                    InternalTransferRequest req = new InternalTransferRequest(
                            accountA, accountB, new BigDecimal("1000.0000"), "VND", "Test Transfer " + index
                    );
                    accountService.transfer(req, "idem-test-transfer-" + index);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Transfer failed: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown(); // start all threads at once
        done.await(30, TimeUnit.SECONDS);

        assertEquals(100, successCount.get(), "All transfers should succeed");

        Account finalA = accountRepository.findByAccountId(accountA).orElseThrow();
        Account finalB = accountRepository.findByAccountId(accountB).orElseThrow();

        assertEquals(new BigDecimal("900000.0000"), finalA.getBalance());
        assertEquals(new BigDecimal("1100000.0000"), finalB.getBalance());
    }
}
