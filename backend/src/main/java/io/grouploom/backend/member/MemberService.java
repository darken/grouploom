package io.grouploom.backend.member;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.config.AppProperties;
import io.grouploom.backend.email.EmailOutboxEntry;
import io.grouploom.backend.email.EmailOutboxService;
import io.grouploom.backend.email.dto.BulkEmailResult;
import io.grouploom.backend.email.dto.EmailDeliveryStatus;
import io.grouploom.backend.email.dto.EmailQueuedResponse;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.error.TooManyRequestsException;
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

	private static final int BULK_PAGE_SIZE = 500;

	private final MemberRepository members;
	private final GroupRepository groups;
	private final MemberTokenService memberTokens;
	private final PreferenceService preferences;
	private final EmailOutboxService emailOutbox;
	private final AppProperties properties;
	private final Clock clock;

	public MemberService(MemberRepository members, GroupRepository groups, MemberTokenService memberTokens,
			PreferenceService preferences, EmailOutboxService emailOutbox, AppProperties properties, Clock clock) {
		this.members = members;
		this.groups = groups;
		this.memberTokens = memberTokens;
		this.preferences = preferences;
		this.emailOutbox = emailOutbox;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<MemberResponse> list(CurrentUser current, String q, Pageable pageable) {
		Page<Member> page = (q == null || q.isBlank())
				? members.findAllByGroupId(current.groupId(), pageable)
				: members.search(current.groupId(), likePattern(q), pageable);
		Set<UUID> paused = preferences.pausedMemberIds(current.groupId());
		Map<UUID, EmailDeliveryStatus> emails = emailOutbox.latestPreferencesStatus(current.groupId(),
				page.getContent().stream().map(Member::getId).toList());
		return page.map(m -> MemberMapper.toResponse(m, paused.contains(m.getId()), emails.get(m.getId())));
	}

	@Transactional(readOnly = true)
	public MemberResponse get(CurrentUser current, UUID id) {
		Member member = find(current, id);
		return toResponse(current, member);
	}

	@Transactional
	public MemberResponse create(CurrentUser current, CreateMemberRequest request) {
		String name = request.name().trim();
		String email = normalizeEmail(request.email());
		if (members.existsByGroupIdAndEmailIgnoreCase(current.groupId(), email)) {
			throw emailTaken();
		}
		Member created = new Member(groups.getReferenceById(current.groupId()), name, email, clock.instant());
		return MemberMapper.toResponse(flush(created), false, null);
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
		return toResponse(current, saved);
	}

	@Transactional
	public PreferencesLinkResponse createPreferencesLink(CurrentUser current, UUID id) {
		Member member = find(current, id);
		var issued = memberTokens.issue(member, MemberTokenPurpose.PREFERENCES);
		String url = PreferencesLinks.url(properties.publicBaseUrl(), issued.raw());
		return new PreferencesLinkResponse(url, issued.expiresAt());
	}

	/** Queues the link email; a background dispatcher sends it. */
	@Transactional
	public EmailQueuedResponse sendPreferencesEmail(CurrentUser current, UUID id) {
		Member member = find(current, id);
		if (emailOutbox.isWithinCooldown(member.getId())) {
			throw new TooManyRequestsException("A link was sent to this member a moment ago. Try again in a few minutes.",
					"email.cooldown");
		}
		EmailOutboxEntry entry = emailOutbox.enqueuePreferencesLink(member, current.userId());
		return new EmailQueuedResponse(entry.getCreatedAt());
	}

	/** Queues the link email for every member except those emailed within the cooldown. */
	@Transactional
	public BulkEmailResult sendPreferencesEmailToAll(CurrentUser current) {
		int queued = 0;
		int skipped = 0;
		Page<Member> page;
		int pageNumber = 0;
		do {
			page = members.findAllByGroupId(current.groupId(), PageRequest.of(pageNumber++, BULK_PAGE_SIZE, Sort.by("id")));
			List<Member> content = page.getContent();
			Set<UUID> coolingDown = emailOutbox.membersWithinCooldown(content.stream().map(Member::getId).toList());
			List<Member> toQueue = content.stream().filter(m -> !coolingDown.contains(m.getId())).toList();
			queued += emailOutbox.enqueuePreferencesLinks(toQueue, current.userId());
			skipped += content.size() - toQueue.size();
		}
		while (page.hasNext());
		return new BulkEmailResult(queued, skipped);
	}

	@Transactional
	public void delete(CurrentUser current, UUID id) {
		members.delete(find(current, id));
	}

	private MemberResponse toResponse(CurrentUser current, Member member) {
		EmailDeliveryStatus email = emailOutbox.latestPreferencesStatus(current.groupId(), List.of(member.getId()))
			.get(member.getId());
		return MemberMapper.toResponse(member, preferences.isPaused(member.getId()), email);
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
