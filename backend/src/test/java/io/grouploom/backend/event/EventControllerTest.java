package io.grouploom.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.auth.CurrentUserArgumentResolver;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.config.WebConfig;
import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.dto.EventResponse;
import io.grouploom.backend.event.dto.EventTypeSummary;
import io.grouploom.backend.eventtype.EventTypeColor;

@WebMvcTest(EventController.class)
@Import({ SecurityConfig.class, WebConfig.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class })
class EventControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();
	private static final UUID ID = UUID.randomUUID();
	private static final UUID TYPE_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired MockMvc mockMvc;
	@MockitoBean EventService service;

	private static RequestPostProcessor auth() {
		return jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", "ORGANIZER"));
	}

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api").with(auth());
	}

	private static EventResponse response() {
		return new EventResponse(ID, "Practice", "Weekly", "Gym", Instant.parse("2026-11-02T09:00:00Z"),
				Instant.parse("2026-11-02T11:00:00Z"), LocalDateTime.parse("2026-11-02T10:00:00"),
				LocalDateTime.parse("2026-11-02T12:00:00"), "Europe/Madrid",
				new EventTypeSummary(TYPE_ID, "Training", EventTypeColor.GREEN, false), 0, NOW, NOW);
	}

	private static String body(String overrides) {
		// Start from a valid body and let each test replace or drop fields.
		java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
		fields.put("eventTypeId", "\"" + TYPE_ID + "\"");
		fields.put("title", "\"Practice\"");
		fields.put("localStart", "\"2026-11-02T10:00:00\"");
		fields.put("localEnd", "\"2026-11-02T12:00:00\"");
		fields.put("timeZone", "\"Europe/Madrid\"");
		fields.put("version", "0");
		for (String override : overrides.isEmpty() ? new String[0] : overrides.split("\\|")) {
			int at = override.indexOf('=');
			String key = override.substring(0, at);
			String value = override.substring(at + 1);
			if (value.equals("<drop>")) {
				fields.remove(key);
			}
			else {
				fields.put(key, value);
			}
		}
		StringBuilder json = new StringBuilder("{");
		fields.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"').append(k).append("\":").append(v));
		return json.append('}').toString();
	}

	private void expectCreateField(String overrides, String field) throws Exception {
		mockMvc.perform(api(post("/api/events")).contentType(MediaType.APPLICATION_JSON).content(body(overrides + (overrides.isEmpty() ? "" : "|") + "version=<drop>")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());
	}

	// --- list ---

	@Test
	void list_withDefaults_returnsUpcomingAsPagedModel() throws Exception {
		given(service.list(any(), any(), any(), any())).willReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1));

		mockMvc.perform(api(get("/api/events")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].title").value("Practice"))
			.andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.page.size").value(20));

		ArgumentCaptor<CurrentUser> user = ArgumentCaptor.forClass(CurrentUser.class);
		verify(service).list(user.capture(), eq(When.UPCOMING), eq(null), any(Pageable.class));
		assertThat(user.getValue().groupId()).isEqualTo(GROUP_ID);
	}

	@Test
	void list_withWhenTypeAndPaging_passesThemToService() throws Exception {
		given(service.list(any(), any(), any(), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/events?when=past&eventTypeId=" + TYPE_ID + "&page=2&size=5"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), eq(When.PAST), eq(TYPE_ID), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort().isSorted()).isFalse();
	}

	@Test
	void list_withHugePageSize_isCappedAt100() throws Exception {
		given(service.list(any(), any(), any(), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/events?size=5000"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), any(), any(), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	void list_withInvalidWhen_returns400() throws Exception {
		mockMvc.perform(api(get("/api/events?when=someday")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='when')]").exists());
	}

	@Test
	void list_withClientSort_ignoresIt() throws Exception {
		given(service.list(any(), any(), any(), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/events?sort=title,desc"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), any(), any(), pageable.capture());
		assertThat(pageable.getValue().getSort().isSorted()).isFalse();
	}

	@Test
	void list_withoutToken_returns401ProblemJson() throws Exception {
		mockMvc.perform(get("/api/events").servletPath("/api"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// --- get ---

	@Test
	void get_withExistingId_returnsTheDocumentedShape() throws Exception {
		given(service.get(any(), eq(ID))).willReturn(response());

		mockMvc.perform(api(get("/api/events/" + ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ID.toString()))
			.andExpect(jsonPath("$.start").value("2026-11-02T09:00:00Z"))
			.andExpect(jsonPath("$.end").value("2026-11-02T11:00:00Z"))
			.andExpect(jsonPath("$.localStart").value("2026-11-02T10:00:00"))
			.andExpect(jsonPath("$.localEnd").value("2026-11-02T12:00:00"))
			.andExpect(jsonPath("$.timeZone").value("Europe/Madrid"))
			.andExpect(jsonPath("$.eventType.id").value(TYPE_ID.toString()))
			.andExpect(jsonPath("$.eventType.name").value("Training"))
			.andExpect(jsonPath("$.eventType.color").value("green"))
			.andExpect(jsonPath("$.eventType.archived").value(false))
			.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void get_withoutOptionalFields_omitsThemInsteadOfSendingNull() throws Exception {
		EventResponse r = response();
		given(service.get(any(), eq(ID))).willReturn(new EventResponse(r.id(), r.title(), null, null, r.start(), r.end(),
				r.localStart(), r.localEnd(), r.timeZone(), r.eventType(), 0, NOW, NOW));

		mockMvc.perform(api(get("/api/events/" + ID)))
			.andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("description"))))
			.andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("location"))));
	}

	@Test
	void get_withEventFromAnotherGroup_returns404ProblemJson() throws Exception {
		given(service.get(any(), eq(ID))).willThrow(new NotFoundException("Event not found."));

		mockMvc.perform(api(get("/api/events/" + ID)))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void get_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/events/" + ID).servletPath("/api")).andExpect(status().isUnauthorized());
	}

	// --- create ---

	@Test
	void create_withValidBody_returns201WithLocation() throws Exception {
		given(service.create(any(), any())).willReturn(response());

		mockMvc.perform(api(post("/api/events")).contentType(MediaType.APPLICATION_JSON).content(body("version=<drop>|description=\"Weekly\"|location=\"Gym\"")))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/events/" + ID)))
			.andExpect(jsonPath("$.title").value("Practice"));
	}

	@Test
	void create_withBlankTitle_returns400WithFieldError() throws Exception {
		expectCreateField("title=\"  \"", "title");
	}

	@Test
	void create_withTitleOver120Chars_returns400() throws Exception {
		expectCreateField("title=\"" + "x".repeat(121) + "\"", "title");
	}

	@Test
	void create_withMissingType_returns400() throws Exception {
		expectCreateField("eventTypeId=<drop>", "eventTypeId");
	}

	@Test
	void create_withInvalidTimeZone_returns400() throws Exception {
		expectCreateField("timeZone=\"Mars/Olympus\"", "timeZone");
	}

	@Test
	void create_withBlankTimeZone_returns400() throws Exception {
		expectCreateField("timeZone=\" \"", "timeZone");
	}

	@Test
	void create_withEndNotAfterStart_returns400OnLocalEnd() throws Exception {
		expectCreateField("localEnd=\"2026-11-02T10:00:00\"", "localEnd");
		expectCreateField("localEnd=\"2026-11-02T09:00:00\"", "localEnd");
	}

	@Test
	void create_withMissingTimes_returns400() throws Exception {
		expectCreateField("localStart=<drop>", "localStart");
		expectCreateField("localEnd=<drop>", "localEnd");
	}

	@Test
	void create_withMalformedDateTime_returns400ProblemJson() throws Exception {
		mockMvc.perform(api(post("/api/events")).contentType(MediaType.APPLICATION_JSON)
				.content(body("version=<drop>|localStart=\"next tuesday\"")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void create_withDescriptionOver2000Chars_returns400() throws Exception {
		expectCreateField("description=\"" + "d".repeat(2001) + "\"", "description");
	}

	@Test
	void create_withLocationOver200Chars_returns400() throws Exception {
		expectCreateField("location=\"" + "l".repeat(201) + "\"", "location");
	}

	@Test
	void create_withServiceBadRequest_returns400WithCodeAndFieldErrors() throws Exception {
		given(service.create(any(), any())).willThrow(new BadRequestException("The start can't be in the past.", "event.start-in-past", "localStart"));

		mockMvc.perform(api(post("/api/events")).contentType(MediaType.APPLICATION_JSON).content(body("version=<drop>")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("event.start-in-past"))
			.andExpect(jsonPath("$.errors[0].field").value("localStart"));
	}

	@Test
	void create_withoutToken_returns401() throws Exception {
		mockMvc.perform(post("/api/events").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body("version=<drop>")))
			.andExpect(status().isUnauthorized());
	}

	// --- update ---

	@Test
	void update_withValidBody_returns200() throws Exception {
		given(service.update(any(), eq(ID), any())).willReturn(response());

		mockMvc.perform(api(put("/api/events/" + ID)).contentType(MediaType.APPLICATION_JSON).content(body("")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Practice"));
	}

	@Test
	void update_withMissingVersion_returns400() throws Exception {
		mockMvc.perform(api(put("/api/events/" + ID)).contentType(MediaType.APPLICATION_JSON).content(body("version=<drop>")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='version')]").exists());
	}

	@Test
	void update_withStaleVersion_returns409WithStaleCode() throws Exception {
		given(service.update(any(), eq(ID), any())).willThrow(new ConflictException("stale", "event.stale", null));

		mockMvc.perform(api(put("/api/events/" + ID)).contentType(MediaType.APPLICATION_JSON).content(body("")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("event.stale"))
			.andExpect(jsonPath("$.field").doesNotExist());
	}

	@Test
	void update_withEventFromAnotherGroup_returns404() throws Exception {
		given(service.update(any(), eq(ID), any())).willThrow(new NotFoundException("Event not found."));

		mockMvc.perform(api(put("/api/events/" + ID)).contentType(MediaType.APPLICATION_JSON).content(body("")))
			.andExpect(status().isNotFound());
	}

	@Test
	void update_withoutToken_returns401() throws Exception {
		mockMvc.perform(put("/api/events/" + ID).servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body("")))
			.andExpect(status().isUnauthorized());
	}

	// --- delete ---

	@Test
	void delete_withOwnEvent_returns204() throws Exception {
		mockMvc.perform(api(delete("/api/events/" + ID))).andExpect(status().isNoContent());

		verify(service).delete(any(), eq(ID));
	}

	@Test
	void delete_withEventFromAnotherGroup_returns404() throws Exception {
		org.mockito.Mockito.doThrow(new NotFoundException("Event not found.")).when(service).delete(any(), eq(ID));

		mockMvc.perform(api(delete("/api/events/" + ID))).andExpect(status().isNotFound());
	}

	@Test
	void delete_withoutToken_returns401() throws Exception {
		mockMvc.perform(delete("/api/events/" + ID).servletPath("/api")).andExpect(status().isUnauthorized());
	}

}
