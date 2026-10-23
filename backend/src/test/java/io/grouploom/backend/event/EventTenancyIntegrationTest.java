package io.grouploom.backend.event;

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
class EventTenancyIntegrationTest {

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

	private String createType(String token, String name) throws Exception {
		String json = mockMvc.perform(as(post("/api/event-types"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"color\":\"blue\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private String eventJson(String typeId, String title, String start, String end, String extra) {
		return "{\"eventTypeId\":\"" + typeId + "\",\"title\":\"" + title + "\",\"localStart\":\"" + start
				+ "\",\"localEnd\":\"" + end + "\",\"timeZone\":\"Europe/Madrid\"" + extra + "}";
	}

	private String createEvent(String token, String typeId, String title) throws Exception {
		String json = mockMvc.perform(as(post("/api/events"), token).contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(typeId, title, "2099-03-10T10:00:00", "2099-03-10T11:00:00", "")))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	@Test
	void groupB_cannotListGetUpdateOrDeleteGroupAsEvents() throws Exception {
		String a = signUp("A");
		String b = signUp("B");
		String id = createEvent(a, createType(a, "Practice"), "Rehearsal");

		mockMvc.perform(as(get("/api/events"), b)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(as(get("/api/events?when=past"), b)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(as(get("/api/events/" + id), b)).andExpect(status().isNotFound());
		String typeB = createType(b, "Mine");
		mockMvc.perform(as(put("/api/events/" + id), b).contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(typeB, "Hijacked", "2099-03-10T10:00:00", "2099-03-10T11:00:00", ",\"version\":0")))
			.andExpect(status().isNotFound());
		mockMvc.perform(as(delete("/api/events/" + id), b)).andExpect(status().isNotFound());

		mockMvc.perform(as(get("/api/events/" + id), a)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Rehearsal"));
		mockMvc.perform(as(get("/api/events"), a)).andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void groupB_cannotCreateAnEventWithGroupAsEventType() throws Exception {
		String a = signUp("A");
		String b = signUp("B");
		String typeA = createType(a, "Practice");

		mockMvc.perform(as(post("/api/events"), b).contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(typeA, "Sneaky", "2099-03-10T10:00:00", "2099-03-10T11:00:00", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("event.event-type-not-found"))
			.andExpect(jsonPath("$.errors[0].field").value("eventTypeId"));
	}

	@Test
	void lifecycle_createsWithZoneConversionEditsAndDeletes() throws Exception {
		String token = signUp("A");
		String typeId = createType(token, "Practice");

		String json = mockMvc.perform(as(post("/api/events"), token).contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(typeId, "  Rehearsal ", "2099-01-10T10:00:00", "2099-01-10T12:00:00", ",\"location\":\"Gym\"")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Rehearsal"))
			.andExpect(jsonPath("$.start").value("2099-01-10T09:00:00Z"))
			.andExpect(jsonPath("$.localStart").value("2099-01-10T10:00:00"))
			.andExpect(jsonPath("$.eventType.name").value("Practice"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(json, "$.id");

		mockMvc.perform(as(put("/api/events/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(typeId, "Renamed", "2099-01-10T10:00:00", "2099-01-10T12:00:00", ",\"version\":0")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.title").value("Renamed"));
		mockMvc.perform(as(put("/api/events/" + id), token).contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(typeId, "Late", "2099-01-10T10:00:00", "2099-01-10T12:00:00", ",\"version\":0")))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("event.stale"));

		mockMvc.perform(as(post("/api/events"), token).contentType(MediaType.APPLICATION_JSON)
				.content(eventJson(typeId, "Old", "2020-01-10T10:00:00", "2020-01-10T12:00:00", "")))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("event.start-in-past"));

		mockMvc.perform(as(get("/api/events?when=past"), token)).andExpect(jsonPath("$.content.length()").value(0));
		mockMvc.perform(as(get("/api/events?eventTypeId=" + typeId), token)).andExpect(jsonPath("$.content.length()").value(1));

		mockMvc.perform(as(delete("/api/events/" + id), token)).andExpect(status().isNoContent());
		mockMvc.perform(as(get("/api/events/" + id), token)).andExpect(status().isNotFound());
	}

	@Test
	void deletingAnEventTypeUsedByAnEvent_isRejectedUntilTheEventIsGone() throws Exception {
		String token = signUp("A");
		String typeId = createType(token, "Practice");
		String eventId = createEvent(token, typeId, "Rehearsal");

		mockMvc.perform(as(delete("/api/event-types/" + typeId), token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("event-type.in-use"));

		mockMvc.perform(as(delete("/api/events/" + eventId), token)).andExpect(status().isNoContent());
		mockMvc.perform(as(delete("/api/event-types/" + typeId), token)).andExpect(status().isNoContent());
	}

}
