package io.grouploom.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.grouploom.backend.TestcontainersConfiguration;

/** The generated frontend types depend on these shapes, so they are pinned here. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIntegrationTest {

	@Autowired MockMvc mockMvc;

	private org.springframework.test.web.servlet.ResultActions docs() throws Exception {
		return mockMvc.perform(get("/api/v3/api-docs").servletPath("/api")).andExpect(status().isOk());
	}

	@Test
	void eventTypeGetAndUpdate_documentTheSuccessResponseAlongsideErrors() throws Exception {
		docs()
			.andExpect(jsonPath("$.paths['/event-types/{id}'].get.responses['200'].content['application/json'].schema['$ref']")
				.value("#/components/schemas/EventTypeResponse"))
			.andExpect(jsonPath("$.paths['/event-types/{id}'].put.responses['200'].content['application/json'].schema['$ref']")
				.value("#/components/schemas/EventTypeResponse"));
	}

	@Test
	void eventTypeErrors_referenceTheProblemDetailSchema() throws Exception {
		docs()
			.andExpect(jsonPath("$.paths['/event-types/{id}'].put.responses['409'].content['application/problem+json'].schema['$ref']")
				.value("#/components/schemas/ProblemDetail"))
			.andExpect(jsonPath("$.paths['/event-types/{id}'].get.responses['404'].content['application/problem+json'].schema['$ref']")
				.value("#/components/schemas/ProblemDetail"));
	}

}
