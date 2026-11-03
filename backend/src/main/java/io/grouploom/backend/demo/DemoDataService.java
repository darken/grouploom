package io.grouploom.backend.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.attendance.AttendanceRepository;
import io.grouploom.backend.attendance.AttendanceResponse;
import io.grouploom.backend.attendance.AttendanceSource;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.member.MemberRepository;
import io.grouploom.backend.preference.EventTypeOptOut;
import io.grouploom.backend.preference.EventTypeOptOutRepository;
import io.grouploom.backend.preference.MemberPreferences;
import io.grouploom.backend.preference.MemberPreferencesRepository;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;
import io.grouploom.backend.user.repository.UserRepository;

/**
 * Seeds the demo group: event types, members, past and upcoming events with replies, and a few preferences. Dates are
 * relative to now so the upcoming events are always upcoming on a fresh seed. Members use example.com addresses and no
 * email or notification rows are created, so seeding never sends anything.
 */
@Service
@ConditionalOnProperty(name = "grouploom.demo.enabled", havingValue = "true")
public class DemoDataService {

	private static final ZoneId ZONE = ZoneId.of("Europe/London");

	private static final List<String> MEMBER_NAMES = List.of("Alice Moreno", "Ben Okafor", "Chloe Nguyen",
			"Daniel Fischer", "Emma Clarke", "Farid Haddad", "Grace Kim", "Hugo Martin", "Isla Murphy", "Jonas Berg",
			"Keiko Tanaka", "Liam O'Brien", "Maya Patel", "Noah Williams", "Olivia Rossi");

	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	private final DemoProperties properties;
	private final GroupRepository groups;
	private final UserRepository users;
	private final EventTypeRepository eventTypes;
	private final MemberRepository members;
	private final EventRepository events;
	private final AttendanceRepository attendances;
	private final MemberPreferencesRepository preferences;
	private final EventTypeOptOutRepository optOuts;
	private final Clock clock;

	public DemoDataService(DemoProperties properties, GroupRepository groups, UserRepository users,
			EventTypeRepository eventTypes, MemberRepository members, EventRepository events,
			AttendanceRepository attendances, MemberPreferencesRepository preferences,
			EventTypeOptOutRepository optOuts, Clock clock) {
		this.properties = properties;
		this.groups = groups;
		this.users = users;
		this.eventTypes = eventTypes;
		this.members = members;
		this.events = events;
		this.attendances = attendances;
		this.preferences = preferences;
		this.optOuts = optOuts;
		this.clock = clock;
	}

	/** Returns false, and changes nothing, when the demo user already exists. */
	@Transactional
	public boolean seedIfMissing() {
		String email = properties.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmailIgnoreCase(email)) {
			return false;
		}
		Instant now = clock.instant();
		LocalDate today = LocalDate.now(clock.withZone(ZONE));

		Group group = groups.save(new Group("Riverside Running Club", now));
		User organizer = users.save(new User(group, email, "Sam Rivera", passwordEncoder.encode(properties.password()),
				Role.OWNER, now));

		EventType weeklyRun = eventTypes.save(new EventType(group, "Weekly Run",
				"Easy 8 km loop along the river. All paces welcome.", EventTypeColor.GREEN, 60, now));
		EventType trailDay = eventTypes.save(new EventType(group, "Trail Day",
				"A longer run on the hills, followed by lunch.", EventTypeColor.TEAL, 240, now));
		EventType social = eventTypes.save(new EventType(group, "Social",
				"Drinks, food and no running.", EventTypeColor.ORANGE, 120, now));
		EventType committee = eventTypes.save(new EventType(group, "Committee Meeting",
				"Planning the season. Replaced by the monthly newsletter.", EventTypeColor.GRAY, 90, now));

		List<Member> roster = MEMBER_NAMES.stream()
			.map(name -> members.save(new Member(group, name, emailFor(name), now)))
			.toList();

		Event pastCommittee = event(group, committee, "Season planning", "Agree on the race calendar.",
				"Riverside Café, back room", today.minusDays(21), LocalTime.of(19, 0), 90, now);
		Event pastTrail = event(group, trailDay, "Box Hill loop", "18 km with about 500 m of climbing.",
				"Box Hill car park", today.minusDays(14), LocalTime.of(9, 0), 240, now);
		Event pastRun = event(group, weeklyRun, "Thursday river run", null, "Boathouse steps",
				today.minusDays(7), LocalTime.of(18, 30), 60, now);
		Event nextRun = event(group, weeklyRun, "Thursday river run", "Head torches recommended.", "Boathouse steps",
				today.plusDays(3), LocalTime.of(18, 30), 60, now);
		Event nextTrail = event(group, trailDay, "Leith Hill trail", "20 km, mostly woodland. Bring water.",
				"Leith Hill Tower", today.plusDays(7), LocalTime.of(9, 0), 240, now);
		event(group, social, "Autumn social", "Food is on the club.", "The Anchor, upstairs", today.plusDays(10),
				LocalTime.of(19, 30), 150, now);
		Event cancelledRun = event(group, weeklyRun, "Thursday river run", null, "Boathouse steps",
				today.plusDays(14), LocalTime.of(18, 30), 60, now);
		cancelledRun.cancel("The towpath is closed for repairs this week. See you the week after!", now);
		event(group, weeklyRun, "Thursday river run", null, "Boathouse steps", today.plusDays(21),
				LocalTime.of(18, 30), 60, now);

		committee.archive(now);

		MemberPreferences paused = new MemberPreferences(roster.get(14), now);
		paused.setInvitationsPaused(true, now);
		preferences.save(paused);
		optOuts.save(new EventTypeOptOut(roster.get(9).getId(), social.getId(), now));

		// Last: the upsert clears the persistence context.
		replies(pastCommittee, roster, now, "GGGNG");
		replies(pastTrail, roster, now, "GGNGGGNGG");
		replies(pastRun, roster, now, "GGGGNGGGGGNG");
		replies(nextRun, roster, now, "GGNGGG-G-N");
		replies(nextTrail, roster, now, "GG-N-G");
		reply(nextRun, roster.get(1), AttendanceResponse.GOING, "Running late, start without me", now);
		reply(nextRun, roster.get(2), AttendanceResponse.NOT_GOING, "Away for work, back next week", now);
		reply(nextTrail, roster.get(3), AttendanceResponse.NOT_GOING, "Twisted my ankle", now);
		attendances.upsert(group.getId(), nextTrail.getId(), roster.get(4).getId(), AttendanceResponse.GOING.name(),
				"Told me in person", AttendanceSource.ORGANIZER.name(), organizer.getId(), now);
		return true;
	}

	private Event event(Group group, EventType type, String title, String description, String location, LocalDate date,
			LocalTime start, int minutes, Instant now) {
		Instant startsAt = date.atTime(start).atZone(ZONE).toInstant();
		return events.save(new Event(group, type, title, description, location, startsAt,
				startsAt.plusSeconds(minutes * 60L), ZONE.getId(), now));
	}

	/** One character per member, in roster order: G going, N not going, - no reply. */
	private void replies(Event event, List<Member> roster, Instant now, String pattern) {
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if (c != '-') {
				AttendanceResponse response = c == 'G' ? AttendanceResponse.GOING : AttendanceResponse.NOT_GOING;
				reply(event, roster.get(i), response, null, now);
			}
		}
	}

	private void reply(Event event, Member member, AttendanceResponse response, String note, Instant now) {
		attendances.upsert(event.getGroup().getId(), event.getId(), member.getId(), response.name(), note,
				AttendanceSource.MEMBER.name(), null, now);
	}

	private static String emailFor(String name) {
		return name.toLowerCase(Locale.ROOT).replace("'", "").replace(' ', '.') + "@example.com";
	}

}
