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

	@Test
	void publicPreferences_documentSuccessResponsesTheMemberTokenHeaderAndNoBearerRequirement() throws Exception {
		docs()
			.andExpect(jsonPath("$.paths['/public/preferences'].get.responses['200'].content['application/json'].schema['$ref']")
				.value("#/components/schemas/PublicPreferencesResponse"))
			.andExpect(jsonPath("$.paths['/public/preferences'].put.responses['200'].content['application/json'].schema['$ref']")
				.value("#/components/schemas/PublicPreferencesResponse"))
			.andExpect(jsonPath("$.paths['/public/preferences'].get.responses['401'].content['application/problem+json'].schema['$ref']")
				.value("#/components/schemas/ProblemDetail"))
			.andExpect(jsonPath("$.paths['/public/preferences'].get.parameters[?(@.name=='X-Member-Token')].in").value("header"))
			.andExpect(jsonPath("$.paths['/public/preferences'].get.parameters[?(@.name=='X-Member-Token')].required").value(true))
			.andExpect(jsonPath("$.paths['/public/preferences'].put.parameters[?(@.name=='X-Member-Token')].required").value(true))
			.andExpect(jsonPath("$.paths['/public/preferences'].get.security").doesNotExist())
			.andExpect(jsonPath("$.paths['/public/preferences'].put.security").doesNotExist());
	}

	@Test
	void preferencesLink_documentsTheCreatedResponse() throws Exception {
		docs()
			.andExpect(jsonPath("$.paths['/members/{id}/preferences-link'].post.responses['201'].content['application/json'].schema['$ref']")
				.value("#/components/schemas/PreferencesLinkResponse"))
			.andExpect(jsonPath("$.paths['/members/{id}/preferences-link'].post.security[0].bearerAuth").exists());
	}

}
