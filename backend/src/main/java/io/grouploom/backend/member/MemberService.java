package io.grouploom.backend.member;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.member.dto.CreateMemberRequest;
import io.grouploom.backend.member.dto.MemberResponse;
import io.grouploom.backend.member.dto.PreferencesLinkResponse;
import io.grouploom.backend.member.dto.UpdateMemberRequest;
import io.grouploom.backend.membertoken.MemberTokenPurpose;
import io.grouploom.backend.membertoken.MemberTokenService;
import io.grouploom.backend.membertoken.PreferencesLinks;
import io.grouploom.backend.preference.PreferenceService;

@Service
public class MemberService {

	private final MemberRepository members;
	private final GroupRepository groups;
	private final MemberTokenService memberTokens;
	private final PreferenceService preferences;
	private final AppProperties properties;
	private final Clock clock;

	public MemberService(MemberRepository members, GroupRepository groups, MemberTokenService memberTokens,
			PreferenceService preferences, AppProperties properties, Clock clock) {
		this.members = members;
		this.groups = groups;
		this.memberTokens = memberTokens;
		this.preferences = preferences;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<MemberResponse> list(CurrentUser current, String q, Pageable pageable) {
		Page<Member> page = (q == null || q.isBlank())
				? members.findAllByGroupId(current.groupId(), pageable)
				: members.search(current.groupId(), likePattern(q), pageable);
		Set<UUID> paused = preferences.pausedMemberIds(current.groupId());
		return page.map(m -> MemberMapper.toResponse(m, paused.contains(m.getId())));
	}

	@Transactional(readOnly = true)
	public MemberResponse get(CurrentUser current, UUID id) {
		Member member = find(current, id);
		return MemberMapper.toResponse(member, preferences.isPaused(member.getId()));
	}

	@Transactional
	public MemberResponse create(CurrentUser current, CreateMemberRequest request) {
		String name = request.name().trim();
		String email = normalizeEmail(request.email());
		if (members.existsByGroupIdAndEmailIgnoreCase(current.groupId(), email)) {
			throw emailTaken();
		}
		Member created = new Member(groups.getReferenceById(current.groupId()), name, email, clock.instant());
		return MemberMapper.toResponse(flush(created), false);
	}

	@Transactional
	public MemberResponse update(CurrentUser current, UUID id, UpdateMemberRequest request) {
		Member member = find(current, id);
		if (request.version() != member.getVersion()) {
			throw stale();
		}
		String email = normalizeEmail(request.email());
		if (members.existsByGroupIdAndEmailIgnoreCaseAndIdNot(current.groupId(), email, id)) {
			throw emailTaken();
		}
		boolean emailChanged = !email.equals(member.getEmail());
		member.update(request.name().trim(), email, clock.instant());
		Member saved = flush(member);
		if (emailChanged) {
			// Earlier links went to the old address.
			memberTokens.revokeAll(saved);
		}
		return MemberMapper.toResponse(saved, preferences.isPaused(saved.getId()));
	}

	@Transactional
	public PreferencesLinkResponse createPreferencesLink(CurrentUser current, UUID id) {
		Member member = find(current, id);
		var issued = memberTokens.issue(member, MemberTokenPurpose.PREFERENCES);
		String url = PreferencesLinks.url(properties.publicBaseUrl(), issued.raw());
		return new PreferencesLinkResponse(url, issued.expiresAt());
	}

	@Transactional
	public void delete(CurrentUser current, UUID id) {
		members.delete(find(current, id));
	}

	private Member find(CurrentUser current, UUID id) {
		// Scoped by group: another group's id is indistinguishable from a missing one.
		return members.findByIdAndGroupId(id, current.groupId())
			.orElseThrow(() -> new NotFoundException("Member not found."));
	}

	private Member flush(Member member) {
		try {
			return members.saveAndFlush(member);
		}
		catch (DataIntegrityViolationException e) {
			// Two requests raced past the existence check; the unique index decided.
			throw emailTaken();
		}
		catch (ObjectOptimisticLockingFailureException e) {
			throw stale();
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/** Lowercases {@code q} and escapes LIKE wildcards so user input only ever matches literally. */
	private static String likePattern(String q) {
		String escaped = q.trim().toLowerCase(Locale.ROOT)
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}

	private static ConflictException emailTaken() {
		return new ConflictException("A member with this email already exists.", "member.email-taken", "email");
	}

	private static ConflictException stale() {
		return new ConflictException("This member was changed by someone else. Reload and try again.",
				"member.stale", null);
	}

}
