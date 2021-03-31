package dk.digitalidentity.rc.controller.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.dto.TitleDTO;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.service.TitleService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class TitleApi {

	@Autowired
	private TitleService titleService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@RequestMapping(value = "/api/title", method = RequestMethod.GET)
	public ResponseEntity<List<TitleDTO>> read() throws Exception {
		if (!configuration.getTitles().isEnabled()) {
			log.error("Attempting to call api when titles are disabled!");
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		return new ResponseEntity<>(titleService.getAll().stream().map(t -> new TitleDTO(t)).collect(Collectors.toList()), HttpStatus.OK);
	}

	@PostMapping(value = "/api/title")
	public ResponseEntity<?> save(@RequestBody @Valid List<TitleDTO> body) {
		if (!configuration.getTitles().isEnabled()) {
			log.error("Attempting to call api when titles are disabled!");
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		List<Title> existingTitles = titleService.getAllIncludingInactive();

		for (TitleDTO titleDTO : body) {
			Title title = null;
			Optional<Title> existingTitle = existingTitles.stream().filter(t -> Objects.equals(t.getUuid(), titleDTO.getUuid())).findAny();

			if (existingTitle.isPresent()) {
				title = existingTitle.get();
				boolean changes = false;
				
				if (!Objects.equals(title.getName(), titleDTO.getName())) {
					title.setName(titleDTO.getName());
					changes = true;
				}
				
				if (changes && title.isActive() == false) {
					title.setActive(true);
					titleService.save(title);
				}
			}
			else {
				title = new Title();
				title.setUuid(titleDTO.getUuid());
				title.setName(titleDTO.getName());
				title.setActive(true);

				titleService.save(title);
			}
		}

		// remove/deactivate
		for (Title title : existingTitles) {
			if (body.stream().noneMatch(t -> Objects.equals(t.getUuid(), title.getUuid()))) {
				title.setActive(false);
				titleService.save(title);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
}