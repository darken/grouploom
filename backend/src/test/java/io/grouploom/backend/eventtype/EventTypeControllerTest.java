package io.grouploom.backend.eventtype;

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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.auth.CurrentUserArgumentResolver;
import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.config.WebConfig;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.eventtype.dto.EventTypeResponse;

@WebMvcTest(EventTypeController.class)
@Import({ SecurityConfig.class, WebConfig.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class })
class EventTypeControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();
	private static final UUID ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final String VALID_CREATE = "{\"name\":\"Practice\",\"description\":\"Weekly\",\"color\":\"green\",\"defaultDurationMinutes\":90}";
	private static final String VALID_UPDATE = "{\"name\":\"Practice\",\"color\":\"green\",\"archived\":false,\"version\":0}";

	@Autowired MockMvc mockMvc;
	@MockitoBean EventTypeService service;

	private static RequestPostProcessor auth() {
		return jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", "ORGANIZER"));
	}

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api").with(auth());
	}

	private static EventTypeResponse response() {
		return new EventTypeResponse(ID, "Practice", "Weekly", EventTypeColor.GREEN, 90, false, 0, NOW, NOW);
	}

	// --- list ---

	@Test
	void list_withDefaults_returnsActiveTypesAsPagedModel() throws Exception {
		given(service.list(any(), eq(false), any())).willReturn(new PageImpl<>(List.of(response()), org.springframework.data.domain.PageRequest.of(0, 20), 1));

		mockMvc.perform(api(get("/api/event-types")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].name").value("Practice"))
			.andExpect(jsonPath("$.content[0].color").value("green"))
			.andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.page.size").value(20));

		ArgumentCaptor<CurrentUser> user = ArgumentCaptor.forClass(CurrentUser.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(user.capture(), eq(false), pageable.capture());
		assertThat(user.getValue().groupId()).isEqualTo(GROUP_ID);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
		assertThat(pageable.getValue().getSort().getOrderFor("name")).isNotNull();
	}

	@Test
	void list_withArchivedTrueAndPaging_passesThemToService() throws Exception {
		given(service.list(any(), eq(true), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/event-types?archived=true&page=2&size=5&sort=createdAt,desc")))
			.andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), eq(true), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
	}

	@Test
	void list_withHugePageSize_isCappedAt100() throws Exception {
		given(service.list(any(), eq(false), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/event-types?size=5000"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), eq(false), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	void list_withUnknownSortProperty_returns400() throws Exception {
		mockMvc.perform(api(get("/api/event-types?sort=password,asc")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void list_withoutToken_returns401ProblemJson() throws Exception {
		mockMvc.perform(get("/api/event-types").servletPath("/api"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// --- get ---

	@Test
	void get_withExistingId_returns200() throws Exception {
		given(service.get(any(), eq(ID))).willReturn(response());

		mockMvc.perform(api(get("/api/event-types/" + ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ID.toString()))
			.andExpect(jsonPath("$.archived").value(false))
			.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void get_withoutOptionalFields_omitsThemInsteadOfSendingNull() throws Exception {
		given(service.get(any(), eq(ID))).willReturn(
				new EventTypeResponse(ID, "Practice", null, EventTypeColor.BLUE, null, false, 0, NOW, NOW));

		mockMvc.perform(api(get("/api/event-types/" + ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Practice"))
			.andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("description"))))
			.andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("defaultDurationMinutes"))));
	}

	@Test
	void get_withTypeFromAnotherGroup_returns404ProblemJson() throws Exception {
		given(service.get(any(), eq(ID))).willThrow(new NotFoundException("Event type not found."));

		mockMvc.perform(api(get("/api/event-types/" + ID)))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// --- create ---

	@Test
	void create_withValidBody_returns201WithLocation() throws Exception {
		given(service.create(any(), any())).willReturn(response());

		mockMvc.perform(api(post("/api/event-types")).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/event-types/" + ID)))
			.andExpect(jsonPath("$.name").value("Practice"));
	}

	@Test
	void create_withInvalidFields_returns400WithFieldErrors() throws Exception {
		String longName = "x".repeat(81);
		String body = "{\"name\":\"" + longName + "\",\"description\":\"" + "d".repeat(501) + "\",\"color\":\"blue\",\"defaultDurationMinutes\":4}";

		mockMvc.perform(api(post("/api/event-types")).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='name')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='description')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='defaultDurationMinutes')]").exists());
	}

	@Test
	void create_withBlankName_returns400() throws Exception {
		mockMvc.perform(api(post("/api/event-types")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  \",\"color\":\"blue\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
	}

	@Test
	void create_withDurationAboveMaximum_returns400() throws Exception {
		mockMvc.perform(api(post("/api/event-types")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"A\",\"color\":\"blue\",\"defaultDurationMinutes\":1441}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='defaultDurationMinutes')]").exists());
	}

	@Test
	void create_withUnknownColor_returns400ProblemJson() throws Exception {
		mockMvc.perform(api(post("/api/event-types")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"A\",\"color\":\"magenta\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void create_withMissingColor_returns400() throws Exception {
		mockMvc.perform(api(post("/api/event-types")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='color')]").exists());
	}

	@Test
	void create_withDuplicateName_returns409WithCodeAndField() throws Exception {
		given(service.create(any(), any())).willThrow(new ConflictException("taken", "event-type.name-taken", "name"));

		mockMvc.perform(api(post("/api/event-types")).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("event-type.name-taken"))
			.andExpect(jsonPath("$.field").value("name"));
	}

	@Test
	void create_withoutToken_returns401() throws Exception {
		mockMvc.perform(post("/api/event-types").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
			.andExpect(status().isUnauthorized());
	}

	// --- update ---

	@Test
	void update_withValidBody_returns200() throws Exception {
		given(service.update(any(), eq(ID), any())).willReturn(response());

		mockMvc.perform(api(put("/api/event-types/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Practice"));
	}

	@Test
	void update_withMissingVersionAndArchived_returns400() throws Exception {
		mockMvc.perform(api(put("/api/event-types/" + ID)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Practice\",\"color\":\"green\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='version')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field=='archived')]").exists());
	}

	@Test
	void update_withStaleVersion_returns409WithStaleCode() throws Exception {
		given(service.update(any(), eq(ID), any())).willThrow(new ConflictException("stale", "event-type.stale", null));

		mockMvc.perform(api(put("/api/event-types/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("event-type.stale"))
			.andExpect(jsonPath("$.field").doesNotExist());
	}

	@Test
	void update_withTypeFromAnotherGroup_returns404() throws Exception {
		given(service.update(any(), eq(ID), any())).willThrow(new NotFoundException("Event type not found."));

		mockMvc.perform(api(put("/api/event-types/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isNotFound());
	}

	// --- delete ---

	@Test
	void delete_withOwnType_returns204() throws Exception {
		mockMvc.perform(api(delete("/api/event-types/" + ID))).andExpect(status().isNoContent());

		verify(service).delete(any(), eq(ID));
	}

	@Test
	void delete_withTypeFromAnotherGroup_returns404() throws Exception {
		org.mockito.Mockito.doThrow(new NotFoundException("Event type not found.")).when(service).delete(any(), eq(ID));

		mockMvc.perform(api(delete("/api/event-types/" + ID))).andExpect(status().isNotFound());
	}

	@Test
	void delete_withoutToken_returns401() throws Exception {
		mockMvc.perform(delete("/api/event-types/" + ID).servletPath("/api")).andExpect(status().isUnauthorized());
	}

}
