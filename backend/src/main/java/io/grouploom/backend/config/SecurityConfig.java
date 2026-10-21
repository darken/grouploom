package io.grouploom.backend.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	// Placeholder until JWT auth is added: keeps the default login for everything
	// except the OpenAPI docs, which `pnpm gen:api` must be able to fetch.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/system/info").permitAll()
				.anyRequest().authenticated())
			.formLogin(withDefaults())
			.httpBasic(withDefaults());
		return http.build();
	}

}
