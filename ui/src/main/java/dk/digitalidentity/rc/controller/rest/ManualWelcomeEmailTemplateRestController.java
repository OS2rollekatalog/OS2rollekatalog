package dk.digitalidentity.rc.controller.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.htmlcleaner.BrowserCompactXmlSerializer;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ManualWelcomeEmailTemplateDTO;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualWelcomeEmailTemplate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.EmailService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManualWelcomeEmailTemplateService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@RequireControllerPermission(section = Section.IT_SYSTEM, permission = Permission.READ)
@RestController
public class ManualWelcomeEmailTemplateRestController {
	private final ManualWelcomeEmailTemplateService manualWelcomeEmailTemplateService;
	private final ItSystemService itSystemService;
	private final EmailService emailService;
	private final UserService userService;

	@RequirePermission(section = Section.IT_SYSTEM, permission = Permission.UPDATE)
	@GetMapping(value = "/rest/itsystem/{id}/manualwelcomeemail")
	public ResponseEntity<ManualWelcomeEmailTemplateDTO> getTemplate(@PathVariable long id, @RequestParam ManualAssignmentEffectuationOperation operation) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null || itSystem.getSystemType() != ItSystemType.MANUAL) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		ManualWelcomeEmailTemplate template = manualWelcomeEmailTemplateService.findByItSystem(itSystem, operation);

		ManualWelcomeEmailTemplateDTO dto = ManualWelcomeEmailTemplateDTO.builder()
			.id(template.getId())
			.itSystemId(itSystem.getId())
			.itSystemName(itSystem.getName())
			.operation(template.getOperation())
			.title(template.getTitle())
			.message(template.getMessage())
			.notes(template.getNotes())
			.enabled(template.isEnabled())
			.emailTemplatePlaceholders(ManualWelcomeEmailTemplateService.PLACEHOLDERS)
			.build();

		return ResponseEntity.ok(dto);
	}

	@RequirePermission(section = Section.IT_SYSTEM, permission = Permission.UPDATE)
	@PostMapping(value = "/rest/itsystem/manualwelcomeemail")
	@ResponseBody
	public ResponseEntity<String> updateTemplate(@RequestBody ManualWelcomeEmailTemplateDTO dto, @RequestParam("tryEmail") boolean tryEmail) {
		ManualWelcomeEmailTemplate template = manualWelcomeEmailTemplateService.findById(dto.getId());
		if (template == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		toXHTML(dto);
		dto.setMessage(toValid3ByteUTF8String(dto.getMessage()));

		if (tryEmail) {
			User user = userService.getByUserId(SecurityUtil.getUserId());
			if (user == null || user.getEmail() == null) {
				return new ResponseEntity<>("Du har ingen email adresse registreret!", HttpStatus.CONFLICT);
			}

			List<InlineImageDTO> inlineImages = transformImages(dto);
			emailService.sendMessage(user.getEmail(), dto.getTitle(), dto.getMessage(), inlineImages, null);

			return new ResponseEntity<>("Test email sendt til " + user.getEmail(), HttpStatus.OK);
		}

		template.setTitle(dto.getTitle());
		template.setMessage(dto.getMessage());
		template.setNotes(dto.getNotes());
		template.setEnabled(dto.isEnabled());
		manualWelcomeEmailTemplateService.save(template);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private List<InlineImageDTO> transformImages(ManualWelcomeEmailTemplateDTO dto) {
		String message = dto.getMessage();
		if (message == null) {
			return Collections.emptyList();
		}

		List<InlineImageDTO> inlineImages = new ArrayList<>();
		Document doc = Jsoup.parse(message);

		for (Element img : doc.select("img")) {
			String src = img.attr("src");
			if (src == null || src.isEmpty()) {
				continue;
			}

			InlineImageDTO inlineImageDto = new InlineImageDTO();
			inlineImageDto.setBase64(src.contains("base64"));

			if (!inlineImageDto.isBase64()) {
				continue;
			}

			String cID = UUID.randomUUID().toString();
			inlineImageDto.setCid(cID);
			inlineImageDto.setSrc(src);
			inlineImages.add(inlineImageDto);
			img.attr("src", "cid:" + cID);
		}

		dto.setMessage(doc.html());

		return inlineImages;
	}

	/**
	 * summernote does not generate valid XHTML. At least the <br/> and <img/> tags are not closed,
	 * so we need to close them, otherwise our PDF processing will fail.
	 */
	private void toXHTML(ManualWelcomeEmailTemplateDTO dto) {
		String message = dto.getMessage();
		if (message != null) {
			try {
				CleanerProperties properties = new CleanerProperties();
				properties.setOmitXmlDeclaration(true);
				TagNode tagNode = new HtmlCleaner(properties).clean(message);

				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				new BrowserCompactXmlSerializer(properties).writeToStream(tagNode, bos);

				dto.setMessage(new String(bos.toByteArray(), Charset.forName("UTF-8")));
			}
			catch (IOException ex) {
				log.error("could not parse: " + dto.getMessage());
			}
		}
	}

	/*
	 * we can store anything above 3 bytes - so replace it with the ?-icon
	 * found here: https://stackoverflow.com/questions/9260836/how-to-replace-remove-4-byte-characters-from-a-utf-8-string-in-java
	 */
	private static String toValid3ByteUTF8String(String message) {
		String LAST_3_BYTE_UTF_CHAR = "￿";
		String REPLACEMENT_CHAR = "�";
		if (message == null) {
			return null;
		}
		final int length = message.length();
		StringBuilder builder = new StringBuilder(length);
		for (int offset = 0; offset < length; ) {
			final int codepoint = message.codePointAt(offset);

			if (codepoint > LAST_3_BYTE_UTF_CHAR.codePointAt(0)) {
				builder.append(REPLACEMENT_CHAR);
			} else {
				if (Character.isValidCodePoint(codepoint)) {
					builder.appendCodePoint(codepoint);
				} else {
					builder.append(REPLACEMENT_CHAR);
				}
			}
			offset += Character.charCount(codepoint);
		}

		return builder.toString();
	}
}
