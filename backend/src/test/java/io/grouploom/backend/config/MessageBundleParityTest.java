package io.grouploom.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

	// The Japanese Size message only names the upper limit; the password, the one @Size with a minimum, has its own key.
	private static final Set<String> PLACEHOLDERS_MAY_DIFFER = Set.of("jakarta.validation.constraints.Size.message");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]+\\}");

	private static Set<String> placeholders(String message) {
		Set<String> found = new TreeSet<>();
		Matcher matcher = PLACEHOLDER.matcher(message);
		while (matcher.find()) {
			found.add(matcher.group());
		}
		return found;
	}

	@Test
	void japanese_usesTheSamePlaceholdersAsEnglish() throws IOException {
		Properties english = load("messages.properties");
		Properties japanese = load("messages_ja.properties");

		for (String key : english.stringPropertyNames()) {
			if (!PLACEHOLDERS_MAY_DIFFER.contains(key)) {
				assertThat(placeholders(japanese.getProperty(key))).as(key).isEqualTo(placeholders(english.getProperty(key)));
			}
		}
	}

	@Test
	void japanese_usesFullWidthPunctuation() throws IOException {
		Properties japanese = load("messages_ja.properties");

		for (String key : japanese.stringPropertyNames()) {
			String withoutPlaceholders = PLACEHOLDER.matcher(japanese.getProperty(key)).replaceAll("");
			assertThat(withoutPlaceholders).as(key).doesNotContain(":").doesNotContain("!").doesNotContain("?");
		}
	}

}
