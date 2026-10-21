package io.grouploom.backend.user.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.user.dto.MeResponse;
import io.grouploom.backend.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class MeController {

	private final CurrentUserService service;

	public MeController(CurrentUserService service) {
		this.service = service;
	}

	@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
	public MeResponse me(CurrentUser current) {
		return service.me(current);
	}

}
