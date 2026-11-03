package io.grouploom.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

class LocaleConfigTest {

	private final LocaleResolver resolver = new LocaleConfig().localeResolver();

	private Locale resolve(String acceptLanguage) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (acceptLanguage != null) {
			request.addHeader("Accept-Language", acceptLanguage);
		}
		return resolver.resolveLocale(request);
	}

	@Test
	void resolveLocale_withJapanese_returnsJapanese() {
		assertThat(resolve("ja")).isEqualTo(Locale.JAPANESE);
	}

	@Test
	void resolveLocale_withRegionalJapanese_returnsJapanese() {
		assertThat(resolve("ja-JP,ja;q=0.9,en;q=0.8").getLanguage()).isEqualTo("ja");
	}

	@Test
	void resolveLocale_withEnglish_returnsEnglish() {
		assertThat(resolve("en-GB").getLanguage()).isEqualTo("en");
	}

	@Test
	void resolveLocale_withUnsupportedLanguage_returnsEnglish() {
		assertThat(resolve("fr")).isEqualTo(Locale.ENGLISH);
		assertThat(resolve("fr-FR,es;q=0.8")).isEqualTo(Locale.ENGLISH);
	}

	@Test
	void resolveLocale_withUnsupportedThenJapanese_picksJapanese() {
		assertThat(resolve("fr, ja").getLanguage()).isEqualTo("ja");
	}

	@Test
	void resolveLocale_withoutHeader_returnsEnglish() {
		assertThat(resolve(null)).isEqualTo(Locale.ENGLISH);
	}

}
