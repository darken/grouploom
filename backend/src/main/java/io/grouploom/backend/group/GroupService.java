package io.grouploom.backend.group;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.ForbiddenException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.group.dto.UpdateGroupRequest;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.dto.GroupSummary;

@Service
public class GroupService {

	private final GroupRepository groups;

	public GroupService(GroupRepository groups) {
		this.groups = groups;
	}

	/** Only the owner may change the group's settings: the language decides what every email says. */
	@Transactional
	public GroupSummary update(CurrentUser current, UpdateGroupRequest request) {
		if (current.role() != Role.OWNER) {
			throw new ForbiddenException("Only the owner can change the group.");
		}
		Group group = groups.findById(current.groupId()).orElseThrow(() -> new NotFoundException("Group not found."));
		group.changeLanguage(request.language());
		return new GroupSummary(group.getId(), group.getName(), group.getLanguage());
	}

}
