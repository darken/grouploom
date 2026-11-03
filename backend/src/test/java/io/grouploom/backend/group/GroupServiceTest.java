package io.grouploom.backend.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.ForbiddenException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.group.dto.UpdateGroupRequest;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.dto.GroupSummary;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

	private static final UUID GROUP_ID = UUID.randomUUID();

	@Mock GroupRepository groups;
	private GroupService service;
	private Group group;

	@BeforeEach
	void setUp() {
		service = new GroupService(groups);
		group = new Group("Chess Club", Language.EN, Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
	}

	private static CurrentUser as(Role role) {
		return new CurrentUser(UUID.randomUUID(), GROUP_ID, role);
	}

	@Test
	void update_byTheOwner_changesTheLanguage() {
		given(groups.findById(GROUP_ID)).willReturn(Optional.of(group));

		GroupSummary result = service.update(as(Role.OWNER), new UpdateGroupRequest(Language.JA));

		assertThat(group.getLanguage()).isEqualTo(Language.JA);
		assertThat(result).isEqualTo(new GroupSummary(GROUP_ID, "Chess Club", Language.JA));
	}

	@Test
	void update_byAnOrganizer_isForbiddenAndChangesNothing() {
		assertThatThrownBy(() -> service.update(as(Role.ORGANIZER), new UpdateGroupRequest(Language.JA)))
			.isInstanceOf(ForbiddenException.class);

		assertThat(group.getLanguage()).isEqualTo(Language.EN);
	}

	@Test
	void update_whenTheGroupIsGone_throwsNotFound() {
		given(groups.findById(GROUP_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(as(Role.OWNER), new UpdateGroupRequest(Language.JA)))
			.isInstanceOf(NotFoundException.class);
	}

}
