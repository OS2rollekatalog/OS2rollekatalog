package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.service.ItSystemUserRolesAttestationService;
import dk.digitalidentity.rc.attestation.service.ItSystemUsersAttestationService;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireSubstituteOrManagerOrItSystemResponsibleOrAttestationAdminOrAdministratorRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequireSubstituteOrManagerOrItSystemResponsibleOrAttestationAdminOrAdministratorRole
@Controller
public class AttestationV2Controller {

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemUserRolesAttestationService itSystemUserRolesAttestationService;

	@Autowired
	private OrganisationAttestationService orgUserAttestationService;

	@Autowired
	private ItSystemUsersAttestationService itSystemUsersAttestationService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private OrgUnitService orgUnitService;

	@GetMapping(value = "/ui/attestation/v2")
	@Timed("attestation.controller.mvc.attestation_v2_controller.index.timer")
	public String index(Model model) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}
		final LocalDate now = LocalDate.now();

		final List<User> substituteFor = securityUtil.getManagersBySubstitute();
        final List<AttestationOverviewDTO> systemAttestations = buildItSystems(itSystemUserRolesAttestationService.listItSystemsForAttestation(now, user.getUuid()), false);
        final List<AttestationOverviewDTO> orgsForAttestation =
				new ArrayList<>(buildOrgUnits(orgUserAttestationService.listOrganisationsForAttestation(now, user, substituteFor), false));
		final List<AttestationOverviewDTO> itSystemUsersAttestation = new ArrayList<>();
		if (SecurityUtil.isSystemResponsible()) {
			itSystemUsersAttestation.addAll(buildItSystemsUsers(itSystemUsersAttestationService.listItSystemUsersForAttestation(now, user.getUuid()), false));
		}
		model.addAttribute("orgUnits", orgsForAttestation);
		model.addAttribute("itSystems", systemAttestations);
		model.addAttribute("itSystemsForRoleAssignmentAttestation", itSystemUsersAttestation);
		return "attestationmodule/index";
	}

	private static List<AttestationOverviewDTO> buildItSystemsUsers(final List<ItSystemRoleAttestationDTO> itSystemUsersAttestation, boolean readOnly) {
		LocalDate now = LocalDate.now();
		return itSystemUsersAttestation.stream()
				.map(its -> {
					long total = its.getUsers().size();
					long verified = its.getUsers().stream()
							.filter(u -> u.getVerifiedByUserId() != null || u.getRemarks() != null)
							.count();
					long totalOrgUnit = its.getOrgUnits().size();
					long verifiedOrgUnit = its.getOrgUnits().stream()
							.filter(o -> o.getVerifiedByUserId() != null || o.getRemarks() != null)
							.count();
					return new AttestationOverviewDTO(readOnly, its.getItSystemName(), String.valueOf(its.getItSystemId()), verified,
							total - verified, total, its.getDeadline(), its.getDeadline().isBefore(now), false,
							null, verifiedOrgUnit, totalOrgUnit - verifiedOrgUnit, totalOrgUnit);
				})
				.collect(Collectors.toList());
	}

	private List<AttestationOverviewDTO> buildOrgUnits(final List<OrganisationAttestationDTO> orgsForAttestation, boolean readOnly) {
		return orgsForAttestation.stream()
				.map(o -> buildOrgUnit(o, readOnly))
				.collect(Collectors.toList());
	}

	private AttestationOverviewDTO buildOrgUnit(final OrganisationAttestationDTO organisationAttestationDto, boolean readOnly) {
		long total = organisationAttestationDto.getUserAttestations().size();
		long verified = organisationAttestationDto.getUserAttestations().stream()
				.filter(u -> u.getVerifiedByUserId() != null || u.getRemarks() != null || u.isAdRemoval())
				.count();

		List<String> substitutes = new ArrayList<>();
		OrgUnit orgUnit = orgUnitService.getByUuid(organisationAttestationDto.getOuUuid());
		if (orgUnit != null) {
			substitutes.addAll(orgUnit.getManager().getManagerSubstitutes().stream().filter(s -> s.getOrgUnit() == null || s.getOrgUnit().getUuid().equals(organisationAttestationDto.getOuUuid())).map(s -> s.getSubstitute().getName()).collect(Collectors.toList()));
		}

		LocalDate now = LocalDate.now();
		return new AttestationOverviewDTO(readOnly, organisationAttestationDto.getOuName(), organisationAttestationDto.getOuUuid(),
				verified, total-verified, total, organisationAttestationDto.getDeadLine(), organisationAttestationDto.getDeadLine().isBefore(now),
				organisationAttestationDto.isOrgUnitRolesVerified(), substitutes,
				0, 0, 0);
	}

	private static List<AttestationOverviewDTO> buildItSystems(final List<ItSystemAttestationDTO> systemAttestationList, boolean readOnly) {
		LocalDate now = LocalDate.now();
		return systemAttestationList.stream()
				.map(s -> {
					long verified = s.getUserRoles().stream()
							.filter(r -> r.getVerifiedByUserId() != null || r.getRemarks() != null)
							.count();
					return new AttestationOverviewDTO(readOnly, s.getItSystemName(), String.valueOf(s.getItSystemId()),
							verified,  s.getUserRoles().size() - verified, s.getUserRoles().size(), s.getDeadLine(), s.getDeadLine().isBefore(now), false, null, 0, 0, 0);
				})
				.collect(Collectors.toList());
	}

}
