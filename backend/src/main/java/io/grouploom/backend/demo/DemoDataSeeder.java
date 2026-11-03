package io.grouploom.backend.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Seeds the demo group on startup when {@code grouploom.demo.enabled} is true. */
@Component
@ConditionalOnProperty(name = "grouploom.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final DemoDataService demoData;

	public DemoDataSeeder(DemoDataService demoData) {
		this.demoData = demoData;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (demoData.seedIfMissing()) {
			log.info("Seeded the demo group");
		}
		else {
			log.info("Demo group already exists; not seeding");
		}
	}

}
