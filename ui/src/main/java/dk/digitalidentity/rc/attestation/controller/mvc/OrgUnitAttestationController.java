package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireSubstituteOrManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.model.dto.enums.RoleType.USERROLE;

@RequireSubstituteOrManagerRole
@Controller
public class OrgUnitAttestationController {

	@Autowired
	private UserService userService;
	@Autowired
	private OrganisationAttestationService attestationService;
	@Autowired
	private OrgUnitService orgUnitService;
	@Autowired
	private SettingsService settingsService;

	@GetMapping(value = "/ui/attestation/v2/orgunits/{uuid}")
	@Timed("attestation.controller.mvc.org_unit_attestation_controller.index.timer")
	public String index(Model model, @PathVariable String uuid) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}

		Set<String> managedOrgUnitUuids = orgUnitService.getByManagerMatchingUser(user).stream().map(OrgUnit::getUuid).collect(Collectors.toSet());
		managedOrgUnitUuids.addAll(user.getSubstituteFor().stream().map(o -> o.getOrgUnit().getUuid()).collect(Collectors.toSet()));
		if (!managedOrgUnitUuids.contains(uuid)) {
			return "attestationmodule/error";
		}

		OrganisationAttestationDTO attestation = attestationService.getAttestation(uuid, user.getUuid(), true);

		model.addAttribute("managerdelegate", false);
		model.addAttribute("attestation", attestation);
		model.addAttribute("totalCount", attestation.getUserAttestations().size());
		model.addAttribute("adAttestationEnabled", settingsService.isADAttestationEnabled());
		model.addAttribute("orgUnitTotalCount", calculateOrgUnitCount(attestation));
		model.addAttribute("changeRequestsEnabled", settingsService.isAttestationRequestChangesEnabled());

		return "attestationmodule/orgunits/attestate";
	}

	@GetMapping(value = "/ui/attestation/v2/orgunits/{uuid}/managerdelegate")
	@Timed("attestation.controller.mvc.org_unit_attestation_controller.index.timer")
	public String managerdelegateDetails(Model model, @PathVariable String uuid) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}

		OrganisationAttestationDTO attestation = attestationService.getManagerDelegatedAttestation(uuid, user.getUuid(), true);

		model.addAttribute("managerdelegate", true);
		model.addAttribute("attestation", attestation);
		model.addAttribute("totalCount", attestation.getUserAttestations().size());
		model.addAttribute("adAttestationEnabled", settingsService.isADAttestationEnabled());
		model.addAttribute("orgUnitTotalCount", calculateOrgUnitCount(attestation));
		model.addAttribute("changeRequestsEnabled", settingsService.isAttestationRequestChangesEnabled());

		boolean descriptionRequired = settingsService.isAttestationDescriptionRequired();
		model.addAttribute("hideDescription", !descriptionRequired && settingsService.isAttestationHideDescription());

		return "attestationmodule/orgunits/attestate";
	}

	@GetMapping(value = "/ui/attestation/v2/orgunits/{attestationUuid}/users/{userUuid}/userRemarkFragment")
	@Timed("attestation.controller.mvc.org_unit_attestation_controller.user_remark_fragment.timer")
	public String userRemarkFragment(Model model, @PathVariable String attestationUuid, @PathVariable String userUuid, @RequestParam int number) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}

		List<RoleAssignmentDTO> roles = attestationService.getUserRoleAssignments(attestationUuid, userUuid);

		model.addAttribute("roleAssignments", roles);
		model.addAttribute("number", number);
		model.addAttribute("userUuid", userUuid);

		boolean descriptionRequired = settingsService.isAttestationDescriptionRequired();
		model.addAttribute("hideDescription", !descriptionRequired && settingsService.isAttestationHideDescription());

		return "attestationmodule/fragments/userRemarkModal :: userRemarkModal";
	}

	public record AttestationRoleAssignmentDTO (
			long roleId,
			String roleName,
			RoleType roleType,
			String itSystemName,
			String titles
	) {}
	@GetMapping(value = "/ui/attestation/v2/orgunits/{attestationUuid}/orgunit/{ouUUID}/ouRemarkFragment")
	@Timed("attestation.controller.mvc.org_unit_attestation_controller.user_remark_fragment.timer")
	public String orgUnitRemarkFragment(Model model, @PathVariable String attestationUuid, @PathVariable String ouUUID) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}

		OrganisationAttestationDTO attestation = attestationService.getAttestation(ouUUID, user.getUuid(), true);
		List<AttestationRoleAssignmentDTO> roles = attestation.getOrgUnitUserRoleAssignmentsPrItSystem().stream()
				.flatMap(ass -> ass.getUserRoles().stream()
						.map(ur -> new AttestationRoleAssignmentDTO(ur.getRoleId(), ur.getRoleName(), USERROLE, ass.getItSystemName(), String.join(", ", ur.getTitles()))))
				.toList();

		model.addAttribute("roleAssignments", roles);
		model.addAttribute("orgUnitUuid", ouUUID);

		boolean descriptionRequired = settingsService.isAttestationDescriptionRequired();
		model.addAttribute("hideDescription", !descriptionRequired && settingsService.isAttestationHideDescription());

		return "attestationmodule/fragments/orgUnitRemarkModal :: orgUnitRemarkModal";
	}

	private int calculateOrgUnitCount(OrganisationAttestationDTO attestation) {
		int count = 0;

		if (attestation.getOrgUnitRoleGroupAssignments() != null) {
			count += attestation.getOrgUnitRoleGroupAssignments().size();
		}

		if (attestation.getOrgUnitUserRoleAssignmentsPrItSystem() != null) {
			for (OrgUnitUserRoleAssignmentItSystemDTO orgUnitUserRoleAssignmentItSystemDTO : attestation.getOrgUnitUserRoleAssignmentsPrItSystem()) {
				count += orgUnitUserRoleAssignmentItSystemDTO.getUserRoles().size();
			}
		}

		return count;
	}
}
