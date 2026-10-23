package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import io.grouploom.backend.config.TestProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class SmtpEmailGatewayTest {

	@Mock JavaMailSender mailSender;

	private SmtpEmailGateway gateway;
	private MimeMessage message;

	@BeforeEach
	void setUp() {
		message = new MimeMessage(Session.getInstance(new Properties()));
		given(mailSender.createMimeMessage()).willReturn(message);
		gateway = new SmtpEmailGateway(mailSender, TestProperties.app("http://localhost", Duration.ofDays(60)));
	}

	private MimeMessage send(OutgoingEmail email) throws Exception {
		gateway.send(email);
		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(captor.capture());
		MimeMessage sent = captor.getValue();
		sent.saveChanges();
		return sent;
	}

	@Test
	void send_usesTheFixedSenderAddressWithGroupNameAsDisplayNameAndOrganizerAsReplyTo() throws Exception {
		MimeMessage sent = send(new OutgoingEmail("ana@example.com", "Chess Club via GroupLoom", "olga@example.com",
				"Subject line", "plain body", "<p>html body</p>"));

		InternetAddress from = (InternetAddress) sent.getFrom()[0];
		assertThat(from.getAddress()).isEqualTo("no-reply@grouploom.test");
		assertThat(from.getPersonal()).isEqualTo("Chess Club via GroupLoom");
		assertThat(((InternetAddress) sent.getReplyTo()[0]).getAddress()).isEqualTo("olga@example.com");
		assertThat(((InternetAddress) sent.getAllRecipients()[0]).getAddress()).isEqualTo("ana@example.com");
		assertThat(sent.getSubject()).isEqualTo("Subject line");
	}

	@Test
	void send_includesBothAPlainTextAndAnHtmlPart() throws Exception {
		MimeMessage sent = send(new OutgoingEmail("ana@example.com", "Club via GroupLoom", null, "S", "plain body",
				"<p>html body</p>"));

		List<Part> leaves = new ArrayList<>();
		collect(sent, leaves);
		assertThat(leaves).anySatisfy(p -> {
			assertThat(p.isMimeType("text/plain")).isTrue();
			assertThat(content(p)).isEqualTo("plain body");
		});
		assertThat(leaves).anySatisfy(p -> {
			assertThat(p.isMimeType("text/html")).isTrue();
			assertThat(content(p)).isEqualTo("<p>html body</p>");
		});
	}

	@Test
	void send_withoutReplyTo_leavesTheHeaderOut() throws Exception {
		MimeMessage sent = send(new OutgoingEmail("ana@example.com", "Club via GroupLoom", null, "S", "t", "<p>h</p>"));

		assertThat(sent.getHeader("Reply-To")).isNull();
	}

	private static void collect(Part part, List<Part> leaves) throws Exception {
		if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			for (int i = 0; i < multipart.getCount(); i++) {
				collect(multipart.getBodyPart(i), leaves);
			}
		}
		else {
			leaves.add(part);
		}
	}

	private static String content(Part part) {
		try {
			return (String) part.getContent();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void send_setsTheCustomHeaders() throws Exception {
		MimeMessage sent = send(new OutgoingEmail("ana@example.com", "Chess Club via GroupLoom", null, "S", "t", "<p>h</p>",
				java.util.Map.of("List-Unsubscribe", "<https://app.example.com/api/public/unsubscribe?t=x>",
						"List-Unsubscribe-Post", "List-Unsubscribe=One-Click")));

		assertThat(sent.getHeader("List-Unsubscribe")).containsExactly("<https://app.example.com/api/public/unsubscribe?t=x>");
		assertThat(sent.getHeader("List-Unsubscribe-Post")).containsExactly("List-Unsubscribe=One-Click");
	}

	@Test
	void send_withoutCustomHeaders_addsNone() throws Exception {
		MimeMessage sent = send(new OutgoingEmail("ana@example.com", "Chess Club via GroupLoom", null, "S", "t", "<p>h</p>"));

		assertThat(sent.getHeader("List-Unsubscribe")).isNull();
	}

}
