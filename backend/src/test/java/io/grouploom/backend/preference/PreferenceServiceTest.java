package io.grouploom.backend.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.preference.dto.EventTypeSubscription;
import io.grouploom.backend.preference.dto.UpdatePreferencesRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreferenceServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock MemberTokenService tokens;
	@Mock EventTypeRepository eventTypes;
	@Mock MemberPreferencesRepository preferences;
	@Mock EventTypeOptOutRepository optOuts;

	private final UUID groupId = UUID.randomUUID();
	private Member member;
	private EventType practice;
	private EventType game;
	private PreferenceService service;

	@BeforeEach
	void setUp() {
		Group group = new Group("Chess Club", NOW);
		ReflectionTestUtils.setField(group, "id", groupId);
		member = new Member(group, "Ana", "ana@example.com", NOW);
		ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
		practice = type(group, "Practice", EventTypeColor.BLUE);
		game = type(group, "game night", EventTypeColor.GREEN);
		given(tokens.resolve("raw", MemberTokenPurpose.PREFERENCES)).willReturn(member);
		given(eventTypes.findAllByGroupIdAndArchivedAtIsNull(groupId)).willReturn(List.of(practice, game));
		given(preferences.findById(member.getId())).willReturn(Optional.empty());
		given(optOuts.findEventTypeIdsByMemberId(member.getId())).willReturn(Set.of());
		service = new PreferenceService(tokens, eventTypes, preferences, optOuts, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static EventType type(Group group, String name, EventTypeColor color) {
		EventType t = new EventType(group, name, name + " description", color, null, NOW);
		ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
		return t;
	}

	private static UpdatePreferencesRequest request(boolean paused, EventTypeSubscription... items) {
		return new UpdatePreferencesRequest(paused, List.of(items));
	}

	// --- get ---

	@Test
	void get_withNoPreferencesRow_returnsDefaultsSubscribedToEverythingAndNotPaused() {
		var response = service.get("raw");

		assertThat(response.invitationsPaused()).isFalse();
		assertThat(response.eventTypes()).extracting("subscribed").containsExactly(true, true);
	}

	@Test
	void get_returnsGreetingDataWithMaskedEmail() {
		var response = service.get("raw");

		assertThat(response.memberName()).isEqualTo("Ana");
		assertThat(response.groupName()).isEqualTo("Chess Club");
		assertThat(response.maskedEmail()).isEqualTo("a••@example.com").doesNotContain("ana@");
	}

	@Test
	void get_listsOnlyActiveEventTypesSortedByNameIgnoringCase() {
		var response = service.get("raw");

		assertThat(response.eventTypes()).extracting("name").containsExactly("game night", "Practice");
		assertThat(response.eventTypes().get(1).id()).isEqualTo(practice.getId());
		assertThat(response.eventTypes().get(1).color()).isEqualTo(EventTypeColor.BLUE);
		verify(eventTypes).findAllByGroupIdAndArchivedAtIsNull(groupId);
	}

	@Test
	void get_marksOptedOutTypesAsNotSubscribed() {
		given(optOuts.findEventTypeIdsByMemberId(member.getId())).willReturn(Set.of(practice.getId()));

		var response = service.get("raw");

		assertThat(response.eventTypes()).filteredOn(t -> t.id().equals(practice.getId()))
			.extracting("subscribed").containsExactly(false);
		assertThat(response.eventTypes()).filteredOn(t -> t.id().equals(game.getId()))
			.extracting("subscribed").containsExactly(true);
	}

	@Test
	void get_ignoresOptOutsForTypesThatAreNotActive() {
		given(optOuts.findEventTypeIdsByMemberId(member.getId())).willReturn(Set.of(UUID.randomUUID()));

		assertThat(service.get("raw").eventTypes()).extracting("subscribed").containsExactly(true, true);
	}

	@Test
	void get_withPausedMember_reportsPausedAndKeepsTypeChoices() {
		MemberPreferences prefs = new MemberPreferences(member, NOW);
		prefs.setInvitationsPaused(true, NOW);
		given(preferences.findById(member.getId())).willReturn(Optional.of(prefs));
		given(optOuts.findEventTypeIdsByMemberId(member.getId())).willReturn(Set.of(game.getId()));

		var response = service.get("raw");

		assertThat(response.invitationsPaused()).isTrue();
		assertThat(response.eventTypes()).extracting("subscribed").containsExactly(false, true);
	}

	@Test
	void get_withInvalidToken_propagatesTheException() {
		given(tokens.resolve("bad", MemberTokenPurpose.PREFERENCES))
			.willThrow(new InvalidMemberTokenException("member-token.invalid"));

		assertThatThrownBy(() -> service.get("bad")).isInstanceOf(InvalidMemberTokenException.class);
	}

	// --- update ---

	@Test
	void update_withUnsubscribedType_addsAnOptOut() {
		var response = service.update("raw", request(false, new EventTypeSubscription(practice.getId(), false)));

		ArgumentCaptor<List<EventTypeOptOut>> saved = ArgumentCaptor.forClass(List.class);
		verify(optOuts).saveAll(saved.capture());
		assertThat(saved.getValue()).singleElement().satisfies(o -> {
			assertThat(o.getMemberId()).isEqualTo(member.getId());
			assertThat(o.getEventTypeId()).isEqualTo(practice.getId());
			assertThat(o.getCreatedAt()).isEqualTo(NOW);
		});
		assertThat(response.eventTypes()).filteredOn(t -> t.id().equals(practice.getId()))
			.extracting("subscribed").containsExactly(false);
	}

	@Test
	void update_withAlreadyOptedOutType_doesNotInsertItAgain() {
		given(optOuts.findEventTypeIdsByMemberId(member.getId())).willReturn(Set.of(practice.getId()));

		service.update("raw", request(false, new EventTypeSubscription(practice.getId(), false)));

		verify(optOuts, never()).saveAll(any());
	}

	@Test
	void update_withSubscribedType_removesItsOptOut() {
		given(optOuts.findEventTypeIdsByMemberId(member.getId())).willReturn(Set.of(practice.getId(), game.getId()));

		var response = service.update("raw", request(false, new EventTypeSubscription(practice.getId(), true)));

		verify(optOuts).deleteByMemberIdAndEventTypeIdIn(eq(member.getId()), eq(Set.of(practice.getId())));
		assertThat(response.eventTypes()).filteredOn(t -> t.id().equals(practice.getId()))
			.extracting("subscribed").containsExactly(true);
		assertThat(response.eventTypes()).filteredOn(t -> t.id().equals(game.getId()))
			.extracting("subscribed").containsExactly(false);
	}

	@Test
	void update_keepsOptOutsForArchivedTypesAndForActiveTypesMissingFromTheRequest() {
		UUID archivedId = UUID.randomUUID();
		given(optOuts.findEventTypeIdsByMemberId(member.getId())).willReturn(Set.of(archivedId, game.getId(), practice.getId()));

		service.update("raw", request(false, new EventTypeSubscription(practice.getId(), true)));

		ArgumentCaptor<java.util.Collection<UUID>> deleted = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(optOuts).deleteByMemberIdAndEventTypeIdIn(eq(member.getId()), deleted.capture());
		assertThat(deleted.getValue()).containsExactly(practice.getId());
		verify(optOuts, never()).saveAll(any());
	}

	@Test
	void update_withUnknownTypeId_throwsBadRequestAndChangesNothing() {
		assertThatThrownBy(() -> service.update("raw", request(true, new EventTypeSubscription(UUID.randomUUID(), false))))
			.isInstanceOfSatisfying(BadRequestException.class, e -> {
				assertThat(e.getCode()).isEqualTo("preference.unknown-event-type");
				assertThat(e.getField()).isEqualTo("eventTypes");
			});
		verify(optOuts, never()).saveAll(any());
		verify(optOuts, never()).deleteByMemberIdAndEventTypeIdIn(any(), anyCollection());
		verify(preferences, never()).save(any());
	}

	@Test
	void update_withArchivedOrOtherGroupsTypeId_isRejectedBecauseOnlyActiveTypesOfTheGroupAreKnown() {
		UUID archivedOrForeign = UUID.randomUUID();
		given(eventTypes.findAllByGroupIdAndArchivedAtIsNull(groupId)).willReturn(List.of(practice));

		assertThatThrownBy(() -> service.update("raw", request(false,
				new EventTypeSubscription(practice.getId(), false), new EventTypeSubscription(archivedOrForeign, true))))
			.isInstanceOfSatisfying(BadRequestException.class,
					e -> assertThat(e.getCode()).isEqualTo("preference.unknown-event-type"));
		verify(optOuts, never()).saveAll(any());
	}

	@Test
	void update_pausingCreatesPreferencesWithPauseTimestamp() {
		var response = service.update("raw", request(true));

		ArgumentCaptor<MemberPreferences> saved = ArgumentCaptor.forClass(MemberPreferences.class);
		verify(preferences).save(saved.capture());
		assertThat(saved.getValue().getInvitationsPausedAt()).isEqualTo(NOW);
		assertThat(response.invitationsPaused()).isTrue();
	}

	@Test
	void update_unpausingClearsTheTimestampOnExistingPreferences() {
		MemberPreferences prefs = new MemberPreferences(member, NOW.minusSeconds(100));
		prefs.setInvitationsPaused(true, NOW.minusSeconds(100));
		given(preferences.findById(member.getId())).willReturn(Optional.of(prefs));

		var response = service.update("raw", request(false));

		assertThat(prefs.getInvitationsPausedAt()).isNull();
		assertThat(prefs.getUpdatedAt()).isEqualTo(NOW);
		assertThat(response.invitationsPaused()).isFalse();
	}

	@Test
	void update_unpausingWithoutAPreferencesRow_createsNothing() {
		service.update("raw", request(false));

		verify(preferences, never()).save(any());
	}

	@Test
	void update_withInvalidToken_changesNothing() {
		given(tokens.resolve("bad", MemberTokenPurpose.PREFERENCES))
			.willThrow(new InvalidMemberTokenException("member-token.invalid"));

		assertThatThrownBy(() -> service.update("bad", request(true)))
			.isInstanceOf(InvalidMemberTokenException.class);
		verify(preferences, never()).save(any());
		verify(optOuts, never()).saveAll(any());
	}

	// --- pausedMemberIds ---

	@Test
	void pausedMemberIds_delegatesToTheGroupScopedQuery() {
		UUID id = UUID.randomUUID();
		given(preferences.findPausedMemberIds(groupId)).willReturn(Set.of(id));

		assertThat(service.pausedMemberIds(groupId)).containsExactly(id);
	}

	@Test
	void isPaused_reflectsThePreferencesRow() {
		assertThat(service.isPaused(member.getId())).isFalse();
		MemberPreferences prefs = new MemberPreferences(member, NOW);
		prefs.setInvitationsPaused(true, NOW);
		given(preferences.findById(member.getId())).willReturn(Optional.of(prefs));

		assertThat(service.isPaused(member.getId())).isTrue();
	}

}
