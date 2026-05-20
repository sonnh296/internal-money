package com.mockbank.account.transaction.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mockbank.account.transaction.model.TransactionLedgerEntry;

public interface TransactionLedgerRepository extends JpaRepository<TransactionLedgerEntry, UUID> {
}
