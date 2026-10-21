package io.grouploom.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.grouploom.backend.auth.controller.AuthController;
import io.grouploom.backend.auth.dto.AuthResponse;
import io.grouploom.backend.auth.service.AuthService;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.InvalidCredentialsException;
import io.grouploom.backend.error.InvalidRefreshTokenException;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.dto.GroupSummary;
import io.grouploom.backend.user.dto.MeResponse;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class AuthControllerTest {

	private static final String VALID_SIGNUP = """
			{"groupName":"Chess Club","displayName":"Ada","email":"ada@example.com","password":"correct horse battery"}""";

	@Autowired MockMvc mockMvc;
	@MockitoBean AuthService service;

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
		return builder.servletPath("/api");
	}

	private static AuthResult result() {
		MeResponse me = new MeResponse(UUID.randomUUID(), "ada@example.com", "Ada", Role.OWNER,
				new GroupSummary(UUID.randomUUID(), "Chess Club"));
		return new AuthResult(new AuthResponse("jwt", 900, me), "raw-refresh");
	}

	@Test
	void signUp_withValidBody_returns201WithTokenAndRefreshCookie() throws Exception {
		given(service.signUp(any())).willReturn(result());

		mockMvc.perform(api(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.accessToken").value("jwt"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.user.role").value("OWNER"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
					org.hamcrest.Matchers.containsString("gl_refresh=raw-refresh"),
					org.hamcrest.Matchers.containsString("HttpOnly"),
					org.hamcrest.Matchers.containsString("SameSite=Strict"),
					org.hamcrest.Matchers.containsString("Path=/api/auth"),
					org.hamcrest.Matchers.containsString("Secure"))));
	}

	@Test
	void signUp_withInvalidBody_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(api(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"groupName\":\"\",\"displayName\":\"Ada\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='groupName')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
	}

	@Test
	void signUp_withMissingPassword_returns400() throws Exception {
		mockMvc.perform(api(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"groupName\":\"Club\",\"displayName\":\"Ada\",\"email\":\"ada@example.com\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
	}

	@Test
	void login_withValidCredentials_returns200() throws Exception {
		given(service.login(any())).willReturn(result());

		mockMvc.perform(api(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ada@example.com\",\"password\":\"correct horse battery\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("jwt"))
			.andExpect(header().exists(HttpHeaders.SET_COOKIE));
	}

	@Test
	void login_withBlankFields_returns400() throws Exception {
		mockMvc.perform(api(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"\",\"password\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void login_withBadCredentials_returnsGeneric401ProblemJson() throws Exception {
		given(service.login(any())).willThrow(new InvalidCredentialsException());

		mockMvc.perform(api(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ada@example.com\",\"password\":\"nope\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Email or password is incorrect."))
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
	}

	@Test
	void refresh_withoutCookie_returns401() throws Exception {
		mockMvc.perform(api(post("/api/auth/refresh")))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void refresh_withInvalidToken_returns401() throws Exception {
		given(service.refresh("bad")).willThrow(new InvalidRefreshTokenException());

		mockMvc.perform(api(post("/api/auth/refresh")).cookie(new jakarta.servlet.http.Cookie("gl_refresh", "bad")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refresh_withValidCookie_returnsNewTokenAndRotatedCookie() throws Exception {
		given(service.refresh("old")).willReturn(result());

		mockMvc.perform(api(post("/api/auth/refresh")).cookie(new jakarta.servlet.http.Cookie("gl_refresh", "old")))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("gl_refresh=raw-refresh")));
	}

	@Test
	void logout_always_returns204AndClearsCookie() throws Exception {
		mockMvc.perform(api(post("/api/auth/logout")).cookie(new jakarta.servlet.http.Cookie("gl_refresh", "tok")))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
		verify(service).logout("tok");
	}

	@Test
	void logout_withoutCookie_returns204() throws Exception {
		mockMvc.perform(api(post("/api/auth/logout")))
			.andExpect(status().isNoContent());
	}

}
