package in.org.gatekeeper.api_management_service.repository;

import in.org.gatekeeper.api_management_service.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
