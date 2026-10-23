package io.grouploom.backend.preference;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.preference.dto.EventTypeSubscription;
import io.grouploom.backend.preference.dto.PreferenceEventType;
import io.grouploom.backend.preference.dto.PublicPreferencesResponse;
import io.grouploom.backend.preference.dto.UpdatePreferencesRequest;

/**
 * Member self-service. Authorized by the link token alone; see {@link MemberTokenService#resolve}.
 * Both operations write ({@code lastUsedAt}), so neither is read-only.
 */
@Service
public class PreferenceService {

	private final MemberTokenService tokens;
	private final EventTypeRepository eventTypes;
	private final MemberPreferencesRepository preferences;
	private final EventTypeOptOutRepository optOuts;
	private final Clock clock;

	public PreferenceService(MemberTokenService tokens, EventTypeRepository eventTypes,
			MemberPreferencesRepository preferences, EventTypeOptOutRepository optOuts, Clock clock) {
		this.tokens = tokens;
		this.eventTypes = eventTypes;
		this.preferences = preferences;
		this.optOuts = optOuts;
		this.clock = clock;
	}

	@Transactional
	public PublicPreferencesResponse get(String rawToken) {
		Member member = tokens.resolve(rawToken, MemberTokenPurpose.PREFERENCES);
		List<EventType> active = activeTypes(member);
		boolean paused = isPaused(member.getId());
		return toResponse(member, active, optOuts.findEventTypeIdsByMemberId(member.getId()), paused);
	}

	@Transactional
	public PublicPreferencesResponse update(String rawToken, UpdatePreferencesRequest request) {
		Member member = tokens.resolve(rawToken, MemberTokenPurpose.PREFERENCES);
		List<EventType> active = activeTypes(member);
		Set<UUID> activeIds = active.stream().map(EventType::getId).collect(Collectors.toSet());

		// Last entry wins for a repeated id.
		Map<UUID, Boolean> requested = new LinkedHashMap<>();
		for (EventTypeSubscription item : request.eventTypes()) {
			if (!activeIds.contains(item.id())) {
				throw new BadRequestException("Unknown event type.", "preference.unknown-event-type", "eventTypes");
			}
			requested.put(item.id(), item.subscribed());
		}

		Instant now = clock.instant();
		Set<UUID> optedOut = new HashSet<>(optOuts.findEventTypeIdsByMemberId(member.getId()));
		List<EventTypeOptOut> toAdd = new ArrayList<>();
		Set<UUID> toRemove = new HashSet<>();
		requested.forEach((typeId, subscribed) -> {
			if (subscribed) {
				if (optedOut.remove(typeId)) {
					toRemove.add(typeId);
				}
			}
			else if (optedOut.add(typeId)) {
				toAdd.add(new EventTypeOptOut(member.getId(), typeId, now));
			}
		});
		if (!toRemove.isEmpty()) {
			optOuts.deleteByMemberIdAndEventTypeIdIn(member.getId(), toRemove);
		}
		if (!toAdd.isEmpty()) {
			optOuts.saveAll(toAdd);
		}

		boolean paused = applyPause(member, request.invitationsPaused(), now);
		return toResponse(member, active, optedOut, paused);
	}

	/** Ids of the group's members who paused all invitations; one query for a whole list page. */
	@Transactional(readOnly = true)
	public Set<UUID> pausedMemberIds(UUID groupId) {
		return preferences.findPausedMemberIds(groupId);
	}

	@Transactional(readOnly = true)
	public boolean isPaused(UUID memberId) {
		return preferences.findById(memberId).map(MemberPreferences::isInvitationsPaused).orElse(false);
	}

	private boolean applyPause(Member member, boolean paused, Instant now) {
		var existing = preferences.findById(member.getId());
		if (existing.isPresent()) {
			existing.get().setInvitationsPaused(paused, now);
		}
		else if (paused) {
			MemberPreferences created = new MemberPreferences(member, now);
			created.setInvitationsPaused(true, now);
			preferences.save(created);
		}
		return paused;
	}

	private List<EventType> activeTypes(Member member) {
		return eventTypes.findAllByGroupIdAndArchivedAtIsNull(member.getGroup().getId());
	}

	private static PublicPreferencesResponse toResponse(Member member, List<EventType> active, Set<UUID> optedOut,
			boolean paused) {
		List<PreferenceEventType> types = active.stream()
			.sorted(Comparator.comparing(EventType::getName, String.CASE_INSENSITIVE_ORDER))
			.map(t -> new PreferenceEventType(t.getId(), t.getName(), t.getDescription(), t.getColor(),
					!optedOut.contains(t.getId())))
			.toList();
		return new PublicPreferencesResponse(member.getName(), EmailMasker.mask(member.getEmail()),
				member.getGroup().getName(), paused, types);
	}

}
