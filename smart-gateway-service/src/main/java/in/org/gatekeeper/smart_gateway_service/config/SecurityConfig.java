package in.org.gatekeeper.smart_gateway_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws IOException {
        httpSecurity.authorizeHttpRequests(
                authorize -> authorize
                        .requestMatchers("/health")
                        .permitAll()
                        .anyRequest().authenticated()
        );
        return httpSecurity.build();
    }
}
