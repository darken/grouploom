package io.grouploom.backend.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import({ GlobalExceptionHandler.class, GlobalExceptionHandlerTest.ThrowingController.class })
@WithMockUser
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void notFound_whenServiceThrowsNotFound_returns404ProblemDetail() throws Exception {
		mockMvc.perform(get("/test/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("missing"));
	}

	@Test
	void conflict_whenServiceThrowsConflict_returns409ProblemDetail() throws Exception {
		mockMvc.perform(get("/test/conflict"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void conflict_withCodeAndField_exposesThemAsProblemProperties() throws Exception {
		mockMvc.perform(get("/test/conflict-coded"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("thing.taken"))
			.andExpect(jsonPath("$.field").value("name"));
	}

	@Test
	void conflict_withoutCode_omitsCodeAndField() throws Exception {
		mockMvc.perform(get("/test/conflict"))
			.andExpect(jsonPath("$.code").doesNotExist())
			.andExpect(jsonPath("$.field").doesNotExist());
	}

	@Test
	void badRequest_withCodeAndField_returns400WithCodeAndFieldErrors() throws Exception {
		mockMvc.perform(get("/test/bad-request"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Start is in the past."))
			.andExpect(jsonPath("$.code").value("thing.in-past"))
			.andExpect(jsonPath("$.errors[0].field").value("start"))
			.andExpect(jsonPath("$.errors[0].message").value("Start is in the past."));
	}

	@Test
	void badRequest_withoutField_omitsErrorsList() throws Exception {
		mockMvc.perform(get("/test/bad-request-plain"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("thing.bad"))
			.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void unknownSortProperty_returns400ProblemDetail() throws Exception {
		mockMvc.perform(get("/test/bad-sort"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Invalid sort property."));
	}

	@Test
	void validation_whenBodyInvalid_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/test/validate")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty());
	}

	@TestConfiguration
	@RestController
	static class ThrowingController {

		record Body(@NotBlank String name) {
		}

		@GetMapping("/test/not-found")
		void notFound() {
			throw new NotFoundException("missing");
		}

		@GetMapping("/test/conflict")
		void conflict() {
			throw new ConflictException("duplicate");
		}

		@GetMapping("/test/conflict-coded")
		void conflictCoded() {
			throw new ConflictException("taken", "thing.taken", "name");
		}

		@GetMapping("/test/bad-request")
		void badRequest() {
			throw new BadRequestException("Start is in the past.", "thing.in-past", "start");
		}

		@GetMapping("/test/bad-request-plain")
		void badRequestPlain() {
			throw new BadRequestException("Bad.", "thing.bad", null);
		}

		@GetMapping("/test/bad-sort")
		void badSort() {
			throw new org.springframework.data.core.PropertyReferenceException("nope",
					org.springframework.data.core.TypeInformation.of(String.class), java.util.List.of());
		}

		@PostMapping("/test/validate")
		void validate(@Valid @RequestBody Body body) {
		}

	}

}
