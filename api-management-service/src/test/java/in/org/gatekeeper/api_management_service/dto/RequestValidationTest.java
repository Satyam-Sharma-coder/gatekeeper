package in.org.gatekeeper.api_management_service.dto;

import in.org.gatekeeper.api_management_service.dto.request.HeaderRuleRequest;
import in.org.gatekeeper.api_management_service.dto.request.LoginRequest;
import in.org.gatekeeper.api_management_service.dto.request.OrganizationRequest;
import in.org.gatekeeper.api_management_service.dto.request.OrganizationStatusRequest;
import in.org.gatekeeper.api_management_service.dto.request.RouteRequest;
import in.org.gatekeeper.api_management_service.dto.request.RouteStatusRequest;
import in.org.gatekeeper.api_management_service.dto.request.TenantAdminRequest;
import in.org.gatekeeper.api_management_service.dto.request.TenantOrganizationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static in.org.gatekeeper.api_management_service.support.TestData.headerRuleRequest;
import static in.org.gatekeeper.api_management_service.support.TestData.routeRequest;
import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private static <T> Set<String> invalidFields(T target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Nested
    class Login {

        @Test
        void acceptsUsernameAndPassword() {
            LoginRequest request = new LoginRequest();
            request.setUsername("user");
            request.setPassword("pass");

            assertThat(invalidFields(request)).isEmpty();
        }

        @Test
        void requiresBothFields() {
            assertThat(invalidFields(new LoginRequest())).containsExactlyInAnyOrder("username", "password");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void rejectsBlankValues(String blank) {
            LoginRequest request = new LoginRequest();
            request.setUsername(blank);
            request.setPassword(blank);

            assertThat(invalidFields(request)).containsExactlyInAnyOrder("username", "password");
        }
    }

    @Nested
    class Organization {

        private OrganizationRequest request(String name, String slug) {
            OrganizationRequest request = new OrganizationRequest();
            request.setName(name);
            request.setSlug(slug);
            return request;
        }

        @ParameterizedTest
        @ValueSource(strings = {"acme", "acme-corp", "a1", "a-b-c", "123", "acme2-corp3"})
        void acceptsWellFormedSlugs(String slug) {
            assertThat(invalidFields(request("Acme", slug))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Acme", "acme corp", "acme_corp", "-acme", "acme-", "acme--corp", "acme.corp",
                "acme/corp", "ünïcode"})
        void rejectsMalformedSlugs(String slug) {
            assertThat(invalidFields(request("Acme", slug))).containsExactly("slug");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        void requiresNameAndSlug(String blank) {
            assertThat(invalidFields(request(blank, blank))).contains("name", "slug");
        }

        @Test
        void enforcesLengthLimits() {
            assertThat(invalidFields(request("n".repeat(150), "s".repeat(100)))).isEmpty();
            assertThat(invalidFields(request("n".repeat(151), "s".repeat(101)))).containsExactlyInAnyOrder("name", "slug");
        }
    }

    @Nested
    class OrganizationStatus {

        @Test
        void requiresAStatus() {
            assertThat(invalidFields(new OrganizationStatusRequest())).containsExactly("status");
        }

        @Test
        void acceptsAStatus() {
            OrganizationStatusRequest request = new OrganizationStatusRequest();
            request.setStatus(in.org.gatekeeper.api_management_service.entity.OrganizationStatus.ACTIVE);

            assertThat(invalidFields(request)).isEmpty();
        }
    }

    @Nested
    class TenantAdmin {

        private TenantAdminRequest request(String username, String password) {
            TenantAdminRequest request = new TenantAdminRequest();
            request.setUsername(username);
            request.setPassword(password);
            return request;
        }

        @Test
        void isEnabledByDefault() {
            assertThat(new TenantAdminRequest().isEnabled()).isTrue();
        }

        @Test
        void acceptsValidAccount() {
            assertThat(invalidFields(request("acme-admin", "12345678"))).isEmpty();
        }

        @Test
        void enforcesPasswordLengthBetweenEightAndOneHundred() {
            assertThat(invalidFields(request("u", "1234567"))).containsExactly("password");
            assertThat(invalidFields(request("u", "12345678"))).isEmpty();
            assertThat(invalidFields(request("u", "p".repeat(100)))).isEmpty();
            assertThat(invalidFields(request("u", "p".repeat(101)))).containsExactly("password");
        }

        @Test
        void enforcesUsernameLimit() {
            assertThat(invalidFields(request("u".repeat(100), "12345678"))).isEmpty();
            assertThat(invalidFields(request("u".repeat(101), "12345678"))).containsExactly("username");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void requiresUsernameAndPassword(String blank) {
            assertThat(invalidFields(request(blank, blank))).contains("username", "password");
        }
    }

    @Nested
    class TenantOrganization {

        @Test
        void requiresAName() {
            assertThat(invalidFields(new TenantOrganizationRequest())).containsExactly("name");
        }

        @Test
        void enforcesNameLimit() {
            TenantOrganizationRequest request = new TenantOrganizationRequest();
            request.setName("n".repeat(150));
            assertThat(invalidFields(request)).isEmpty();
            request.setName("n".repeat(151));
            assertThat(invalidFields(request)).containsExactly("name");
        }
    }

    @Nested
    class RouteStatus {

        @Test
        void requiresTheActiveFlag() {
            assertThat(invalidFields(new RouteStatusRequest())).containsExactly("active");
        }

        @Test
        void acceptsTrueAndFalse() {
            RouteStatusRequest request = new RouteStatusRequest();
            request.setActive(false);
            assertThat(invalidFields(request)).isEmpty();
            request.setActive(true);
            assertThat(invalidFields(request)).isEmpty();
        }
    }

    @Nested
    class HeaderRule {

        @Test
        void acceptsCompleteRule() {
            assertThat(invalidFields(headerRuleRequest("REQUEST", "ADD", "X-A", "v"))).isEmpty();
        }

        @Test
        void acceptsMissingValueBecauseTheServiceDecidesPerAction() {
            assertThat(invalidFields(headerRuleRequest("REQUEST", "REMOVE", "X-A", null))).isEmpty();
        }

        @Test
        void requiresDirectionActionAndName() {
            assertThat(invalidFields(new HeaderRuleRequest()))
                    .containsExactlyInAnyOrder("direction", "action", "headerName");
        }

        @Test
        void enforcesLengthLimits() {
            assertThat(invalidFields(headerRuleRequest("d".repeat(21), "a".repeat(21), "h".repeat(201), "v".repeat(2001))))
                    .containsExactlyInAnyOrder("direction", "action", "headerName", "headerValue");
            assertThat(invalidFields(headerRuleRequest("d".repeat(20), "a".repeat(20), "h".repeat(200), "v".repeat(2000))))
                    .isEmpty();
        }
    }

    @Nested
    class Route {

        @Test
        void acceptsTypicalRequest() {
            assertThat(invalidFields(routeRequest("orders", "acme"))).isEmpty();
        }

        @Test
        void defaultsToInactiveWithoutIdempotency() {
            RouteRequest request = new RouteRequest();

            assertThat(request.isActive()).isFalse();
            assertThat(request.isIdempotencyEnabled()).isFalse();
            assertThat(request.getAllowedMethods()).isEmpty();
            assertThat(request.getHeaderRules()).isEmpty();
        }

        @Test
        void requiresMandatoryFields() {
            assertThat(invalidFields(new RouteRequest())).containsExactlyInAnyOrder(
                    "routeId", "name", "pathPattern", "targetBaseUrl", "targetPathPrefix",
                    "responseTimeoutMs", "allowedMethods");
        }

        @Test
        void rateLimitIsOptionalButMustBePositive() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setRequestsPerMinute(null);
            assertThat(invalidFields(request)).isEmpty();

            request.setRequestsPerMinute(0);
            assertThat(invalidFields(request)).containsExactly("requestsPerMinute");

            request.setRequestsPerMinute(-5);
            assertThat(invalidFields(request)).containsExactly("requestsPerMinute");

            request.setRequestsPerMinute(1);
            assertThat(invalidFields(request)).isEmpty();
        }

        @Test
        void timeoutMustBePositive() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setResponseTimeoutMs(0);
            assertThat(invalidFields(request)).containsExactly("responseTimeoutMs");

            request.setResponseTimeoutMs(null);
            assertThat(invalidFields(request)).containsExactly("responseTimeoutMs");

            request.setResponseTimeoutMs(1);
            assertThat(invalidFields(request)).isEmpty();
        }

        @Test
        void enforcesStringLengthLimits() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setRouteId("r".repeat(101));
            request.setName("n".repeat(201));
            request.setPathPattern("p".repeat(501));
            request.setTargetBaseUrl("u".repeat(1001));
            request.setTargetPathPrefix("t".repeat(501));

            assertThat(invalidFields(request)).containsExactlyInAnyOrder(
                    "routeId", "name", "pathPattern", "targetBaseUrl", "targetPathPrefix");
        }

        @Test
        void rejectsEmptyMethodSet() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setAllowedMethods(new HashSet<String>());

            assertThat(invalidFields(request)).containsExactly("allowedMethods");
        }

        @Test
        void rejectsBlankMethodElement() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setAllowedMethods(new HashSet<String>(List.of("GET", " ")));

            assertThat(invalidFields(request)).hasSize(1);
            assertThat(invalidFields(request).iterator().next()).startsWith("allowedMethods");
        }

        @Test
        void rejectsNullHeaderRuleList() {
            RouteRequest request = routeRequest("orders", "acme");
            request.setHeaderRules(null);

            assertThat(invalidFields(request)).containsExactly("headerRules");
        }

        @Test
        void validatesNestedHeaderRules() {
            RouteRequest request = routeRequest("orders", "acme");
            request.getHeaderRules().add(headerRuleRequest("", "ADD", "", null));

            Set<String> fields = invalidFields(request);

            assertThat(fields).anyMatch(path -> path.startsWith("headerRules[1].direction"));
            assertThat(fields).anyMatch(path -> path.startsWith("headerRules[1].headerName"));
        }
    }
}
