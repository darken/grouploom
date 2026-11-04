package io.grouploom.backend.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.config.LocaleConfig;
import io.grouploom.backend.event.dto.HasLocalTimes;
import io.grouploom.backend.event.dto.ValidTimeRange;
import io.grouploom.backend.event.dto.ValidTimeZone;
import io.grouploom.backend.notification.dto.ScheduleNotificationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Validation messages come from the same bundles as domain errors. The first test also proves the wiring: Boot's
 * validator reads Spring's MessageSource in the request's language.
 */
@WebMvcTest(ValidationMessagesTest.Controller.class)
@Import({ GlobalExceptionHandler.class, LocaleConfig.class, ValidationMessagesTest.Controller.class })
@WithMockUser
class ValidationMessagesTest {

	@Autowired
	MockMvc mockMvc;

	private MockHttpServletRequestBuilder send(String path, String language, String json) {
		MockHttpServletRequestBuilder request = post(path)
			.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json);
		return language == null ? request : request.header("Accept-Language", language);
	}

	@Test
	void notBlank_withJapaneseBrowser_returnsTheOverriddenJapaneseMessage() throws Exception {
		mockMvc.perform(send("/test/basic", "ja", "{\"name\":\"\",\"email\":\"a@example.com\",\"count\":10}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("入力内容に誤りがあります。"))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].message").value("入力してください。"));
	}

	@Test
	void notBlank_withEnglishBrowser_keepsHibernateValidatorsText() throws Exception {
		mockMvc.perform(send("/test/basic", "en-US", "{\"name\":\"\",\"email\":\"a@example.com\",\"count\":10}"))
			.andExpect(jsonPath("$.detail").value("Validation failed"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
	}

	@Test
	void validation_withUnsupportedLanguage_returnsEnglishNotHibernateValidatorsFrench() throws Exception {
		mockMvc.perform(send("/test/basic", "fr", "{\"name\":\"\",\"email\":\"a@example.com\",\"count\":10}"))
			.andExpect(jsonPath("$.detail").value("Validation failed"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
	}

	@Test
	void validation_withoutAcceptLanguage_returnsEnglish() throws Exception {
		mockMvc.perform(send("/test/basic", null, "{\"name\":\"\",\"email\":\"a@example.com\",\"count\":10}"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
	}

	@Test
	void notNull_isTranslated() throws Exception {
		mockMvc.perform(send("/test/basic", "ja", "{\"name\":\"x\",\"email\":\"a@example.com\"}"))
			.andExpect(jsonPath("$.errors[0].field").value("count"))
			.andExpect(jsonPath("$.errors[0].message").value("入力してください。"));
		mockMvc.perform(send("/test/basic", null, "{\"name\":\"x\",\"email\":\"a@example.com\"}"))
			.andExpect(jsonPath("$.errors[0].message").value("must not be null"));
	}

	@Test
	void size_fillsTheLimitIntoTheMessage() throws Exception {
		String json = "{\"name\":\"abcd\",\"email\":\"a@example.com\",\"count\":10}";
		mockMvc.perform(send("/test/basic", "ja", json))
			.andExpect(jsonPath("$.errors[0].message").value("3文字以内で入力してください。"));
		mockMvc.perform(send("/test/basic", null, json))
			.andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 3"));
	}

	@Test
	void email_isTranslated() throws Exception {
		String json = "{\"name\":\"x\",\"email\":\"nope\",\"count\":10}";
		mockMvc.perform(send("/test/basic", "ja", json))
			.andExpect(jsonPath("$.errors[0].message").value("メールアドレスの形式が正しくありません。"));
		mockMvc.perform(send("/test/basic", null, json))
			.andExpect(jsonPath("$.errors[0].message").value("must be a well-formed email address"));
	}

	@Test
	void minAndMax_fillTheBoundIntoTheMessage() throws Exception {
		mockMvc.perform(send("/test/basic", "ja", "{\"name\":\"x\",\"email\":\"a@example.com\",\"count\":1}"))
			.andExpect(jsonPath("$.errors[0].message").value("5以上の値を入力してください。"));
		mockMvc.perform(send("/test/basic", "ja", "{\"name\":\"x\",\"email\":\"a@example.com\",\"count\":9999}"))
			.andExpect(jsonPath("$.errors[0].message").value("100以下の値を入力してください。"));
		mockMvc.perform(send("/test/basic", null, "{\"name\":\"x\",\"email\":\"a@example.com\",\"count\":1}"))
			.andExpect(jsonPath("$.errors[0].message").value("must be greater than or equal to 5"));
		mockMvc.perform(send("/test/basic", null, "{\"name\":\"x\",\"email\":\"a@example.com\",\"count\":9999}"))
			.andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));
	}

	@Test
	void passwordLength_namesBothBoundsInBothLanguages() throws Exception {
		mockMvc.perform(send("/test/password", "ja", "{\"password\":\"short\"}"))
			.andExpect(jsonPath("$.errors[0].message").value("12文字以上128文字以内で入力してください。"));
		mockMvc.perform(send("/test/password", null, "{\"password\":\"short\"}"))
			.andExpect(jsonPath("$.errors[0].message").value("size must be between 12 and 128"));
	}

	@Test
	void timeRange_isTranslatedAndLandsOnLocalEnd() throws Exception {
		String json = "{\"localStart\":\"2026-11-07T10:00:00\",\"localEnd\":\"2026-11-07T09:00:00\",\"timeZone\":\"UTC\"}";
		mockMvc.perform(send("/test/times", "ja", json))
			.andExpect(jsonPath("$.errors[0].field").value("localEnd"))
			.andExpect(jsonPath("$.errors[0].message").value("終了は開始より後にしてください。"));
		mockMvc.perform(send("/test/times", null, json))
			.andExpect(jsonPath("$.errors[0].field").value("localEnd"))
			.andExpect(jsonPath("$.errors[0].message").value("must be after the start"));
	}

	@Test
	void timeZone_isTranslated() throws Exception {
		String json = "{\"localStart\":\"2026-11-07T10:00:00\",\"localEnd\":\"2026-11-07T11:00:00\",\"timeZone\":\"Mars/Base\"}";
		mockMvc.perform(send("/test/times", "ja", json))
			.andExpect(jsonPath("$.errors[0].field").value("timeZone"))
			.andExpect(jsonPath("$.errors[0].message").value("有効なタイムゾーンを指定してください。例：Europe/Madrid"));
		mockMvc.perform(send("/test/times", null, json))
			.andExpect(jsonPath("$.errors[0].message").value("must be a valid time zone, e.g. Europe/Madrid"));
	}

	@Test
	void timing_everyIncoherentCombinationIsTranslated() throws Exception {
		check("{\"kind\":\"CANCELLATION\",\"timing\":\"NOW\"}", "kind", "must be INVITATION or REMINDER",
				"INVITATIONまたはREMINDERを指定してください。");
		check("{\"kind\":\"INVITATION\",\"timing\":\"NOW\",\"offsetMinutes\":30}", "offsetMinutes",
				"isn't allowed when sending now", "今すぐ送信する場合は指定できません。");
		check("{\"kind\":\"INVITATION\",\"timing\":\"NOW\",\"localSendAt\":\"2026-11-07T10:00:00\"}", "localSendAt",
				"isn't allowed when sending now", "今すぐ送信する場合は指定できません。");
		check("{\"kind\":\"INVITATION\",\"timing\":\"BEFORE_START\"}", "offsetMinutes", "is required for BEFORE_START",
				"BEFORE_STARTの場合は必須です。");
		check("{\"kind\":\"INVITATION\",\"timing\":\"BEFORE_START\",\"offsetMinutes\":30,\"localSendAt\":\"2026-11-07T10:00:00\"}",
				"localSendAt", "isn't allowed for BEFORE_START", "BEFORE_STARTの場合は指定できません。");
		check("{\"kind\":\"INVITATION\",\"timing\":\"AT\"}", "localSendAt", "is required for AT", "ATの場合は必須です。");
		check("{\"kind\":\"INVITATION\",\"timing\":\"AT\",\"localSendAt\":\"2026-11-07T10:00:00\",\"offsetMinutes\":30}",
				"offsetMinutes", "isn't allowed for AT", "ATの場合は指定できません。");
	}

	private void check(String json, String field, String english, String japanese) throws Exception {
		mockMvc.perform(send("/test/timing", null, json))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field=='" + field + "')].message").value(english));
		mockMvc.perform(send("/test/timing", "ja", json))
			.andExpect(jsonPath("$.errors[?(@.field=='" + field + "')].message").value(japanese));
	}

	@Test
	void invalidEnumValue_isReportedInTheRequestLanguage() throws Exception {
		mockMvc.perform(send("/test/timing", "ja", "{\"kind\":\"BOGUS\",\"timing\":\"NOW\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("入力内容に誤りがあります。"))
			.andExpect(jsonPath("$.errors[0].message").value("値が正しくありません。"));
		mockMvc.perform(send("/test/timing", null, "{\"kind\":\"BOGUS\",\"timing\":\"NOW\"}"))
			.andExpect(jsonPath("$.detail").value("Validation failed"))
			.andExpect(jsonPath("$.errors[0].message").value("Invalid value."));
	}

	@TestConfiguration
	@RestController
	static class Controller {

		record Basic(@NotBlank @Size(max = 3) String name, @Email String email, @NotNull @Min(5) @Max(100) Integer count) {
		}

		record Password(@NotNull @Size(min = 12, max = 128, message = "{validation.password.size}") String password) {
		}

		@ValidTimeRange
		record Times(LocalDateTime localStart, LocalDateTime localEnd, @ValidTimeZone String timeZone)
				implements HasLocalTimes {
		}

		@PostMapping("/test/basic")
		void basic(@Valid @RequestBody Basic body) {
		}

		@PostMapping("/test/password")
		void password(@Valid @RequestBody Password body) {
		}

		@PostMapping("/test/times")
		void times(@Valid @RequestBody Times body) {
		}

		@PostMapping("/test/timing")
		void timing(@Valid @RequestBody ScheduleNotificationRequest body) {
		}

	}

}
