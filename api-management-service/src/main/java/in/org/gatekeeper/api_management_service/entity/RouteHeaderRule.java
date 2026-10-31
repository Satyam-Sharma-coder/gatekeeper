package in.org.gatekeeper.api_management_service.entity;

import jakarta.persistence.Entity;
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
@Table(name = "route_header_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteHeaderRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_pk", nullable = false)
    private ApiRoute route;

    private String direction;
    private String action;
    private String headerName;
    private String headerValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
