package dk.digitalidentity.rc.controller.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dk.digitalidentity.rc.controller.mvc.viewmodel.EmailTemplateDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import lombok.RequiredArgsConstructor;
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

import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.service.AttestationRunService;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.RepeatingPartDescriptor;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.EmailService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@RestController
public class EmailTemplateRestController {
	private final EmailTemplateService emailTemplateService;
	private final EmailService emailService;
	private final UserService userService;
	private final AttestationRunService attestationRunService;

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@GetMapping(value = "/rest/mailtemplates/{id}")
	public ResponseEntity<EmailTemplateDTO> getTemplate(@PathVariable long id) {
		EmailTemplate template = emailTemplateService.findById(id);
		if (template == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		EmailTemplateDTO dto = EmailTemplateDTO.builder()
			.id(template.getId())
			.title(template.getTitle())
			.message(template.getMessage())
			.enabled(template.isEnabled())
			.notes(template.getNotes())
			.templateTypeName(emailTemplateService.getTemplateName(template.getId()))
			.repeatingPart(template.getRepeatingPart())
			.nestedRepeatingPart(template.getNestedRepeatingPart())
			.build();

		return ResponseEntity.ok(dto);
	}

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@PostMapping(value = "/rest/mailtemplates/{id}/toggle")
	public ResponseEntity<String> toggleTemplate(@PathVariable long id) {
		EmailTemplate template = emailTemplateService.findById(id);
		if (template == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		template.setEnabled(!template.isEnabled());
		emailTemplateService.save(template);

		return ResponseEntity.ok(template.isEnabled() ? "true" : "false");
	}

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@PostMapping(value = "/rest/mailtemplates/{id}/test")
	public ResponseEntity<String> testTemplate(@PathVariable long id) {
		EmailTemplate template = emailTemplateService.findById(id);
		if (template == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null || user.getEmail() == null) {
			return new ResponseEntity<>("Du har ingen email adresse registreret!", HttpStatus.CONFLICT);
		}

		EmailTemplateDTO dto = EmailTemplateDTO.builder()
			.message(flattenRepeatingParts(template.getTemplateType(), template.getMessage(), template.getRepeatingPart(), template.getNestedRepeatingPart()))
			.title(template.getTitle())
			.build();

		List<InlineImageDTO> inlineImages = transformImages(dto);
		emailService.sendMessage(user.getEmail(), dto.getTitle(), dto.getMessage(), inlineImages, null);

		return new ResponseEntity<>("Test email sendt til " + user.getEmail(), HttpStatus.OK);
	}

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@PostMapping(value = "/rest/mailtemplates")
	@ResponseBody
	public ResponseEntity<String> updateTemplate(@RequestBody EmailTemplateDTO emailTemplateDTO, @RequestParam("tryEmail") boolean tryEmail) {
		EmailTemplate template = emailTemplateService.findById(emailTemplateDTO.getId());
		if (template == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		// the message is a full HTML document for every template type, so it always gets the XHTML cleanup
		toXHTML(emailTemplateDTO);
		toValid3ByteUTF8String(emailTemplateDTO);

		boolean noEmailSentWarning = false;
		if (tryEmail) {
			User user = userService.getByUserId(SecurityUtil.getUserId());

			if (user != null) {
				String email = user.getEmail();
				if (email != null) {
					// expand repeating parts into the message so the test mail shows the full structure
					emailTemplateDTO.setMessage(flattenRepeatingParts(template.getTemplateType(), emailTemplateDTO.getMessage(), emailTemplateDTO.getRepeatingPart(), emailTemplateDTO.getNestedRepeatingPart()));

					List<InlineImageDTO> inlineImages = transformImages(emailTemplateDTO);
					emailService.sendMessage(email, emailTemplateDTO.getTitle(), emailTemplateDTO.getMessage(), inlineImages, null);

					return new ResponseEntity<>("Test email sendt til " + email, HttpStatus.OK);
				}
			}

			return new ResponseEntity<>("Du har ingen email adresse registreret!", HttpStatus.CONFLICT);
		}
		else {
			template.setMessage(emailTemplateDTO.getMessage());
			template.setTitle(emailTemplateDTO.getTitle());
			template.setEnabled(emailTemplateDTO.isEnabled());
			template.setNotes(emailTemplateDTO.getNotes());

			// repeating parts are summernote-edited HTML fragments; they get a fragment-aware cleanup
			// (no <html><body> wrapper, unlike the full-document toXHTML for the message)
			if (template.getTemplateType().hasRepeatingPart()) {
				template.setRepeatingPart(toValid3ByteUTF8String(toXHTMLFragment(emailTemplateDTO.getRepeatingPart())));
			}
			if (template.getTemplateType().hasNestedRepeatingPart()) {
				template.setNestedRepeatingPart(toValid3ByteUTF8String(toXHTMLFragment(emailTemplateDTO.getNestedRepeatingPart())));
			}

			if (template.getTemplateType().isAllowDaysBeforeDeadline()) {
				int validDays = Math.clamp(emailTemplateDTO.getDaysBeforeEvent(), -30, 30);
				if (findAffectedAttestations(template, validDays)) {
					noEmailSentWarning = true;
				}
				template.setDaysBeforeEvent(validDays);
			}
			emailTemplateService.save(template);
		}

		return new ResponseEntity<>(noEmailSentWarning ? "Bemærk mailen kan ikke nå at blive sendt i indeværende rul." : null, HttpStatus.OK);
	}


	/**
	 * Inlines the repeating parts at their trigger placeholders so previews/test mails show the full
	 * mail structure (placeholders themselves stay unexpanded, as for all other templates). The list
	 * wrapping is applied from the descriptor, mirroring how {@link dk.digitalidentity.rc.service.EmailTemplateRenderer}
	 * renders one row.
	 */
	private static String flattenRepeatingParts(EmailTemplateType templateType, String message, String repeatingPart, String nestedRepeatingPart) {
		RepeatingPartDescriptor descriptor = templateType.getRepeatingPart();
		if (descriptor == null || message == null) {
			return message;
		}

		String part = repeatingPart != null ? repeatingPart : "";
		if (descriptor.nested() != null) {
			RepeatingPartDescriptor nested = descriptor.nested();
			String nestedPart = nested.wrapGroup(nested.wrapItem(nestedRepeatingPart != null ? nestedRepeatingPart : ""));
			part = part.replace(nested.trigger().getPlaceholder(), nestedPart);
		}

		return message.replace(descriptor.trigger().getPlaceholder(), descriptor.wrapGroup(descriptor.wrapItem(part)));
	}

	private boolean findAffectedAttestations(EmailTemplate template, int validDays) {
		final Optional<AttestationRun> currentRun = attestationRunService.getCurrentRun();

		if (currentRun.isEmpty()) {
			return false;
		}

		return currentRun.get().getDeadline().minusDays(validDays).isBefore(LocalDate.now());
	}


	private List<InlineImageDTO> transformImages(EmailTemplateDTO emailTemplateDTO) {
		final String message = emailTemplateDTO.getMessage();
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

		emailTemplateDTO.setMessage(doc.html());

		return inlineImages;
	}

	/**
	 * summernote does not generate valid XHTML. At least the <br/> and <img/> tags are not closed,
	 * so we need to close them, otherwise our PDF processing will fail.
	 */
	private void toXHTML(EmailTemplateDTO emailTemplateDTO) {
		String message = emailTemplateDTO.getMessage();
		if (message != null) {
			try {
				CleanerProperties properties = new CleanerProperties();
				properties.setOmitXmlDeclaration(true);
				TagNode tagNode = new HtmlCleaner(properties).clean(message);

				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				new BrowserCompactXmlSerializer(properties).writeToStream(tagNode, bos);

				emailTemplateDTO.setMessage(new String(bos.toByteArray(), Charset.forName("UTF-8")));
			}
			catch (IOException ex) {
				log.error("could not parse: " + emailTemplateDTO.getMessage());
			}
		}
	}

	/**
	 * Cleans a summernote-edited HTML fragment (a repeating part). Unlike {@link #toXHTML}, which treats
	 * its input as a full document, this keeps the result as a bare fragment - the full-document cleaner
	 * would wrap it in {@code <html><body>}, which would then be inlined into the mail body. Void tags
	 * are self-closed (XHTML) to match the message handling.
	 */
	private static String toXHTMLFragment(String fragment) {
		if (fragment == null) {
			return null;
		}
		Document doc = Jsoup.parseBodyFragment(fragment);
		doc.outputSettings()
			.syntax(Document.OutputSettings.Syntax.xml)
			.prettyPrint(false);
		return doc.body().html();
	}

	/*
	 * we can store anything above 3 bytes - so replace it with the ?-icon
	 * found here: https://stackoverflow.com/questions/9260836/how-to-replace-remove-4-byte-characters-from-a-utf-8-string-in-java
	 */
	public void toValid3ByteUTF8String(EmailTemplateDTO emailTemplateDTO)  {
		emailTemplateDTO.setMessage(toValid3ByteUTF8String(emailTemplateDTO.getMessage()));
	}

	private static String toValid3ByteUTF8String(String message) {
		String LAST_3_BYTE_UTF_CHAR = "\uFFFF";
		String REPLACEMENT_CHAR = "\uFFFD";
		if (message == null) {
			return null;
		}
		final int length = message.length();
		StringBuilder builder = new StringBuilder(length);
		for (int offset = 0; offset < length; ) {
			final int codepoint = message.codePointAt(offset);

			// do something with the codepoint
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
