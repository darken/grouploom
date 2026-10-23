package io.grouploom.backend.email;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.MemberTokenService.IssuedToken;

/**
 * Sends queued emails: claim a batch, then send each with no transaction or row lock held, then record the
 * result. Delivery is at-least-once: a crash between sending and recording makes the claim expire and the
 * email go out again. Logs ids and status only, never addresses or links.
 */
@Component
public class EmailDispatcher {

	private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

	private final EmailOutboxRepository outbox;
	private final EmailOutboxRecorder recorder;
	private final MemberTokenService memberTokens;
	private final PreferencesLinkEmailComposer composer;
	private final EmailGateway gateway;
	private final AppProperties properties;
	private final Clock clock;

	public EmailDispatcher(EmailOutboxRepository outbox, EmailOutboxRecorder recorder,
			MemberTokenService memberTokens, PreferencesLinkEmailComposer composer, EmailGateway gateway,
			AppProperties properties, Clock clock) {
		this.outbox = outbox;
		this.recorder = recorder;
		this.memberTokens = memberTokens;
		this.composer = composer;
		this.gateway = gateway;
		this.properties = properties;
		this.clock = clock;
	}

	/** Processes one batch of due emails and returns how many were claimed. */
	public int dispatchBatch() {
		AppProperties.Mail mail = properties.mail();
		Instant now = clock.instant();
		List<UUID> ids = outbox.claimDue(now, now.plus(mail.claimTimeout()), mail.batchSize());
		for (UUID id : ids) {
			try {
				deliver(id);
			}
			catch (RuntimeException e) {
				// Recording the outcome itself failed; the claim expires and the row is picked up again.
				log.error("Could not record the result for outbox entry {}", id, e);
			}
		}
		return ids.size();
	}

	private void deliver(UUID id) {
		Optional<EmailOutboxEntry> loaded = outbox.findWithDetailsById(id);
		if (loaded.isEmpty() || loaded.get().getStatus() != EmailStatus.SENDING) {
			log.info("Outbox entry {} is gone or no longer being sent; skipping", id);
			return;
		}
		EmailOutboxEntry entry = loaded.get();
		try {
			// A fresh token per attempt, so nothing stored in the outbox can be used as a link.
			IssuedToken token = memberTokens.issue(entry.getMember(), MemberTokenPurpose.PREFERENCES);
			gateway.send(composer.compose(entry.getMember(), entry.getGroup(), entry.getRequestedBy(), token));
		}
		catch (MailParseException | MailPreparationException e) {
			log.warn("Outbox entry {} failed permanently: {}", id, e.getClass().getSimpleName());
			recorder.failed(id, describe(e));
			return;
		}
		catch (RuntimeException e) {
			log.warn("Outbox entry {} failed, will retry: {}", id, e.getClass().getSimpleName());
			recorder.retryLater(id, describe(e));
			return;
		}
		recorder.sent(id);
		log.info("Outbox entry {} sent", id);
	}

	private static String describe(RuntimeException e) {
		return e.getClass().getSimpleName() + ": " + e.getMessage();
	}

}
