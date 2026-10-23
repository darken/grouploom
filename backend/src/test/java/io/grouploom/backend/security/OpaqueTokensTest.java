package io.grouploom.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class OpaqueTokensTest {

	@Test
	void generate_returns43UrlSafeCharactersWithoutPadding() {
		String token = OpaqueTokens.generate();

		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
	}

	@Test
	void generate_calledRepeatedly_neverRepeats() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			seen.add(OpaqueTokens.generate());
		}

		assertThat(seen).hasSize(1000);
	}

	@Test
	void hash_ofKnownValue_isLowercaseSha256Hex() {
		assertThat(OpaqueTokens.hash("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void hash_isDeterministicAndDiffersPerInput() {
		assertThat(OpaqueTokens.hash("a")).isEqualTo(OpaqueTokens.hash("a")).hasSize(64);
		assertThat(OpaqueTokens.hash("a")).isNotEqualTo(OpaqueTokens.hash("b"));
	}

}
