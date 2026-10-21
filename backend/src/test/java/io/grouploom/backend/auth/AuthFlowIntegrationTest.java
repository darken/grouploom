package io.grouploom.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.grouploom.backend.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

	private static final String PASSWORD = "correct horse battery";

	@Autowired MockMvc mockMvc;

	private static String signUpBody(String email) {
		return "{\"groupName\":\"Chess Club\",\"displayName\":\"Ada\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
	}

	private MvcResult postJson(String path, String body) throws Exception {
		return mockMvc.perform(post("/api" + path).servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}

	private static Cookie refreshCookie(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie("gl_refresh");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	@Test
	void authFlow_signUpMeRefreshLogoutLogin_works() throws Exception {
		String email = "flow-" + System.nanoTime() + "@example.com";

		MvcResult signUp = postJson("/auth/signup", signUpBody(email));
		assertThat(signUp.getResponse().getStatus()).isEqualTo(201);
		String access = JsonPath.read(signUp.getResponse().getContentAsString(), "$.accessToken");
		Cookie first = refreshCookie(signUp);

		mockMvc.perform(get("/api/me").servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.role").value("OWNER"))
			.andExpect(jsonPath("$.group.name").value("Chess Club"));

		MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").servletPath("/api").cookie(first))
			.andExpect(status().isOk()).andReturn();
		Cookie second = refreshCookie(refreshed);
		assertThat(second.getValue()).isNotEqualTo(first.getValue());

		// Replaying the rotated-out token is reuse: it fails and revokes the whole family.
		mockMvc.perform(post("/api/auth/refresh").servletPath("/api").cookie(first)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh").servletPath("/api").cookie(second)).andExpect(status().isUnauthorized());

		MvcResult login = postJson("/auth/login", "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}");
		assertThat(login.getResponse().getStatus()).isEqualTo(200);
		Cookie third = refreshCookie(login);

		mockMvc.perform(post("/api/auth/logout").servletPath("/api").cookie(third)).andExpect(status().isNoContent());
		mockMvc.perform(post("/api/auth/refresh").servletPath("/api").cookie(third)).andExpect(status().isUnauthorized());
	}

	@Test
	void signUp_withDuplicateEmail_returns409() throws Exception {
		String email = "dup-" + System.nanoTime() + "@example.com";
		assertThat(postJson("/auth/signup", signUpBody(email)).getResponse().getStatus()).isEqualTo(201);

		assertThat(postJson("/auth/signup", signUpBody(email.toUpperCase())).getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void login_withWrongPassword_returns401() throws Exception {
		assertThat(postJson("/auth/login", "{\"email\":\"ghost@example.com\",\"password\":\"x\"}").getResponse().getStatus())
			.isEqualTo(401);
	}

	@Test
	void me_withTamperedToken_returns401() throws Exception {
		mockMvc.perform(get("/api/me").servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
			.andExpect(status().isUnauthorized());
	}

}
