package io.grouploom.backend.config;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** A template engine dedicated to email: HTML for {@code *.html}, plain text for {@code *.txt}. */
@Configuration
public class MailTemplateConfig {

	@Bean
	public TemplateEngine mailTemplateEngine() {
		TemplateEngine engine = new SpringTemplateEngine();
		engine.addTemplateResolver(resolver("*.html", TemplateMode.HTML, 1));
		engine.addTemplateResolver(resolver("*.txt", TemplateMode.TEXT, 2));
		return engine;
	}

	private static ClassLoaderTemplateResolver resolver(String pattern, TemplateMode mode, int order) {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/mail/");
		resolver.setResolvablePatterns(Set.of(pattern));
		resolver.setTemplateMode(mode);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setOrder(order);
		resolver.setCacheable(true);
		return resolver;
	}

}
