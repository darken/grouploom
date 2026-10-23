package io.grouploom.backend.preference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailMaskerTest {

	@Test
	void mask_withNormalAddress_keepsFirstCharacterAndDomain() {
		assertThat(EmailMasker.mask("ana@example.com")).isEqualTo("a••@example.com");
	}

	@Test
	void mask_withLongLocalPart_stillUsesAFixedMask() {
		assertThat(EmailMasker.mask("ana.perez+news@example.com")).isEqualTo("a••@example.com");
	}

	@Test
	void mask_withSingleCharacterLocalPart_masksItFully() {
		assertThat(EmailMasker.mask("a@example.com")).isEqualTo("•@example.com");
	}

	@Test
	void mask_withUppercaseAddress_preservesCase() {
		assertThat(EmailMasker.mask("Ana@Example.COM")).isEqualTo("A••@Example.COM");
	}

	@Test
	void mask_withSurrogatePairFirstCharacter_keepsTheWholeCodePoint() {
		assertThat(EmailMasker.mask("😀ab@example.com")).isEqualTo("😀••@example.com");
	}

}
