package com.authuser.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.authuser.dto.CreateUserRequest;
import com.authuser.model.AuthUser;
import com.authuser.service.InternalAuthService;

import jakarta.validation.Valid;

/**
 * Internal controller for provisioning application users after KYC verification.
 *
 * <p>This controller exposes an internal administrative endpoint used by backend
 * services (such as CustomerService after KYC approval) to create new database
 * users in the local auth database. It is secured with RBAC and requires the caller to have the
 * {@code admin:users.write} permission on their access token.</p>
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>This controller stores user credentials in local DB owned by this service.</li>
 *   <li>Access tokens must include the {@code SCOPE_admin:users.write} authority for the request
 *       to be accepted.</li>
 *   <li>The service uses client credentials flow for M2M calls and should never be exposed to
 *       frontend clients directly.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class CreateUserController {

    private final InternalAuthService internalAuthService;

    /**
     * Constructs a {@code CreateUserController} with the provided {@link Auth0UserService}.
     *
     * @param internalAuthService service responsible for local user provisioning
     */
    public CreateUserController(InternalAuthService internalAuthService) {
        this.internalAuthService = internalAuthService;
    }

    /**
     * Creates or updates a local user bound to a customer record.
     *
     * <p>This endpoint is protected with {@link PreAuthorize} and requires
     * {@code SCOPE_admin:users.write} authority. Typically invoked by internal backend
     * services after a customer completes KYC verification.</p>
     *
     * <h3>Request</h3>
     * <pre>
     * POST /api/v1/internal/users
     * Authorization: Bearer &lt;access_token&gt;
     * Content-Type: application/json
     *
     * {
     *   "email": "customer@example.com",
     *   "password": "Temp@1234",
     *   "customerId": "ext-12345"
     * }
     * </pre>
     *
     * <h3>Response</h3>
     * Returns a JSON map containing the created Auth0 user's details, including the
     * generated {@code user_id}.
     *
     * @param req the user creation request payload containing email, password, and customerId
     * @return a {@link ResponseEntity} containing local user summary
     */
    @PreAuthorize("hasAuthority('SCOPE_admin:users.write')")
    @PostMapping("/internal/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest req) {
        AuthUser user = internalAuthService.createInternalUser(
                req.getEmail(),
                req.getCustomerId(),
                req.getTemporaryPassword()
        );
        return ResponseEntity.ok(
                java.util.Map.of(
                        "userId", user.getId(),
                        "email", user.getEmail(),
                        "customerId", user.getCustomerId(),
                        "enabled", user.isEnabled()
                )
        );
    }

    // Backward compatibility for legacy callers still using /iam/users.
    @PreAuthorize("hasAuthority('SCOPE_admin:users.write')")
    @PostMapping("/iam/users")
    public ResponseEntity<?> createUserLegacy(@Valid @RequestBody CreateUserRequest req) {
        return createUser(req);
    }
}
