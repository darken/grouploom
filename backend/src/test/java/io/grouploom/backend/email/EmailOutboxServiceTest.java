package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.config.TestProperties;
import io.grouploom.backend.email.dto.EmailDeliveryStatus;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;
import io.grouploom.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock EmailOutboxRepository outbox;
	@Mock UserRepository users;

	private EmailOutboxService service;
	private Group group;
	private Member member;
	private User organizer;

	@BeforeEach
	void setUp() {
		service = new EmailOutboxService(outbox, users, TestProperties.app("http://localhost", Duration.ofDays(60)),
				Clock.fixed(NOW, ZoneOffset.UTC));
		group = new Group("Club", NOW);
		member = new Member(group, "Ana", "ana@example.com", NOW);
		ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
		organizer = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, NOW);
	}

	@Test
	void enqueuePreferencesLink_savesAPendingEntryDueNowAttributedToTheOrganizer() {
		UUID organizerId = UUID.randomUUID();
		given(users.getReferenceById(organizerId)).willReturn(organizer);
		given(outbox.save(any(EmailOutboxEntry.class))).willAnswer(i -> i.getArgument(0));

		EmailOutboxEntry entry = service.enqueuePreferencesLink(member, organizerId);

		assertThat(entry.getStatus()).isEqualTo(EmailStatus.PENDING);
		assertThat(entry.getKind()).isEqualTo(EmailKind.PREFERENCES_LINK);
		assertThat(entry.getMember()).isSameAs(member);
		assertThat(entry.getGroup()).isSameAs(group);
		assertThat(entry.getRequestedBy()).isSameAs(organizer);
		assertThat(entry.getNextAttemptAt()).isEqualTo(NOW);
		assertThat(entry.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void enqueuePreferencesLinks_savesOneEntryPerMemberInOneCall() {
		UUID organizerId = UUID.randomUUID();
		given(users.getReferenceById(organizerId)).willReturn(organizer);
		Member other = new Member(group, "Bob", "bob@example.com", NOW);

		int queued = service.enqueuePreferencesLinks(List.of(member, other), organizerId);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Iterable<EmailOutboxEntry>> captor = ArgumentCaptor.forClass(Iterable.class);
		verify(outbox).saveAll(captor.capture());
		assertThat(captor.getValue()).extracting(EmailOutboxEntry::getMember).containsExactly(member, other);
		assertThat(queued).isEqualTo(2);
	}

	@Test
	void enqueuePreferencesLinks_withNoMembers_doesNothing() {
		assertThat(service.enqueuePreferencesLinks(List.of(), UUID.randomUUID())).isZero();

		verifyNoInteractions(outbox, users);
	}

	@Test
	void isWithinCooldown_asksForNonFailedEntriesNewerThanTheCooldown() {
		given(outbox.existsByMemberIdAndKindAndCreatedAtAfterAndStatusNot(member.getId(), EmailKind.PREFERENCES_LINK,
				NOW.minus(Duration.ofMinutes(5)), EmailStatus.FAILED)).willReturn(true);

		assertThat(service.isWithinCooldown(member.getId())).isTrue();
	}

	@Test
	void membersWithinCooldown_usesOneQueryForAllMembers() {
		UUID other = UUID.randomUUID();
		given(outbox.findMemberIdsEmailedSince(List.of(member.getId(), other), EmailKind.PREFERENCES_LINK,
				NOW.minus(Duration.ofMinutes(5)))).willReturn(Set.of(other));

		assertThat(service.membersWithinCooldown(List.of(member.getId(), other))).containsExactly(other);
	}

	@Test
	void latestPreferencesStatus_usesSentAtForSentAndCreatedAtOtherwise() {
		UUID pendingId = UUID.randomUUID();
		Instant sentAt = NOW.minusSeconds(30);
		Instant createdAt = NOW.minusSeconds(60);
		given(outbox.findLatestStatus(group.getId(), List.of(member.getId(), pendingId), "PREFERENCES_LINK"))
			.willReturn(List.of(row(member.getId(), "SENT", sentAt, createdAt),
					row(pendingId, "PENDING", null, createdAt)));

		Map<UUID, EmailDeliveryStatus> result = service.latestPreferencesStatus(group.getId(),
				List.of(member.getId(), pendingId));

		assertThat(result.get(member.getId())).isEqualTo(new EmailDeliveryStatus(EmailStatus.SENT, sentAt));
		assertThat(result.get(pendingId)).isEqualTo(new EmailDeliveryStatus(EmailStatus.PENDING, createdAt));
	}

	@Test
	void latestPreferencesStatus_withNoMembers_skipsTheQuery() {
		assertThat(service.latestPreferencesStatus(group.getId(), List.of())).isEmpty();

		verify(outbox, never()).findLatestStatus(any(), any(), any());
	}

	private static LatestEmailStatus row(UUID memberId, String status, Instant sentAt, Instant createdAt) {
		return new LatestEmailStatus() {
			public UUID getMemberId() {
				return memberId;
			}

			public String getStatus() {
				return status;
			}

			public Instant getSentAt() {
				return sentAt;
			}

			public Instant getCreatedAt() {
				return createdAt;
			}
		};
	}

}
