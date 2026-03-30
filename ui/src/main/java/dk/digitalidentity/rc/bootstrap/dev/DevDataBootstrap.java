package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(200)
@Component
@Profile("dev & !test")
@RequiredArgsConstructor
public class DevDataBootstrap implements ApplicationListener<ApplicationReadyEvent> {

	private final SettingsService settingsService;
	private final DevDataSeeder devDataSeeder;

	@Override
	public void onApplicationEvent(final @NotNull ApplicationReadyEvent event) {
		if (settingsService.isDevDataSeeded()) {
			log.info("Dev bootstrap skipped — already seeded");
			return;
		}

		devDataSeeder.seedAll();
	}

}
