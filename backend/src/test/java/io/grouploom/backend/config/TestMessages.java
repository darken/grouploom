package io.grouploom.backend.config;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/** The app's real message bundles, configured like {@code spring.messages.*}, for tests that build beans by hand. */
public final class TestMessages {

	private TestMessages() {
	}

	public static MessageSource source() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		return source;
	}

}
