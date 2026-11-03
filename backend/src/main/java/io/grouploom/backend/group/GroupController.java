package io.grouploom.backend.group;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.group.dto.UpdateGroupRequest;
import io.grouploom.backend.user.dto.GroupSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/group", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

	private final GroupService service;

	public GroupController(GroupService service) {
		this.service = service;
	}

	@PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Change the group's settings (owner only)")
	public GroupSummary update(@Parameter(hidden = true) CurrentUser current, @Valid @RequestBody UpdateGroupRequest request) {
		return service.update(current, request);
	}

}
