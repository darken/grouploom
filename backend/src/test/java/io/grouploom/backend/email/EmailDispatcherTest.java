package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.config.TestProperties;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;

@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final String SECRET = "raw-secret-token";

	@Mock EmailOutboxRepository outbox;
	@Mock EmailComposer composer;
	@Mock EmailGateway gateway;

	private final AppProperties properties = TestProperties.app("http://localhost", Duration.ofDays(60));
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private EmailDispatcher dispatcher;
	private Group group;
	private Member member;
	private User organizer;
	private OutgoingEmail composed;

	@BeforeEach
	void setUp() {
		lenient().when(composer.kinds()).thenReturn(Set.of(EmailKind.PREFERENCES_LINK));
		dispatcher = new EmailDispatcher(outbox, new EmailOutboxRecorder(outbox, properties, clock),
				List.of(composer), gateway, properties, clock);
		group = new Group("Club", NOW);
		member = new Member(group, "Ana", "ana@example.com", NOW);
		organizer = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, NOW);
		composed = new OutgoingEmail("ana@example.com", "Club via GroupLoom", "olga@example.com", "S", "t", "<p>h</p>");
	}

	private EmailOutboxEntry claimedEntry() {
		EmailOutboxEntry entry = new EmailOutboxEntry(group, member, organizer, EmailKind.PREFERENCES_LINK, NOW);
		ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
		entry.claim(NOW, Duration.ofMinutes(2));
		lenient().when(outbox.findWithDetailsById(entry.getId())).thenReturn(Optional.of(entry));
		lenient().when(outbox.findById(entry.getId())).thenReturn(Optional.of(entry));
		return entry;
	}

	private void claims(EmailOutboxEntry... entries) {
		given(outbox.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20))
			.willReturn(java.util.Arrays.stream(entries).map(EmailOutboxEntry::getId).toList());
	}

	private void composes() {
		given(composer.compose(any(EmailOutboxEntry.class))).willReturn(composed);
	}

	@Test
	void dispatchBatch_claimsUsingTheConfiguredBatchSizeAndTimeout() {
		given(outbox.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).willReturn(List.of());

		assertThat(dispatcher.dispatchBatch()).isZero();

		verify(outbox).claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20);
	}

	@Test
	void dispatchBatch_onSuccess_composesAtSendTimeAndMarksTheEntrySent() {
		EmailOutboxEntry entry = claimedEntry();
		claims(entry);
		composes();

		assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

		verify(gateway).send(composed);
		assertThat(entry.getStatus()).isEqualTo(EmailStatus.SENT);
		assertThat(entry.getSentAt()).isEqualTo(NOW);
		assertThat(entry.getAttempts()).isEqualTo(1);
	}

	@Test
	void dispatchBatch_onTransientMailFailure_schedulesARetry() {
		EmailOutboxEntry entry = claimedEntry();
		claims(entry);
		composes();
		doThrow(new MailSendException("smtp down")).when(gateway).send(composed);

		dispatcher.dispatchBatch();

		assertThat(entry.getStatus()).isEqualTo(EmailStatus.PENDING);
		assertThat(entry.getAttempts()).isEqualTo(1);
		assertThat(entry.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
		assertThat(entry.getLastError()).contains("smtp down");
	}

	@Test
	void dispatchBatch_onParseFailure_failsImmediately() {
		EmailOutboxEntry entry = claimedEntry();
		claims(entry);
		composes();
		doThrow(new MailParseException("bad address")).when(gateway).send(composed);

		dispatcher.dispatchBatch();

		assertThat(entry.getStatus()).isEqualTo(EmailStatus.FAILED);
		assertThat(entry.getLastError()).contains("bad address");
	}

	@Test
	void dispatchBatch_onUnexpectedError_retriesInsteadOfLosingTheEmail() {
		EmailOutboxEntry entry = claimedEntry();
		claims(entry);
		given(composer.compose(any(EmailOutboxEntry.class))).willThrow(new IllegalStateException("db blip"));

		dispatcher.dispatchBatch();

		assertThat(entry.getStatus()).isEqualTo(EmailStatus.PENDING);
		assertThat(entry.getLastError()).contains("db blip");
		verify(gateway, never()).send(any());
	}

	@Test
	void dispatchBatch_whenOneEntryFails_stillSendsTheRest() {
		EmailOutboxEntry failing = claimedEntry();
		EmailOutboxEntry healthy = claimedEntry();
		claims(failing, healthy);
		composes();
		doThrow(new MailSendException("first fails")).doNothing().when(gateway).send(composed);

		assertThat(dispatcher.dispatchBatch()).isEqualTo(2);

		assertThat(failing.getStatus()).isEqualTo(EmailStatus.PENDING);
		assertThat(healthy.getStatus()).isEqualTo(EmailStatus.SENT);
	}

	@Test
	void dispatchBatch_composesPerAttemptAndNeverStoresContentOnTheEntry() throws Exception {
		EmailOutboxEntry entry = claimedEntry();
		claims(entry);
		composes();
		doThrow(new MailSendException("smtp down")).doNothing().when(gateway).send(composed);

		dispatcher.dispatchBatch();
		entry.claim(NOW, Duration.ofMinutes(2));
		given(outbox.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).willReturn(List.of(entry.getId()));
		dispatcher.dispatchBatch();

		verify(composer, times(2)).compose(entry);
		for (Field field : EmailOutboxEntry.class.getDeclaredFields()) {
			field.setAccessible(true);
			assertThat(String.valueOf(field.get(entry))).doesNotContain(SECRET);
		}
	}

	@Test
	void dispatchBatch_whenTheEntryIsGone_skipsIt() {
		UUID id = UUID.randomUUID();
		given(outbox.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).willReturn(List.of(id));
		given(outbox.findWithDetailsById(id)).willReturn(Optional.empty());

		assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

		verify(gateway, never()).send(any());
	}

	@Test
	void dispatchBatch_whenTheEntryIsNoLongerBeingSent_skipsIt() {
		EmailOutboxEntry entry = claimedEntry();
		entry.markSent(NOW);
		claims(entry);

		dispatcher.dispatchBatch();

		verify(gateway, never()).send(any());
		verify(composer, never()).compose(any());
	}

	@Test
	void dispatchBatch_routesEachEntryToTheComposerForItsKind() {
		EmailComposer other = org.mockito.Mockito.mock(EmailComposer.class);
		given(other.kinds()).willReturn(Set.of(EmailKind.EVENT_CANCELLATION));
		EmailDispatcher routed = new EmailDispatcher(outbox, new EmailOutboxRecorder(outbox, properties, clock),
				List.of(composer, other), gateway, properties, clock);
		EmailOutboxEntry entry = new EmailOutboxEntry(group, member, organizer, EmailKind.EVENT_CANCELLATION, NOW);
		ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
		entry.claim(NOW, Duration.ofMinutes(2));
		given(outbox.findWithDetailsById(entry.getId())).willReturn(Optional.of(entry));
		given(outbox.findById(entry.getId())).willReturn(Optional.of(entry));
		given(outbox.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).willReturn(List.of(entry.getId()));
		given(other.compose(entry)).willReturn(composed);

		routed.dispatchBatch();

		verify(other).compose(entry);
		verify(composer, never()).compose(any());
		assertThat(entry.getStatus()).isEqualTo(EmailStatus.SENT);
	}

	@Test
	void dispatchBatch_whenNoComposerHandlesTheKind_failsTheEntry() {
		EmailOutboxEntry entry = claimedEntry();
		ReflectionTestUtils.setField(entry, "kind", EmailKind.EVENT_INVITATION);
		claims(entry);

		dispatcher.dispatchBatch();

		assertThat(entry.getStatus()).isEqualTo(EmailStatus.FAILED);
		assertThat(entry.getLastError()).contains("EVENT_INVITATION");
		verify(gateway, never()).send(any());
	}

}
