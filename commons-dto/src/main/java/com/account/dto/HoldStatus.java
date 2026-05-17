package com.account.dto;

public enum HoldStatus {
    ACTIVE, RELEASED, CANCELED, EXPIRED,
    /** Hold đã được chuyển thành debit — không còn chiếm số dư khả dụng */
    CAPTURED
}
