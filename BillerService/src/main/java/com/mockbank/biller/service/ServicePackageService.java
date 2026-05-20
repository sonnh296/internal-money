package com.mockbank.biller.service;

import com.mockbank.biller.dto.ServicePackageRequest;
import com.mockbank.biller.dto.ServicePackageResponse;
import com.mockbank.biller.model.ServicePackage;
import com.mockbank.biller.repository.ServicePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicePackageService {

    private final ServicePackageRepository repo;

    public ServicePackageResponse create(ServicePackageRequest req) {
        if (repo.existsByReferenceNumber(req.getReferenceNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Service package with this referenceNumber already exists");
        }
        ServicePackage pkg = ServicePackage.builder()
                .name(req.getName())
                .category(req.getCategory())
                .referenceNumber(req.getReferenceNumber())
                .monthlyAmount(req.getMonthlyAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "VND")
                .description(req.getDescription())
                .status("ACTIVE")
                .build();
        return toDto(repo.save(pkg));
    }

    public List<ServicePackageResponse> listActive(int limit, int offset) {
        int pageIndex = Math.max(0, offset) / Math.max(1, limit);
        Page<ServicePackage> page = repo.findByStatus("ACTIVE", PageRequest.of(pageIndex, Math.max(1, limit)));
        return page.getContent().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ServicePackageResponse> listAll() {
        return repo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public ServicePackageResponse get(UUID id) {
        return toDto(repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service package not found")));
    }

    public ServicePackageResponse toggleStatus(UUID id) {
        ServicePackage pkg = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service package not found"));
        pkg.setStatus("ACTIVE".equals(pkg.getStatus()) ? "INACTIVE" : "ACTIVE");
        return toDto(repo.save(pkg));
    }

    public void delete(UUID id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service package not found");
        }
        repo.deleteById(id);
    }

    ServicePackageResponse toDto(ServicePackage p) {
        return ServicePackageResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .category(p.getCategory())
                .referenceNumber(p.getReferenceNumber())
                .monthlyAmount(p.getMonthlyAmount())
                .currency(p.getCurrency())
                .description(p.getDescription())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
