package com.mockbank.account.management.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(length = 128, nullable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String serviceName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column
    private Integer responseStatus;

    @Column(columnDefinition = "TEXT")
    private String responseSnapshot;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
