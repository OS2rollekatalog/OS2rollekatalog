package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailTemplateRendererTest {

	private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

	@Test
	@DisplayName("Simple pladsholdere erstattes, null bliver til tom streng, ukendte står urørt")
	void simplePlaceholders() {
		String result = renderer.render("Hej {bruger} fra {enhed}", Map.of(
				EmailTemplatePlaceholder.USER_PLACEHOLDER, "Hans"));
		assertEquals("Hej Hans fra {enhed}", result);

		String nullResult = renderer.render("Beskrivelse: {rollebeskrivelse}", java.util.Collections.singletonMap(
				EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, null));
		assertEquals("Beskrivelse: ", nullResult);
	}

	@Test
	@DisplayName("Indsatte værdier re-scannes ikke - pladsholder-tokens i data forbliver litterale")
	void substitutedValuesAreNotRescanned() {
		String result = renderer.render("{rolle} / {tildeler}", Map.of(
				EmailTemplatePlaceholder.ROLE_NAME, "Rolle med {tildeler} i navnet",
				EmailTemplatePlaceholder.ASSIGNED_BY_PLACEHOLDER, "Bob"));

		assertEquals("Rolle med {tildeler} i navnet / Bob", result);
	}

	@Test
	@DisplayName("{rollebeskrivelse:N} afkorter til første linje og højst N tegn")
	void parameterizedTruncation() {
		Map<EmailTemplatePlaceholder, String> values = Map.of(
				EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, "0123456789\nanden linje");

		// uden parameter: uændret
		assertEquals("0123456789\nanden linje", renderer.render("{rollebeskrivelse}", values));
		// første linje, hård cut ved N
		assertEquals("01234", renderer.render("{rollebeskrivelse:5}", values));
		// N større end første linje: hele første linje
		assertEquals("0123456789", renderer.render("{rollebeskrivelse:40}", values));
		// grænse: N == længden af første linje
		assertEquals("0123456789", renderer.render("{rollebeskrivelse:10}", values));
		// begge varianter i samme tekst
		assertEquals("01234 / 0123456789\nanden linje", renderer.render("{rollebeskrivelse:5} / {rollebeskrivelse}", values));
	}

	@Test
	@DisplayName("{rollebeskrivelse:N} afkorter også ved <br/> som linjeskift")
	void parameterizedTruncationHtmlLineBreak() {
		Map<EmailTemplatePlaceholder, String> values = Map.of(
				EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, "første<br/>anden");

		assertEquals("første", renderer.render("{rollebeskrivelse:40}", values));
	}

	@Test
	@DisplayName("Ukendte pladsholdere og forkerte parametre røres ikke")
	void unknownPlaceholdersUntouched() {
		Map<EmailTemplatePlaceholder, String> values = Map.of(
				EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, "desc",
				EmailTemplatePlaceholder.ROLE_NAME, "X");

		assertEquals("{ukendt} {rollebeskrivelse:x}", renderer.render("{ukendt} {rollebeskrivelse:x}", values));
		// :N on a non-parameterized placeholder stays literal
		assertEquals("{rolle:5}", renderer.render("{rolle:5}", values));
		// N beyond 9 digits does not match and cannot overflow Integer.parseInt
		assertEquals("{rollebeskrivelse:9999999999}", renderer.render("{rollebeskrivelse:9999999999}", values));
	}

	@Test
	@DisplayName("Gentaget del med nestet gentaget del ekspanderes rekursivt, og rendereren sætter <ul>/<li> om de nestede rækker")
	void nestedRepeatingParts() {
		EmailTemplate template = new EmailTemplate();
		template.setTemplateType(EmailTemplateType.MANUAL_SYSTEM_CONTACT_PERFORMER);
		template.setTitle("Mail om {itsystem}");
		template.setMessage("System: {itsystem}|{brugere}");
		template.setRepeatingPart("[{bruger}:{ændringer}]");
		template.setNestedRepeatingPart("({handling} {rolle})");

		EmailTemplateRenderer.Row user1 = new EmailTemplateRenderer.Row(
				Map.of(EmailTemplatePlaceholder.USER_PLACEHOLDER, "Hans"),
				List.of(
						new EmailTemplateRenderer.Row(Map.of(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, "Tilføj", EmailTemplatePlaceholder.ROLE_NAME, "A")),
						new EmailTemplateRenderer.Row(Map.of(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, "Fjern", EmailTemplatePlaceholder.ROLE_NAME, "B"))));
		EmailTemplateRenderer.Row user2 = new EmailTemplateRenderer.Row(
				Map.of(EmailTemplatePlaceholder.USER_PLACEHOLDER, "Grete"),
				List.of(new EmailTemplateRenderer.Row(Map.of(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, "Tilføj", EmailTemplatePlaceholder.ROLE_NAME, "C"))));

		Map<EmailTemplatePlaceholder, String> values = Map.of(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, "AD");

		assertEquals("Mail om AD", renderer.renderTitle(template, values));
		// the renderer wraps the nested change rows in <ul>/<li> (from the descriptor); the per-user
		// level is a free block (no wrapper)
		assertEquals("System: AD|[Hans:<ul><li>(Tilføj A)</li><li>(Fjern B)</li></ul>][Grete:<ul><li>(Tilføj C)</li></ul>]",
				renderer.renderMessage(template, values, List.of(user1, user2)));
	}

	@Test
	@DisplayName("Skabelontype med ét gentagelsesniveau ignorerer nestet del")
	void singleLevelRepeatingPart() {
		EmailTemplate template = new EmailTemplate();
		template.setTemplateType(EmailTemplateType.MANUAL_ROLE_CONTACT_PERFORMER);
		template.setTitle("t");
		template.setMessage("Rolle {rolle}: {brugere}");
		template.setRepeatingPart("<{handling} {bruger}>");

		List<EmailTemplateRenderer.Row> rows = List.of(
				new EmailTemplateRenderer.Row(Map.of(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, "Tilføj", EmailTemplatePlaceholder.USER_PLACEHOLDER, "Hans")));

		// the role descriptor wraps each user row in <li> inside a <ul>
		assertEquals("Rolle X: <ul><li><Tilføj Hans></li></ul>",
				renderer.renderMessage(template, Map.of(EmailTemplatePlaceholder.ROLE_NAME, "X"), rows));
	}
}
