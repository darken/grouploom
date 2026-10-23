package io.grouploom.backend.email;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.email.dto.EmailDeliveryStatus;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.user.User;
import io.grouploom.backend.user.repository.UserRepository;

@Service
public class EmailOutboxService {

	private final EmailOutboxRepository outbox;
	private final UserRepository users;
	private final AppProperties properties;
	private final Clock clock;

	public EmailOutboxService(EmailOutboxRepository outbox, UserRepository users, AppProperties properties,
			Clock clock) {
		this.outbox = outbox;
		this.users = users;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public EmailOutboxEntry enqueuePreferencesLink(Member member, UUID requestedByUserId) {
		User requestedBy = users.getReferenceById(requestedByUserId);
		return outbox.save(new EmailOutboxEntry(member.getGroup(), member, requestedBy, EmailKind.PREFERENCES_LINK,
				clock.instant()));
	}

	/** Returns how many were queued. */
	@Transactional
	public int enqueuePreferencesLinks(Collection<Member> members, UUID requestedByUserId) {
		if (members.isEmpty()) {
			return 0;
		}
		User requestedBy = users.getReferenceById(requestedByUserId);
		Instant now = clock.instant();
		List<EmailOutboxEntry> entries = members.stream()
			.map(m -> new EmailOutboxEntry(m.getGroup(), m, requestedBy, EmailKind.PREFERENCES_LINK, now))
			.toList();
		outbox.saveAll(entries);
		return entries.size();
	}

	@Transactional(readOnly = true)
	public boolean isWithinCooldown(UUID memberId) {
		return outbox.existsByMemberIdAndKindAndCreatedAtAfterAndStatusNot(memberId, EmailKind.PREFERENCES_LINK,
				cooldownStart(), EmailStatus.FAILED);
	}

	@Transactional(readOnly = true)
	public Set<UUID> membersWithinCooldown(Collection<UUID> memberIds) {
		if (memberIds.isEmpty()) {
			return Set.of();
		}
		return outbox.findMemberIdsEmailedSince(memberIds, EmailKind.PREFERENCES_LINK, cooldownStart());
	}

	/** One query for a whole page of members; members never emailed are absent from the map. */
	@Transactional(readOnly = true)
	public Map<UUID, EmailDeliveryStatus> latestPreferencesStatus(UUID groupId, Collection<UUID> memberIds) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, EmailDeliveryStatus> result = new HashMap<>();
		for (LatestEmailStatus row : outbox.findLatestStatus(groupId, memberIds, EmailKind.PREFERENCES_LINK.name())) {
			EmailStatus status = EmailStatus.valueOf(row.getStatus());
			Instant at = status == EmailStatus.SENT && row.getSentAt() != null ? row.getSentAt() : row.getCreatedAt();
			result.put(row.getMemberId(), new EmailDeliveryStatus(status, at));
		}
		return result;
	}

	private Instant cooldownStart() {
		return clock.instant().minus(properties.mail().preferencesCooldown());
	}

}
