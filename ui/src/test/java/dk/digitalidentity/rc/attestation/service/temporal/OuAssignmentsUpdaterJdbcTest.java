package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OuAssignmentsUpdaterJdbcTest {

	@Mock
	private TemporalDao temporalDao;

	@InjectMocks
	private OuAssignmentsUpdaterJdbc updater;

	private static final LocalDate WHEN = LocalDate.of(2025, 6, 1);

	// ---- Common base builder ---- //

	private HistoricOuAssignment.HistoricOuAssignmentBuilder base() {
		return HistoricOuAssignment.builder()
				.recordHash("record-hash")
				.ouUuid("ou-uuid")
				.ouName("Test OU")
				.itSystemId(10L)
				.itSystemName("Test IT System")
				.itSystemAttestationExempt(false)
				.roleId(20L)
				.roleName("Test Role")
				.roleDescription("Role description")
				.assignedThroughType(AssignedThrough.DIRECT)
				.assignedThroughUuid("ou-uuid")
				.assignedThroughName("Test OU")
				.sensitiveRole(false)
				.extraSensitiveRole(false)
				.appliesOnlyToManager(false)
				.appliesAlsoToSubstitutes(false)
				.inheritToChildren(false)
				.exclusions(new ArrayList<>());
	}

	private AttestationOuRoleAssignment runAndCapture(HistoricOuAssignment assignment) {
		given(temporalDao.listHistoricOuAssignmentsByDate(WHEN)).willReturn(List.of(assignment));
		given(temporalDao.findValidOuRoleAssignmentWithHash(any(), any())).willReturn(null);

		updater.updateOuAssignments(WHEN);

		ArgumentCaptor<AttestationOuRoleAssignment> captor = ArgumentCaptor.forClass(AttestationOuRoleAssignment.class);
		verify(temporalDao).saveAttestationOuRoleAssignment(captor.capture());
		return captor.getValue();
	}

	// ---- ------------- ---- //

	@Nested
	@DisplayName("attestation-exempt filter")
	class AttestationExemptFilter {

		@Test
		@DisplayName("returns null (skips) when IT system is attestation-exempt")
		void skipsWhenItSystemIsAttestationExempt() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().itSystemAttestationExempt(true).build();
			given(temporalDao.listHistoricOuAssignmentsByDate(WHEN)).willReturn(List.of(assignment));

			// ---- When ---- //
			updater.updateOuAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao, org.mockito.Mockito.never()).saveAttestationOuRoleAssignment(any());
		}

		@Test
		@DisplayName("processes normally when IT system is not attestation-exempt")
		void processesWhenNotAttestationExempt() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().itSystemAttestationExempt(false).build();

			// ---- When/Then ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);
			assertThat(result).isNotNull();
		}
	}

	@Nested
	@DisplayName("role and IT system fields")
	class RoleAndItSystemFields {

		@Test
		@DisplayName("role fields are mapped correctly")
		void roleFieldsAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.roleId(42L)
					.roleName("My Role")
					.roleDescription("My Role Description")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRoleId()).isEqualTo(42L);
			assertThat(result.getRoleName()).isEqualTo("My Role");
			assertThat(result.getRoleDescription()).isEqualTo("My Role Description");
		}

		@Test
		@DisplayName("IT system fields are mapped correctly")
		void itSystemFieldsAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.itSystemId(99L)
					.itSystemName("My IT System")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getItSystemId()).isEqualTo(99L);
			assertThat(result.getItSystemName()).isEqualTo("My IT System");
		}

		@Test
		@DisplayName("OU fields are mapped correctly")
		void ouFieldsAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.ouUuid("specific-ou-uuid")
					.ouName("Specific OU")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getOuUuid()).isEqualTo("specific-ou-uuid");
			assertThat(result.getOuName()).isEqualTo("Specific OU");
		}

		@Test
		@DisplayName("sensitive role flags are mapped correctly")
		void sensitiveRoleFlagsAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.sensitiveRole(true)
					.extraSensitiveRole(true)
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isSensitiveRole()).isTrue();
			assertThat(result.isExtraSensitiveRole()).isTrue();
		}

		@Test
		@DisplayName("role group fields are mapped when present")
		void roleGroupFieldsAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.roleRoleGroupId(50L)
					.roleRoleGroupName("My Role Group")
					.roleGroupDescription("Role Group Description")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRoleGroupId()).isEqualTo(50L);
			assertThat(result.getRoleGroupName()).isEqualTo("My Role Group");
			assertThat(result.getRoleGroupDescription()).isEqualTo("Role Group Description");
		}

		@Test
		@DisplayName("role group fields are null when absent")
		void roleGroupFieldsAreNullWhenAbsent() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRoleGroupId()).isNull();
			assertThat(result.getRoleGroupName()).isNull();
			assertThat(result.getRoleGroupDescription()).isNull();
		}
	}

	@Nested
	@DisplayName("policy flags")
	class PolicyFlags {

		@Test
		@DisplayName("manager flag is mapped correctly")
		void managerFlagIsMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().appliesOnlyToManager(true).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isManager()).isTrue();
		}

		@Test
		@DisplayName("substitutes flag is mapped correctly")
		void substitutesFlagIsMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().appliesAlsoToSubstitutes(true).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isSubstitutes()).isTrue();
		}

		@Test
		@DisplayName("inheritToChildren flag is mapped to inherit")
		void inheritFlagIsMapped() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(true).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isInherit()).isTrue();
		}
	}

	@Nested
	@DisplayName("inherited calculation")
	class InheritedCalculation {

		@Test
		@DisplayName("inherited is false when assignedThroughUuid equals ouUuid (direct OU assignment)")
		void notInheritedWhenAssignedThroughUuidMatchesOuUuid() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.assignedThroughType(AssignedThrough.ORGUNIT)
					.ouUuid("ou-uuid")
					.assignedThroughUuid("ou-uuid")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isInherited()).isFalse();
		}

		@Test
		@DisplayName("inherited is true when assignedThroughType is ORGUNIT and assignedThroughUuid differs from ouUuid")
		void inheritedWhenOrgUnitAndDifferentUuid() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.assignedThroughType(AssignedThrough.ORGUNIT)
					.ouUuid("child-ou-uuid")
					.assignedThroughUuid("parent-ou-uuid")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isInherited()).isTrue();
		}

		@Test
		@DisplayName("inherited is false when assignedThroughType is DIRECT")
		void notInheritedWhenDirect() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.assignedThroughType(AssignedThrough.DIRECT)
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isInherited()).isFalse();
		}
	}

	@Nested
	@DisplayName("responsibleOuUuid derivation")
	class ResponsibleOuUuidDerivation {

		@Test
		@DisplayName("responsibleOuUuid equals the OU the role was assigned to (assignedThroughUuid) when not inherited and responsibleUserUuid is null")
		void responsibleOuUuidIsTheAssignedOu() {
			// ---- Given ---- //
			// In practice assignedThroughUuid always equals ouUuid (set by HistoricOuAssignmentService)
			HistoricOuAssignment assignment = base()
					.assignedThroughType(AssignedThrough.ORGUNIT)
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.assignedThroughUuid("ou-uuid")
					.assignedThroughName("Test OU")
					.responsibleUserUuid(null)
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getResponsibleOuUuid()).isEqualTo("ou-uuid");
			assertThat(result.getResponsibleOuName()).isEqualTo("Test OU");
			assertThat(result.getResponsibleUserUuid()).isNull();
		}

		@Test
		@DisplayName("responsibleOuUuid is null when responsibleUserUuid is set (IT system responsible overrides OU)")
		void responsibleOuUuidIsNullWhenResponsibleUserUuidIsSet() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.assignedThroughType(AssignedThrough.DIRECT)
					.assignedThroughUuid("some-ou-uuid")
					.responsibleUserUuid("it-system-responsible-uuid")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getResponsibleOuUuid()).isNull();
			assertThat(result.getResponsibleOuName()).isNull();
			assertThat(result.getResponsibleUserUuid()).isEqualTo("it-system-responsible-uuid");
		}

		@Test
		@DisplayName("responsibleOuUuid is null when inherited")
		void responsibleOuUuidIsNullWhenInherited() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.assignedThroughType(AssignedThrough.ORGUNIT)
					.ouUuid("child-ou-uuid")
					.assignedThroughUuid("parent-ou-uuid")
					.responsibleUserUuid(null)
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getResponsibleOuUuid()).isNull();
			assertThat(result.getResponsibleOuName()).isNull();
		}
	}

	@Nested
	@DisplayName("assignedThrough name and UUID fallback")
	class AssignedThroughFallback {

		@Test
		@DisplayName("assignedThroughName falls back to ouName when null")
		void assignedThroughNameFallsBackToOuName() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.ouName("Fallback OU")
					.assignedThroughName(null)
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughName()).isEqualTo("Fallback OU");
		}

		@Test
		@DisplayName("assignedThroughUuid falls back to ouUuid when null")
		void assignedThroughUuidFallsBackToOuUuid() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.ouUuid("fallback-ou-uuid")
					.assignedThroughUuid(null)
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isEqualTo("fallback-ou-uuid");
		}

		@Test
		@DisplayName("assignedThroughName is used as-is when not null")
		void assignedThroughNameUsedWhenPresent() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.ouName("OU Name")
					.assignedThroughName("Parent OU Name")
					.build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughName()).isEqualTo("Parent OU Name");
		}
	}

	@Nested
	@DisplayName("AssignedThrough → AssignedThroughType mapping")
	class AssignedThroughTypeMapping {

		@Test
		@DisplayName("DIRECT maps to DIRECT")
		void directMapsToDirect() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().assignedThroughType(AssignedThrough.DIRECT).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}

		@Test
		@DisplayName("ORGUNIT maps to ORGUNIT")
		void orgUnitMapsToOrgUnit() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().assignedThroughType(AssignedThrough.ORGUNIT).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.ORGUNIT);
		}

		@Test
		@DisplayName("TITLE maps to TITLE")
		void titleMapsToTitle() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().assignedThroughType(AssignedThrough.TITLE).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.TITLE);
		}

		@Test
		@DisplayName("null assignedThroughType defaults to DIRECT")
		void nullDefaultsToDirect() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().assignedThroughType(null).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}
	}

	@Nested
	@DisplayName("exclusion extraction")
	class ExclusionExtraction {

		@Test
		@DisplayName("POSITIVE_TITLES exclusion is mapped to titleUuids")
		void positiveTitlesAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignmentExclusion exclusion = HistoricOuAssignmentExclusion.builder()
					.exclusionType(HistoricOuAssignmentExclusion.ExclusionType.POSITIVE_TITLES)
					.uuids(List.of("title-uuid-1", "title-uuid-2"))
					.build();
			HistoricOuAssignment assignment = base().exclusions(new ArrayList<>(List.of(exclusion))).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getTitleUuids()).containsExactlyInAnyOrder("title-uuid-1", "title-uuid-2");
		}

		@Test
		@DisplayName("NEGATIVE_TITLES exclusion is mapped to exceptedTitleUuids")
		void negativeTitlesAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignmentExclusion exclusion = HistoricOuAssignmentExclusion.builder()
					.exclusionType(HistoricOuAssignmentExclusion.ExclusionType.NEGATIVE_TITLES)
					.uuids(List.of("negative-title-uuid"))
					.build();
			HistoricOuAssignment assignment = base().exclusions(new ArrayList<>(List.of(exclusion))).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getExceptedTitleUuids()).containsExactly("negative-title-uuid");
		}

		@Test
		@DisplayName("EXCEPTED_USERS exclusion is mapped to exceptedUserUuids")
		void exceptedUsersAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignmentExclusion exclusion = HistoricOuAssignmentExclusion.builder()
					.exclusionType(HistoricOuAssignmentExclusion.ExclusionType.EXCEPTED_USERS)
					.uuids(List.of("user-uuid-1", "user-uuid-2"))
					.build();
			HistoricOuAssignment assignment = base().exclusions(new ArrayList<>(List.of(exclusion))).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getExceptedUserUuids()).containsExactlyInAnyOrder("user-uuid-1", "user-uuid-2");
		}

		@Test
		@DisplayName("FUNCTIONS exclusion is mapped to functionUuids")
		void functionsAreMapped() {
			// ---- Given ---- //
			HistoricOuAssignmentExclusion exclusion = HistoricOuAssignmentExclusion.builder()
					.exclusionType(HistoricOuAssignmentExclusion.ExclusionType.FUNCTIONS)
					.uuids(List.of("function-uuid-1"))
					.build();
			HistoricOuAssignment assignment = base().exclusions(new ArrayList<>(List.of(exclusion))).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getFunctionUuids()).containsExactly("function-uuid-1");
		}

		@Test
		@DisplayName("each exclusion type is independent — other lists remain empty")
		void otherExclusionListsRemainEmpty() {
			// ---- Given ---- //
			HistoricOuAssignmentExclusion exclusion = HistoricOuAssignmentExclusion.builder()
					.exclusionType(HistoricOuAssignmentExclusion.ExclusionType.EXCEPTED_USERS)
					.uuids(List.of("user-uuid-1"))
					.build();
			HistoricOuAssignment assignment = base().exclusions(new ArrayList<>(List.of(exclusion))).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getTitleUuids()).isEmpty();
			assertThat(result.getExceptedTitleUuids()).isEmpty();
			assertThat(result.getFunctionUuids()).isEmpty();
		}

		@Test
		@DisplayName("all exclusion lists are empty when no exclusions are present")
		void allExclusionListsEmptyWhenNoExclusions() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().exclusions(new ArrayList<>()).build();

			// ---- When ---- //
			AttestationOuRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getTitleUuids()).isEmpty();
			assertThat(result.getExceptedTitleUuids()).isEmpty();
			assertThat(result.getExceptedUserUuids()).isEmpty();
			assertThat(result.getFunctionUuids()).isEmpty();
		}
	}
}
