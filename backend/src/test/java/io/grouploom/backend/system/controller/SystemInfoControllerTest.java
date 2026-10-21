package io.grouploom.backend.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;

import io.grouploom.backend.config.SecurityConfig;
import io.grouploom.backend.system.dto.SystemInfoResponse;
import io.grouploom.backend.system.service.SystemInfoService;

@WebMvcTest(SystemInfoController.class)
@Import(SecurityConfig.class)
class SystemInfoControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	SystemInfoService service;

	@Test
	void info_withoutAuth_returnsNameAndVersion() throws Exception {
		given(service.getInfo()).willReturn(new SystemInfoResponse("backend", "1.2.3"));

		mockMvc.perform(get("/api/system/info").servletPath("/api"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("backend"))
			.andExpect(jsonPath("$.version").value("1.2.3"));
	}

	@Test
	void unknownPath_withoutAuth_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/does-not-exist").servletPath("/api"))
			.andExpect(status().isUnauthorized());
	}

}
