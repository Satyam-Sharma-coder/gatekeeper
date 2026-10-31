package in.org.gatekeeper.api_management_service.service;

import in.org.gatekeeper.api_management_service.entity.ControlPlaneAuditRecord;
import in.org.gatekeeper.api_management_service.entity.Organization;
import in.org.gatekeeper.api_management_service.repository.ControlPlaneAuditRecordRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final ControlPlaneAuditRecordRepository auditRecordRepository;

    public AuditService(ControlPlaneAuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    public void record(Organization organization, String action,
                       String resourceType, String resourceId,
                       long startedAt, String details) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String actor = authentication == null ? "system" : authentication.getName();

        ControlPlaneAuditRecord record = ControlPlaneAuditRecord.builder()
                .organization(organization)
                .actor(actor)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .correlationId(getCorrelationId())
                .durationMs((System.nanoTime() - startedAt) / 1000000)
                .result("SUCCESS")
                .details(details)
                .createdAt(LocalDateTime.now())
                .build();
        auditRecordRepository.save(record);
    }

    private String getCorrelationId() {
        if(!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes)) {
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes())
                .getRequest();
        return request.getHeader("X-Correlation-ID");
    }
}
