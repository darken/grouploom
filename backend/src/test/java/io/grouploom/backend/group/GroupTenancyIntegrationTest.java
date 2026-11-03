package io.grouploom.backend.group;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import io.grouploom.backend.auth.service.TokenService;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;
import io.grouploom.backend.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GroupTenancyIntegrationTest {

	@Autowired MockMvc mockMvc;
	@Autowired UserRepository users;
	@Autowired GroupRepository groups;
	@Autowired TokenService tokens;

	private String signUp(String label, String language) throws Exception {
		String email = label + "-" + System.nanoTime() + "@example.com";
		String body = "{\"groupName\":\"" + label + "\",\"displayName\":\"" + label + "\",\"email\":\"" + email
				+ "\",\"password\":\"correct horse battery\"" + (language == null ? "" : ",\"language\":\"" + language + "\"") + "}";
		String json = mockMvc.perform(post("/api/auth/signup").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.accessToken");
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
		return b.servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	@Test
	void signUp_inJapanese_showsUpInMe() throws Exception {
		String token = signUp("A", "ja");

		mockMvc.perform(as(get("/api/me"), token)).andExpect(jsonPath("$.group.language").value("ja"));
	}

	@Test
	void update_changesOnlyTheCallersGroup() throws Exception {
		String a = signUp("A", null);
		String b = signUp("B", null);

		mockMvc.perform(as(patch("/api/group"), a).contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"ja\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.language").value("ja"));

		mockMvc.perform(as(get("/api/me"), a)).andExpect(jsonPath("$.group.language").value("ja"));
		mockMvc.perform(as(get("/api/me"), b)).andExpect(jsonPath("$.group.language").value("en"));
	}

	@Test
	void update_byAnOrganizer_returns403AndKeepsTheLanguage() throws Exception {
		String owner = signUp("A", null);
		String groupId = JsonPath.read(mockMvc.perform(as(get("/api/me"), owner)).andReturn().getResponse().getContentAsString(), "$.group.id");
		Group group = groups.findById(java.util.UUID.fromString(groupId)).orElseThrow();
		User organizer = users.save(new User(group, "org-" + System.nanoTime() + "@example.com", "Org", "h", Role.ORGANIZER, Instant.now()));
		String organizerToken = tokens.issue(organizer).value();

		mockMvc.perform(as(patch("/api/group"), organizerToken).contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"ja\"}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(as(get("/api/me"), owner)).andExpect(jsonPath("$.group.language").value("en"));
	}

}
