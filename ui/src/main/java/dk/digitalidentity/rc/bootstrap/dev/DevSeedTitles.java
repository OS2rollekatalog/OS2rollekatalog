package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.Title;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedTitles {

	public record SeedResult(List<Title> titles) {}

	private final TitleDao titleDao;

	public SeedResult seed() {
		log.info("Seeding titles...");
		List<Title> titles = DevDataDefinitions.TITLE_NAMES.stream().map(name -> {
			Title title = new Title();
			title.setName(name);
			title.setUuid(UUID.randomUUID().toString());
			title.setActive(true);
			return title;
		}).toList();
		List<Title> saved = new ArrayList<>();
		titleDao.saveAll(titles).forEach(saved::add);
		return new SeedResult(saved);
	}

}
