package io.grouploom.backend.preference;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.grouploom.backend.config.JwtConfig;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.InvalidMemberTokenException;

@WebMvcTest(PublicUnsubscribeController.class)
@Import({ SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class })
class PublicUnsubscribeControllerTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean UnsubscribeService service;

	@Test
	void unsubscribe_withTokenAndNoBearer_returns200() throws Exception {
		mockMvc.perform(post("/api/public/unsubscribe").servletPath("/api").queryParam("t", "tok")).andExpect(status().isOk());

		verify(service).unsubscribe("tok");
	}

	@Test
	void unsubscribe_acceptsTheOneClickFormBody() throws Exception {
		mockMvc.perform(post("/api/public/unsubscribe").servletPath("/api")
			.queryParam("t", "tok")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.content("List-Unsubscribe=One-Click"))
			.andExpect(status().isOk());

		verify(service).unsubscribe("tok");
	}

	@Test
	void unsubscribe_withAnInvalidToken_returns401WithCode() throws Exception {
		doThrow(new InvalidMemberTokenException(InvalidMemberTokenException.INVALID)).when(service).unsubscribe("bad");

		mockMvc.perform(post("/api/public/unsubscribe").servletPath("/api").queryParam("t", "bad"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
	}

	@Test
	void unsubscribe_withoutAToken_returns401WithoutCallingTheService() throws Exception {
		mockMvc.perform(post("/api/public/unsubscribe").servletPath("/api"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));

		verifyNoInteractions(service);
	}

}
