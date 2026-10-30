package io.grouploom.backend.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import io.grouploom.backend.attendance.dto.AttendanceRowResponse;
import io.grouploom.backend.attendance.dto.AttendanceRowResponse.MemberSummary;
import io.grouploom.backend.attendance.dto.AttendanceSummaryResponse;
import io.grouploom.backend.attendance.dto.SetAttendanceRequest;
import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.auth.CurrentUserArgumentResolver;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.config.WebConfig;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.NotFoundException;

@WebMvcTest(AttendanceController.class)
@Import({ SecurityConfig.class, WebConfig.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class })
class AttendanceControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final UUID MEMBER_ID = UUID.randomUUID();
	private static final Instant AT = Instant.parse("2026-11-01T10:00:00Z");
	private static final String BASE = "/api/events/" + EVENT_ID + "/attendance";

	@Autowired MockMvc mockMvc;
	@MockitoBean AttendanceService service;

	private static RequestPostProcessor auth(String role) {
		return jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", role));
	}

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api").with(auth("ORGANIZER"));
	}

	private static AttendanceRowResponse row(AttendanceResponse response) {
		return new AttendanceRowResponse(new MemberSummary(MEMBER_ID, "Ana", "ana@example.com"), response,
				response == null ? null : "Sorry", response == null ? null : AttendanceSource.ORGANIZER,
				response == null ? null : AT);
	}

	// --- summary ---

	@Test
	void summary_returns200WithTheCounts() throws Exception {
		given(service.summary(any(), eq(EVENT_ID))).willReturn(new AttendanceSummaryResponse(12, 3, 9, 24));

		mockMvc.perform(api(get(BASE + "/summary")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.going").value(12))
			.andExpect(jsonPath("$.notGoing").value(3))
			.andExpect(jsonPath("$.noReply").value(9))
			.andExpect(jsonPath("$.members").value(24));
		ArgumentCaptor<CurrentUser> user = ArgumentCaptor.forClass(CurrentUser.class);
		verify(service).summary(user.capture(), eq(EVENT_ID));
		assertThat(user.getValue().groupId()).isEqualTo(GROUP_ID);
	}

	@Test
	void summary_withoutBearer_returns401() throws Exception {
		mockMvc.perform(get(BASE + "/summary").servletPath("/api")).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void summary_forAnotherGroupsEvent_returns404() throws Exception {
		given(service.summary(any(), eq(EVENT_ID))).willThrow(new NotFoundException("Event not found."));

		mockMvc.perform(api(get(BASE + "/summary")))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// --- list ---

	@Test
	void list_returns200WithAPageAndDefaultsToAllMembersFiftyPerPage() throws Exception {
		given(service.list(any(), eq(EVENT_ID), any(), any())).willAnswer(i -> new PageImpl<>(
				List.of(row(AttendanceResponse.NOT_GOING), row(null)), i.<Pageable>getArgument(3), 2));

		mockMvc.perform(api(get(BASE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].member.name").value("Ana"))
			.andExpect(jsonPath("$.content[0].response").value("NOT_GOING"))
			.andExpect(jsonPath("$.content[0].source").value("ORGANIZER"))
			.andExpect(jsonPath("$.content[0].note").value("Sorry"))
			.andExpect(jsonPath("$.content[1].response").doesNotExist())
			.andExpect(jsonPath("$.page.totalElements").value(2));
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), eq(EVENT_ID), eq(AttendanceFilter.ALL), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
		assertThat(pageable.getValue().getPageNumber()).isZero();
		assertThat(pageable.getValue().getSort().isSorted()).isFalse();
	}

	@Test
	void list_passesTheFilterPageAndSizeThrough() throws Exception {
		given(service.list(any(), eq(EVENT_ID), any(), any())).willAnswer(i -> new PageImpl<>(List.of(), i.<Pageable>getArgument(3), 0));

		mockMvc.perform(api(get(BASE + "?filter=NO_REPLY&page=2&size=10"))).andExpect(status().isOk());

		verify(service).list(any(), eq(EVENT_ID), eq(AttendanceFilter.NO_REPLY), eq(PageRequest.of(2, 10)));
	}

	@Test
	void list_capsThePageSizeAt100() throws Exception {
		given(service.list(any(), eq(EVENT_ID), any(), any())).willAnswer(i -> new PageImpl<>(List.of(), i.<Pageable>getArgument(3), 0));

		mockMvc.perform(api(get(BASE + "?size=500"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), eq(EVENT_ID), any(), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	void list_ignoresASortParameter() throws Exception {
		given(service.list(any(), eq(EVENT_ID), any(), any())).willAnswer(i -> new PageImpl<>(List.of(), i.<Pageable>getArgument(3), 0));

		mockMvc.perform(api(get(BASE + "?sort=respondedAt,desc"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), eq(EVENT_ID), any(), pageable.capture());
		assertThat(pageable.getValue().getSort().isSorted()).isFalse();
	}

	@Test
	void list_withAnInvalidFilter_returns400() throws Exception {
		mockMvc.perform(api(get(BASE + "?filter=MAYBE")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verifyNoInteractions(service);
	}

	@Test
	void list_withoutBearer_returns401() throws Exception {
		mockMvc.perform(get(BASE).servletPath("/api")).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void list_forAnotherGroupsEvent_returns404() throws Exception {
		given(service.list(any(), eq(EVENT_ID), any(), any())).willThrow(new NotFoundException("Event not found."));

		mockMvc.perform(api(get(BASE))).andExpect(status().isNotFound());
	}

	// --- set ---

	@Test
	void set_returns200WithTheRow() throws Exception {
		given(service.set(any(), eq(EVENT_ID), eq(MEMBER_ID), any())).willReturn(row(AttendanceResponse.GOING));

		mockMvc.perform(api(put(BASE + "/" + MEMBER_ID)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"response\":\"GOING\",\"note\":\"Sorry\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.member.id").value(MEMBER_ID.toString()))
			.andExpect(jsonPath("$.response").value("GOING"));
		verify(service).set(any(), eq(EVENT_ID), eq(MEMBER_ID), eq(new SetAttendanceRequest(AttendanceResponse.GOING, "Sorry")));
	}

	@Test
	void set_withoutBearer_returns401() throws Exception {
		mockMvc.perform(put(BASE + "/" + MEMBER_ID).servletPath("/api").contentType(MediaType.APPLICATION_JSON)
				.content("{\"response\":\"GOING\"}"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void set_withNullResponse_returns400WithFieldError() throws Exception {
		mockMvc.perform(api(put(BASE + "/" + MEMBER_ID)).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"hi\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='response')]").exists());
		verifyNoInteractions(service);
	}

	@Test
	void set_withANoteOver280Characters_returns400WithFieldError() throws Exception {
		mockMvc.perform(api(put(BASE + "/" + MEMBER_ID)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"response\":\"GOING\",\"note\":\"" + "x".repeat(281) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='note')]").exists());
	}

	@Test
	void set_forAnotherGroupsMember_returns404() throws Exception {
		given(service.set(any(), eq(EVENT_ID), eq(MEMBER_ID), any())).willThrow(new NotFoundException("Member not found."));

		mockMvc.perform(api(put(BASE + "/" + MEMBER_ID)).contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"GOING\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void set_forACancelledEvent_returns409WithCode() throws Exception {
		given(service.set(any(), eq(EVENT_ID), eq(MEMBER_ID), any()))
			.willThrow(new ConflictException("This event is cancelled.", "attendance.closed", null));

		mockMvc.perform(api(put(BASE + "/" + MEMBER_ID)).contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"GOING\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("attendance.closed"));
	}

	// --- clear ---

	@Test
	void clear_returns204() throws Exception {
		mockMvc.perform(api(delete(BASE + "/" + MEMBER_ID))).andExpect(status().isNoContent());

		verify(service).clear(any(), eq(EVENT_ID), eq(MEMBER_ID));
	}

	@Test
	void clear_withoutBearer_returns401() throws Exception {
		mockMvc.perform(delete(BASE + "/" + MEMBER_ID).servletPath("/api")).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void clear_forAnotherGroupsMember_returns404() throws Exception {
		org.mockito.Mockito.doThrow(new NotFoundException("Member not found.")).when(service).clear(any(), eq(EVENT_ID), eq(MEMBER_ID));

		mockMvc.perform(api(delete(BASE + "/" + MEMBER_ID))).andExpect(status().isNotFound());
	}

	@Test
	void clear_forACancelledEvent_returns409WithCode() throws Exception {
		org.mockito.Mockito.doThrow(new ConflictException("This event is cancelled.", "attendance.closed", null))
			.when(service).clear(any(), eq(EVENT_ID), eq(MEMBER_ID));

		mockMvc.perform(api(delete(BASE + "/" + MEMBER_ID)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("attendance.closed"));
	}

}
