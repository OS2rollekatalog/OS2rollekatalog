package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.ManualWelcomeEmailTemplateDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualWelcomeEmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualWelcomeEmailTemplateService")
class ManualWelcomeEmailTemplateServiceTest {

	@Mock
	private ManualWelcomeEmailTemplateDao manualWelcomeEmailTemplateDao;

	@InjectMocks
	private ManualWelcomeEmailTemplateService service;

	@Test
	@DisplayName("returns the existing template when one is already stored for the it-system")
	void returnsExistingTemplate() {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(1L);

		ManualWelcomeEmailTemplate existing = new ManualWelcomeEmailTemplate();
		existing.setItSystem(itSystem);
		existing.setOperation(ManualAssignmentEffectuationOperation.ASSIGN);
		existing.setTitle("Existing title");
		when(manualWelcomeEmailTemplateDao.findByItSystem_IdAndOperation(1L, ManualAssignmentEffectuationOperation.ASSIGN)).thenReturn(existing);

		ManualWelcomeEmailTemplate result = service.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.ASSIGN);

		assertThat(result).isSameAs(existing);
		verify(manualWelcomeEmailTemplateDao, never()).save(any());
	}

	@Test
	@DisplayName("lazily seeds and persists a default, disabled template per operation when none exists yet")
	void seedsDefaultTemplateWhenMissing() {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(1L);

		when(manualWelcomeEmailTemplateDao.findByItSystem_IdAndOperation(1L, ManualAssignmentEffectuationOperation.ASSIGN)).thenReturn(null);
		when(manualWelcomeEmailTemplateDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ManualWelcomeEmailTemplate result = service.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.ASSIGN);

		ArgumentCaptor<ManualWelcomeEmailTemplate> captor = ArgumentCaptor.forClass(ManualWelcomeEmailTemplate.class);
		verify(manualWelcomeEmailTemplateDao).save(captor.capture());

		ManualWelcomeEmailTemplate saved = captor.getValue();
		assertThat(saved.getItSystem()).isEqualTo(itSystem);
		assertThat(saved.getOperation()).isEqualTo(ManualAssignmentEffectuationOperation.ASSIGN);
		assertThat(saved.isEnabled()).isFalse();
		assertThat(saved.getTitle()).isNotBlank();
		assertThat(saved.getMessage()).isNotBlank();
		assertThat(result).isEqualTo(saved);
	}

	@Test
	@DisplayName("seeds a distinct default template for REMOVE, independent of ASSIGN")
	void seedsDefaultTemplateForRemovalOperation() {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(1L);

		when(manualWelcomeEmailTemplateDao.findByItSystem_IdAndOperation(1L, ManualAssignmentEffectuationOperation.REMOVE)).thenReturn(null);
		when(manualWelcomeEmailTemplateDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ManualWelcomeEmailTemplate result = service.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.REMOVE);

		assertThat(result.getOperation()).isEqualTo(ManualAssignmentEffectuationOperation.REMOVE);
		assertThat(result.isEnabled()).isFalse();
		assertThat(result.getTitle()).isNotBlank();
		assertThat(result.getMessage()).isNotBlank();
	}

	@Test
	@DisplayName("save delegates to the dao")
	void saveDelegatesToDao() {
		ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
		when(manualWelcomeEmailTemplateDao.save(template)).thenReturn(template);

		ManualWelcomeEmailTemplate result = service.save(template);

		assertThat(result).isSameAs(template);
		verify(manualWelcomeEmailTemplateDao).save(template);
	}

	@Test
	@DisplayName("findById returns null when no template exists with that id")
	void findByIdReturnsNullWhenMissing() {
		when(manualWelcomeEmailTemplateDao.findById(42L)).thenReturn(Optional.empty());

		assertThat(service.findById(42L)).isNull();
	}

	@Test
	@DisplayName("findById returns the template when found")
	void findByIdReturnsTemplate() {
		ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
		when(manualWelcomeEmailTemplateDao.findById(42L)).thenReturn(Optional.of(template));

		assertThat(service.findById(42L)).isSameAs(template);
	}
}
