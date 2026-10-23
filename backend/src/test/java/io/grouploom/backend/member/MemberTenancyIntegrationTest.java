package io.grouploom.backend.member;

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
class MemberTenancyIntegrationTest {

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

	private String create(String token, String name, String email) throws Exception {
		String json = mockMvc.perform(as(post("/api/members"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	@Test
	void groupB_cannotListSearchGetUpdateOrDeleteGroupAsMembers() throws Exception {
		String a = signUp("A");
		String b = signUp("B");
		String id = create(a, "Ana Pérez", "ana@example.com");

		mockMvc.perform(as(get("/api/members"), b)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(as(get("/api/members?q=ana"), b)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(as(get("/api/members/" + id), b)).andExpect(status().isNotFound());
		mockMvc.perform(as(put("/api/members/" + id), b).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hijacked\",\"email\":\"evil@example.com\",\"version\":0}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(as(delete("/api/members/" + id), b)).andExpect(status().isNotFound());

		mockMvc.perform(as(get("/api/members/" + id), a)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ana Pérez"));
		mockMvc.perform(as(get("/api/members?q=ANA"), a)).andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void sameEmailInTwoGroups_isAllowedButNotTwiceInOneGroup() throws Exception {
		String a = signUp("A");
		String b = signUp("B");
		create(a, "Ana", "ana@example.com");
		create(b, "Ana", "ana@example.com");

		mockMvc.perform(as(post("/api/members"), a).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Other\",\"email\":\"  ANA@Example.com \"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("member.email-taken"))
			.andExpect(jsonPath("$.field").value("email"));
	}

	@Test
	void lifecycle_createEditSearchDelete_worksWithVersioningAndLiteralSearch() throws Exception {
		String token = signUp("A");
		String id = create(token, "  Ana  ", "ANA@Example.com");
		create(token, "100% Fun", "fun@example.com");

		mockMvc.perform(as(get("/api/members/" + id), token))
			.andExpect(jsonPath("$.name").value("Ana"))
			.andExpect(jsonPath("$.email").value("ana@example.com"));

		mockMvc.perform(as(put("/api/members/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana María\",\"email\":\"ANA@example.com\",\"version\":0}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(as(put("/api/members/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Late\",\"email\":\"ana@example.com\",\"version\":0}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("member.stale"));

		// '%' is literal, not a wildcard: "%" alone matches only the member whose name contains it.
		mockMvc.perform(as(get("/api/members"), token).param("q", "%")).andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].name").value("100% Fun"));
		mockMvc.perform(as(get("/api/members?sort=name,asc"), token)).andExpect(jsonPath("$.content[0].name").value("100% Fun"));

		mockMvc.perform(as(delete("/api/members/" + id), token)).andExpect(status().isNoContent());
		mockMvc.perform(as(get("/api/members/" + id), token)).andExpect(status().isNotFound());
	}

}
