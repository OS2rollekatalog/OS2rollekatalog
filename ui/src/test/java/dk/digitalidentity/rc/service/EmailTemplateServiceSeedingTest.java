package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.EmailTemplateDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailTemplateServiceSeedingTest {

	@Mock private EmailTemplateDao emailTemplateDao;

	@InjectMocks private EmailTemplateService emailTemplateService;

	@Test
	@DisplayName("Kontakt-skabeloner seedes med gentagne dele - udfører aktiv, advis inaktiv")
	void contactTemplatesSeededWithRepeatingParts() {
		given(emailTemplateDao.findByTemplateType(any())).willReturn(null);
		given(emailTemplateDao.save(any())).willAnswer(inv -> inv.getArgument(0));

		EmailTemplate systemPerformer = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_SYSTEM_CONTACT_PERFORMER);
		assertTrue(systemPerformer.isEnabled());
		assertNotNull(systemPerformer.getRepeatingPart());
		assertNotNull(systemPerformer.getNestedRepeatingPart());
		assertEquals("Rettighedsændringer i {itsystem}", systemPerformer.getTitle());

		EmailTemplate systemAdvis = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_SYSTEM_CONTACT_ADVIS);
		assertFalse(systemAdvis.isEnabled());
		assertEquals(systemPerformer.getMessage(), systemAdvis.getMessage());
		assertEquals(systemPerformer.getRepeatingPart(), systemAdvis.getRepeatingPart());
		assertEquals(systemPerformer.getNestedRepeatingPart(), systemAdvis.getNestedRepeatingPart());

		EmailTemplate rolePerformer = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ROLE_CONTACT_PERFORMER);
		assertTrue(rolePerformer.isEnabled());
		assertNotNull(rolePerformer.getRepeatingPart());
		assertNull(rolePerformer.getNestedRepeatingPart());

		EmailTemplate roleAdvis = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ROLE_CONTACT_ADVIS);
		assertFalse(roleAdvis.isEnabled());
		assertEquals(rolePerformer.getMessage(), roleAdvis.getMessage());
	}

	@Test
	@DisplayName("Eksisterende skabeloner uden gentagne dele seedes fortsat med null-felter")
	void legacyTemplatesUnaffected() {
		given(emailTemplateDao.findByTemplateType(any())).willReturn(null);
		given(emailTemplateDao.save(any())).willAnswer(inv -> inv.getArgument(0));

		EmailTemplate substitute = emailTemplateService.findByTemplateType(EmailTemplateType.SUBSTITUTE);
		assertNull(substitute.getRepeatingPart());
		assertNull(substitute.getNestedRepeatingPart());
	}
}
