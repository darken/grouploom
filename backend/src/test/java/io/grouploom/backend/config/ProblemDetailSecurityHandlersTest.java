package io.grouploom.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProblemDetailSecurityHandlersTest {

	private final JsonMapper json = JsonMapper.builder().build();

	private final ProblemDetailSecurityHandlers handlers = new ProblemDetailSecurityHandlers(
			new LocaleConfig().localeResolver(), messages(), json);

	private static ResourceBundleMessageSource messages() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		return source;
	}

	private static MockHttpServletRequest request(String acceptLanguage) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (acceptLanguage != null) {
			request.addHeader("Accept-Language", acceptLanguage);
		}
		return request;
	}

	private JsonNode unauthorized(String acceptLanguage) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		handlers.commence(request(acceptLanguage), response, new BadCredentialsException("x"));
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
	}

	private JsonNode forbidden(String acceptLanguage) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		handlers.handle(request(acceptLanguage), response, new AccessDeniedException("x"));
		assertThat(response.getStatus()).isEqualTo(403);
		return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
	}

	@Test
	void commence_withoutAcceptLanguage_keepsTheEnglishBody() throws Exception {
		JsonNode body = unauthorized(null);

		assertThat(body.get("type").asString()).isEqualTo("about:blank");
		assertThat(body.get("title").asString()).isEqualTo("Unauthorized");
		assertThat(body.get("status").asInt()).isEqualTo(401);
		assertThat(body.get("detail").asString()).isEqualTo("Authentication is required.");
		assertThat(body.get("code").asString()).isEqualTo("auth.required");
	}

	@Test
	void commence_withJapaneseBrowser_returnsJapanese() throws Exception {
		JsonNode body = unauthorized("ja-JP,ja;q=0.9");

		assertThat(body.get("detail").asString()).isEqualTo("認証が必要です。ログインしてください。");
		assertThat(body.get("code").asString()).isEqualTo("auth.required");
	}

	@Test
	void commence_withUnsupportedLanguage_returnsEnglish() throws Exception {
		assertThat(unauthorized("fr").get("detail").asString()).isEqualTo("Authentication is required.");
	}

	@Test
	void handle_withoutAcceptLanguage_keepsTheEnglishBody() throws Exception {
		JsonNode body = forbidden(null);

		assertThat(body.get("title").asString()).isEqualTo("Forbidden");
		assertThat(body.get("status").asInt()).isEqualTo(403);
		assertThat(body.get("detail").asString()).isEqualTo("You do not have access to this resource.");
		assertThat(body.get("code").asString()).isEqualTo("auth.access-denied");
	}

	@Test
	void handle_withJapaneseBrowser_returnsJapanese() throws Exception {
		assertThat(forbidden("ja").get("detail").asString()).isEqualTo("このリソースにアクセスする権限がありません。");
	}

}
