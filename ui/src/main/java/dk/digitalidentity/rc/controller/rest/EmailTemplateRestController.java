package dk.digitalidentity.rc.controller.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.htmlcleaner.BrowserCompactXmlSerializer;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.viewmodel.EmailTemplateDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.EmailService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
@RequireAdministratorRole
@RestController
public class EmailTemplateRestController {

	@Autowired
	private  EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private UserService userService;

	@PostMapping(value = "/rest/mailtemplates")
	@ResponseBody
	public ResponseEntity<String> updateTemplate(@RequestBody EmailTemplateDTO emailTemplateDTO, @RequestParam("tryEmail") boolean tryEmail) {
		toXHTML(emailTemplateDTO);
		
		if (tryEmail) {
			User user = userService.getByUserId(SecurityUtil.getUserId());

			if (user != null) {
				String email = user.getEmail();
				if (email != null) {
					List<InlineImageDTO> inlineImages = transformImages(emailTemplateDTO);
					emailService.sendMessage(email, emailTemplateDTO.getTitle(), emailTemplateDTO.getMessage(), inlineImages);
					
					return new ResponseEntity<>("Test email sendt til " + email, HttpStatus.OK);
				}
			}
			
			return new ResponseEntity<>("Du har ingen email adresse registreret!", HttpStatus.CONFLICT);
		}
		else {
			EmailTemplate template = emailTemplateService.findById(emailTemplateDTO.getId());
			if (template == null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}

			template.setMessage(emailTemplateDTO.getMessage());
			template.setTitle(emailTemplateDTO.getTitle());
			template.setEnabled(emailTemplateDTO.isEnabled());
			emailTemplateService.save(template);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private List<InlineImageDTO> transformImages(EmailTemplateDTO emailTemplateDTO) {
		List<InlineImageDTO> inlineImages = new ArrayList<>();
		String message = emailTemplateDTO.getMessage();
		Document doc = Jsoup.parse(message);
		int counter = 1;

		for (Element img : doc.select("img")) {
			String src = img.attr("src");
			if (src == null || src == "") {
				continue;
			}

			String filename = img.attr("data-filename");
			if (filename == null || filename == "") {
				filename = "file" + counter;
				counter ++;
			}

			InlineImageDTO inlineImageDto = new InlineImageDTO();
			inlineImageDto.setBase64(src.contains("base64"));
			
			if (inlineImageDto.isBase64()) {
				inlineImageDto.setUrl(false);
			}
			else {
				inlineImageDto.setUrl(true);
			}
			
			inlineImageDto.setCid(filename);
			inlineImageDto.setSrc(src);
			inlineImages.add(inlineImageDto);
			
			img.attr("src", "cid:" + filename);
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
}
