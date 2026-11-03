package io.grouploom.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.LocaleResolver;

import io.grouploom.backend.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401/403 as RFC 9457 problem+json, without a WWW-Authenticate challenge (no browser login dialog). These
 * run in the security filter chain, before Spring MVC has resolved the request language, so they do it themselves.
 */
class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final LocaleResolver localeResolver;

	private final MessageSource messages;

	private final JsonMapper json;

	ProblemDetailSecurityHandlers(LocaleResolver localeResolver, MessageSource messages, JsonMapper json) {
		this.localeResolver = localeResolver;
		this.messages = messages;
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(request, response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, ErrorCode code)
			throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", messages.getMessage(code.messageKey(), null, localeResolver.resolveLocale(request)));
		body.put("code", code.code());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(json.writeValueAsString(body));
	}

}
