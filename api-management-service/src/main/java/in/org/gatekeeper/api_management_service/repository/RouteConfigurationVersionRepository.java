package in.org.gatekeeper.api_management_service.repository;

import in.org.gatekeeper.api_management_service.entity.RouteConfigurationVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteConfigurationVersionRepository extends JpaRepository<RouteConfigurationVersion, Long> {

    List<RouteConfigurationVersion> findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(Long organizationId, String routeId);

}