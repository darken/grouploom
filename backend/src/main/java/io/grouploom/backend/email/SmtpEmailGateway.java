package io.grouploom.backend.email;

import java.io.UnsupportedEncodingException;

import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import io.grouploom.backend.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
public class SmtpEmailGateway implements EmailGateway {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailGateway(JavaMailSender mailSender, AppProperties properties) {
		this.mailSender = mailSender;
		this.from = properties.mail().from();
	}

	@Override
	public void send(OutgoingEmail email) {
		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			// A fixed From keeps SPF/DKIM aligned; the organizer is only the Reply-To.
			helper.setFrom(from, email.fromName());
			if (email.replyTo() != null) {
				helper.setReplyTo(email.replyTo());
			}
			helper.setTo(email.to());
			helper.setSubject(email.subject());
			helper.setText(email.text(), email.html());
			for (var header : email.headers().entrySet()) {
				message.addHeader(header.getKey(), header.getValue());
			}
		}
		catch (MessagingException | UnsupportedEncodingException e) {
			throw new MailPreparationException(e);
		}
		mailSender.send(message);
	}

}
