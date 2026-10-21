package io.grouploom.backend.system.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.system.dto.SystemInfoResponse;
import io.grouploom.backend.system.service.SystemInfoService;

@RestController
@RequestMapping("/system")
public class SystemInfoController {

	private final SystemInfoService service;

	public SystemInfoController(SystemInfoService service) {
		this.service = service;
	}

	@GetMapping(path = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
	public SystemInfoResponse info() {
		return service.getInfo();
	}

}
