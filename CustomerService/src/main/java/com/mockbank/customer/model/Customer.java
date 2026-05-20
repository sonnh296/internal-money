package com.mockbank.customer.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "customers",
  uniqueConstraints = {
    @UniqueConstraint(name="uniq_customers_email", columnNames = "email"),
    @UniqueConstraint(name="uniq_customers_external_id", columnNames = "external_id")
})
public class Customer {


	  @Id @GeneratedValue(strategy = GenerationType.UUID)
	  private UUID id;

	  @Version
	  @Column(name = "version", nullable = false)
	  private Integer version; // optimistic concurrency

	  @Column(name="first_name", nullable=false) private String firstName;
	  @Column(name="last_name",  nullable=false) private String lastName;
	  @Column(name="email",      nullable=false) private String email;
	  @Column(name="phone")      private String phone;
	  @Column(name="address",    nullable=false) private String address;

	  @Column(name="external_id", nullable=false, unique=true)
	  private String externalId; // treat as idempotency key for Create

	  @Enumerated(EnumType.STRING)
	  @Column(name="kyc_status", nullable=false)
	  private KycStatus kycStatus;

	  @Column(name="active", nullable=false) private Boolean active;

	  @Column(name="request_fingerprint", nullable=false)
	  private String requestFingerprint;

	  @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
	  @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;

	  @PrePersist
	  void prePersist() {
	    this.createdAt = this.updatedAt = LocalDateTime.now();
	  }
	  @PreUpdate
	  void preUpdate() {
	    this.updatedAt = LocalDateTime.now();
	  }
}
