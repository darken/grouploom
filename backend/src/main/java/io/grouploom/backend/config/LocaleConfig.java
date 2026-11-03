package io.grouploom.backend.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfig {

	/**
	 * The request language is the first {@code Accept-Language} entry we support, otherwise English. Restricting
	 * the list matters: Hibernate Validator ships French, Spanish and other translations, so without it a French
	 * browser would get French validation messages next to an English UI.
	 */
	@Bean
	LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
		resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.JAPANESE));
		resolver.setDefaultLocale(Locale.ENGLISH);
		return resolver;
	}

}
