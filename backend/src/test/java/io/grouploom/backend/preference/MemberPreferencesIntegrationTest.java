package io.grouploom.backend.preference;

import static org.assertj.core.api.Assertions.assertThat;
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
class MemberPreferencesIntegrationTest {

	@Autowired MockMvc mockMvc;

	private String signUp(String label) throws Exception {
		String email = "owner-" + System.nanoTime() + "@example.com";
		String body = "{\"groupName\":\"" + label + "\",\"displayName\":\"" + label + "\",\"email\":\"" + email
				+ "\",\"password\":\"correct horse battery\"}";
		String json = mockMvc.perform(post("/api/auth/signup").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.accessToken");
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
		return b.servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private MockHttpServletRequestBuilder asMember(MockHttpServletRequestBuilder b, String token) {
		return b.servletPath("/api").header("X-Member-Token", token);
	}

	private String createEventType(String token, String name) throws Exception {
		String json = mockMvc.perform(as(post("/api/event-types"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"color\":\"blue\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private String createMember(String token, String name, String email) throws Exception {
		String json = mockMvc.perform(as(post("/api/members"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	/** Returns the raw token from the fragment of a freshly generated link. */
	private String linkToken(String organizer, String memberId) throws Exception {
		String json = mockMvc.perform(as(post("/api/members/" + memberId + "/preferences-link"), organizer))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(json, "$.url");
		assertThat(url).startsWith("http://localhost:5173/m/preferences#t=");
		return url.substring(url.indexOf("#t=") + 3);
	}

	private String updateBody(boolean paused, String... idAndSubscribed) {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < idAndSubscribed.length; i += 2) {
			items.append(i > 0 ? "," : "").append("{\"id\":\"").append(idAndSubscribed[i]).append("\",\"subscribed\":")
				.append(idAndSubscribed[i + 1]).append("}");
		}
		return "{\"invitationsPaused\":" + paused + ",\"eventTypes\":[" + items + "]}";
	}

	@Test
	void memberLink_canReadAndChangePreferencesAndOrganizerSeesPausedState() throws Exception {
		String groupName = "Chess Club " + System.nanoTime();
		String organizer = signUp(groupName);
		String practice = createEventType(organizer, "Practice");
		String game = createEventType(organizer, "Game night");
		String memberId = createMember(organizer, "Ana", "ana@example.com");
		String token = linkToken(organizer, memberId);

		mockMvc.perform(asMember(get("/api/public/preferences"), token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberName").value("Ana"))
			.andExpect(jsonPath("$.maskedEmail").value("a••@example.com"))
			.andExpect(jsonPath("$.groupName").value(groupName))
			.andExpect(jsonPath("$.invitationsPaused").value(false))
			.andExpect(jsonPath("$.eventTypes.length()").value(2))
			.andExpect(jsonPath("$.eventTypes[0].name").value("Game night"))
			.andExpect(jsonPath("$.eventTypes[0].subscribed").value(true));

		mockMvc.perform(asMember(put("/api/public/preferences"), token).contentType(MediaType.APPLICATION_JSON)
				.content(updateBody(true, practice, "false")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invitationsPaused").value(true))
			.andExpect(jsonPath("$.eventTypes[?(@.name=='Practice')].subscribed").value(false))
			.andExpect(jsonPath("$.eventTypes[?(@.name=='Game night')].subscribed").value(true));

		mockMvc.perform(asMember(get("/api/public/preferences"), token))
			.andExpect(jsonPath("$.invitationsPaused").value(true))
			.andExpect(jsonPath("$.eventTypes[?(@.id=='" + practice + "')].subscribed").value(false))
			.andExpect(jsonPath("$.eventTypes[?(@.id=='" + game + "')].subscribed").value(true));

		mockMvc.perform(as(get("/api/members"), organizer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].invitationsPaused").value(true));
		mockMvc.perform(as(get("/api/members/" + memberId), organizer))
			.andExpect(jsonPath("$.invitationsPaused").value(true))
			.andExpect(jsonPath("$.version").value(0));

		// Unpausing keeps the per-type choice.
		mockMvc.perform(asMember(put("/api/public/preferences"), token).contentType(MediaType.APPLICATION_JSON)
				.content(updateBody(false)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invitationsPaused").value(false))
			.andExpect(jsonPath("$.eventTypes[?(@.id=='" + practice + "')].subscribed").value(false));
	}

	@Test
	void archivedTypeOptOut_isHiddenButRestoredWhenTheTypeIsUnarchived() throws Exception {
		String organizer = signUp("Club");
		String practice = createEventType(organizer, "Practice");
		String memberId = createMember(organizer, "Ana", "ana@example.com");
		String token = linkToken(organizer, memberId);
		mockMvc.perform(asMember(put("/api/public/preferences"), token).contentType(MediaType.APPLICATION_JSON)
				.content(updateBody(false, practice, "false"))).andExpect(status().isOk());

		archive(organizer, practice, true, 0);
		mockMvc.perform(asMember(get("/api/public/preferences"), token))
			.andExpect(jsonPath("$.eventTypes.length()").value(0));
		mockMvc.perform(asMember(put("/api/public/preferences"), token).contentType(MediaType.APPLICATION_JSON)
				.content(updateBody(false, practice, "true")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("preference.unknown-event-type"));

		archive(organizer, practice, false, 1);
		mockMvc.perform(asMember(get("/api/public/preferences"), token))
			.andExpect(jsonPath("$.eventTypes[0].subscribed").value(false));
	}

	private void archive(String organizer, String typeId, boolean archived, int version) throws Exception {
		mockMvc.perform(as(put("/api/event-types/" + typeId), organizer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Practice\",\"color\":\"blue\",\"archived\":" + archived + ",\"version\":" + version + "}"))
			.andExpect(status().isOk());
	}

	@Test
	void changingTheMembersEmail_revokesEarlierLinksButNotAFreshOne() throws Exception {
		String organizer = signUp("Club");
		String memberId = createMember(organizer, "Ana", "ana@example.com");
		String oldLink = linkToken(organizer, memberId);
		String secondOldLink = linkToken(organizer, memberId);
		mockMvc.perform(asMember(get("/api/public/preferences"), oldLink)).andExpect(status().isOk());
		mockMvc.perform(asMember(get("/api/public/preferences"), secondOldLink)).andExpect(status().isOk());

		// A case-only change keeps links working.
		mockMvc.perform(as(put("/api/members/" + memberId), organizer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\",\"email\":\"ANA@example.com\",\"version\":0}"))
			.andExpect(status().isOk());
		mockMvc.perform(asMember(get("/api/public/preferences"), oldLink)).andExpect(status().isOk());

		mockMvc.perform(as(put("/api/members/" + memberId), organizer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ana\",\"email\":\"ana.new@example.com\",\"version\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("ana.new@example.com"));

		for (String revoked : new String[] { oldLink, secondOldLink }) {
			mockMvc.perform(asMember(get("/api/public/preferences"), revoked))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("member-token.invalid"));
		}
		String fresh = linkToken(organizer, memberId);
		mockMvc.perform(asMember(get("/api/public/preferences"), fresh))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.maskedEmail").value("a••@example.com"));
	}

	@Test
	void publicEndpoint_withBadTokensOrOrganizerBearer_returns401() throws Exception {
		String organizer = signUp("Club");
		String memberId = createMember(organizer, "Ana", "ana@example.com");
		linkToken(organizer, memberId);

		mockMvc.perform(asMember(get("/api/public/preferences"), "not-a-real-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		mockMvc.perform(get("/api/public/preferences").servletPath("/api"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
		// The organizer's bearer token is not a substitute for a member token.
		mockMvc.perform(as(get("/api/public/preferences"), organizer))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));
	}

	@Test
	void memberLink_cannotBeUsedOnOrganizerEndpoints() throws Exception {
		String organizer = signUp("Club");
		String memberId = createMember(organizer, "Ana", "ana@example.com");
		String token = linkToken(organizer, memberId);

		mockMvc.perform(get("/api/members").servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(asMember(get("/api/members"), token)).andExpect(status().isUnauthorized());
	}

	@Test
	void tenancy_otherGroupsEventTypeIdIsRejectedAndOtherGroupsMemberHasNoLink() throws Exception {
		String a = signUp("A");
		String b = signUp("B");
		createEventType(a, "Practice");
		String groupBType = createEventType(b, "Secret");
		String memberA = createMember(a, "Ana", "ana@example.com");
		String token = linkToken(a, memberA);

		mockMvc.perform(asMember(put("/api/public/preferences"), token).contentType(MediaType.APPLICATION_JSON)
				.content(updateBody(false, groupBType, "false")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("preference.unknown-event-type"));
		mockMvc.perform(asMember(get("/api/public/preferences"), token))
			.andExpect(jsonPath("$.eventTypes.length()").value(1))
			.andExpect(jsonPath("$.eventTypes[0].name").value("Practice"));

		mockMvc.perform(as(post("/api/members/" + memberA + "/preferences-link"), b)).andExpect(status().isNotFound());
	}

}
