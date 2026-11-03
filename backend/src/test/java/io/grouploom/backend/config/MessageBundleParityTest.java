package io.grouploom.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Like the frontend's catalog test: every message exists in both languages, so none can fall back silently. */
public class MessageBundleParityTest {

	public static Properties load(String name) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(new ClassPathResource(name).getInputStream(),
				StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private static Set<String> keys(Properties properties) {
		return new TreeSet<>(properties.stringPropertyNames());
	}

	@Test
	void bundles_haveExactlyTheSameKeys() throws IOException {
		Set<String> english = keys(load("messages.properties"));
		Set<String> japanese = keys(load("messages_ja.properties"));

		assertThat(english).isNotEmpty();
		assertThat(japanese).containsExactlyElementsOf(english);
	}

	@Test
	void bundles_haveNoBlankValues() throws IOException {
		for (String name : new String[] { "messages.properties", "messages_ja.properties" }) {
			Properties properties = load(name);
			assertThat(properties.stringPropertyNames()).allSatisfy(key -> assertThat(properties.getProperty(key))
				.as(name + ": " + key)
				.isNotBlank());
		}
	}

}
