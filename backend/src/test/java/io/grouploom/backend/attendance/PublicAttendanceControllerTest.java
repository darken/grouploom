package io.grouploom.backend.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
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

import io.grouploom.backend.attendance.dto.AnswerRequest;
import io.grouploom.backend.attendance.dto.PublicAttendanceResponse;
import io.grouploom.backend.attendance.dto.PublicEvent;
import io.grouploom.backend.config.JwtConfig;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.eventtype.EventTypeColor;

@WebMvcTest(PublicAttendanceController.class)
@Import({ SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class })
class PublicAttendanceControllerTest {

	private static final String TOKEN = "attendance-token-value";
	private static final String URL = "/api/public/attendance";
	private static final String VALID_PUT = "{\"response\":\"GOING\",\"note\":\"Bringing snacks\"}";

	@Autowired MockMvc mockMvc;
	@MockitoBean PublicAttendanceService service;

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api");
	}

	private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder b) {
		return api(b).header("X-Member-Token", TOKEN);
	}

	private static PublicAttendanceResponse response(AttendanceResponse answer, boolean open) {
		Instant start = Instant.parse("2026-11-07T09:00:00Z");
		PublicEvent event = new PublicEvent("Practice", null, "Main hall", start, start.plusSeconds(7200),
				LocalDateTime.parse("2026-11-07T10:00:00"), LocalDateTime.parse("2026-11-07T12:00:00"), "Europe/Madrid",
				"Practice", EventTypeColor.GREEN, false, null);
		return new PublicAttendanceResponse("Chess Club", "Ana", event, answer, answer == null ? null : "Bringing snacks",
				answer == null ? null : start.minusSeconds(86_400), open);
	}

	@Test
	void get_withMemberTokenAndNoBearer_returns200WithTheEventAndTheAnswer() throws Exception {
		given(service.get(TOKEN)).willReturn(response(AttendanceResponse.GOING, true));

		mockMvc.perform(withToken(get(URL)))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.groupName").value("Chess Club"))
			.andExpect(jsonPath("$.memberName").value("Ana"))
			.andExpect(jsonPath("$.event.title").value("Practice"))
			.andExpect(jsonPath("$.event.typeColor").value("green"))
			.andExpect(jsonPath("$.event.localStart").value("2026-11-07T10:00:00"))
			.andExpect(jsonPath("$.event.cancelled").value(false))
			.andExpect(jsonPath("$.event.description").doesNotExist())
			.andExpect(jsonPath("$.response").value("GOING"))
			.andExpect(jsonPath("$.note").value("Bringing snacks"))
			.andExpect(jsonPath("$.open").value(true));
	}

	@Test
	void get_withoutAnAnswer_omitsTheResponse() throws Exception {
		given(service.get(TOKEN)).willReturn(response(null, true));

		mockMvc.perform(withToken(get(URL)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.response").doesNotExist())
			.andExpect(jsonPath("$.respondedAt").doesNotExist());
	}

	@Test
	void get_withoutMemberTokenHeader_returns401Invalid() throws Exception {
		mockMvc.perform(api(get(URL)))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		verifyNoInteractions(service);
	}

	@Test
	void get_withInvalidToken_returns401Invalid() throws Exception {
		given(service.get(TOKEN)).willThrow(new InvalidMemberTokenException("member-token.invalid"));

		mockMvc.perform(withToken(get(URL)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
	}

	@Test
	void get_withExpiredToken_returns401Expired() throws Exception {
		given(service.get(TOKEN)).willThrow(new InvalidMemberTokenException("member-token.expired"));

		mockMvc.perform(withToken(get(URL)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.expired"));
	}

	@Test
	void get_withOrganizerBearerButNoMemberToken_returns401Invalid() throws Exception {
		mockMvc.perform(api(get(URL)).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
				.claim("gid", UUID.randomUUID().toString()).claim("role", "OWNER"))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		verifyNoInteractions(service);
	}

	@Test
	void put_withValidBody_returns200WithTheFreshAnswer() throws Exception {
		given(service.answer(eq(TOKEN), any())).willReturn(response(AttendanceResponse.GOING, true));

		mockMvc.perform(withToken(put(URL)).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.response").value("GOING"));

		ArgumentCaptor<AnswerRequest> request = ArgumentCaptor.forClass(AnswerRequest.class);
		verify(service).answer(eq(TOKEN), request.capture());
		org.assertj.core.api.Assertions.assertThat(request.getValue())
			.isEqualTo(new AnswerRequest(AttendanceResponse.GOING, "Bringing snacks"));
	}

	@Test
	void put_withoutMemberTokenHeader_returns401Invalid() throws Exception {
		mockMvc.perform(api(put(URL)).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		verifyNoInteractions(service);
	}

	@Test
	void put_withExpiredToken_returns401Expired() throws Exception {
		given(service.answer(eq(TOKEN), any())).willThrow(new InvalidMemberTokenException("member-token.expired"));

		mockMvc.perform(withToken(put(URL)).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.expired"));
	}

	@Test
	void put_withNullResponse_returns400WithFieldError() throws Exception {
		mockMvc.perform(withToken(put(URL)).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"hi\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='response')]").exists());
		verifyNoInteractions(service);
	}

	@Test
	void put_withUnknownResponse_returns400() throws Exception {
		mockMvc.perform(withToken(put(URL)).contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"MAYBE\"}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	void put_withANoteOver280Characters_returns400WithFieldError() throws Exception {
		mockMvc.perform(withToken(put(URL)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"response\":\"GOING\",\"note\":\"" + "x".repeat(281) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='note')]").exists());
		verifyNoInteractions(service);
	}

	@Test
	void put_withANoteOf280Characters_isAccepted() throws Exception {
		given(service.answer(eq(TOKEN), any())).willReturn(response(AttendanceResponse.GOING, true));

		mockMvc.perform(withToken(put(URL)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"response\":\"GOING\",\"note\":\"" + "x".repeat(280) + "\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void put_whenRepliesAreClosed_returns409WithCode() throws Exception {
		given(service.answer(eq(TOKEN), any()))
			.willThrow(new ConflictException("Replies are closed for this event.", "attendance.closed", null));

		mockMvc.perform(withToken(put(URL)).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("attendance.closed"));
	}

}
