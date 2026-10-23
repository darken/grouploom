package io.grouploom.backend.member;

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
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.GlobalExceptionHandler;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.member.dto.MemberResponse;

@WebMvcTest(MemberController.class)
@Import({ SecurityConfig.class, WebConfig.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class })
class MemberControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();
	private static final UUID ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final String VALID_CREATE = "{\"name\":\"Ana Pérez\",\"email\":\"ana@example.com\"}";
	private static final String VALID_UPDATE = "{\"name\":\"Ana Pérez\",\"email\":\"ana@example.com\",\"version\":0}";

	@Autowired MockMvc mockMvc;
	@MockitoBean MemberService service;

	private static RequestPostProcessor auth() {
		return jwt().jwt(j -> j.subject(USER_ID.toString()).claim("gid", GROUP_ID.toString()).claim("role", "ORGANIZER"));
	}

	private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder b) {
		return b.servletPath("/api").with(auth());
	}

	private static MemberResponse response() {
		return new MemberResponse(ID, "Ana Pérez", "ana@example.com", 0, NOW, NOW);
	}

	private static String createJson(String name, String email) {
		return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
	}

	// --- list ---

	@Test
	void list_withDefaults_returnsMembersAsPagedModel() throws Exception {
		given(service.list(any(), any(), any())).willReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1));

		mockMvc.perform(api(get("/api/members")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].name").value("Ana Pérez"))
			.andExpect(jsonPath("$.content[0].email").value("ana@example.com"))
			.andExpect(jsonPath("$.page.totalElements").value(1))
			.andExpect(jsonPath("$.page.size").value(20));

		ArgumentCaptor<CurrentUser> user = ArgumentCaptor.forClass(CurrentUser.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(user.capture(), eq(null), pageable.capture());
		assertThat(user.getValue().groupId()).isEqualTo(GROUP_ID);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
		assertThat(pageable.getValue().getSort().getOrderFor("name")).isNotNull();
	}

	@Test
	void list_withQueryPagingAndSort_passesThemToService() throws Exception {
		given(service.list(any(), any(), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/members?q=ana&page=2&size=5&sort=createdAt,desc"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), eq("ana"), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
	}

	@Test
	void list_sortedByNameOrEmail_sortsCaseInsensitively() throws Exception {
		given(service.list(any(), any(), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/members?sort=email,desc"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), any(), pageable.capture());
		assertThat(pageable.getValue().getSort().getOrderFor("email").isIgnoreCase()).isTrue();
		assertThat(pageable.getValue().getSort().getOrderFor("email").isDescending()).isTrue();
	}

	@Test
	void list_withHugePageSize_isCappedAt100() throws Exception {
		given(service.list(any(), any(), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/members?size=5000"))).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).list(any(), any(), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	void list_withUnknownSortProperty_returns400() throws Exception {
		mockMvc.perform(api(get("/api/members?sort=password,asc")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void list_withQueryOver100Chars_returns400WithFieldError() throws Exception {
		mockMvc.perform(api(get("/api/members?q=" + "a".repeat(101))))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='q')]").exists());
	}

	@Test
	void list_withQueryOf100Chars_isAccepted() throws Exception {
		given(service.list(any(), any(), any())).willReturn(new PageImpl<>(List.of()));

		mockMvc.perform(api(get("/api/members?q=" + "a".repeat(100)))).andExpect(status().isOk());
	}

	@Test
	void list_withoutToken_returns401ProblemJson() throws Exception {
		mockMvc.perform(get("/api/members").servletPath("/api"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// --- get ---

	@Test
	void get_withExistingId_returns200() throws Exception {
		given(service.get(any(), eq(ID))).willReturn(response());

		mockMvc.perform(api(get("/api/members/" + ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ID.toString()))
			.andExpect(jsonPath("$.email").value("ana@example.com"))
			.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void get_withMemberFromAnotherGroup_returns404ProblemJson() throws Exception {
		given(service.get(any(), eq(ID))).willThrow(new NotFoundException("Member not found."));

		mockMvc.perform(api(get("/api/members/" + ID)))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void get_withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/members/" + ID).servletPath("/api")).andExpect(status().isUnauthorized());
	}

	// --- create ---

	@Test
	void create_withValidBody_returns201WithLocation() throws Exception {
		given(service.create(any(), any())).willReturn(response());

		mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/members/" + ID)))
			.andExpect(jsonPath("$.name").value("Ana Pérez"));
	}

	@Test
	void create_withPaddedNameAndEmail_trimsBeforeValidatingAndPassesTrimmedValues() throws Exception {
		given(service.create(any(), any())).willReturn(response());

		mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON)
				.content(createJson("  Ana Pérez ", "  ana@example.com ")))
			.andExpect(status().isCreated());

		ArgumentCaptor<io.grouploom.backend.member.dto.CreateMemberRequest> request = ArgumentCaptor
			.forClass(io.grouploom.backend.member.dto.CreateMemberRequest.class);
		verify(service).create(any(), request.capture());
		assertThat(request.getValue().name()).isEqualTo("Ana Pérez");
		assertThat(request.getValue().email()).isEqualTo("ana@example.com");
	}

	@Test
	void update_withPaddedEmail_trimsBeforeValidating() throws Exception {
		given(service.update(any(), eq(ID), any())).willReturn(response());

		mockMvc.perform(api(put("/api/members/" + ID)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\",\"email\":\" ana@example.com \",\"version\":0}"))
			.andExpect(status().isOk());
	}

	@Test
	void create_withBlankName_returns400WithFieldError() throws Exception {
		mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON).content(createJson("  ", "ana@example.com")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
	}

	@Test
	void create_withNameOver120Chars_returns400() throws Exception {
		mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON)
				.content(createJson("x".repeat(121), "ana@example.com")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
	}

	@Test
	void create_withBlankEmail_returns400() throws Exception {
		mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON).content(createJson("Ana", " ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
	}

	@Test
	void create_withMalformedEmail_returns400() throws Exception {
		for (String email : List.of("a@b", "plainaddress", "@example.com")) {
			mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON).content(createJson("Ana", email)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
		}
	}

	@Test
	void create_withEmailOver254Chars_returns400() throws Exception {
		String email = "a".repeat(250) + "@b.co";

		mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON).content(createJson("Ana", email)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
	}

	@Test
	void create_withDuplicateEmail_returns409WithCodeAndField() throws Exception {
		given(service.create(any(), any())).willThrow(new ConflictException("taken", "member.email-taken", "email"));

		mockMvc.perform(api(post("/api/members")).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("member.email-taken"))
			.andExpect(jsonPath("$.field").value("email"));
	}

	@Test
	void create_withoutToken_returns401() throws Exception {
		mockMvc.perform(post("/api/members").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
			.andExpect(status().isUnauthorized());
	}

	// --- update ---

	@Test
	void update_withValidBody_returns200() throws Exception {
		given(service.update(any(), eq(ID), any())).willReturn(response());

		mockMvc.perform(api(put("/api/members/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Ana Pérez"));
	}

	@Test
	void update_withMissingVersion_returns400() throws Exception {
		mockMvc.perform(api(put("/api/members/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='version')]").exists());
	}

	@Test
	void update_withMalformedEmail_returns400() throws Exception {
		mockMvc.perform(api(put("/api/members/" + ID)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\",\"email\":\"a@b\",\"version\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
	}

	@Test
	void update_withStaleVersion_returns409WithStaleCode() throws Exception {
		given(service.update(any(), eq(ID), any())).willThrow(new ConflictException("stale", "member.stale", null));

		mockMvc.perform(api(put("/api/members/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("member.stale"))
			.andExpect(jsonPath("$.field").doesNotExist());
	}

	@Test
	void update_withDuplicateEmail_returns409WithCodeAndField() throws Exception {
		given(service.update(any(), eq(ID), any())).willThrow(new ConflictException("taken", "member.email-taken", "email"));

		mockMvc.perform(api(put("/api/members/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("member.email-taken"))
			.andExpect(jsonPath("$.field").value("email"));
	}

	@Test
	void update_withMemberFromAnotherGroup_returns404() throws Exception {
		given(service.update(any(), eq(ID), any())).willThrow(new NotFoundException("Member not found."));

		mockMvc.perform(api(put("/api/members/" + ID)).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isNotFound());
	}

	@Test
	void update_withoutToken_returns401() throws Exception {
		mockMvc.perform(put("/api/members/" + ID).servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
			.andExpect(status().isUnauthorized());
	}

	// --- delete ---

	@Test
	void delete_withOwnMember_returns204() throws Exception {
		mockMvc.perform(api(delete("/api/members/" + ID))).andExpect(status().isNoContent());

		verify(service).delete(any(), eq(ID));
	}

	@Test
	void delete_withMemberFromAnotherGroup_returns404() throws Exception {
		org.mockito.Mockito.doThrow(new NotFoundException("Member not found.")).when(service).delete(any(), eq(ID));

		mockMvc.perform(api(delete("/api/members/" + ID))).andExpect(status().isNotFound());
	}

	@Test
	void delete_withoutToken_returns401() throws Exception {
		mockMvc.perform(delete("/api/members/" + ID).servletPath("/api")).andExpect(status().isUnauthorized());
	}

}
