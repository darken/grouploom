package io.grouploom.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.config.TestProperties;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.member.dto.CreateMemberRequest;
import io.grouploom.backend.member.dto.UpdateMemberRequest;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.preference.PreferenceService;
import io.grouploom.backend.user.Role;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

	@Mock MemberRepository repository;
	@Mock GroupRepository groups;
	@Mock MemberTokenService memberTokens;
	@Mock PreferenceService preferences;

	private final UUID groupId = UUID.randomUUID();
	private final CurrentUser current = new CurrentUser(UUID.randomUUID(), groupId, Role.ORGANIZER);
	private Group group;
	private MemberService service;

	@BeforeEach
	void setUp() {
		group = new Group("Club", NOW);
		ReflectionTestUtils.setField(group, "id", groupId);
		given(groups.getReferenceById(groupId)).willReturn(group);
		given(repository.saveAndFlush(any(Member.class))).willAnswer(i -> i.getArgument(0));
		service = new MemberService(repository, groups, memberTokens, preferences,
				TestProperties.app("https://app.example.com", Duration.ofDays(60)),
				Clock.fixed(LATER, ZoneOffset.UTC));
	}

	private Member existing(String name, String email) {
		Member m = new Member(group, name, email, NOW);
		ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
		given(repository.findByIdAndGroupId(m.getId(), groupId)).willReturn(Optional.of(m));
		return m;
	}

	private UpdateMemberRequest update(String name, String email, long version) {
		return new UpdateMemberRequest(name, email, version);
	}

	// --- create ---

	@Test
	void create_withPaddedNameAndMixedCaseEmail_trimsNameAndLowercasesEmail() {
		var response = service.create(current, new CreateMemberRequest("  Ana Pérez  ", "  Ana.Perez@Example.COM "));

		ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
		verify(repository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("Ana Pérez");
		assertThat(captor.getValue().getEmail()).isEqualTo("ana.perez@example.com");
		assertThat(captor.getValue().getGroup()).isSameAs(group);
		assertThat(captor.getValue().getCreatedAt()).isEqualTo(LATER);
		assertThat(response.name()).isEqualTo("Ana Pérez");
		assertThat(response.email()).isEqualTo("ana.perez@example.com");
	}

	@Test
	void create_withDuplicateEmail_throwsEmailTakenOnEmailField() {
		given(repository.existsByGroupIdAndEmailIgnoreCase(groupId, "ana@example.com")).willReturn(true);

		assertThatThrownBy(() -> service.create(current, new CreateMemberRequest("Ana", " ANA@example.com ")))
			.isInstanceOfSatisfying(ConflictException.class, e -> {
				assertThat(e.getCode()).isEqualTo("member.email-taken");
				assertThat(e.getField()).isEqualTo("email");
				assertThat(e.getMessage()).isEqualTo("A member with this email already exists.");
			});
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void create_whenUniqueIndexRaces_translatesToEmailTaken() {
		given(repository.saveAndFlush(any(Member.class))).willThrow(new DataIntegrityViolationException("dup"));

		assertThatThrownBy(() -> service.create(current, new CreateMemberRequest("Ana", "ana@example.com")))
			.isInstanceOfSatisfying(ConflictException.class, e -> {
				assertThat(e.getCode()).isEqualTo("member.email-taken");
				assertThat(e.getField()).isEqualTo("email");
			});
	}

	// --- update ---

	@Test
	void update_withMatchingVersion_appliesTrimmedAndLowercasedChanges() {
		Member m = existing("Ana", "ana@example.com");

		var response = service.update(current, m.getId(), update(" Ana María ", " Ana.M@Example.com", 0));

		assertThat(response.name()).isEqualTo("Ana María");
		assertThat(response.email()).isEqualTo("ana.m@example.com");
		assertThat(response.updatedAt()).isEqualTo(LATER);
		assertThat(response.createdAt()).isEqualTo(NOW);
	}

	@Test
	void update_withStaleVersion_throwsStale() {
		Member m = existing("Ana", "ana@example.com");
		ReflectionTestUtils.setField(m, "version", 3L);

		assertThatThrownBy(() -> service.update(current, m.getId(), update("Ana", "ana@example.com", 2)))
			.isInstanceOfSatisfying(ConflictException.class, e -> {
				assertThat(e.getCode()).isEqualTo("member.stale");
				assertThat(e.getField()).isNull();
			});
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void update_changingToAnotherMembersEmail_throwsEmailTaken() {
		Member m = existing("Ana", "ana@example.com");
		given(repository.existsByGroupIdAndEmailIgnoreCaseAndIdNot(groupId, "bob@example.com", m.getId())).willReturn(true);

		assertThatThrownBy(() -> service.update(current, m.getId(), update("Ana", "Bob@example.com", 0)))
			.isInstanceOfSatisfying(ConflictException.class, e -> {
				assertThat(e.getCode()).isEqualTo("member.email-taken");
				assertThat(e.getField()).isEqualTo("email");
			});
	}

	@Test
	void update_keepingOwnEmailWithDifferentCase_isAllowed() {
		Member m = existing("Ana", "ana@example.com");
		// The "IdNot" lookup excludes the member itself, so the repository reports no clash.
		given(repository.existsByGroupIdAndEmailIgnoreCaseAndIdNot(groupId, "ana@example.com", m.getId())).willReturn(false);

		var response = service.update(current, m.getId(), update("Ana", "ANA@Example.com", 0));

		assertThat(response.email()).isEqualTo("ana@example.com");
	}

	@Test
	void update_whenFlushRacesOnUniqueIndex_translatesToEmailTaken() {
		Member m = existing("Ana", "ana@example.com");
		given(repository.saveAndFlush(any(Member.class))).willThrow(new DataIntegrityViolationException("dup"));

		assertThatThrownBy(() -> service.update(current, m.getId(), update("Ana", "new@example.com", 0)))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("member.email-taken"));
	}

	@Test
	void update_whenFlushDetectsConcurrentEdit_translatesToStale() {
		Member m = existing("Ana", "ana@example.com");
		given(repository.saveAndFlush(any(Member.class)))
			.willThrow(new ObjectOptimisticLockingFailureException(Member.class, m.getId()));

		assertThatThrownBy(() -> service.update(current, m.getId(), update("Ana", "ana@example.com", 0)))
			.isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("member.stale"));
	}

	@Test
	void update_changingTheEmail_revokesTheMembersLinks() {
		Member m = existing("Ana", "ana@example.com");

		service.update(current, m.getId(), update("Ana", "new@example.com", 0));

		verify(memberTokens).revokeAll(m);
	}

	@Test
	void update_changingOnlyTheCaseOfTheEmail_keepsTheMembersLinks() {
		Member m = existing("Ana", "ana@example.com");

		service.update(current, m.getId(), update("Ana", "ANA@example.com", 0));

		verify(memberTokens, never()).revokeAll(any());
	}

	@Test
	void update_changingOnlyTheName_keepsTheMembersLinks() {
		Member m = existing("Ana", "ana@example.com");

		service.update(current, m.getId(), update("Ana María", "ana@example.com", 0));

		verify(memberTokens, never()).revokeAll(any());
	}

	@Test
	void update_whenStale_doesNotRevokeLinks() {
		Member m = existing("Ana", "ana@example.com");
		ReflectionTestUtils.setField(m, "version", 3L);

		assertThatThrownBy(() -> service.update(current, m.getId(), update("Ana", "new@example.com", 2)))
			.isInstanceOf(ConflictException.class);
		verify(memberTokens, never()).revokeAll(any());
	}

	// --- preferences link ---

	@Test
	void createPreferencesLink_buildsTheFragmentUrlFromTheIssuedToken() {
		Member m = existing("Ana", "ana@example.com");
		Instant expires = LATER.plus(Duration.ofDays(60));
		given(memberTokens.issue(m, MemberTokenPurpose.PREFERENCES))
			.willReturn(new MemberTokenService.IssuedToken("abc-DEF_123", expires));

		var link = service.createPreferencesLink(current, m.getId());

		assertThat(link.url()).isEqualTo("https://app.example.com/m/preferences#t=abc-DEF_123");
		assertThat(link.expiresAt()).isEqualTo(expires);
	}

	@Test
	void createPreferencesLink_withTrailingSlashInBaseUrl_doesNotDoubleTheSlash() {
		service = new MemberService(repository, groups, memberTokens, preferences,
				TestProperties.app("https://app.example.com/", Duration.ofDays(60)),
				Clock.fixed(LATER, ZoneOffset.UTC));
		Member m = existing("Ana", "ana@example.com");
		given(memberTokens.issue(m, MemberTokenPurpose.PREFERENCES))
			.willReturn(new MemberTokenService.IssuedToken("tok", LATER));

		assertThat(service.createPreferencesLink(current, m.getId()).url())
			.isEqualTo("https://app.example.com/m/preferences#t=tok");
	}

	@Test
	void createPreferencesLink_withMemberFromAnotherGroup_throwsNotFoundWithoutIssuing() {
		UUID foreignId = UUID.randomUUID();
		given(repository.findByIdAndGroupId(foreignId, groupId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.createPreferencesLink(current, foreignId)).isInstanceOf(NotFoundException.class);
		verify(memberTokens, never()).issue(any(), any());
	}

	// --- paused flag ---

	@Test
	void get_fillsInvitationsPausedFromThePreferences() {
		Member m = existing("Ana", "ana@example.com");
		given(preferences.isPaused(m.getId())).willReturn(true);

		assertThat(service.get(current, m.getId()).invitationsPaused()).isTrue();
	}

	@Test
	void create_returnsMemberAsNotPaused() {
		assertThat(service.create(current, new CreateMemberRequest("Ana", "ana@example.com")).invitationsPaused())
			.isFalse();
	}

	@Test
	void list_fillsInvitationsPausedWithASingleQueryForThePage() {
		Member ana = existing("Ana", "ana@example.com");
		Member bob = existing("Bob", "bob@example.com");
		PageRequest page = PageRequest.of(0, 20);
		given(repository.findAllByGroupId(groupId, page)).willReturn(new PageImpl<>(List.of(ana, bob), page, 2));
		given(preferences.pausedMemberIds(groupId)).willReturn(Set.of(bob.getId()));

		var result = service.list(current, null, page).getContent();

		assertThat(result).extracting("invitationsPaused").containsExactly(false, true);
		verify(preferences, times(1)).pausedMemberIds(groupId);
		verify(preferences, never()).isPaused(any());
	}

	// --- get / delete / tenancy ---

	@Test
	void get_withOwnMember_returnsIt() {
		Member m = existing("Ana", "ana@example.com");

		assertThat(service.get(current, m.getId()).name()).isEqualTo("Ana");
	}

	@Test
	void getUpdateDelete_withMemberFromAnotherGroup_throwNotFound() {
		UUID foreignId = UUID.randomUUID();
		given(repository.findByIdAndGroupId(foreignId, groupId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(current, foreignId)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(current, foreignId, update("X", "x@example.com", 0)))
			.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(current, foreignId)).isInstanceOf(NotFoundException.class);
		verify(repository, never()).delete(any());
	}

	@Test
	void delete_withOwnMember_deletesIt() {
		Member m = existing("Ana", "ana@example.com");

		service.delete(current, m.getId());

		verify(repository).delete(m);
	}

	// --- list ---

	@Test
	void list_withBlankQuery_listsTheWholeGroup() {
		Member m = existing("Ana", "ana@example.com");
		PageRequest page = PageRequest.of(0, 20);
		given(repository.findAllByGroupId(groupId, page)).willReturn(new PageImpl<>(List.of(m), page, 1));

		assertThat(service.list(current, "   ", page).getContent()).extracting("name").containsExactly("Ana");
		assertThat(service.list(current, null, page).getContent()).hasSize(1);
		verify(repository, never()).search(any(), any(), any());
	}

	@Test
	void list_withQuery_searchesWithTrimmedLowercasedWrappedPattern() {
		PageRequest page = PageRequest.of(0, 20);
		given(repository.search(groupId, "%ana%", page)).willReturn(new PageImpl<>(List.of(), page, 0));

		service.list(current, "  AnA ", page);

		verify(repository).search(groupId, "%ana%", page);
		verify(repository, never()).findAllByGroupId(any(), any());
	}

	@Test
	void list_withLikeWildcardsInQuery_escapesThemBeforeSearching() {
		PageRequest page = PageRequest.of(0, 20);
		given(repository.search(any(), any(), any())).willReturn(new PageImpl<>(List.of(), page, 0));

		service.list(current, "50%_off\\", page);

		verify(repository).search(groupId, "%50\\%\\_off\\\\%", page);
	}

}
