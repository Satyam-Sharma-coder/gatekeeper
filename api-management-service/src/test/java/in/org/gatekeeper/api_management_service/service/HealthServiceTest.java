package in.org.gatekeeper.api_management_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void reportsUpWhenTheDatabaseAnswersOne() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        assertThat(new HealthService(jdbcTemplate).getHealth()).isEqualTo("UP");
    }

    @Test
    void reportsDownWhenTheDatabaseAnswersSomethingElse() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(0);

        assertThat(new HealthService(jdbcTemplate).getHealth()).isEqualTo("DOWN");
    }

    @Test
    void reportsDownWhenTheDatabaseReturnsNothing() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(null);

        assertThat(new HealthService(jdbcTemplate).getHealth()).isEqualTo("DOWN");
    }

    @Test
    void reportsDownInsteadOfThrowingWhenTheDatabaseIsUnreachable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new CannotGetJdbcConnectionException("connection refused"));

        assertThat(new HealthService(jdbcTemplate).getHealth()).isEqualTo("DOWN");
    }
}
