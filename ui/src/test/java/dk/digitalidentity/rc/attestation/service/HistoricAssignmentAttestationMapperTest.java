package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricAssignmentAttestationMapperTest {

	// ---- Common test data ---- //

	private HistoricAssignment base;

	@BeforeEach
	void setup() {
		base = HistoricAssignment.builder()
			.userUuid("user-uuid")
			.userId("user-id")
			.userName("User Name")
			.userRoleId(20L)
			.userRoleName("Test Role")
			.userRoleDescription("Role description")
			.itSystemId(10L)
			.itSystemName("Test IT System")
			.sensitiveRole(false)
			.extraSensitiveRole(false)
			.recordHash("record-hash")
			.validFrom(LocalDateTime.of(2025, 1, 15, 10, 30))
			.assignedThroughType(AssignedThrough.DIRECT)
			.build();
	}

	// ---- ------------- ---- //

	@Nested
	@DisplayName("toAttestationAssignment creates a correct record")
	class ToAttestationAssignment {

		@Test
		@DisplayName("user fields are mapped correctly")
		void userFieldsAreMapped() {
			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getUserUuid()).isEqualTo("user-uuid");
			assertThat(result.getUserId()).isEqualTo("user-id");
			assertThat(result.getUserName()).isEqualTo("User Name");
		}

		@Test
		@DisplayName("role and IT system fields are mapped correctly, including sensitive flags")
		void roleAndItSystemFieldsAreMapped() {
			// ---- Given ---- //
			base.setSensitiveRole(true);
			base.setExtraSensitiveRole(true);

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getUserRoleId()).isEqualTo(20L);
			assertThat(result.getUserRoleName()).isEqualTo("Test Role");
			assertThat(result.getUserRoleDescription()).isEqualTo("Role description");
			assertThat(result.getItSystemId()).isEqualTo(10L);
			assertThat(result.getItSystemName()).isEqualTo("Test IT System");
			assertThat(result.isSensitiveRole()).isTrue();
			assertThat(result.isExtraSensitiveRole()).isTrue();
		}

		@Test
		@DisplayName("userRoleId defaults to 0 when null")
		void userRoleIdDefaultsToZeroWhenNull() {
			// ---- Given ---- //
			base.setUserRoleId(null);

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getUserRoleId()).isZero();
		}

		@Test
		@DisplayName("role group fields are mapped when present")
		void roleGroupFieldsAreMapped() {
			// ---- Given ---- //
			base.setRoleGroupId(30L);
			base.setRoleGroupName("Test Role Group");
			base.setRoleGroupDescription("Role group description");

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getRoleGroupId()).isEqualTo(30L);
			assertThat(result.getRoleGroupName()).isEqualTo("Test Role Group");
			assertThat(result.getRoleGroupDescription()).isEqualTo("Role group description");
		}

		@Test
		@DisplayName("role group fields are null when absent")
		void roleGroupFieldsAreNullWhenAbsent() {
			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getRoleGroupId()).isNull();
			assertThat(result.getRoleGroupName()).isNull();
			assertThat(result.getRoleGroupDescription()).isNull();
		}

		@Test
		@DisplayName("responsible fields are passed through directly")
		void responsibleFieldsAreMapped() {
			// ---- Given ---- //
			base.setResponsibleOUUuid("responsible-ou-uuid");
			base.setResponsibleOUName("Responsible OU");
			base.setResponsibleUserUuid(null);

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getResponsibleOuUuid()).isEqualTo("responsible-ou-uuid");
			assertThat(result.getResponsibleOuName()).isEqualTo("Responsible OU");
			assertThat(result.getResponsibleUserUuid()).isNull();
		}

		@Test
		@DisplayName("assignedThroughOUUuid is mapped to roleOuUuid")
		void assignedThroughOuIsMappedToRoleOu() {
			// ---- Given ---- //
			base.setAssignedThroughType(AssignedThrough.ORGUNIT);
			base.setAssignedThroughOUUuid("through-ou-uuid");
			base.setAssignedThroughOUName("Through OU");

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getRoleOuUuid()).isEqualTo("through-ou-uuid");
			assertThat(result.getRoleOuName()).isEqualTo("Through OU");
		}

		@Test
		@DisplayName("temporal fields: validFrom/validTo are truncated to LocalDate, validTo is null for open records")
		void temporalFieldsAreTruncatedToLocalDate() {
			// ---- Given ---- //
			base.setValidFrom(LocalDateTime.of(2025, 3, 10, 14, 30, 0));
			base.setValidTo(null);

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getValidFrom()).isEqualTo(LocalDate.of(2025, 3, 10));
			assertThat(result.getValidTo()).isNull();
			assertThat(result.getAssignedFrom()).isEqualTo(LocalDate.of(2025, 3, 10));
		}

		@Test
		@DisplayName("validTo is truncated to LocalDate when set")
		void validToIsTruncatedWhenSet() {
			// ---- Given ---- //
			base.setValidTo(LocalDateTime.of(2025, 6, 20, 23, 59, 59));

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2025, 6, 20));
		}

		@Test
		@DisplayName("recordHash and inherited=false are set on the result")
		void recordHashAndInheritedAreMapped() {
			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getRecordHash()).isEqualTo("record-hash");
			assertThat(result.isInherited()).isFalse();
		}

		@Test
		@DisplayName("postponedConstraints is always null (constraints are not formatted by this mapper)")
		void postponedConstraintsIsNull() {
			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getPostponedConstraints()).isNull();
		}

		@Test
		@DisplayName("validFrom null yields null validFrom and null assignedFrom")
		void validFromNullYieldsNullDates() {
			// ---- Given ---- //
			base.setValidFrom(null);

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getValidFrom()).isNull();
			assertThat(result.getAssignedFrom()).isNull();
		}

		@Test
		@DisplayName("updatedAt is truncated to LocalDate when set")
		void updatedAtIsTruncatedWhenSet() {
			// ---- Given ---- //
			base.setUpdatedAt(LocalDateTime.of(2025, 4, 5, 9, 0, 0));

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getUpdatedAt()).isEqualTo(LocalDate.of(2025, 4, 5));
		}

		@Test
		@DisplayName("updatedAt null yields null updatedAt")
		void updatedAtNullYieldsNull() {
			// ---- Given ---- //
			base.setUpdatedAt(null);

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getUpdatedAt()).isNull();
		}
	}

	@Nested
	@DisplayName("AssignedThrough → AssignedThroughType mapping")
	class AssignedThroughTypeMapping {

		@Test
		@DisplayName("DIRECT maps to DIRECT")
		void directMapsToDirect() {
			base.setAssignedThroughType(AssignedThrough.DIRECT);
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}

		@Test
		@DisplayName("ROLEGROUP collapses to DIRECT")
		void roleGroupCollapsesToDirect() {
			base.setAssignedThroughType(AssignedThrough.ROLEGROUP);
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}

		@Test
		@DisplayName("POSITION collapses to DIRECT")
		void positionCollapsesToDirect() {
			base.setAssignedThroughType(AssignedThrough.POSITION);
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}

		@Test
		@DisplayName("ORGUNIT maps to ORGUNIT")
		void orgunitMapsToOrgunit() {
			base.setAssignedThroughType(AssignedThrough.ORGUNIT);
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.ORGUNIT);
		}

		@Test
		@DisplayName("TITLE maps to TITLE")
		void titleMapsToTitle() {
			base.setAssignedThroughType(AssignedThrough.TITLE);
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.TITLE);
		}

		@Test
		@DisplayName("(negative) null assignedThroughType defaults to DIRECT")
		void nullAssignedThroughTypeDefaultsToDirect() {
			base.setAssignedThroughType(null);
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}
	}

	@Nested
	@DisplayName("assignedThrough name and UUID resolution")
	class AssignedThroughResolution {

		@Test
		@DisplayName("ORGUNIT assignment: assignedThroughName and UUID come from the OU fields")
		void orgUnitAssignmentResolvesOuNameAndUuid() {
			// ---- Given ---- //
			base.setAssignedThroughType(AssignedThrough.ORGUNIT);
			base.setAssignedThroughOUUuid("ou-uuid");
			base.setAssignedThroughOUName("Test OU");

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughName()).isEqualTo("Test OU");
			assertThat(result.getAssignedThroughUuid()).isEqualTo("ou-uuid");
		}

		@Test
		@DisplayName("TITLE assignment: assignedThroughName and UUID come from the title fields")
		void titleAssignmentResolvesTitleNameAndUuid() {
			// ---- Given ---- //
			base.setAssignedThroughType(AssignedThrough.TITLE);
			base.setAssignedThroughTitleUuid("title-uuid");
			base.setAssignedThroughTitleName("Test Title");
			base.setAssignedThroughOUUuid("ou-uuid");
			base.setAssignedThroughOUName("Test OU");

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughName()).isEqualTo("Test Title");
			assertThat(result.getAssignedThroughUuid()).isEqualTo("title-uuid");
		}

		@Test
		@DisplayName("DIRECT assignment: assignedThroughName and UUID are null")
		void directAssignmentHasNullNameAndUuid() {
			// ---- Given ---- //
			base.setAssignedThroughType(AssignedThrough.DIRECT);

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughName()).isNull();
			assertThat(result.getAssignedThroughUuid()).isNull();
		}

		@Test
		@DisplayName("ROLEGROUP assignment (collapsed to DIRECT): assignedThroughName and UUID are null")
		void roleGroupAssignmentHasNullNameAndUuid() {
			// ---- Given ---- //
			base.setAssignedThroughType(AssignedThrough.ROLEGROUP);
			base.setAssignedThroughRoleGroupId(30L);
			base.setAssignedThroughRoleGroupName("Some Role Group");

			// ---- When ---- //
			AttestationUserRoleAssignment result = HistoricAssignmentAttestationMapper.toAttestationAssignment(base);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughName()).isNull();
			assertThat(result.getAssignedThroughUuid()).isNull();
		}
	}

	@Nested
	@DisplayName("toDto creates a correct record")
	class ToDto {

		@Test
		@DisplayName("user fields are mapped correctly")
		void userFieldsAreMapped() {
			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getUserUuid()).isEqualTo("user-uuid");
			assertThat(result.getUserId()).isEqualTo("user-id");
			assertThat(result.getUserName()).isEqualTo("User Name");
		}

		@Test
		@DisplayName("role and IT system fields are mapped correctly, including sensitive flags")
		void roleAndItSystemFieldsAreMapped() {
			// ---- Given ---- //
			base.setSensitiveRole(true);
			base.setExtraSensitiveRole(true);

			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getUserRoleId()).isEqualTo(20L);
			assertThat(result.getUserRoleName()).isEqualTo("Test Role");
			assertThat(result.getUserRoleDescription()).isEqualTo("Role description");
			assertThat(result.getItSystemId()).isEqualTo(10L);
			assertThat(result.getItSystemName()).isEqualTo("Test IT System");
			assertThat(result.isSensitiveRole()).isTrue();
			assertThat(result.isExtraSensitiveRole()).isTrue();
		}

		@Test
		@DisplayName("userRoleId defaults to 0 when null")
		void userRoleIdDefaultsToZeroWhenNull() {
			// ---- Given ---- //
			base.setUserRoleId(null);

			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getUserRoleId()).isZero();
		}

		@Test
		@DisplayName("responsible fields are passed through directly")
		void responsibleFieldsAreMapped() {
			// ---- Given ---- //
			base.setResponsibleOUUuid("responsible-ou-uuid");
			base.setResponsibleOUName("Responsible OU");
			base.setResponsibleUserUuid(null);

			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getResponsibleOuUuid()).isEqualTo("responsible-ou-uuid");
			assertThat(result.getResponsibleOuName()).isEqualTo("Responsible OU");
			assertThat(result.getResponsibleUserUuid()).isNull();
		}

		@Test
		@DisplayName("assignedThroughOUUuid is mapped to roleOuUuid")
		void assignedThroughOuIsMappedToRoleOu() {
			// ---- Given ---- //
			base.setAssignedThroughType(AssignedThrough.ORGUNIT);
			base.setAssignedThroughOUUuid("through-ou-uuid");
			base.setAssignedThroughOUName("Through OU");

			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getRoleOuUuid()).isEqualTo("through-ou-uuid");
			assertThat(result.getRoleOuName()).isEqualTo("Through OU");
		}

		@Test
		@DisplayName("temporal fields are truncated to LocalDate")
		void temporalFieldsAreTruncated() {
			// ---- Given ---- //
			base.setValidFrom(LocalDateTime.of(2025, 3, 10, 14, 30, 0));
			base.setValidTo(LocalDateTime.of(2025, 6, 20, 23, 59, 59));
			base.setUpdatedAt(LocalDateTime.of(2025, 4, 5, 9, 0, 0));

			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getValidFrom()).isEqualTo(LocalDate.of(2025, 3, 10));
			assertThat(result.getValidTo()).isEqualTo(LocalDate.of(2025, 6, 20));
			assertThat(result.getUpdatedAt()).isEqualTo(LocalDate.of(2025, 4, 5));
			assertThat(result.getAssignedFrom()).isEqualTo(LocalDate.of(2025, 3, 10));
		}

		@Test
		@DisplayName("null temporal fields yield null dates")
		void nullTemporalFieldsYieldNullDates() {
			// ---- Given ---- //
			base.setValidFrom(null);
			base.setValidTo(null);
			base.setUpdatedAt(null);

			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getValidFrom()).isNull();
			assertThat(result.getValidTo()).isNull();
			assertThat(result.getUpdatedAt()).isNull();
			assertThat(result.getAssignedFrom()).isNull();
		}

		@Test
		@DisplayName("recordHash is mapped and inherited is always false")
		void recordHashAndInheritedAreMapped() {
			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getRecordHash()).isEqualTo("record-hash");
			assertThat(result.isInherited()).isFalse();
		}

		@Test
		@DisplayName("postponedConstraints is always null")
		void postponedConstraintsIsNull() {
			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getPostponedConstraints()).isNull();
		}

		@Test
		@DisplayName("assignedThrough type and name/UUID resolution uses the same logic as toAttestationAssignment")
		void assignedThroughResolutionMatchesToAttestationAssignment() {
			// ---- Given ---- //
			base.setAssignedThroughType(AssignedThrough.TITLE);
			base.setAssignedThroughTitleUuid("title-uuid");
			base.setAssignedThroughTitleName("Test Title");
			base.setAssignedThroughOUUuid("ou-uuid");
			base.setAssignedThroughOUName("Test OU");

			// ---- When ---- //
			AttestationUserRoleAssignmentDto result = HistoricAssignmentAttestationMapper.toDto(base);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.TITLE);
			assertThat(result.getAssignedThroughName()).isEqualTo("Test Title");
			assertThat(result.getAssignedThroughUuid()).isEqualTo("title-uuid");
		}
	}
}
