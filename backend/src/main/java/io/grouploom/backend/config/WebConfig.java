package io.grouploom.backend.config;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {

	private static final int MAX_PAGE_SIZE = 100;

	// Configured here rather than via spring.data.web.pageable.*: Boot's auto-configuration backs off
	// once @EnableSpringDataWebSupport is present, so those properties would be silently ignored.
	// The default page size (20) is set per endpoint with @PageableDefault.
	@Bean
	PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
		return resolver -> {
			resolver.setMaxPageSize(MAX_PAGE_SIZE);
		};
	}

}
