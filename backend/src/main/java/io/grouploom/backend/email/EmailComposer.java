package io.grouploom.backend.email;

import java.util.Set;

/** Renders the outbox entries of one or more kinds. Implementations issue whatever member tokens they need. */
public interface EmailComposer {

	Set<EmailKind> kinds();

	OutgoingEmail compose(EmailOutboxEntry entry);

}
