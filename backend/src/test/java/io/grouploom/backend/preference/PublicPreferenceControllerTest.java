package io.grouploom.backend.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

import io.grouploom.backend.config.JwtConfig;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.preference.dto.PreferenceEventType;
import io.grouploom.backend.preference.dto.PublicPreferencesResponse;
import io.grouploom.backend.preference.dto.UpdatePreferencesRequest;

@WebMvcTest(PublicPreferenceController.class)
@Import({ SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class })
class PublicPreferenceControllerTest {

	private static final String TOKEN = "member-token-value";
	private static final UUID TYPE_ID = UUID.randomUUID();
	private static final String VALID_PUT = "{\"invitationsPaused\":true,\"eventTypes\":[{\"id\":\"" + TYPE_ID
			+ "\",\"subscribed\":false}]}";

	@Autowired MockMvc mockMvc;
	@MockitoBean PreferenceService service;

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api");
	}

	private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder b) {
		return api(b).header("X-Member-Token", TOKEN);
	}

	private static PublicPreferencesResponse response(boolean paused) {
		return new PublicPreferencesResponse("Ana", "a••@example.com", "Chess Club", paused,
				List.of(new PreferenceEventType(TYPE_ID, "Practice", null, EventTypeColor.BLUE, true)));
	}

	// --- GET ---

	@Test
	void get_withMemberTokenAndNoBearer_returns200WithPreferences() throws Exception {
		given(service.get(TOKEN)).willReturn(response(false));

		mockMvc.perform(withToken(get("/api/public/preferences")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.memberName").value("Ana"))
			.andExpect(jsonPath("$.maskedEmail").value("a••@example.com"))
			.andExpect(jsonPath("$.groupName").value("Chess Club"))
			.andExpect(jsonPath("$.invitationsPaused").value(false))
			.andExpect(jsonPath("$.eventTypes[0].id").value(TYPE_ID.toString()))
			.andExpect(jsonPath("$.eventTypes[0].name").value("Practice"))
			.andExpect(jsonPath("$.eventTypes[0].color").value("blue"))
			.andExpect(jsonPath("$.eventTypes[0].subscribed").value(true))
			.andExpect(jsonPath("$.eventTypes[0].description").doesNotExist())
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
	}

	@Test
	void get_withoutMemberTokenHeader_returns401Invalid() throws Exception {
		mockMvc.perform(api(get("/api/public/preferences")))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		verifyNoInteractions(service);
	}

	@Test
	void get_withBlankMemberTokenHeader_returns401Invalid() throws Exception {
		mockMvc.perform(api(get("/api/public/preferences")).header("X-Member-Token", "  "))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
	}

	@Test
	void get_withInvalidToken_returns401Invalid() throws Exception {
		given(service.get(TOKEN)).willThrow(new InvalidMemberTokenException("member-token.invalid"));

		mockMvc.perform(withToken(get("/api/public/preferences")))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
	}

	@Test
	void get_withExpiredToken_returns401Expired() throws Exception {
		given(service.get(TOKEN)).willThrow(new InvalidMemberTokenException("member-token.expired"));

		mockMvc.perform(withToken(get("/api/public/preferences")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.expired"));
	}

	@Test
	void get_withOrganizerBearerTokenButNoMemberToken_returns401Invalid() throws Exception {
		mockMvc.perform(api(get("/api/public/preferences")).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
				.claim("gid", UUID.randomUUID().toString()).claim("role", "ORGANIZER"))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		verifyNoInteractions(service);
	}

	// --- PUT ---

	@Test
	void put_withValidBody_returns200WithFreshPreferences() throws Exception {
		given(service.update(eq(TOKEN), any())).willReturn(response(true));

		mockMvc.perform(withToken(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invitationsPaused").value(true));

		ArgumentCaptor<UpdatePreferencesRequest> request = ArgumentCaptor.forClass(UpdatePreferencesRequest.class);
		verify(service).update(eq(TOKEN), request.capture());
		assertThat(request.getValue().invitationsPaused()).isTrue();
		assertThat(request.getValue().eventTypes()).singleElement().satisfies(i -> {
			assertThat(i.id()).isEqualTo(TYPE_ID);
			assertThat(i.subscribed()).isFalse();
		});
	}

	@Test
	void put_withoutMemberTokenHeader_returns401Invalid() throws Exception {
		mockMvc.perform(api(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		verifyNoInteractions(service);
	}

	@Test
	void put_withExpiredToken_returns401Expired() throws Exception {
		given(service.update(eq(TOKEN), any())).willThrow(new InvalidMemberTokenException("member-token.expired"));

		mockMvc.perform(withToken(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.expired"));
	}

	@Test
	void put_withNullInvitationsPaused_returns400WithFieldError() throws Exception {
		mockMvc.perform(withToken(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventTypes\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='invitationsPaused')]").exists());
		verifyNoInteractions(service);
	}

	@Test
	void put_withNullEventTypesList_returns400WithFieldError() throws Exception {
		mockMvc.perform(withToken(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"invitationsPaused\":false}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='eventTypes')]").exists());
	}

	@Test
	void put_withItemMissingIdOrSubscribed_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(withToken(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"invitationsPaused\":false,\"eventTypes\":[{\"subscribed\":true},{\"id\":\"" + TYPE_ID + "\"}]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='eventTypes[0].id')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='eventTypes[1].subscribed')]").exists());
	}

	@Test
	void put_withMoreThan200EventTypes_returns400() throws Exception {
		String items = String.join(",", java.util.Collections.nCopies(201,
				"{\"id\":\"" + TYPE_ID + "\",\"subscribed\":true}"));

		mockMvc.perform(withToken(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"invitationsPaused\":false,\"eventTypes\":[" + items + "]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='eventTypes')]").exists());
	}

	@Test
	void put_withUnknownEventType_returns400WithCode() throws Exception {
		given(service.update(eq(TOKEN), any())).willThrow(new io.grouploom.backend.error.BadRequestException(
				"Unknown event type.", "preference.unknown-event-type", "eventTypes"));

		mockMvc.perform(withToken(put("/api/public/preferences")).contentType(MediaType.APPLICATION_JSON).content(VALID_PUT))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("preference.unknown-event-type"))
			.andExpect(jsonPath("$.errors[0].field").value("eventTypes"));
	}

}
