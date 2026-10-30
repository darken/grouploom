package io.grouploom.backend.attendance;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import io.grouploom.backend.notification.NotificationFanOut;

/** Invitation emails with reply links, member replies, a reminder that skips "can't go", and the closed window. */
@SpringBootTest(properties = { "grouploom.mail.dispatcher-enabled=false", "grouploom.notifications.fan-out-enabled=false" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AttendanceIntegrationTest.ClockConfig.class })
class AttendanceIntegrationTest {

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

	/** A clock the test can move forward; it starts at the real time so token and mail timestamps stay plausible. */
	static class MutableClock extends Clock {

		private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

		void advance(java.time.Duration by) {
			now.updateAndGet(i -> i.plus(by));
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

	}

	private static final Pattern GOING = Pattern.compile("Going: \\S+/m/attend#t=([A-Za-z0-9_-]+)&r=going");
	private static final Pattern NOT_GOING = Pattern.compile("Can't go: \\S+/m/attend#t=([A-Za-z0-9_-]+)&r=not-going");
	private static final Pattern CHANGE = Pattern.compile("Change answer: \\S+/m/attend#t=([A-Za-z0-9_-]+)\\s");
	private static final Pattern PREFERENCES_TOKEN = Pattern.compile("/m/preferences#t=([A-Za-z0-9_-]+)");

	@Autowired MockMvc mockMvc;
	@Autowired EmailDispatcher dispatcher;
	@Autowired NotificationFanOut fanOut;
	@Autowired MutableClock clock;
	@Autowired JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();

	private record Organizer(String token) {
	}

	private Organizer signUp(String groupName) throws Exception {
		String email = "owner-" + System.nanoTime() + "@example.com";
		String body = "{\"groupName\":\"" + groupName + "\",\"displayName\":\"Olga\",\"email\":\"" + email
				+ "\",\"password\":\"correct horse battery\"}";
		String json = mockMvc.perform(post("/api/auth/signup").servletPath("/api").contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn().getResponse().getContentAsString();
		return new Organizer(JsonPath.read(json, "$.accessToken"));
	}

	private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, Organizer organizer) {
		return b.servletPath("/api").header(HttpHeaders.AUTHORIZATION, "Bearer " + organizer.token());
	}

	private String create(Organizer organizer, String path, String body) throws Exception {
		String json = mockMvc.perform(as(post(path), organizer).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private String createEvent(Organizer organizer, String typeId, String title, LocalDateTime start) throws Exception {
		return create(organizer, "/api/events", "{\"eventTypeId\":\"" + typeId + "\",\"title\":\"" + title + "\","
				+ "\"localStart\":\"" + start + "\",\"localEnd\":\"" + start.plusHours(2) + "\",\"timeZone\":\"UTC\"}");
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

	private record Mail(String subject, String text, String html) {
	}

	private Mail message(String id) throws Exception {
		String json = mailpit("/api/v1/message/" + id);
		return new Mail(JsonPath.read(json, "$.Subject"), JsonPath.read(json, "$.Text"), JsonPath.read(json, "$.HTML"));
	}

	/** The one email to this recipient whose subject starts with the prefix. */
	private Mail mailTo(String recipient, String subjectPrefix) throws Exception {
		List<Mail> matching = new java.util.ArrayList<>();
		for (String id : messagesTo(recipient)) {
			Mail mail = message(id);
			if (mail.subject().startsWith(subjectPrefix)) {
				matching.add(mail);
			}
		}
		assertThat(matching).hasSize(1);
		return matching.get(0);
	}

	private static String token(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		assertThat(matcher.find()).as("%s in %s", pattern, text).isTrue();
		return matcher.group(1);
	}

	private void sendEverythingDue() {
		fanOut.fanOutDue();
		dispatcher.dispatchBatch();
	}

	private void schedule(Organizer organizer, String eventId, String kind) throws Exception {
		mockMvc.perform(as(post("/api/events/" + eventId + "/notifications"), organizer).contentType(MediaType.APPLICATION_JSON)
			.content("{\"kind\":\"" + kind + "\",\"timing\":\"NOW\"}")).andExpect(status().isCreated());
		sendEverythingDue();
	}

	private MockHttpServletRequestBuilder publicRequest(MockHttpServletRequestBuilder b, String token) {
		return b.servletPath("/api").header("X-Member-Token", token);
	}

	@Test
	void attendance_fromInvitationLinksToTheClosedWindow() throws Exception {
		String stamp = String.valueOf(System.nanoTime());
		Organizer organizer = signUp("Chess Club " + stamp);
		String typeId = create(organizer, "/api/event-types", "{\"name\":\"Practice\",\"color\":\"green\"}");
		String anaEmail = "ana-" + stamp + "@example.com";
		String bobEmail = "bob-" + stamp + "@example.com";
		String ana = create(organizer, "/api/members", "{\"name\":\"Ana\",\"email\":\"" + anaEmail + "\"}");
		String bob = create(organizer, "/api/members", "{\"name\":\"Bob\",\"email\":\"" + bobEmail + "\"}");
		LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).plusDays(10).truncatedTo(ChronoUnit.MINUTES);
		String eventId = createEvent(organizer, typeId, "Practice night", start);
		String attendance = "/api/events/" + eventId + "/attendance";

		// An invitation: every member gets a Going and a Can't go link.
		schedule(organizer, eventId, "INVITATION");
		Mail anaInvite = mailTo(anaEmail, "Practice night");
		Mail bobInvite = mailTo(bobEmail, "Practice night");
		String anaToken = token(GOING, anaInvite.text());
		assertThat(token(NOT_GOING, anaInvite.text())).isEqualTo(anaToken);
		assertThat(anaInvite.html()).contains(">Going<").contains(">Can't go<").contains("#t=" + anaToken + "&amp;r=going");
		String bobToken = token(NOT_GOING, bobInvite.text());
		assertThat(bobToken).isNotEqualTo(anaToken);

		// Opening a link records nothing.
		mockMvc.perform(publicRequest(get("/api/public/attendance"), anaToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberName").value("Ana"))
			.andExpect(jsonPath("$.event.title").value("Practice night"))
			.andExpect(jsonPath("$.response").doesNotExist())
			.andExpect(jsonPath("$.open").value(true));
		mockMvc.perform(as(get(attendance + "/summary"), organizer))
			.andExpect(jsonPath("$.going").value(0))
			.andExpect(jsonPath("$.noReply").value(2));

		// Replies, each with the token from its own member's email.
		mockMvc.perform(publicRequest(put("/api/public/attendance"), anaToken).contentType(MediaType.APPLICATION_JSON)
			.content("{\"response\":\"GOING\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.response").value("GOING"));
		mockMvc.perform(publicRequest(put("/api/public/attendance"), bobToken).contentType(MediaType.APPLICATION_JSON)
			.content("{\"response\":\"NOT_GOING\",\"note\":\"Away that week\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(as(get(attendance + "/summary"), organizer))
			.andExpect(jsonPath("$.going").value(1))
			.andExpect(jsonPath("$.notGoing").value(1))
			.andExpect(jsonPath("$.noReply").value(0))
			.andExpect(jsonPath("$.members").value(2));
		mockMvc.perform(as(get("/api/events/" + eventId), organizer)).andExpect(jsonPath("$.goingCount").value(1));
		mockMvc.perform(as(get(attendance + "?filter=NOT_GOING"), organizer))
			.andExpect(jsonPath("$.content[0].member.name").value("Bob"))
			.andExpect(jsonPath("$.content[0].note").value("Away that week"))
			.andExpect(jsonPath("$.content[0].source").value("MEMBER"));

		// A reminder reaches only Ana, and her email shows her answer instead of the buttons.
		schedule(organizer, eventId, "REMINDER");
		assertThat(messagesTo(anaEmail)).hasSize(2);
		assertThat(messagesTo(bobEmail)).hasSize(1);
		Mail reminder = mailTo(anaEmail, "Reminder: Practice night");
		assertThat(reminder.text()).contains("You're going").doesNotContain("r=going");
		assertThat(reminder.html()).contains("You're going").contains("Change answer");
		assertThat(token(CHANGE, reminder.text() + "\n")).isNotEqualTo(anaToken);

		// A link only answers for its own purpose and its own event.
		mockMvc.perform(publicRequest(get("/api/public/preferences"), anaToken))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("member-token.invalid"));
		String preferencesToken = token(PREFERENCES_TOKEN, anaInvite.text());
		mockMvc.perform(publicRequest(get("/api/public/attendance"), preferencesToken))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("member-token.invalid"));
		String otherEventId = createEvent(organizer, typeId, "Social night", start.plusDays(1));
		schedule(organizer, otherEventId, "INVITATION");
		String anaOtherToken = token(GOING, mailTo(anaEmail, "Social night").text());
		mockMvc.perform(publicRequest(put("/api/public/attendance"), anaOtherToken).contentType(MediaType.APPLICATION_JSON)
			.content("{\"response\":\"NOT_GOING\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(publicRequest(get("/api/public/attendance"), anaToken))
			.andExpect(jsonPath("$.event.title").value("Practice night"))
			.andExpect(jsonPath("$.response").value("GOING"));
		mockMvc.perform(as(get("/api/events/" + otherEventId + "/attendance/summary"), organizer))
			.andExpect(jsonPath("$.notGoing").value(1)).andExpect(jsonPath("$.going").value(0));
		mockMvc.perform(as(get(attendance + "/summary"), organizer)).andExpect(jsonPath("$.going").value(1));

		// Another group sees none of it.
		Organizer stranger = signUp("Other " + stamp);
		mockMvc.perform(as(get(attendance + "/summary"), stranger)).andExpect(status().isNotFound());
		mockMvc.perform(as(put(attendance + "/" + ana), stranger).contentType(MediaType.APPLICATION_JSON)
			.content("{\"response\":\"GOING\"}")).andExpect(status().isNotFound());

		// Once the event has started members can't reply, but the organizer can still correct records.
		clock.advance(java.time.Duration.ofDays(11));
		mockMvc.perform(publicRequest(put("/api/public/attendance"), bobToken).contentType(MediaType.APPLICATION_JSON)
			.content("{\"response\":\"GOING\"}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("attendance.closed"));
		mockMvc.perform(publicRequest(get("/api/public/attendance"), bobToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.open").value(false))
			.andExpect(jsonPath("$.response").value("NOT_GOING"));
		mockMvc.perform(as(put(attendance + "/" + bob), organizer).contentType(MediaType.APPLICATION_JSON)
			.content("{\"response\":\"GOING\",\"note\":\"Showed up\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("ORGANIZER"));
		mockMvc.perform(as(get(attendance + "/summary"), organizer))
			.andExpect(jsonPath("$.going").value(2)).andExpect(jsonPath("$.notGoing").value(0));

		// Removing a member takes their answers with them.
		mockMvc.perform(as(delete("/api/members/" + bob), organizer)).andExpect(status().isNoContent());
		mockMvc.perform(as(get(attendance + "/summary"), organizer))
			.andExpect(jsonPath("$.going").value(1))
			.andExpect(jsonPath("$.noReply").value(0))
			.andExpect(jsonPath("$.members").value(1));
		assertThat(jdbc.queryForObject("select count(*) from attendances where member_id = ?", Long.class, UUID.fromString(bob)))
			.isZero();
	}

}
