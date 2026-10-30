package in.org.gatekeeper.api_management_service.repository;

import in.org.gatekeeper.api_management_service.entity.ControlPlaneAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ControlPlaneAuditRecordRepository extends JpaRepository<ControlPlaneAuditRecord, Long> {

    List<ControlPlaneAuditRecord> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
