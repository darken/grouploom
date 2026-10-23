package io.grouploom.backend.email;

import org.springframework.mail.MailException;

public interface EmailGateway {

	/** @throws MailException when the message cannot be handed to the mail server */
	void send(OutgoingEmail email);

}
