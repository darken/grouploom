package io.grouploom.backend.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LanguageTest {

	@Test
	void fromTag_withSupportedTag_returnsTheLanguage() {
		assertThat(Language.fromTag("ja")).isEqualTo(Language.JA);
		assertThat(Language.fromTag("en")).isEqualTo(Language.EN);
	}

	@Test
	void fromTag_withUnsupportedOrUppercaseTag_isRejected() {
		assertThatThrownBy(() -> Language.fromTag("fr")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Language.fromTag("EN")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void toLocale_returnsTheMatchingLocale() {
		assertThat(Language.JA.toLocale().getLanguage()).isEqualTo("ja");
		assertThat(Language.EN.toLocale().getLanguage()).isEqualTo("en");
	}

}
