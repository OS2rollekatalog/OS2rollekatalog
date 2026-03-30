package dk.digitalidentity.rc.attestation.service.util;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Attestation Validation Tests")
class AttestationValidationTest {

	private Attestation attestation;

	@BeforeEach
	void setUp() {
		attestation = Attestation.builder()
			.id(1L)
			.itSystemUserAttestationEntries(new HashSet<>())
			.itSystemOrganisationAttestationEntries(new HashSet<>())
			.itSystemUserRoleAttestationEntries(new HashSet<>())
			.build();
	}

	@Nested
	@DisplayName("validateAttestationOfItSystemUserIsNotPerformed() Tests")
	class ValidateItSystemUserTests {

		@Test
		@DisplayName("Should not throw exception when no attestation entry exists for user")
		void validateItSystemUser_NoEntry_ShouldNotThrowException() {
			// Arrange
			String userUuid = "user-123";

			// Act & Assert
			assertDoesNotThrow(() ->
				AttestationValidation.validateAttestationOfItSystemUserIsNotPerformed(attestation, userUuid));
		}

		@Test
		@DisplayName("Should throw exception when user has been approved")
		void validateItSystemUser_UserApproved_ShouldThrowException() {
			// Arrange
			String userUuid = "user-123";
			ZonedDateTime createdAt = ZonedDateTime.now();

			ItSystemUserAttestationEntry entry = ItSystemUserAttestationEntry.builder()
				.userUuid(userUuid)
				.remarks(null)
				.createdAt(createdAt)
				.attestation(attestation)
				.build();
			attestation.getItSystemUserAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemUserIsNotPerformed(attestation, userUuid));

			assertTrue(exception.getReason().contains("User already approved at"));
		}

		@Test
		@DisplayName("Should throw exception when user has been rejected")
		void validateItSystemUser_UserRejected_ShouldThrowException() {
			// Arrange
			String userUuid = "user-123";
			ZonedDateTime createdAt = ZonedDateTime.now();

			ItSystemUserAttestationEntry entry = ItSystemUserAttestationEntry.builder()
				.userUuid(userUuid)
				.remarks("Some rejection reason")
				.createdAt(createdAt)
				.attestation(attestation)
				.build();
			attestation.getItSystemUserAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemUserIsNotPerformed(attestation, userUuid));

			assertTrue(exception.getReason().contains("User already rejected at"));
		}

		@Test
		@DisplayName("Should not throw exception when entry exists for different user")
		void validateItSystemUser_DifferentUser_ShouldNotThrowException() {
			// Arrange
			String userUuid = "user-123";
			String differentUserUuid = "user-456";

			ItSystemUserAttestationEntry entry = ItSystemUserAttestationEntry.builder()
				.userUuid(differentUserUuid)
				.remarks(null)
				.createdAt(ZonedDateTime.now())
				.attestation(attestation)
				.build();
			attestation.getItSystemUserAttestationEntries().add(entry);

			// Act & Assert
			assertDoesNotThrow(() ->
				AttestationValidation.validateAttestationOfItSystemUserIsNotPerformed(attestation, userUuid));
		}

		@Test
		@DisplayName("Should prioritize rejection message when remarks are present")
		void validateItSystemUser_WithRemarks_ShouldShowRejectedMessage() {
			// Arrange
			String userUuid = "user-123";

			ItSystemUserAttestationEntry entry = ItSystemUserAttestationEntry.builder()
				.userUuid(userUuid)
				.remarks("Rejection reason")
				.createdAt(ZonedDateTime.now())
				.attestation(attestation)
				.build();
			attestation.getItSystemUserAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemUserIsNotPerformed(attestation, userUuid));

			assertTrue(exception.getReason().contains("rejected"));
			assertFalse(exception.getReason().contains("approved"));
		}
	}

	@Nested
	@DisplayName("validateAttestationOfItSystemOuIsNotPerformed() Tests")
	class ValidateItSystemOuTests {

		@Test
		@DisplayName("Should not throw exception when no attestation entry exists for OU")
		void validateItSystemOu_NoEntry_ShouldNotThrowException() {
			// Arrange
			String ouUuid = "ou-123";

			// Act & Assert
			assertDoesNotThrow(() ->
				AttestationValidation.validateAttestationOfItSystemOuIsNotPerformed(attestation, ouUuid));
		}

		@Test
		@DisplayName("Should throw exception when OU has been approved")
		void validateItSystemOu_OuApproved_ShouldThrowException() {
			// Arrange
			String ouUuid = "ou-123";
			ZonedDateTime createdAt = ZonedDateTime.now();

			ItSystemOrganisationAttestationEntry entry = ItSystemOrganisationAttestationEntry.builder()
				.organisationUuid(ouUuid)
				.remarks(null)
				.createdAt(createdAt)
				.attestation(attestation)
				.build();
			attestation.getItSystemOrganisationAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemOuIsNotPerformed(attestation, ouUuid));

			assertTrue(exception.getReason().contains("Organisation already approved at"));
		}

		@Test
		@DisplayName("Should throw exception when OU has been rejected")
		void validateItSystemOu_OuRejected_ShouldThrowException() {
			// Arrange
			String ouUuid = "ou-123";
			ZonedDateTime createdAt = ZonedDateTime.now();

			ItSystemOrganisationAttestationEntry entry = ItSystemOrganisationAttestationEntry.builder()
				.organisationUuid(ouUuid)
				.remarks("Some rejection reason")
				.createdAt(createdAt)
				.attestation(attestation)
				.build();
			attestation.getItSystemOrganisationAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemOuIsNotPerformed(attestation, ouUuid));

			assertTrue(exception.getReason().contains("Organisation already rejected at"));
		}

		@Test
		@DisplayName("Should not throw exception when entry exists for different OU")
		void validateItSystemOu_DifferentOu_ShouldNotThrowException() {
			// Arrange
			String ouUuid = "ou-123";
			String differentOuUuid = "ou-456";

			ItSystemOrganisationAttestationEntry entry = ItSystemOrganisationAttestationEntry.builder()
				.organisationUuid(differentOuUuid)
				.remarks(null)
				.createdAt(ZonedDateTime.now())
				.attestation(attestation)
				.build();
			attestation.getItSystemOrganisationAttestationEntries().add(entry);

			// Act & Assert
			assertDoesNotThrow(() ->
				AttestationValidation.validateAttestationOfItSystemOuIsNotPerformed(attestation, ouUuid));
		}

		@Test
		@DisplayName("Should prioritize rejection message when remarks are present")
		void validateItSystemOu_WithRemarks_ShouldShowRejectedMessage() {
			// Arrange
			String ouUuid = "ou-123";

			ItSystemOrganisationAttestationEntry entry = ItSystemOrganisationAttestationEntry.builder()
				.organisationUuid(ouUuid)
				.remarks("Rejection reason")
				.createdAt(ZonedDateTime.now())
				.attestation(attestation)
				.build();
			attestation.getItSystemOrganisationAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemOuIsNotPerformed(attestation, ouUuid));

			assertTrue(exception.getReason().contains("rejected"));
			assertFalse(exception.getReason().contains("approved"));
		}
	}

	@Nested
	@DisplayName("validateAttestationOfItSystemUserRoleIsNotPerformed() Tests")
	class ValidateItSystemUserRoleTests {

		@Test
		@DisplayName("Should not throw exception when no attestation entry exists for role")
		void validateItSystemUserRole_NoEntry_ShouldNotThrowException() {
			// Arrange
			Long roleId = 123L;

			// Act & Assert
			assertDoesNotThrow(() ->
				AttestationValidation.validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, roleId));
		}

		@Test
		@DisplayName("Should throw exception when role has been approved")
		void validateItSystemUserRole_RoleApproved_ShouldThrowException() {
			// Arrange
			Long roleId = 123L;
			ZonedDateTime createdAt = ZonedDateTime.now();

			ItSystemRoleAttestationEntry entry = ItSystemRoleAttestationEntry.builder()
				.userRoleId(roleId)
				.remarks(null)
				.createdAt(createdAt)
				.attestation(attestation)
				.build();
			attestation.getItSystemUserRoleAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, roleId));

			assertTrue(exception.getReason().contains("Role already approved at"));
		}

		@Test
		@DisplayName("Should throw exception when role has been rejected")
		void validateItSystemUserRole_RoleRejected_ShouldThrowException() {
			// Arrange
			Long roleId = 123L;
			ZonedDateTime createdAt = ZonedDateTime.now();

			ItSystemRoleAttestationEntry entry = ItSystemRoleAttestationEntry.builder()
				.userRoleId(roleId)
				.remarks("Some rejection reason")
				.createdAt(createdAt)
				.attestation(attestation)
				.build();
			attestation.getItSystemUserRoleAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, roleId));

			assertTrue(exception.getReason().contains("Role already rejected at"));
		}

		@Test
		@DisplayName("Should not throw exception when entry exists for different role")
		void validateItSystemUserRole_DifferentRole_ShouldNotThrowException() {
			// Arrange
			Long roleId = 123L;
			Long differentRoleId = 456L;

			ItSystemRoleAttestationEntry entry = ItSystemRoleAttestationEntry.builder()
				.userRoleId(differentRoleId)
				.remarks(null)
				.createdAt(ZonedDateTime.now())
				.attestation(attestation)
				.build();
			attestation.getItSystemUserRoleAttestationEntries().add(entry);

			// Act & Assert
			assertDoesNotThrow(() ->
				AttestationValidation.validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, roleId));
		}

		@Test
		@DisplayName("Should prioritize rejection message when remarks are present")
		void validateItSystemUserRole_WithRemarks_ShouldShowRejectedMessage() {
			// Arrange
			Long roleId = 123L;

			ItSystemRoleAttestationEntry entry = ItSystemRoleAttestationEntry.builder()
				.userRoleId(roleId)
				.remarks("Rejection reason")
				.createdAt(ZonedDateTime.now())
				.attestation(attestation)
				.build();
			attestation.getItSystemUserRoleAttestationEntries().add(entry);

			// Act & Assert
			ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
				AttestationValidation.validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, roleId));

			assertTrue(exception.getReason().contains("rejected"));
			assertFalse(exception.getReason().contains("approved"));
		}

		@Test
		@DisplayName("Should handle null roleId gracefully")
		void validateItSystemUserRole_NullRoleId_ShouldNotThrowException() {
			// Arrange
			Long roleId = null;

			ItSystemRoleAttestationEntry entry = ItSystemRoleAttestationEntry.builder()
				.userRoleId(123L)
				.remarks(null)
				.createdAt(ZonedDateTime.now())
				.attestation(attestation)
				.build();
			attestation.getItSystemUserRoleAttestationEntries().add(entry);

			// Act & Assert
			assertDoesNotThrow(() ->
				AttestationValidation.validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, roleId));
		}
	}
}
