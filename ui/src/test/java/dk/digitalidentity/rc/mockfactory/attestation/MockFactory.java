package dk.digitalidentity.rc.mockfactory.attestation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAssignmentOrgUnitDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAssignmentUserDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;

public class MockFactory {

	// ==================== User ====================

	public static User createUser(String uuid, String userId, String name) {
		User user = new User();
		user.setUuid(uuid);
		user.setUserId(userId);
		user.setName(name);
		user.setPositions(new ArrayList<>());
		user.setUserRoleAssignments(new ArrayList<>());
		user.setRoleGroupAssignments(new ArrayList<>());
		return user;
	}

	public static User createUser(String uuid, String userId, String name, String email) {
		User user = createUser(uuid, userId, name);
		user.setEmail(email);
		return user;
	}

	public static ItSystemRoleAssignmentUserDTO createUserDto(String userId, String verifiedByUserId, String remarks) {
		return ItSystemRoleAssignmentUserDTO.builder()
			.userId(userId)
			.verifiedByUserId(verifiedByUserId)
			.remarks(remarks)
			.build();
	}

	public static ItSystemRoleAssignmentOrgUnitDTO createOrgUnitDto(String orgUnitUuid, String verifiedByUserId, String remarks) {
		return ItSystemRoleAssignmentOrgUnitDTO.builder()
			.orgUnitUuid(orgUnitUuid)
			.verifiedByUserId(verifiedByUserId)
			.remarks(remarks)
			.build();
	}

	public static UserRoleAttestationDTO createUserRoleDto(String roleName, String verifiedByUserId, String remarks) {
		return UserRoleAttestationDTO.builder()
			.roleName(roleName)
			.verifiedByUserId(verifiedByUserId)
			.remarks(remarks)
			.build();
	}

	public static UserAttestationDTO createUserAttestationDto(String userId, String verifiedByUserId, String remarks, boolean adRemoval) {
		return UserAttestationDTO.builder()
			.userId(userId)
			.verifiedByUserId(verifiedByUserId)
			.remarks(remarks)
			.adRemoval(adRemoval)
			.build();
	}

	// ==================== OrgUnit ====================

	public static OrgUnit createOrgUnit(String uuid, String name) {
		OrgUnit orgUnit = new OrgUnit();
		orgUnit.setUuid(uuid);
		orgUnit.setName(name);
		orgUnit.setActive(true);
		orgUnit.setUserRoleAssignments(new ArrayList<>());
		orgUnit.setRoleGroupAssignments(new ArrayList<>());
		return orgUnit;
	}

	public static OrgUnit createOrgUnit(String uuid, String name, User manager) {
		OrgUnit orgUnit = createOrgUnit(uuid, name);
		orgUnit.setManager(manager);
		return orgUnit;
	}

	public static OrgUnit createOrgUnit(String uuid, String name, OrgUnit parent) {
		OrgUnit orgUnit = createOrgUnit(uuid, name);
		orgUnit.setParent(parent);
		return orgUnit;
	}

	public static OrgUnit createOrgUnit(String uuid, String name, User manager, OrgUnit parent) {
		OrgUnit orgUnit = createOrgUnit(uuid, name, manager);
		orgUnit.setParent(parent);
		return orgUnit;
	}

	public static OrgUnit createOrgUnitWithManager(String uuid, String managerUuid) {
		OrgUnit orgUnit = createOrgUnit(uuid, "Test OU " + uuid);
		if (managerUuid != null) {
			User manager = new User();
			manager.setUuid(managerUuid);
			orgUnit.setManager(manager);
		}
		return orgUnit;
	}

	// ==================== ItSystem ====================

	public static ItSystem createItSystem(long id, String name) {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(id);
		itSystem.setName(name);
		return itSystem;
	}

	public static ItSystem createItSystem(long id, String name, User attestationResponsible) {
		ItSystem itSystem = createItSystem(id, name);
		itSystem.addAttestationResponsible(attestationResponsible);
		return itSystem;
	}

	// ==================== Attestation ====================

	public static Attestation createAttestation(Long id, String uuid, Attestation.AttestationType type) {
		Attestation attestation = new Attestation();
		attestation.setId(id);
		attestation.setUuid(uuid);
		attestation.setAttestationType(type);
		attestation.setDeadline(LocalDate.now().plusDays(14));
		attestation.setCreatedAt(LocalDate.now());
		attestation.setOrganisationUserAttestationEntries(new HashSet<>());
		attestation.setItSystemUserAttestationEntries(new HashSet<>());
		attestation.setItSystemOrganisationAttestationEntries(new HashSet<>());
		attestation.setMails(new HashSet<>());
		return attestation;
	}

	public static Attestation createItSystemAttestation(Long id, String uuid, long itSystemId, String itSystemName) {
		Attestation attestation = createAttestation(id, uuid, Attestation.AttestationType.IT_SYSTEM_ATTESTATION);
		attestation.setItSystemId(itSystemId);
		attestation.setItSystemName(itSystemName);
		return attestation;
	}

	public static Attestation createItSystemAttestation(Long id, String uuid, long itSystemId, String itSystemName, Long responsibleCollectionId) {
		Attestation attestation = createItSystemAttestation(id, uuid, itSystemId, itSystemName);
		attestation.setResponsibleCollectionId(responsibleCollectionId);
		return attestation;
	}

	public static Attestation createOrganisationAttestation(Long id, String uuid, String responsibleOuUuid, String responsibleOuName) {
		Attestation attestation = createAttestation(id, uuid, Attestation.AttestationType.ORGANISATION_ATTESTATION);
		attestation.setResponsibleOuUuid(responsibleOuUuid);
		attestation.setResponsibleOuName(responsibleOuName);
		return attestation;
	}

	public static Attestation createItSystemRolesAttestation(Long id, String uuid, long itSystemId, String itSystemName, Long responsibleCollectionId) {
		Attestation attestation = createAttestation(id, uuid, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);
		attestation.setItSystemId(itSystemId);
		attestation.setItSystemName(itSystemName);
		attestation.setResponsibleCollectionId(responsibleCollectionId);
		return attestation;
	}

	// ==================== AttestationRun ====================

	public static AttestationRun createAttestationRun(Long id, LocalDate deadline) {
		AttestationRun run = new AttestationRun();
		run.setId(id);
		run.setDeadline(deadline);
		run.setSensitive(false);
		run.setExtraSensitive(false);
		run.setAttestations(new ArrayList<>());
		return run;
	}

	// ==================== EmailTemplate ====================

	public static EmailTemplate createEmailTemplate(EmailTemplateType type, String title, String message, boolean enabled) {
		EmailTemplate template = new EmailTemplate();
		template.setTemplateType(type);
		template.setTitle(title);
		template.setMessage(message);
		template.setEnabled(enabled);
		template.setDaysBeforeEvent(7);
		return template;
	}

	public static EmailTemplate createDisabledEmailTemplate(EmailTemplateType type) {
		return createEmailTemplate(type, "Test Title", "Test Message", false);
	}


	// ==================== RoleAssignmentDTO ====================

	public static RoleAssignmentDTO createUserRoleAssignment(Long roleId, String roleName, String itSystemName) {
		return RoleAssignmentDTO.builder()
				.roleType(RoleType.USERROLE)
				.roleId(roleId)
				.roleName(roleName)
				.itSystemName(itSystemName)
				.build();
	}

	public static RoleAssignmentDTO createRoleGroupAssignment(Long roleId, String roleName, String itSystemName) {
		return RoleAssignmentDTO.builder()
				.roleType(RoleType.ROLEGROUP)
				.roleId(roleId)
				.roleName(roleName)
				.itSystemName(itSystemName)
				.build();
	}

	public static List<RoleAssignmentDTO> createMixedRoleAssignments() {
		return List.of(
				createUserRoleAssignment(1L, "Test User Role", "Test System"),
				createRoleGroupAssignment(2L, "Test Role Group", "Test System")
		);
	}
}
