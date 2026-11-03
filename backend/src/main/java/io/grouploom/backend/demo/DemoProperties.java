package io.grouploom.backend.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Demo mode seeds a sample group at startup (portfolio deploys only). The login is public, so it is not a secret. */
@ConfigurationProperties("grouploom.demo")
public record DemoProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("demo@example.com") String email,
		@DefaultValue("grouploom-demo") String password) {
}
