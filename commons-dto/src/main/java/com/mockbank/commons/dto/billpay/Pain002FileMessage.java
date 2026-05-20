package com.mockbank.commons.dto.billpay;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.mockbank.commons.dto.events.billpay.Pain002Message;

public record Pain002FileMessage(
        UUID batchId,
        List<Pain002Message> items,
        OffsetDateTime generatedAt
) {}