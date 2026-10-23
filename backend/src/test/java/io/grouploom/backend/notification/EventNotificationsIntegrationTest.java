package io.grouploom.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.jayway.jsonpath.JsonPath;

import io.grouploom.backend.TestcontainersConfiguration;
import io.grouploom.backend.email.EmailDispatcher;

/** The whole pipeline: schedule, fan out, send through SMTP to Mailpit, unsubscribe, move, cancel. */
@SpringBootTest(properties = { "grouploom.mail.dispatcher-enabled=false", "grouploom.notifications.fan-out-enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventNotificationsIntegrationTest {

	private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.27.8")
		.withExposedPorts(1025, 8025)
		.waitingFor(Wait.forHttp("/livez").forPort(8025));

	static {
		MAILPIT.start();
	}

	@DynamicPropertySource
	static void mail(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", MAILPIT::getHost);
		registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
	}

	private static final Pattern PREFERENCES_TOKEN = Pattern.compile("/m/preferences#t=([A-Za-z0-9_-]+)");

	@Autowired MockMvc mockMvc;
	@Autowired EmailDispatcher dispatcher;
	@Autowired NotificationFanOut fanOut;
	@Autowired JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();

	private record Organizer(String token, String email) {
	}

	private Organizer signUp(String groupName) throws Exception {
		String email = "owner-" + System.nanoTime() + "@example.com";
		String body = "{\"groupName\":\"" + groupName + "\",\"displayName\":\"Olga\",\"email\":\"" + email
				+ "\",\"password\":\"correct horse battery\"}";
		String json = mockMvc.perform(post("/api/auth/signup").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn().getResponse().getContentAsString();
		return new Organizer(JsonPath.read(json, "$.accessToken"), email);
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, Organizer organizer) {
		return b.servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + organizer.token());
	}

	private String create(Organizer organizer, String path, String body) throws Exception {
		String json = mockMvc.perform(as(post(path), organizer).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private String mailpit(String path) throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path)).build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		assertThat(response.statusCode()).isEqualTo(200);
		return response.body();
	}

	private List<String> messagesTo(String recipient) throws Exception {
		String query = URLEncoder.encode("to:" + recipient, StandardCharsets.UTF_8);
		return JsonPath.read(mailpit("/api/v1/search?query=" + query), "$.messages[*].ID");
	}

	private void sendEverythingDue() {
		fanOut.fanOutDue();
		dispatcher.dispatchBatch();
	}

	@Test
	void eventNotifications_fromSchedulingToCancellation() throws Exception {
		String stamp = String.valueOf(System.nanoTime());
		Organizer organizer = signUp("Chess Club " + stamp);
		String typeId = create(organizer, "/api/event-types", "{\"name\":\"Practice\",\"color\":\"green\"}");
		String plainEmail = "plain-" + stamp + "@example.com";
		String pausedEmail = "paused-" + stamp + "@example.com";
		String optedOutEmail = "optout-" + stamp + "@example.com";
		String plain = create(organizer, "/api/members", "{\"name\":\"Plain\",\"email\":\"" + plainEmail + "\"}");
		String paused = create(organizer, "/api/members", "{\"name\":\"Paused\",\"email\":\"" + pausedEmail + "\"}");
		String optedOut = create(organizer, "/api/members", "{\"name\":\"Opt\",\"email\":\"" + optedOutEmail + "\"}");
		jdbc.update("insert into member_preferences (member_id, invitations_paused_at, updated_at) values (?, now(), now())",
				UUID.fromString(paused));
		jdbc.update("insert into member_event_type_opt_outs (member_id, event_type_id) values (?, ?)",
				UUID.fromString(optedOut), UUID.fromString(typeId));

		LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).plusDays(10).truncatedTo(ChronoUnit.MINUTES);
		String eventBody = "{\"eventTypeId\":\"" + typeId + "\",\"title\":\"Practice night\",\"location\":\"Main hall\","
				+ "\"localStart\":\"" + start + "\",\"localEnd\":\"" + start.plusHours(2) + "\",\"timeZone\":\"UTC\"}";
		String eventId = create(organizer, "/api/events", eventBody);
		String base = "/api/events/" + eventId + "/notifications";

		mockMvc.perform(as(get(base + "/audience"), organizer))
			.andExpect(jsonPath("$.eligible").value(1))
			.andExpect(jsonPath("$.total").value(3));

		// Invitation now: one email, to the only member whose preferences allow it.
		mockMvc.perform(as(post(base), organizer).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"INVITATION\",\"timing\":\"NOW\",\"message\":\"Bring a board\"}"))
			.andExpect(status().isCreated());
		sendEverythingDue();

		assertThat(messagesTo(plainEmail)).hasSize(1);
		assertThat(messagesTo(pausedEmail)).isEmpty();
		assertThat(messagesTo(optedOutEmail)).isEmpty();
		String invitationId = messagesTo(plainEmail).get(0);
		String message = mailpit("/api/v1/message/" + invitationId);
		assertThat((String) JsonPath.read(message, "$.Subject")).startsWith("Practice night · ");
		assertThat((String) JsonPath.read(message, "$.HTML")).contains("Main hall").contains("Bring a board");
		String headers = mailpit("/api/v1/message/" + invitationId + "/headers");
		assertThat((String) JsonPath.read(headers, "$['List-Unsubscribe-Post'][0]")).isEqualTo("List-Unsubscribe=One-Click");
		String listUnsubscribe = JsonPath.read(headers, "$['List-Unsubscribe'][0]");
		assertThat(listUnsubscribe).startsWith("<http://localhost:5173/api/public/unsubscribe?t=").endsWith(">");

		mockMvc.perform(as(get(base), organizer))
			.andExpect(jsonPath("$[0].status").value("SENT"))
			.andExpect(jsonPath("$[0].recipientCount").value(1))
			.andExpect(jsonPath("$[0].delivery.sent").value(1))
			.andExpect(jsonPath("$[0].delivery.pending").value(0));

		// The preferences token from the footer is not accepted where only an unsubscribe token is.
		Matcher preferences = PREFERENCES_TOKEN.matcher((String) JsonPath.read(message, "$.Text"));
		assertThat(preferences.find()).isTrue();
		mockMvc.perform(post("/api/public/unsubscribe").servletPath("/api").queryParam("t", preferences.group(1)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("member-token.invalid"));

		// One click, no bearer token: the member is paused.
		String unsubscribeToken = listUnsubscribe.substring(listUnsubscribe.indexOf("?t=") + 3, listUnsubscribe.length() - 1);
		mockMvc.perform(post("/api/public/unsubscribe").servletPath("/api").queryParam("t", unsubscribeToken)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED).content("List-Unsubscribe=One-Click"))
			.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select invitations_paused_at is not null from member_preferences where member_id = ?",
				Boolean.class, UUID.fromString(plain))).isTrue();

		// A reminder a day before follows the event when it moves a day later.
		String reminderJson = mockMvc.perform(as(post(base), organizer).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"REMINDER\",\"timing\":\"BEFORE_START\",\"offsetMinutes\":1440}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String reminderId = JsonPath.read(reminderJson, "$.id");
		assertThat((String) JsonPath.read(reminderJson, "$.localSendAt")).startsWith(start.minusDays(1).toString());
		String eventJson = mockMvc.perform(as(get("/api/events/" + eventId), organizer)).andReturn().getResponse().getContentAsString();
		long version = ((Number) JsonPath.read(eventJson, "$.version")).longValue();
		mockMvc.perform(as(put("/api/events/" + eventId), organizer).contentType(MediaType.APPLICATION_JSON)
			.content("{\"eventTypeId\":\"" + typeId + "\",\"title\":\"Practice night\",\"location\":\"Main hall\","
					+ "\"localStart\":\"" + start.plusDays(1) + "\",\"localEnd\":\"" + start.plusDays(1).plusHours(2)
					+ "\",\"timeZone\":\"UTC\",\"version\":" + version + "}"))
			.andExpect(status().isOk());
		mockMvc.perform(as(get(base), organizer))
			.andExpect(jsonPath("$[?(@.id=='" + reminderId + "')].localSendAt").value(
					org.hamcrest.Matchers.contains(org.hamcrest.Matchers.startsWith(start.toString()))));

		// Another group sees none of it.
		Organizer stranger = signUp("Other " + stamp);
		mockMvc.perform(as(get(base), stranger)).andExpect(status().isNotFound());
		mockMvc.perform(as(post(base), stranger).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"INVITATION\",\"timing\":\"NOW\"}")).andExpect(status().isNotFound());
		mockMvc.perform(as(delete(base + "/" + reminderId), stranger)).andExpect(status().isNotFound());

		// Cancelling drops the reminder and tells the member who was invited, though they have since paused.
		mockMvc.perform(as(post("/api/events/" + eventId + "/cancel"), organizer).contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\":\"The gym is closed\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cancelledAt").isNotEmpty())
			.andExpect(jsonPath("$.cancellationMessage").value("The gym is closed"));
		mockMvc.perform(as(get(base), organizer))
			.andExpect(jsonPath("$[?(@.id=='" + reminderId + "')].status").value(org.hamcrest.Matchers.contains("CANCELLED")))
			.andExpect(jsonPath("$[?(@.kind=='CANCELLATION')]").isNotEmpty());
		sendEverythingDue();

		List<String> toPlain = messagesTo(plainEmail);
		assertThat(toPlain).hasSize(2);
		assertThat(messagesTo(pausedEmail)).isEmpty();
		assertThat(messagesTo(optedOutEmail)).isEmpty();
		boolean cancellationSeen = false;
		for (String id : toPlain) {
			String subject = JsonPath.read(mailpit("/api/v1/message/" + id), "$.Subject");
			cancellationSeen |= subject.startsWith("Cancelled: Practice night on ");
		}
		assertThat(cancellationSeen).isTrue();

		// Cancelled events can't be edited, scheduled for, or deleted.
		mockMvc.perform(as(post(base), organizer).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"INVITATION\",\"timing\":\"NOW\"}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("event.cancelled"));
		mockMvc.perform(as(delete("/api/events/" + eventId), organizer))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("event.has-notifications"));
	}

}
