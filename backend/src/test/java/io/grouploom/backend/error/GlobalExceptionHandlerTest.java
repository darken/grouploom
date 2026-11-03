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

import io.grouploom.backend.config.LocaleConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import({ GlobalExceptionHandler.class, LocaleConfig.class, GlobalExceptionHandlerTest.ThrowingController.class })
@WithMockUser
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void notFound_whenServiceThrowsNotFound_returns404ProblemDetailWithCode() throws Exception {
		mockMvc.perform(get("/test/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("Event not found."))
			.andExpect(jsonPath("$.code").value("event.not-found"));
	}

	@Test
	void forbidden_returns403ProblemDetailWithCode() throws Exception {
		mockMvc.perform(get("/test/forbidden"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Only the owner can change the group."))
			.andExpect(jsonPath("$.code").value("group.owner-only"));
	}

	@Test
	void conflict_withoutField_omitsField() throws Exception {
		mockMvc.perform(get("/test/conflict"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.detail").value("An account with this email already exists."))
			.andExpect(jsonPath("$.code").value("account.email-taken"))
			.andExpect(jsonPath("$.field").doesNotExist());
	}

	@Test
	void conflict_withField_exposesCodeAndField() throws Exception {
		mockMvc.perform(get("/test/conflict-field"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("A member with this email already exists."))
			.andExpect(jsonPath("$.code").value("member.email-taken"))
			.andExpect(jsonPath("$.field").value("email"));
	}

	@Test
	void conflict_withArguments_fillsThemIntoTheMessage() throws Exception {
		mockMvc.perform(get("/test/conflict-args"))
			.andExpect(jsonPath("$.detail").value("An event can have at most 10 scheduled notifications."))
			.andExpect(jsonPath("$.code").value("notification.limit"));
		mockMvc.perform(get("/test/conflict-args").header("Accept-Language", "ja"))
			.andExpect(jsonPath("$.detail").value("1つのイベントに予約できる通知は最大10件です。"));
	}

	@Test
	void badRequest_withField_returns400WithCodeAndFieldErrors() throws Exception {
		mockMvc.perform(get("/test/bad-request"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("The start can't be in the past."))
			.andExpect(jsonPath("$.code").value("event.start-in-past"))
			.andExpect(jsonPath("$.errors[0].field").value("localStart"))
			.andExpect(jsonPath("$.errors[0].message").value("The start can't be in the past."));
	}

	@Test
	void badRequest_withoutField_omitsErrorsList() throws Exception {
		mockMvc.perform(get("/test/bad-request-plain"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("event.invalid-when"))
			.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	void invalidMemberToken_returns401ProblemDetailWithCode() throws Exception {
		mockMvc.perform(get("/test/member-token-expired"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("member-token.expired"))
			.andExpect(jsonPath("$.detail").value("This link has expired."));
	}

	@Test
	void invalidCredentials_returns401ProblemDetailWithCode() throws Exception {
		mockMvc.perform(get("/test/credentials"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Email or password is incorrect."))
			.andExpect(jsonPath("$.code").value("auth.invalid-credentials"));
	}

	@Test
	void tooManyRequests_returns429ProblemDetailWithCode() throws Exception {
		mockMvc.perform(get("/test/too-many"))
			.andExpect(status().isTooManyRequests())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.code").value("email.cooldown"))
			.andExpect(jsonPath("$.detail").value("A link was sent to this member a moment ago. Try again in a few minutes."));
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
			.andExpect(jsonPath("$.detail").value("Validation failed"))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
	}

	@Test
	void conflict_withJapaneseBrowser_returnsJapaneseDetailAndTheSameCode() throws Exception {
		mockMvc.perform(get("/test/stale").header("Accept-Language", "ja"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("このイベントは他の人が変更しました。再読み込みしてからもう一度お試しください。"))
			.andExpect(jsonPath("$.code").value("event.stale"));
	}

	@Test
	void conflict_withRegionalJapanese_returnsJapanese() throws Exception {
		mockMvc.perform(get("/test/stale").header("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8"))
			.andExpect(jsonPath("$.detail").value("このイベントは他の人が変更しました。再読み込みしてからもう一度お試しください。"));
	}

	@Test
	void conflict_withUnsupportedLanguage_returnsEnglish() throws Exception {
		mockMvc.perform(get("/test/stale").header("Accept-Language", "fr"))
			.andExpect(jsonPath("$.detail").value("This event was changed by someone else. Reload and try again."));
	}

	@Test
	void conflict_withoutAcceptLanguage_returnsEnglish() throws Exception {
		mockMvc.perform(get("/test/stale"))
			.andExpect(jsonPath("$.detail").value("This event was changed by someone else. Reload and try again."));
	}

	@Test
	void badRequest_withJapaneseBrowser_translatesTheDetailAndTheFieldMessage() throws Exception {
		mockMvc.perform(get("/test/bad-request").header("Accept-Language", "ja"))
			.andExpect(jsonPath("$.detail").value("開始日時を過去にすることはできません。"))
			.andExpect(jsonPath("$.errors[0].message").value("開始日時を過去にすることはできません。"));
	}

	@Test
	void invalidCredentials_withJapaneseBrowser_returnsJapanese() throws Exception {
		mockMvc.perform(get("/test/credentials").header("Accept-Language", "ja"))
			.andExpect(jsonPath("$.detail").value("メールアドレスまたはパスワードが正しくありません。"));
	}

	@Test
	void memberToken_withJapaneseBrowser_returnsJapanese() throws Exception {
		mockMvc.perform(get("/test/member-token-expired").header("Accept-Language", "ja"))
			.andExpect(jsonPath("$.detail").value("このリンクは有効期限が切れています。"))
			.andExpect(jsonPath("$.code").value("member-token.expired"));
	}

	@TestConfiguration
	@RestController
	static class ThrowingController {

		record Body(@NotBlank String name) {
		}

		@GetMapping("/test/not-found")
		void notFound() {
			throw new NotFoundException(ErrorCode.EVENT_NOT_FOUND);
		}

		@GetMapping("/test/forbidden")
		void forbidden() {
			throw new ForbiddenException(ErrorCode.GROUP_OWNER_ONLY);
		}

		@GetMapping("/test/conflict")
		void conflict() {
			throw new ConflictException(ErrorCode.ACCOUNT_EMAIL_TAKEN);
		}

		@GetMapping("/test/conflict-field")
		void conflictField() {
			throw new ConflictException(ErrorCode.MEMBER_EMAIL_TAKEN, "email");
		}

		@GetMapping("/test/conflict-args")
		void conflictArgs() {
			throw new ConflictException(ErrorCode.NOTIFICATION_LIMIT, null, 10);
		}

		@GetMapping("/test/stale")
		void stale() {
			throw new ConflictException(ErrorCode.EVENT_STALE);
		}

		@GetMapping("/test/too-many")
		void tooMany() {
			throw new TooManyRequestsException(ErrorCode.EMAIL_COOLDOWN);
		}

		@GetMapping("/test/bad-request")
		void badRequest() {
			throw new BadRequestException(ErrorCode.EVENT_START_IN_PAST, "localStart");
		}

		@GetMapping("/test/bad-request-plain")
		void badRequestPlain() {
			throw new BadRequestException(ErrorCode.EVENT_INVALID_WHEN, null);
		}

		@GetMapping("/test/member-token-expired")
		void memberTokenExpired() {
			throw new InvalidMemberTokenException(ErrorCode.MEMBER_TOKEN_EXPIRED);
		}

		@GetMapping("/test/credentials")
		void credentials() {
			throw new InvalidCredentialsException();
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
