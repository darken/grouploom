package io.grouploom.backend.system.service;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import io.grouploom.backend.system.dto.SystemInfoResponse;

@Service
public class SystemInfoService {

	private final BuildProperties buildProperties;

	public SystemInfoService(BuildProperties buildProperties) {
		this.buildProperties = buildProperties;
	}

	public SystemInfoResponse getInfo() {
		return new SystemInfoResponse(buildProperties.getName(), buildProperties.getVersion());
	}

}
