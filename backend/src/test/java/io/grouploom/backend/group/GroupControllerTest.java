package io.grouploom.backend.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.auth.CurrentUserArgumentResolver;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.config.WebConfig;
import io.grouploom.backend.error.ErrorCode;
import io.grouploom.backend.error.ForbiddenException;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.group.dto.UpdateGroupRequest;
import io.grouploom.backend.user.dto.GroupSummary;

@WebMvcTest(GroupController.class)
@Import({ SecurityConfig.class, WebConfig.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class })
class GroupControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();

	@Autowired MockMvc mockMvc;
	@MockitoBean GroupService service;

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api")
			.with(jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", "OWNER")))
			.contentType(MediaType.APPLICATION_JSON);
	}

	@Test
	void update_withSupportedLanguage_returnsTheGroup() throws Exception {
		given(service.update(any(), any())).willReturn(new GroupSummary(GROUP_ID, "Chess Club", Language.JA));

		mockMvc.perform(api(patch("/api/group")).content("{\"language\":\"ja\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Chess Club"))
			.andExpect(jsonPath("$.language").value("ja"));

		ArgumentCaptor<CurrentUser> user = ArgumentCaptor.forClass(CurrentUser.class);
		ArgumentCaptor<UpdateGroupRequest> request = ArgumentCaptor.forClass(UpdateGroupRequest.class);
		verify(service).update(user.capture(), request.capture());
		assertThat(user.getValue().groupId()).isEqualTo(GROUP_ID);
		assertThat(request.getValue().language()).isEqualTo(Language.JA);
	}

	@Test
	void update_withoutLanguage_returns400WithFieldError() throws Exception {
		mockMvc.perform(api(patch("/api/group")).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='language')]").exists());
		verifyNoInteractions(service);
	}

	@Test
	void update_withUnsupportedLanguage_returns400WithFieldError() throws Exception {
		mockMvc.perform(api(patch("/api/group")).content("{\"language\":\"fr\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='language')]").exists());
		verifyNoInteractions(service);
	}

	@Test
	void update_withoutToken_returns401() throws Exception {
		mockMvc.perform(patch("/api/group").servletPath("/api").contentType(MediaType.APPLICATION_JSON)
				.content("{\"language\":\"ja\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void update_whenTheCallerIsNotTheOwner_returns403ProblemJson() throws Exception {
		given(service.update(any(), any())).willThrow(new ForbiddenException(ErrorCode.GROUP_OWNER_ONLY));

		mockMvc.perform(api(patch("/api/group")).content("{\"language\":\"ja\"}"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Only the owner can change the group."));
	}

}
