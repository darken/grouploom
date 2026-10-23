package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.jayway.jsonpath.JsonPath;

import io.grouploom.backend.TestcontainersConfiguration;

/** Real SMTP round trip against Mailpit: queue, dispatch, read the message back, open its link. */
@SpringBootTest(properties = "grouploom.mail.dispatcher-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailDeliveryIntegrationTest {

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

	private static final Pattern LINK = Pattern.compile("http://localhost:5173/m/preferences#t=([A-Za-z0-9_-]+)");

	@Autowired MockMvc mockMvc;
	@Autowired EmailDispatcher dispatcher;

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

	private String createMember(Organizer organizer, String name, String email) throws Exception {
		String json = mockMvc.perform(as(post("/api/members"), organizer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}"))
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

	/** Ids of the messages Mailpit holds for one recipient. */
	private List<String> messagesTo(String recipient) throws Exception {
		String query = URLEncoder.encode("to:" + recipient, StandardCharsets.UTF_8);
		return JsonPath.read(mailpit("/api/v1/search?query=" + query), "$.messages[*].ID");
	}

	@Test
	void preferencesEmail_isQueuedSentThroughSmtpAndItsLinkOpensThePreferencesPage() throws Exception {
		String groupName = "Chess Club " + System.nanoTime();
		Organizer organizer = signUp(groupName);
		String memberEmail = "ana-" + System.nanoTime() + "@example.com";
		String memberId = createMember(organizer, "Ana", memberEmail);

		mockMvc.perform(as(post("/api/members/" + memberId + "/preferences-email"), organizer))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.queuedAt").isNotEmpty());
		assertThat(messagesTo(memberEmail)).isEmpty();
		mockMvc.perform(as(get("/api/members/" + memberId), organizer))
			.andExpect(jsonPath("$.preferencesEmail.status").value("PENDING"));

		assertThat(dispatcher.dispatchBatch()).isGreaterThanOrEqualTo(1);

		List<String> ids = messagesTo(memberEmail);
		assertThat(ids).hasSize(1);
		String message = mailpit("/api/v1/message/" + ids.get(0));
		assertThat((String) JsonPath.read(message, "$.Subject")).isEqualTo("Choose which " + groupName + " events you hear about");
		assertThat((String) JsonPath.read(message, "$.From.Address")).isEqualTo("no-reply@grouploom.test");
		assertThat((String) JsonPath.read(message, "$.From.Name")).isEqualTo(groupName + " via GroupLoom");
		assertThat((String) JsonPath.read(message, "$.ReplyTo[0].Address")).isEqualTo(organizer.email());
		String text = JsonPath.read(message, "$.Text");
		String html = JsonPath.read(message, "$.HTML");
		assertThat(html).contains("Set your preferences");

		Matcher link = LINK.matcher(text);
		assertThat(link.find()).as("link in the text part").isTrue();
		assertThat(html).contains(link.group());
		mockMvc.perform(get("/api/public/preferences").servletPath("/api").header("X-Member-Token", link.group(1)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberName").value("Ana"))
			.andExpect(jsonPath("$.groupName").value(groupName));

		mockMvc.perform(as(get("/api/members/" + memberId), organizer))
			.andExpect(jsonPath("$.preferencesEmail.status").value("SENT"));

		mockMvc.perform(as(post("/api/members/" + memberId + "/preferences-email"), organizer))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("email.cooldown"));
	}

	@Test
	void bulkPreferencesEmail_queuesEveryMemberExceptThoseInTheCooldown() throws Exception {
		Organizer organizer = signUp("Club " + System.nanoTime());
		String stamp = String.valueOf(System.nanoTime());
		String ana = createMember(organizer, "Ana", "ana-" + stamp + "@example.com");
		createMember(organizer, "Bob", "bob-" + stamp + "@example.com");
		createMember(organizer, "Cy", "cy-" + stamp + "@example.com");
		mockMvc.perform(as(post("/api/members/" + ana + "/preferences-email"), organizer)).andExpect(status().isAccepted());

		mockMvc.perform(as(post("/api/members/preferences-email"), organizer))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.queued").value(2))
			.andExpect(jsonPath("$.skipped").value(1));
		dispatcher.dispatchBatch();

		assertThat(messagesTo("ana-" + stamp + "@example.com")).hasSize(1);
		assertThat(messagesTo("bob-" + stamp + "@example.com")).hasSize(1);
		assertThat(messagesTo("cy-" + stamp + "@example.com")).hasSize(1);
		mockMvc.perform(as(get("/api/members"), organizer))
			.andExpect(jsonPath("$.content[?(@.name=='Bob')].preferencesEmail.status").value("SENT"));
	}

	@Test
	void preferencesEmail_withMemberFromAnotherGroup_returns404AndQueuesNothing() throws Exception {
		Organizer a = signUp("A " + System.nanoTime());
		Organizer b = signUp("B " + System.nanoTime());
		String memberEmail = "ana-" + System.nanoTime() + "@example.com";
		String memberOfA = createMember(a, "Ana", memberEmail);

		mockMvc.perform(as(post("/api/members/" + memberOfA + "/preferences-email"), b)).andExpect(status().isNotFound());
		dispatcher.dispatchBatch();

		assertThat(messagesTo(memberEmail)).isEmpty();
		mockMvc.perform(as(get("/api/members/" + memberOfA), a))
			.andExpect(jsonPath("$.preferencesEmail").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void preferencesEmail_withoutToken_returns401() throws Exception {
		mockMvc.perform(post("/api/members/preferences-email").servletPath("/api")).andExpect(status().isUnauthorized());
	}

}
