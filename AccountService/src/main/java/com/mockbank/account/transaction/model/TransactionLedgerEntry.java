package com.mockbank.account.transaction.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transaction_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLedgerEntry {

	public enum EntryType {
		DEBIT, CREDIT
	}

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID transactionGroupId;

	@Column(nullable = false)
	private UUID accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private EntryType entryType;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(length = 32)
	private String referenceType;

	@Column(length = 64)
	private String referenceId;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	@Column(length = 255)
	private String createdBy;
}
