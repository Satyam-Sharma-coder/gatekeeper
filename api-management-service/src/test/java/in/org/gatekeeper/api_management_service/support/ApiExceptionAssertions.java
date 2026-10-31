package in.org.gatekeeper.api_management_service.support;

import in.org.gatekeeper.api_management_service.exception.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class ApiExceptionAssertions {

    private ApiExceptionAssertions() {
    }

    public static void assertApiException(ThrowingCallable action, HttpStatus expectedStatus) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatus())
                                .isEqualTo(expectedStatus));
    }

    public static void assertApiException(ThrowingCallable action, HttpStatus expectedStatus, String messagePart) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getStatus()).isEqualTo(expectedStatus);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains(messagePart);
                });
    }
}
