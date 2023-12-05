package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.dao.model.FrontPageLink;
import dk.digitalidentity.rc.service.FrontPageLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FrontPageConfigurationRestController {

	@Autowired
	private FrontPageLinkService frontPageLinkService;

	record SaveDTO(long id, String title, String icon, String link, String description) {}
	@PostMapping(value = { "/rest/frontpage/links/save" })
	@ResponseBody
	public HttpEntity<String> saveLink(@RequestBody SaveDTO saveDTO) {
		FrontPageLink frontPageLink = frontPageLinkService.getById(saveDTO.id());
		if (frontPageLink == null) {
			frontPageLink = new FrontPageLink();
			frontPageLink.setActive(true);
			frontPageLink.setEditable(true);
		}

		if (frontPageLink.isEditable()) {
			frontPageLink.setLink(saveDTO.link());
			frontPageLink.setIcon(saveDTO.icon());
			frontPageLink.setTitle(saveDTO.title());
			frontPageLink.setDescription(saveDTO.description());
		} else {
			return new ResponseEntity<>("Du kan ikke redigere dette link.", HttpStatus.BAD_REQUEST);
		}

		frontPageLinkService.save(frontPageLink);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = { "/rest/frontpage/links/{id}/delete" })
	@ResponseBody
	public HttpEntity<String> deleteLink(@PathVariable long id) {
		FrontPageLink frontPageLink = frontPageLinkService.getById(id);
		if (frontPageLink == null) {
			return new ResponseEntity<>("Kunne ikke finde link med id " + id, HttpStatus.NOT_FOUND);
		}

		frontPageLinkService.delete(frontPageLink);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = { "/rest/frontpage/links/{id}" })
	@ResponseBody
	public HttpEntity<String> setActive(@PathVariable long id, @RequestParam boolean active) {
		FrontPageLink frontPageLink = frontPageLinkService.getById(id);
		if (frontPageLink == null) {
			return new ResponseEntity<>("Kunne ikke finde link med id " + id, HttpStatus.NOT_FOUND);
		}

		frontPageLink.setActive(active);
		frontPageLinkService.save(frontPageLink);

		return new ResponseEntity<>(HttpStatus.OK);
	}

}
