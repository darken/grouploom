package io.grouploom.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Import(JwtConfig.class)
public class SecurityConfig {

	// Stateless bearer-token API, so CSRF protection is disabled: there is no session cookie to ride on.
	// The only cookie (gl_refresh) is HttpOnly, SameSite=Strict and scoped to /api/auth.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
		ProblemDetailSecurityHandlers handlers = new ProblemDetailSecurityHandlers();
		http
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/system/info").permitAll()
				.requestMatchers("/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
				// Member endpoints: authorized in the service by validating the link token.
				.requestMatchers("/public/**").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
			.oauth2ResourceServer(o -> o
				.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(handlers)
				.accessDeniedHandler(handlers));
		return http.build();
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
