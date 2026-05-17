package com.account.management.controller;

import com.account.dto.AccountBalanceResponse;
import com.account.dto.AccountLookupResponse;
import com.account.dto.AccountOwnerResponse;
import com.account.dto.AccountRequest;
import com.account.dto.AccountResponse;
import com.account.dto.AccountStatus;
import com.account.dto.CreateHoldRequest;
import com.account.dto.HoldResponse;
import com.account.dto.InternalTransferRequest;
import com.account.dto.InternalTransferResponse;
import com.account.dto.PostingRequest;
import com.account.dto.ProvisionAccountRequest;
import com.account.management.service.AccountService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class AccountController {

    /** Prevents literal paths like /accounts/me from matching UUID handlers. */
    private static final String ACCOUNT_ID = "[0-9a-fA-F\\-]{36}";
    private static final String HOLD_ID = "[0-9a-fA-F\\-]{36}";

    private final AccountService service;

    /* ---------------- Accounts ---------------- */

    @GetMapping("/accounts/me")
    @PreAuthorize("hasAuthority('SCOPE_fdx:accounts.read')")
    public ResponseEntity<AccountResponse> getMyAccount() {
        return ResponseEntity.ok(service.getMyAccount());
    }

    @GetMapping("/accounts/lookup")
    @PreAuthorize("hasAuthority('SCOPE_fdx:accounts.read')")
    public ResponseEntity<AccountLookupResponse> lookup(
            @RequestParam("accountNumber") String accountNumber) {
        return ResponseEntity.ok(service.lookupByAccountNumber(accountNumber));
    }

    @PostMapping("/internal/accounts/provision")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<AccountResponse> provision(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProvisionAccountRequest request) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : "provision-" + request.customerId();
        AccountResponse resp = service.provisionForCustomer(request, key);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAnyAuthority('SCOPE_fdx:accounts.write','SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<AccountResponse> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AccountRequest request) {
        AccountResponse resp = service.create(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag('"' + String.valueOf(resp.version()) + '"')
                .body(resp);
    }

    @GetMapping("/accounts/{id:" + ACCOUNT_ID + "}")
    @PreAuthorize("hasAuthority('SCOPE_fdx:accounts.read')")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("id") UUID id) {
        AccountResponse r = service.get(id);
        return ResponseEntity.ok()
                .eTag('"' + String.valueOf(r.version()) + '"')
                .body(r);
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('SCOPE_admin:accounts.read')")
    public ResponseEntity<List<AccountResponse>> listAccounts(
            @RequestParam(name = "status", required = false) AccountStatus status,
            @RequestParam(name = "currency", required = false) String currency) {
        // Simple version: return all. (Optional: add filters in service later.)
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/accounts/{id:" + ACCOUNT_ID + "}/balance")
    @PreAuthorize("hasAuthority('SCOPE_fdx:accounts.read')")
    public ResponseEntity<AccountBalanceResponse> getBalances(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.getBalance(id));
    }

    @GetMapping("/customer/{id}/accounts")
    @PreAuthorize("hasAuthority('SCOPE_fdx:accounts.read')")
    public ResponseEntity<List<AccountResponse>> getByCustomer(@PathVariable("id") String id) {
        log.info("Fetching accounts for customer: {}", id);
        return ResponseEntity.ok(service.findByCustomerId(id));
    }

    @PatchMapping("/accounts/{id:" + ACCOUNT_ID + "}/status")
    @PreAuthorize("hasAuthority('SCOPE_admin:accounts.write')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable("id") UUID id,
            @RequestParam("status") AccountStatus status) {
        service.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/accounts/{id:" + ACCOUNT_ID + "}/owner")
    @PreAuthorize("hasAnyAuthority('SCOPE_fdx:accounts.read','SCOPE_admin:accounts')")
    public AccountOwnerResponse getAccountOwner(@PathVariable("id") UUID id) {
        String customerId = service.getCustomerIdForAccount(id);
        return new AccountOwnerResponse(id, customerId);
    }

    /* ---------------- Holds ---------------- */

    @PostMapping("/accounts/{id:" + ACCOUNT_ID + "}/holds")
    @PreAuthorize("hasAuthority('SCOPE_fdx:accounts.write')")
    public ResponseEntity<HoldResponse> placeHold(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateHoldRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.commons.exception.BadRequestException("Idempotency-Key là bắt buộc khi đặt hold");
        }
        HoldResponse resp = service.createHold(id, request.withIdempotencyKey(idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/accounts/{id:" + ACCOUNT_ID + "}/holds/{holdId:" + HOLD_ID + "}/capture")
    @PreAuthorize("hasAnyAuthority('SCOPE_fdx:accounts.write','SCOPE_admin:accounts')")
    public ResponseEntity<AccountResponse> captureHold(
            @PathVariable("id") UUID id,
            @PathVariable("holdId") UUID holdId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostingRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.commons.exception.BadRequestException("Idempotency-Key là bắt buộc khi capture hold");
        }
        AccountResponse resp = service.captureHoldAndDebit(id, holdId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag('"' + String.valueOf(resp.version()) + '"')
                .body(resp);
    }

    @PostMapping("/accounts/{id:" + ACCOUNT_ID + "}/holds/{holdId:" + HOLD_ID + "}/release")
    @PreAuthorize("hasAnyAuthority('SCOPE_fdx:accounts.write','SCOPE_admin:accounts')")
    public ResponseEntity<HoldResponse> releaseHold(
            @PathVariable("id") UUID id,
            @PathVariable("holdId") UUID holdId) {
        HoldResponse resp = service.releaseHold(id, holdId, "manual_release");
        return ResponseEntity.ok(resp);
    }

    /* ---------------- Postings ---------------- */

    @PostMapping("/accounts/{id:" + ACCOUNT_ID + "}/credit")
    @PreAuthorize("hasAnyAuthority('SCOPE_fdx:accounts.write','SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<AccountResponse> credit(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostingRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.commons.exception.BadRequestException("Idempotency-Key là bắt buộc cho credit");
        }
        Integer expected = parseIfMatch(ifMatch);
        AccountResponse r = service.credit(id, request, expected, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag('"' + String.valueOf(r.version()) + '"')
                .body(r);
    }

    @PostMapping("/accounts/{id:" + ACCOUNT_ID + "}/debit")
    @PreAuthorize("hasAnyAuthority('SCOPE_fdx:accounts.write','SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<AccountResponse> debit(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostingRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.commons.exception.BadRequestException("Idempotency-Key là bắt buộc cho debit");
        }
        Integer expected = parseIfMatch(ifMatch);
        AccountResponse r = service.debit(id, request, expected, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag('"' + String.valueOf(r.version()) + '"')
                .body(r);
    }

    @PostMapping("/accounts/transfer")
    @PreAuthorize("hasAnyAuthority('SCOPE_fdx:accounts.write','SCOPE_admin:accounts')")
    @RateLimiter(name = "transfer")
    public ResponseEntity<InternalTransferResponse> transfer(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InternalTransferRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.commons.exception.BadRequestException("Idempotency-Key là bắt buộc cho chuyển khoản");
        }
        InternalTransferResponse r = request.fromAccountId() != null
                ? service.transfer(request, idempotencyKey)
                : service.transferForCurrentUser(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    /* ---------------- Helpers ---------------- */

    private Integer parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return null;
        String v = ifMatch.replace("\"", "").trim();
        return Integer.valueOf(v);
    }
}
