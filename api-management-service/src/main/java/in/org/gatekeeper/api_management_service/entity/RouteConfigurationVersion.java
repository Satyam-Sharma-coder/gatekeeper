package in.org.gatekeeper.api_management_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "route_configuration_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteConfigurationVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String routeId;
    private Long versionNumber;
    private String changeType;

    @Column(columnDefinition = "LONGTEXT")
    private String configurationSnapshot;

    private LocalDateTime createdAt;
}
