package com.digitalbank.customerservice.service;

import java.util.List;
import java.util.Optional;
import java.security.SecureRandom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.commons.exception.ConflictException;
import com.commons.exception.CustomerNotFoundException;
import com.commons.exception.PreconditionRequiredException;
import com.commons.exception.ResourceNotFoundException;
import com.commons.exception.VersionMismatchException;
import com.account.dto.ProvisionAccountRequest;
import com.digitalbank.customerservice.client.AccountServiceClient;
import com.digitalbank.customerservice.client.AuthServiceClient;
import com.digitalbank.customerservice.dto.CustomerCreatedResponse;
import com.digitalbank.customerservice.dto.CustomerRegistrationRequest;
import com.digitalbank.customerservice.dto.CustomerRequest;
import com.digitalbank.customerservice.dto.CustomerResponse;
import com.digitalbank.customerservice.dto.UpdateCustomerRequest;
import com.digitalbank.customerservice.mapper.CustomerMapper;
import com.digitalbank.customerservice.model.Customer;
import com.digitalbank.customerservice.model.KycStatus;
import com.digitalbank.customerservice.repository.CustomerRepository;
import com.digitalbank.customerservice.util.Fingerprints;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

	private final AuthServiceClient authServiceClient;
	private final AccountServiceClient accountServiceClient;
	private final CustomerRepository repository;
	private final CustomerMapper mapper;
	private final EmailNotificationService emailNotificationService;

	private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
	private static final String DIGIT = "23456789";
	private static final String SYMBOL = "@#$%&*!";
	private static final String ALL = UPPER + LOWER + DIGIT + SYMBOL;
	private static final SecureRandom RANDOM = new SecureRandom();

	public CustomerCreatedResponse create(CustomerRequest request) {

		String externalId = request.getExternalId();
		String email = request.getEmail();

		String fp = Fingerprints.customerCreate(request.getFirstName(), request.getLastName(), request.getEmail(),
				request.getPhone(), request.getAddress());

		// Fast path: same externalId already present?
		Optional<Customer> byExt = repository.findByExternalId(externalId);
		if (byExt.isPresent()) {
			Customer ex = byExt.get();
			if (fp.equals(ex.getRequestFingerprint())) {
				return mapper.toCreateResponse(ex); // idempotent replay
			}
			throw new ConflictException("Same externalId used with different data");
		}

		// Fast path: same Email already present?
		Optional<Customer> byEmail = repository.findByEmail(email);
		if (byEmail.isPresent()) {
			Customer ex = byEmail.get();
			if (fp.equals(ex.getRequestFingerprint())) {
				return mapper.toCreateResponse(ex); // idempotent replay
			}
			throw new ConflictException("Same email used with different data");
		}

		// New record attempt
		Customer entity = mapper.toEntity(request);
		entity.setExternalId(externalId);
		entity.setActive(false);
		entity.setKycStatus(KycStatus.PENDING);
		entity.setRequestFingerprint(fp);
		Customer saved = repository.saveAndFlush(entity);
		return mapper.toCreateResponse(saved);
	}

	public CustomerResponse getByExternalId(String externalId) {
		Customer customer = repository.findByExternalId(externalId)
				.orElseThrow(() -> new ResourceNotFoundException("Customer not found with externalId: " + externalId));
		return mapper.toResponse(customer);
	}

	public List<CustomerResponse> listAll(int page, int size) {
		Page<Customer> paged = repository.findAll(
				PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
						Sort.by("createdAt").descending()));
		return paged.getContent().stream().map(mapper::toResponse).toList();
	}

	public boolean exists(String externalId) {
		return repository.findByExternalId(externalId).isPresent();
	}

	public boolean existsByEmail(String email) {
		return repository.existsByEmail(email);
	}

	public Integer updateCustomer(String id, UpdateCustomerRequest request, Integer expected) {
		Customer c = repository.findByExternalId(id)
				.orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + id));

		if (expected == null)
			throw new PreconditionRequiredException("If-Match header required");
		if (!expected.equals(c.getVersion())) {
			throw new VersionMismatchException("Stale version. Current=" + c.getVersion() + ", If-Match=" + expected);
		}

		mapper.updateCustomerFromRequest(request, c);
		Customer saved = repository.save(c);
		return mapper.toResponse(saved).getVersion();
	}

	public Integer updateKycStatus(String id, String kycStatus) {
		Customer c = repository.findByExternalId(id)
				.orElseThrow(() -> new ResourceNotFoundException("Customer not found with externalId: " + id));

		if ("VERIFIED".equalsIgnoreCase(kycStatus)) {
			String temporaryPassword = generateTemporaryPassword();
			CustomerRegistrationRequest request =
					new CustomerRegistrationRequest(c.getEmail(), temporaryPassword, c.getExternalId());
			authServiceClient.registerCustomer(request);
			c.setKycStatus(KycStatus.VERIFIED);
			c.setActive(true);
			repository.save(c);
			try {
				String displayName = (c.getFirstName() + " " + c.getLastName()).trim();
				accountServiceClient.provisionAccount(
						"provision-" + c.getExternalId(),
						new ProvisionAccountRequest(c.getExternalId(), displayName));
			} catch (Exception e) {
				log.warn("Bank account provisioning failed for customer {}: {}", c.getExternalId(), e.getMessage());
			}
			emailNotificationService.sendKycVerified(c.getEmail(), c.getFirstName(), temporaryPassword);
		} else if ("REJECTED".equalsIgnoreCase(kycStatus)) {
			c.setKycStatus(KycStatus.REJECTED);
			repository.save(c);
			// Email notification
			try {
				emailNotificationService.sendKycRejected(c.getEmail(), c.getFirstName());
			} catch (Exception e) {
				log.warn("Email notification failed for {}: {}", c.getEmail(), e.getMessage());
			}
		}
		return c.getVersion();
	}

	private String generateTemporaryPassword() {
		StringBuilder sb = new StringBuilder();
		sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
		sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
		sb.append(DIGIT.charAt(RANDOM.nextInt(DIGIT.length())));
		sb.append(SYMBOL.charAt(RANDOM.nextInt(SYMBOL.length())));
		while (sb.length() < 14) {
			sb.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
		}
		char[] chars = sb.toString().toCharArray();
		for (int i = chars.length - 1; i > 0; i--) {
			int j = RANDOM.nextInt(i + 1);
			char tmp = chars[i];
			chars[i] = chars[j];
			chars[j] = tmp;
		}
		return new String(chars);
	}

	public CustomerResponse getById(Long id) {
		return repository.findById(id)
				.map(mapper::toResponse)
				.orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
	}
}
