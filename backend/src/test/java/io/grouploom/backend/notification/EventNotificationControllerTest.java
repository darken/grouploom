package io.grouploom.backend.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.grouploom.backend.auth.CurrentUserArgumentResolver;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.config.WebConfig;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.notification.dto.AudienceResponse;
import io.grouploom.backend.notification.dto.DeliveryCounts;
import io.grouploom.backend.notification.dto.EventNotificationResponse;

@WebMvcTest(EventNotificationController.class)
@Import({ SecurityConfig.class, WebConfig.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class })
class EventNotificationControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final UUID ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final String BASE = "/api/events/" + EVENT_ID + "/notifications";

	@Autowired MockMvc mockMvc;
	@MockitoBean EventNotificationService service;

	private static RequestPostProcessor auth() {
		return jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", "ORGANIZER"));
	}

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api").with(auth());
	}

	private static EventNotificationResponse response() {
		return new EventNotificationResponse(ID, NotificationKind.INVITATION, NotificationStatus.SCHEDULED, NOW,
				LocalDateTime.parse("2026-01-01T01:00:00"), "Europe/Madrid", null, null, null, DeliveryCounts.NONE, NOW, null);
	}

	private static String json(String timing, String extra) {
		return "{\"kind\":\"REMINDER\",\"timing\":\"" + timing + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	// --- list ---

	@Test
	void list_returns200WithTheNotifications() throws Exception {
		given(service.list(any(), eq(EVENT_ID))).willReturn(List.of(response()));

		mockMvc.perform(api(get(BASE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(ID.toString()))
			.andExpect(jsonPath("$[0].delivery.sent").value(0))
			.andExpect(jsonPath("$[0].localSendAt").value("2026-01-01T01:00:00"));
	}

	@Test
	void list_forAnotherGroupsEvent_returns404() throws Exception {
		given(service.list(any(), eq(EVENT_ID))).willThrow(new NotFoundException("Event not found."));

		mockMvc.perform(api(get(BASE))).andExpect(status().isNotFound());
	}

	@Test
	void list_withoutToken_returns401() throws Exception {
		mockMvc.perform(get(BASE).servletPath("/api")).andExpect(status().isUnauthorized());
	}

	// --- audience ---

	@Test
	void audience_returns200() throws Exception {
		given(service.audience(any(), eq(EVENT_ID), eq(NotificationKind.INVITATION))).willReturn(new AudienceResponse(18, 24));

		mockMvc.perform(api(get(BASE + "/audience")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eligible").value(18))
			.andExpect(jsonPath("$.total").value(24));
	}

	@Test
	void audience_withKindReminder_asksForTheReminderAudience() throws Exception {
		given(service.audience(any(), eq(EVENT_ID), eq(NotificationKind.REMINDER))).willReturn(new AudienceResponse(15, 24));

		mockMvc.perform(api(get(BASE + "/audience?kind=REMINDER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eligible").value(15));
	}

	@Test
	void audience_withUnknownKind_returns400() throws Exception {
		mockMvc.perform(api(get(BASE + "/audience?kind=NOPE"))).andExpect(status().isBadRequest());
	}

	@Test
	void audience_forAnotherGroupsEvent_returns404() throws Exception {
		given(service.audience(any(), eq(EVENT_ID), any())).willThrow(new NotFoundException("Event not found."));

		mockMvc.perform(api(get(BASE + "/audience"))).andExpect(status().isNotFound());
	}

	@Test
	void audience_withoutToken_returns401() throws Exception {
		mockMvc.perform(get(BASE + "/audience").servletPath("/api")).andExpect(status().isUnauthorized());
	}

	// --- schedule ---

	@Test
	void schedule_now_returns201WithLocation() throws Exception {
		given(service.schedule(any(), eq(EVENT_ID), any())).willReturn(response());

		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(json("NOW", "")))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.endsWith(BASE + "/" + ID)))
			.andExpect(jsonPath("$.id").value(ID.toString()));
	}

	@Test
	void schedule_passesTheRequestToTheService() throws Exception {
		given(service.schedule(any(), eq(EVENT_ID), any())).willReturn(response());

		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON)
			.content(json("BEFORE_START", "\"offsetMinutes\":1440,\"message\":\"Bring water\"")))
			.andExpect(status().isCreated());

		org.mockito.ArgumentCaptor<io.grouploom.backend.notification.dto.ScheduleNotificationRequest> captor = org.mockito.ArgumentCaptor
			.forClass(io.grouploom.backend.notification.dto.ScheduleNotificationRequest.class);
		verify(service).schedule(any(), eq(EVENT_ID), captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().offsetMinutes()).isEqualTo(1440);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().message()).isEqualTo("Bring water");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"{\"timing\":\"NOW\"}|kind",
			"{\"kind\":\"INVITATION\"}|timing",
			"{\"kind\":\"CANCELLATION\",\"timing\":\"NOW\"}|kind",
			"{\"kind\":\"REMINDER\",\"timing\":\"BEFORE_START\"}|offsetMinutes",
			"{\"kind\":\"REMINDER\",\"timing\":\"AT\"}|localSendAt",
			"{\"kind\":\"REMINDER\",\"timing\":\"NOW\",\"offsetMinutes\":60}|offsetMinutes",
			"{\"kind\":\"REMINDER\",\"timing\":\"NOW\",\"localSendAt\":\"2026-11-01T10:00:00\"}|localSendAt",
			"{\"kind\":\"REMINDER\",\"timing\":\"AT\",\"localSendAt\":\"2026-11-01T10:00:00\",\"offsetMinutes\":60}|offsetMinutes",
			"{\"kind\":\"REMINDER\",\"timing\":\"BEFORE_START\",\"offsetMinutes\":14}|offsetMinutes",
			"{\"kind\":\"REMINDER\",\"timing\":\"BEFORE_START\",\"offsetMinutes\":43201}|offsetMinutes" })
	void schedule_withInvalidCombination_returns400OnTheField(String body, String field) throws Exception {
		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());
	}

	@Test
	void schedule_withOversizedMessage_returns400OnMessage() throws Exception {
		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON)
			.content(json("NOW", "\"message\":\"" + "x".repeat(1001) + "\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='message')]").exists());
	}

	@Test
	void schedule_withMessageOfExactlyTheLimit_isAccepted() throws Exception {
		given(service.schedule(any(), eq(EVENT_ID), any())).willReturn(response());

		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON)
			.content(json("NOW", "\"message\":\"" + "x".repeat(1000) + "\"")))
			.andExpect(status().isCreated());
	}

	@Test
	void schedule_whenTheServiceRejectsTheSendTime_returns400WithCode() throws Exception {
		given(service.schedule(any(), eq(EVENT_ID), any())).willThrow(new io.grouploom.backend.error.BadRequestException(
				"no", "notification.send-at-invalid", "offsetMinutes"));

		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON)
			.content(json("BEFORE_START", "\"offsetMinutes\":60")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("notification.send-at-invalid"))
			.andExpect(jsonPath("$.errors[0].field").value("offsetMinutes"));
	}

	@Test
	void schedule_atTheLimit_returns409() throws Exception {
		given(service.schedule(any(), eq(EVENT_ID), any())).willThrow(new ConflictException("no", "notification.limit", null));

		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(json("NOW", "")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("notification.limit"));
	}

	@Test
	void schedule_forAnotherGroupsEvent_returns404() throws Exception {
		given(service.schedule(any(), eq(EVENT_ID), any())).willThrow(new NotFoundException("Event not found."));

		mockMvc.perform(api(post(BASE)).contentType(MediaType.APPLICATION_JSON).content(json("NOW", "")))
			.andExpect(status().isNotFound());
	}

	@Test
	void schedule_withoutToken_returns401() throws Exception {
		mockMvc.perform(post(BASE).servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(json("NOW", "")))
			.andExpect(status().isUnauthorized());
	}

	// --- unschedule ---

	@Test
	void unschedule_returns204() throws Exception {
		mockMvc.perform(api(delete(BASE + "/" + ID))).andExpect(status().isNoContent());

		verify(service).unschedule(any(), eq(EVENT_ID), eq(ID));
	}

	@Test
	void unschedule_afterItWasSent_returns409() throws Exception {
		doThrow(new ConflictException("no", "notification.already-sent", null)).when(service).unschedule(any(), eq(EVENT_ID),
				eq(ID));

		mockMvc.perform(api(delete(BASE + "/" + ID)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("notification.already-sent"));
	}

	@Test
	void unschedule_aMissingNotification_returns404() throws Exception {
		doThrow(new NotFoundException("Notification not found.")).when(service).unschedule(any(), eq(EVENT_ID), eq(ID));

		mockMvc.perform(api(delete(BASE + "/" + ID))).andExpect(status().isNotFound());
	}

	@Test
	void unschedule_withoutToken_returns401() throws Exception {
		mockMvc.perform(delete(BASE + "/" + ID).servletPath("/api")).andExpect(status().isUnauthorized());
	}

}
