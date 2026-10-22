package io.grouploom.backend.eventtype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import io.grouploom.backend.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventTypeTenancyIntegrationTest {

	@Autowired MockMvc mockMvc;

	private String signUp(String label) throws Exception {
		String email = label + "-" + System.nanoTime() + "@example.com";
		String body = "{\"groupName\":\"" + label + "\",\"displayName\":\"" + label + "\",\"email\":\"" + email
				+ "\",\"password\":\"correct horse battery\"}";
		String json = mockMvc.perform(post("/api/auth/signup").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.accessToken");
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
		return b.servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private String create(String token, String name) throws Exception {
		String json = mockMvc.perform(as(post("/api/event-types"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"color\":\"blue\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	@Test
	void groupB_cannotListGetUpdateOrDeleteGroupAsEventTypes() throws Exception {
		String a = signUp("A");
		String b = signUp("B");
		String id = create(a, "Practice");

		mockMvc.perform(as(get("/api/event-types"), b)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(as(get("/api/event-types/" + id), b)).andExpect(status().isNotFound());
		mockMvc.perform(as(put("/api/event-types/" + id), b).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hijacked\",\"color\":\"red\",\"archived\":false,\"version\":0}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(as(delete("/api/event-types/" + id), b)).andExpect(status().isNotFound());

		mockMvc.perform(as(get("/api/event-types/" + id), a)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Practice"));
	}

	@Test
	void sameNameInTwoGroups_isAllowedButNotTwiceInOneGroup() throws Exception {
		String a = signUp("A");
		String b = signUp("B");
		create(a, "Practice");
		create(b, "Practice");

		mockMvc.perform(as(post("/api/event-types"), a).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  PRACTICE \",\"color\":\"red\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("event-type.name-taken"))
			.andExpect(jsonPath("$.field").value("name"));
	}

	@Test
	void lifecycle_createRenameArchiveUnarchiveDelete_worksWithVersioning() throws Exception {
		String token = signUp("A");
		String id = create(token, "Practice");

		String renamed = mockMvc.perform(as(put("/api/event-types/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Rehearsal\",\"color\":\"green\",\"defaultDurationMinutes\":60,\"archived\":false,\"version\":0}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(renamed, "$.name")).isEqualTo("Rehearsal");

		// Reusing the old version is a lost-update attempt.
		mockMvc.perform(as(put("/api/event-types/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Other\",\"color\":\"green\",\"archived\":false,\"version\":0}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("event-type.stale"));

		mockMvc.perform(as(put("/api/event-types/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Rehearsal\",\"color\":\"green\",\"archived\":true,\"version\":1}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
		mockMvc.perform(as(get("/api/event-types"), token)).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(as(get("/api/event-types?archived=true"), token)).andExpect(jsonPath("$.content.length()").value(1));

		mockMvc.perform(as(put("/api/event-types/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Rehearsal\",\"color\":\"green\",\"archived\":false,\"version\":2}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));

		mockMvc.perform(as(delete("/api/event-types/" + id), token)).andExpect(status().isNoContent());
		mockMvc.perform(as(get("/api/event-types/" + id), token)).andExpect(status().isNotFound());
	}

	@Test
	void get_withoutOptionalFields_omitsThemSoTheGeneratedTypesHold() throws Exception {
		String token = signUp("A");
		String id = create(token, "Practice");

		mockMvc.perform(as(get("/api/event-types/" + id), token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("description"))))
			.andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("defaultDurationMinutes"))));
	}

	@Test
	void list_withUnknownSortAndHugePage_isRejectedAndCapped() throws Exception {
		String token = signUp("A");

		mockMvc.perform(as(get("/api/event-types?sort=group,asc"), token)).andExpect(status().isBadRequest());
		mockMvc.perform(as(get("/api/event-types?size=100000"), token)).andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(100));
	}

}
