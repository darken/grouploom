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

		@PostMapping("/test/validate")
		void validate(@Valid @RequestBody Body body) {
		}

	}

}
