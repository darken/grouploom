package io.grouploom.backend.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.grouploom.backend.auth.CurrentUserArgumentResolver;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.user.controller.MeController;
import io.grouploom.backend.user.dto.GroupSummary;
import io.grouploom.backend.user.dto.MeResponse;
import io.grouploom.backend.user.service.CurrentUserService;

@WebMvcTest(MeController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class })
class MeControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();

	@Autowired MockMvc mockMvc;
	@MockitoBean CurrentUserService service;

	@Test
	void me_withValidJwt_returnsCurrentUser() throws Exception {
		given(service.me(any())).willReturn(new MeResponse(USER_ID, "ada@example.com", "Ada", Role.OWNER,
				new GroupSummary(GROUP_ID, "Chess Club")));

		mockMvc.perform(get("/api/me").servletPath("/api")
				.with(jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", "OWNER"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("ada@example.com"))
			.andExpect(jsonPath("$.group.name").value("Chess Club"));
	}

	@Test
	void me_withoutToken_returns401ProblemJsonWithoutChallenge() throws Exception {
		mockMvc.perform(get("/api/me").servletPath("/api"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
	}

	@Test
	void me_whenUserWasDeleted_returns404() throws Exception {
		given(service.me(any())).willThrow(new NotFoundException("User not found."));

		mockMvc.perform(get("/api/me").servletPath("/api")
				.with(jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", "OWNER"))))
			.andExpect(status().isNotFound());
	}

}
