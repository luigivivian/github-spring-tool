package dev.luigivivian.githubtool.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Everything stays public (same behavior as before security was added). OAuth2 login is
     * activated only when a ClientRegistrationRepository bean exists — in the full app that is
     * always true (application.yml ships dummy placeholder credentials so boot never fails;
     * login just fails upstream until real ones are set). The conditional matters for
     * @WebMvcTest slices, which import this config without any registration bean.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registrations,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(login -> login.defaultSuccessUrl(frontendUrl, true))
                    .logout(logout -> logout.logoutSuccessUrl(frontendUrl));
        }
        return http.build();
    }
}
