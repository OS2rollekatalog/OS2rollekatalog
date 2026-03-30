package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.model.dto.SystemRoleConstraintDTO;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Constraint Service Tests")
class AttestationConstraintServiceTest {

	@Mock
	private ItSystemDao itSystemDao;

	@Mock
	private OrgUnitDao orgUnitDao;

	@Mock
	private MessageSource messageSource;

	@InjectMocks
	private AttestationConstraintService attestationConstraintService;

	private Locale locale;

	@BeforeEach
	void setUp() {
		locale = Locale.forLanguageTag("da-DK");
		LocaleContextHolder.setLocale(locale);
	}

	@Nested
	@DisplayName("When valueType is POSTPONED")
	class PostponedTests {

		@Test
		@DisplayName("Should return name with 'udskudt' suffix")
		void caption_PostponedType_ShouldReturnNameWithUdskudt() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("TestConstraint", ConstraintValueType.POSTPONED, "someValue");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("TestConstraint udskudt", result);
		}
	}

	@Nested
	@DisplayName("When valueType is READ_AND_WRITE")
	class ReadAndWriteTests {

		@Test
		@DisplayName("Should return localized message for KLE constraint")
		void caption_ReadAndWriteKLE_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("KLE", ConstraintValueType.READ_AND_WRITE, "someValue");

			when(messageSource.getMessage("html.constraint.kle.read_and_write", null, locale))
				.thenReturn("KLE læse- og skriveadgang");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("KLE læse- og skriveadgang", result);
		}

		@Test
		@DisplayName("Should return default caption for non-KLE constraint")
		void caption_ReadAndWriteNonKLE_ShouldReturnDefaultCaption() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("OtherConstraint", ConstraintValueType.READ_AND_WRITE, "someValue");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("OtherConstraint someValue", result);
		}
	}

	@Nested
	@DisplayName("When valueType is INHERITED")
	class InheritedTests {

		@Test
		@DisplayName("Should return localized message for KLE inherited")
		void caption_InheritedKLE_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("KLE", ConstraintValueType.INHERITED, "someValue");

			when(messageSource.getMessage("html.constraint.kle.inherited", null, locale))
				.thenReturn("KLE arvet");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("KLE arvet", result);
		}

		@Test
		@DisplayName("Should return localized message for Organisation inherited")
		void caption_InheritedOrganisation_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.INHERITED, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.inherited", null, locale))
				.thenReturn("Organisation arvet");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Organisation arvet", result);
		}

		@Test
		@DisplayName("Should return default caption for other inherited types")
		void caption_InheritedOther_ShouldReturnDefaultCaption() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("OtherType", ConstraintValueType.INHERITED, "someValue");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("OtherType someValue", result);
		}
	}

	@Nested
	@DisplayName("When valueType is EXTENDED_INHERITED")
	class ExtendedInheritedTests {

		@Test
		@DisplayName("Should return localized message for KLE extended inherited")
		void caption_ExtendedInheritedKLE_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("KLE", ConstraintValueType.EXTENDED_INHERITED, "someValue");

			when(messageSource.getMessage("html.constraint.kle.extended", null, locale))
				.thenReturn("KLE udvidet arvet");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("KLE udvidet arvet", result);
		}

		@Test
		@DisplayName("Should return localized message for Organisation extended inherited")
		void caption_ExtendedInheritedOrganisation_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.EXTENDED_INHERITED, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.extended", null, locale))
				.thenReturn("Organisation udvidet arvet");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Organisation udvidet arvet", result);
		}

		@Test
		@DisplayName("Should return default caption for other extended inherited types")
		void caption_ExtendedInheritedOther_ShouldReturnDefaultCaption() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("OtherType", ConstraintValueType.EXTENDED_INHERITED, "someValue");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("OtherType someValue", result);
		}
	}

	@Nested
	@DisplayName("When valueType is SELECTED_INHERITED")
	class SelectedInheritedTests {

		@Test
		@DisplayName("Should return localized message for KLE selected inherited")
		void caption_SelectedInheritedKLE_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("KLE", ConstraintValueType.SELECTED_INHERITED, "someValue");

			when(messageSource.getMessage("html.constraint.kle.selected_inherited", null, locale))
				.thenReturn("KLE valgt arvet");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("KLE valgt arvet", result);
		}

		@Test
		@DisplayName("Should return localized message for Organisation selected inherited")
		void caption_SelectedInheritedOrganisation_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.SELECTED_INHERITED, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.selected.inherited", null, locale))
				.thenReturn("Organisation valgt arvet");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Organisation valgt arvet", result);
		}

		@Test
		@DisplayName("Should return default caption for other selected inherited types")
		void caption_SelectedInheritedOther_ShouldReturnDefaultCaption() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("OtherType", ConstraintValueType.SELECTED_INHERITED, "someValue");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("OtherType someValue", result);
		}
	}

	@Nested
	@DisplayName("When valueType is LEVEL")
	class LevelTests {

		@Test
		@DisplayName("Should return localized message for LEVEL_1")
		void caption_Level1_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.LEVEL_1, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.level.1", null, locale))
				.thenReturn("Niveau 1");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Niveau 1", result);
		}

		@Test
		@DisplayName("Should return localized message for LEVEL_2")
		void caption_Level2_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.LEVEL_2, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.level.2", null, locale))
				.thenReturn("Niveau 2");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Niveau 2", result);
		}

		@Test
		@DisplayName("Should return localized message for LEVEL_3")
		void caption_Level3_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.LEVEL_3, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.level.3", null, locale))
				.thenReturn("Niveau 3");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Niveau 3", result);
		}

		@Test
		@DisplayName("Should return localized message for LEVEL_4")
		void caption_Level4_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.LEVEL_4, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.level.4", null, locale))
				.thenReturn("Niveau 4");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Niveau 4", result);
		}

		@Test
		@DisplayName("Should return localized message for LEVEL_5")
		void caption_Level5_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.LEVEL_5, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.level.5", null, locale))
				.thenReturn("Niveau 5");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Niveau 5", result);
		}

		@Test
		@DisplayName("Should return localized message for LEVEL_6")
		void caption_Level6_ShouldReturnLocalizedMessage() {
			// Arrange
			SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.LEVEL_6, "someValue");

			when(messageSource.getMessage("html.constraint.organisation.level.6", null, locale))
				.thenReturn("Niveau 6");

			// Act
			String result = attestationConstraintService.caption(constraintDTO);

			// Assert
			assertEquals("Niveau 6", result);
		}
	}

	@Nested
	@DisplayName("When valueType is VALUE")
	class ValueTests {

		@Nested
		@DisplayName("For It-system constraints")
		class ItSystemValueTests {

			@Test
			@DisplayName("Should return IT system names when systems exist")
			void caption_ItSystemValue_WithExistingSystems_ShouldReturnSystemNames() {
				// Arrange
				SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("It-system", ConstraintValueType.VALUE, "1,2");

				ItSystem system1 = new ItSystem();
				system1.setName("System A");
				ItSystem system2 = new ItSystem();
				system2.setName("System B");

				lenient().when(itSystemDao.findById(1L)).thenReturn(Optional.of(system1));
				lenient().when(itSystemDao.findById(2L)).thenReturn(Optional.of(system2));

				when(messageSource.getMessage("html.entity.itsystem", null, locale))
					.thenReturn("IT-system");

				// Act
				String result = attestationConstraintService.caption(constraintDTO);

				// Assert
				assertEquals("IT-system: System A, System B", result);
				verify(itSystemDao, atLeastOnce()).findById(anyLong());
			}

			@Test
			@DisplayName("Should return default caption when no systems found")
			void caption_ItSystemValue_WithNoSystems_ShouldReturnDefaultCaption() {
				// Arrange
				SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("It-system", ConstraintValueType.VALUE, "999");

				lenient().when(itSystemDao.findById(999L)).thenAnswer(invocation -> Optional.empty());

				// Act
				String result = attestationConstraintService.caption(constraintDTO);

				// Assert
				assertEquals("It-system 999", result);
			}

			@Test
			@DisplayName("Should handle single IT system")
			void caption_ItSystemValue_WithSingleSystem_ShouldReturnSystemName() {
				// Arrange
				SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("It-system", ConstraintValueType.VALUE, "1");

				ItSystem system = new ItSystem();
				system.setName("Single System");

				lenient().when(itSystemDao.findById(1L)).thenAnswer(invocation -> Optional.of(system));
				when(messageSource.getMessage("html.entity.itsystem", null, locale))
					.thenReturn("IT-system");

				// Act
				String result = attestationConstraintService.caption(constraintDTO);

				// Assert
				assertEquals("IT-system: Single System", result);
			}
		}

		@Nested
		@DisplayName("For Organisation constraints")
		class OrganisationValueTests {

			@Test
			@DisplayName("Should return org unit names when units exist")
			void caption_OrganisationValue_WithExistingOrgUnits_ShouldReturnOrgUnitNames() {
				// Arrange
				SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.VALUE, "uuid1,uuid2");

				OrgUnit ou1 = new OrgUnit();
				ou1.setName("Org Unit A");
				OrgUnit ou2 = new OrgUnit();
				ou2.setName("Org Unit B");

				when(orgUnitDao.findById("uuid1")).thenReturn(Optional.of(ou1));
				when(orgUnitDao.findById("uuid2")).thenReturn(Optional.of(ou2));
				when(messageSource.getMessage("html.entity.ou.type", null, locale))
					.thenReturn("Organisationsenhed");

				// Act
				String result = attestationConstraintService.caption(constraintDTO);

				// Assert
				assertEquals("Organisationsenhed: Org Unit A, Org Unit B", result);
			}

			@Test
			@DisplayName("Should return default caption when no org units found")
			void caption_OrganisationValue_WithNoOrgUnits_ShouldReturnDefaultCaption() {
				// Arrange
				SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.VALUE, "nonexistent-uuid");

				when(orgUnitDao.findById("nonexistent-uuid")).thenReturn(Optional.empty());

				// Act
				String result = attestationConstraintService.caption(constraintDTO);

				// Assert
				assertEquals("Organisation nonexistent-uuid", result);
			}

			@Test
			@DisplayName("Should handle single org unit")
			void caption_OrganisationValue_WithSingleOrgUnit_ShouldReturnOrgUnitName() {
				// Arrange
				SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("Organisation", ConstraintValueType.VALUE, "uuid1");

				OrgUnit ou = new OrgUnit();
				ou.setName("Single Org Unit");

				when(orgUnitDao.findById("uuid1")).thenReturn(Optional.of(ou));
				when(messageSource.getMessage("html.entity.ou.type", null, locale))
					.thenReturn("Organisationsenhed");

				// Act
				String result = attestationConstraintService.caption(constraintDTO);

				// Assert
				assertEquals("Organisationsenhed: Single Org Unit", result);
			}
		}

		@Nested
		@DisplayName("For other value types")
		class OtherValueTests {

			@Test
			@DisplayName("Should return default caption for unknown constraint type")
			void caption_OtherValue_ShouldReturnDefaultCaption() {
				// Arrange
				SystemRoleConstraintDTO constraintDTO = new SystemRoleConstraintDTO("UnknownConstraint", ConstraintValueType.VALUE, "someValue");

				// Act
				String result = attestationConstraintService.caption(constraintDTO);

				// Assert
				assertEquals("UnknownConstraint someValue", result);
			}
		}
	}
}
