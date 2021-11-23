package dk.digitalidentity.rc.controller.mvc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationConfirmDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationConfirmPersonalListDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationConfirmShowDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationConfirmUnitListDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationOrgUnit;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationRolesDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentConstraintValueDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerOrManagerRole;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import lombok.extern.log4j.Log4j;

@Controller
@RequireAssignerOrManagerRole
@Log4j
public class AttestationController {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private TitleService titleService;

	@Autowired
	private SettingsService settingService;
    
	@Autowired
	private ItSystemService itSystemService;

	@RequireAdministratorRole
	@GetMapping("/ui/admin/attestations")
	public String getAttestationsForAdmin(Model model) {
		model.addAttribute("ous", orgUnitService.getAll().stream().map(ou -> new AttestationOrgUnit(ou, false)).collect(Collectors.toList()));
		model.addAttribute("page", "admin.attestation");

		return "manager/list_attestations";
	}

	@RequireManagerRole
	@GetMapping("/ui/users/attestations")
	public String getAttestations(Model model) {
		int daysBefore = settingService.getDaysBeforeDeadline();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, daysBefore);
		Date tts = cal.getTime();
		
		model.addAttribute("ous", orgUnitService.getByManager().stream().map(ou -> new AttestationOrgUnit(ou, canAttest(ou, tts))).collect(Collectors.toList()));
		model.addAttribute("page", "attestation");

		return "manager/list_attestations";
	}
	
	private boolean canAttest(OrgUnit ou, Date tts) {
		if (ou.getNextAttestation() == null) {
			return false;
		}
		else if (ou.getNextAttestation().before(tts)) {
			return true;
		}
		
		return false;
	}

	@RequireManagerRole
	@GetMapping("/ui/users/attestations/{uuid}")
	public String getAttestationsConfirment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + uuid);

			return "redirect:/ui/users/attestations";
		}

		List<AttestationRolesDTO> personal = new ArrayList<AttestationRolesDTO>();
		List<AttestationRolesDTO> unit = new ArrayList<AttestationRolesDTO>();

		List<OrgUnitUserRoleAssignment> ouuras = orgUnit.getUserRoleAssignments();
		List<OrgUnitRoleGroupAssignment> ourgas = orgUnit.getRoleGroupAssignments();

		// adding the userRoles from this OU
		for (OrgUnitUserRoleAssignment ouura : ouuras) {
			UserRoleAssignedToUser uratu  = new UserRoleAssignedToUser();
			uratu.setAssignedThrough(AssignedThrough.ORGUNIT);
			uratu.setUserRole(ouura.getUserRole());
			uratu.setOrgUnit(orgUnit);

			AttestationRolesDTO dto = new AttestationRolesDTO();
			dto.setRoleType("Jobfunktionsrolle");
			dto.setRoleAssignedToUser(uratu);

			if (ouura.isContainsExceptedUsers()) {
				dto.setExceptedUsers(ouura.getExceptedUsers().stream().map(user -> user.getName() + " (" + user.getUserId() + ")").collect(Collectors.toList()));
			}

			unit.add(dto);
		}
		
		// adding the roleGroups for this OU
		for (OrgUnitRoleGroupAssignment ourga : ourgas) {
			RoleGroupAssignedToUser uratu  = new RoleGroupAssignedToUser();
			uratu.setAssignedThrough(AssignedThrough.ORGUNIT);
			uratu.setRoleGroup(ourga.getRoleGroup());
			uratu.setOrgUnit(orgUnit);

			AttestationRolesDTO dto = new AttestationRolesDTO();
			dto.setRoleType("Rollebuket");
			dto.setRoleAssignedToUser(uratu);

			if (ourga.isContainsExceptedUsers()) {
				dto.setExceptedUsers(ourga.getExceptedUsers().stream().map(user -> user.getName() + " (" + user.getUserId() + ")").collect(Collectors.toList()));
			}

			unit.add(dto);
		}
		
		// getting from titles
		List<OrgUnitUserRoleAssignment> userRoleAssignments = orgUnit.getUserRoleAssignments().stream().filter(ura -> ura.isContainsTitles()).collect(Collectors.toList());
		for (OrgUnitUserRoleAssignment oura : userRoleAssignments) {
			for (Title t : oura.getTitles()) {
				UserRoleAssignedToUser uratu  = new UserRoleAssignedToUser();
				uratu.setAssignedThrough(AssignedThrough.TITLE);
				uratu.setUserRole(oura.getUserRole());
				uratu.setTitle(t);

				AttestationRolesDTO dto = new AttestationRolesDTO();
				dto.setRoleType("Jobfunktionsrolle");
				dto.setRoleAssignedToUser(uratu);
				
				unit.add(dto);
			}
		}
		
		List<OrgUnitRoleGroupAssignment> roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().filter(rga -> rga.isContainsTitles()).collect(Collectors.toList());
		for (OrgUnitRoleGroupAssignment trga : roleGroupAssignments) {
			for (Title t : trga.getTitles()) {
				RoleGroupAssignedToUser uratu  = new RoleGroupAssignedToUser();
				uratu.setAssignedThrough(AssignedThrough.TITLE);
				uratu.setRoleGroup(trga.getRoleGroup());
				uratu.setTitle(t);
	
				AttestationRolesDTO dto = new AttestationRolesDTO();
				dto.setRoleType("Rollebuket");
				dto.setRoleAssignedToUser(uratu);
	
				unit.add(dto);
			}
		}
		
		List<User> users = userService.findByOrgUnit(orgUnit);

		for (User user : users) {
			List<UserRoleAssignedToUser> allRolesForUser = userService.getAllUserRolesAssignedToUserExemptingRoleGroups(user, null);
			List<RoleGroupAssignedToUser> allRoleGroupsForUser = userService.getAllRoleGroupsAssignedToUser(user);

			for (UserRoleAssignedToUser uratu : allRolesForUser) {
				AttestationRolesDTO dto = new AttestationRolesDTO();
				dto.setRoleAssignedToUser(uratu);
				dto.setRoleType("Jobfunktionsrolle");
				dto.setAssignmentId(uratu.getAssignmentId());
				dto.setFromPosition(uratu.isFromPosition());
				
				if (uratu.getUserRole().getItSystem().isReadonly()) {
					dto.setDisabled(true);
				}

				switch (uratu.getAssignedThrough()) {
					case DIRECT:
						dto.setUser(user);
						
						if (uratu.getAssignmentId() != null) {
							dto.setAssignmentId(uratu.getAssignmentId());
							UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == uratu.getAssignmentId()).findAny().orElse(null);

							if (userUserRoleAssignment != null) {
								List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();
								UserRole role = userUserRoleAssignment.getUserRole();

								if (role.isAllowPostponing()) {
									for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
										List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();

										for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
											if (constraintValue.isPostponed()) {
												SystemRoleAssignmentConstraintValueDTO valueDto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);
												PostponedConstraint postponedConstraint = userUserRoleAssignment.getPostponedConstraints().stream().filter(p -> p.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId() && p.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid())).findAny().orElse(null);

												if (postponedConstraint != null) {
													if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.REGEX)) {
														if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
															String[] uuids = postponedConstraint.getValue().split(",");
															String ouString = "";

															for (String ouUuid : uuids) {
																OrgUnit ou = orgUnitService.getByUuid(ouUuid);
																if (ou != null) {
																	ouString += ou.getName() + ", ";
																}
															}
															
															valueDto.setConstraintValue(ouString.substring(0, ouString.length()-2));
														}
														else if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
															String[] ids = postponedConstraint.getValue().split(",");
															String itSystemsString = "";

															for (String id : ids) {
																ItSystem itSystem = itSystemService.getById(Integer.parseInt(id));
																if (itSystem != null) {
																	itSystemsString += itSystem.getName() + ", ";
																}
															}
															
															valueDto.setConstraintValue(itSystemsString.substring(0, itSystemsString.length()-2));
														}
														else {
															valueDto.setConstraintValue(postponedConstraint.getValue());
														}
													}
													else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_SINGLE)) {
														ConstraintTypeValueSet valueSet = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> v.getConstraintKey().equals(postponedConstraint.getValue())).findAny().orElse(null);
														valueDto.setConstraintValue(valueSet == null ? "" : valueSet.getConstraintValue());
													}
													else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_MULTI)) {
														String[] keysArr = postponedConstraint.getValue().split(",");
														List<String> keys = Arrays.asList(keysArr);
														List<ConstraintTypeValueSet> valueSets = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> keys.contains(v.getConstraintKey())).collect(Collectors.toList());
														String valuesString = "";

														for (ConstraintTypeValueSet valueSet : valueSets) {
															valuesString += valueSet.getConstraintValue() + ", ";
														}
														
														valueDto.setConstraintValue(valuesString.substring(0, valuesString.length()-2));
													}
												}
												
												postponedConstraintValues.add(valueDto);
											}
										}
										
										if (!postponedConstraintValues.isEmpty()) {
											SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
											systemRoleAssignmentDTO.setSystemRole(systemRoleAssignment.getSystemRole());
											systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);
											
											systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
										}
									}
								}

								dto.setSystemRoleAssignmentsDTOs(systemRoleAssignmentsDTOs);
							}
						}
						
						personal.add(dto);
						break;
					case POSITION:
						dto.setUser(user);
						personal.add(dto);
						break;
					default:
						break;
				}
			}
			
			for (RoleGroupAssignedToUser uratu : allRoleGroupsForUser) {
				AttestationRolesDTO dto = new AttestationRolesDTO();
				dto.setRoleAssignedToUser(uratu);
				dto.setRoleType("Rollebuket");
				dto.setAssignmentId(uratu.getAssignmentId());
				dto.setFromPosition(uratu.isFromPosition());

				switch (uratu.getAssignedThrough()) {
					case DIRECT:
					case POSITION:
					case ROLEGROUP:
						dto.setUser(user);
						personal.add(dto);
						break;
					default:
						break;
				}
			}
		}

		AttestationConfirmDTO confirmDTO = new AttestationConfirmDTO();
		confirmDTO.setOrgUnitUuid(orgUnit.getUuid());

		model.addAttribute("personal", personal);
		model.addAttribute("unit", unit);
		model.addAttribute("orgUnit", orgUnit);
		model.addAttribute("confirmDTO", confirmDTO);

		return "manager/attestations_walkthrough";
	}

	@RequireManagerRole
	@PostMapping("/ui/users/attestations/confirm")
	public String postNew(Model model, @ModelAttribute("confirmDTO") AttestationConfirmDTO confirmDTO, BindingResult bindingResult, HttpServletRequest httpServletRequest) throws JsonParseException, JsonMappingException, IOException {
		if (bindingResult.hasErrors()) {
			return "redirect:/ui/users/attestations/" + confirmDTO.getOrgUnitUuid();
		}

		List<AttestationConfirmPersonalListDTO> aprovedPersonal = jsonDesializePersonal(confirmDTO.getAprovedPersonal());
		List<AttestationConfirmPersonalListDTO> toBeRemoved = jsonDesializePersonal(confirmDTO.getToBeRemoved());
		List<AttestationConfirmUnitListDTO> aprovedUnit = jsonDesializeUnit(confirmDTO.getAprovedUnit());
		List<AttestationConfirmUnitListDTO> toEmail = jsonDesializeUnit(confirmDTO.getToEmail());

		List<AttestationConfirmShowDTO> AttestationRolesDTOsAprovedPersonal = new ArrayList<AttestationConfirmShowDTO>();
		List<AttestationConfirmShowDTO> AttestationRolesDTOsAprovedUnit = new ArrayList<AttestationConfirmShowDTO>();
		List<AttestationConfirmShowDTO> AttestationRolesDTOsToBeRemoved = new ArrayList<AttestationConfirmShowDTO>();
		List<AttestationConfirmShowDTO> AttestationRolesDTOsToEmail = new ArrayList<AttestationConfirmShowDTO>();
		
		// deal with personal roles to be removed
		for (AttestationConfirmPersonalListDTO tbr : toBeRemoved) {
			User user = userService.getByUuid(tbr.getUserUuid());
			if (user == null) {
				log.warn("Unknown user: " + tbr.getUserUuid());
				continue;
			}
			
			AttestationConfirmShowDTO dto = new AttestationConfirmShowDTO();
			dto.setUserOrUnitName(user.getName());
			dto.setAssignmentId(tbr.getAssignmentId());
			dto.setFromPosition(tbr.isFromPosition());
			
			String roleType = tbr.getRoleType();
			if (roleType.equals("Jobfunktionsrolle")) {
				UserRole ur = userRoleService.getById(tbr.getRoleId());
				if (ur == null) {
					log.warn("Unknown UserRole: " + tbr.getRoleId());
					continue;
				}

				dto.setRoleName(ur.getName());
				dto.setItSystemName(ur.getItSystem().getName());
				tbr.setItSystemName(dto.getItSystemName());

				if (tbr.getAssignedThrough().equals(AssignedThrough.DIRECT)) {
					// handle postponed constraints
					if (tbr.getAssignmentId() != null) {
						UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == tbr.getAssignmentId()).findAny().orElse(null);

						if (userUserRoleAssignment != null) {
							List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();
							UserRole role = userUserRoleAssignment.getUserRole();
							
							if (role.isAllowPostponing()) {
								for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
									List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();

									for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
										if (constraintValue.isPostponed()) {
											SystemRoleAssignmentConstraintValueDTO valueDto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);
											PostponedConstraint postponedConstraint = userUserRoleAssignment.getPostponedConstraints().stream().filter(p -> p.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId() && p.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid())).findAny().orElse(null);

											if (postponedConstraint != null) {
												if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.REGEX)) {
													if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
														String[] uuids = postponedConstraint.getValue().split(",");
														String ouString = "";

														for (String ouUuid : uuids) {
															OrgUnit ou = orgUnitService.getByUuid(ouUuid);
															if (ou != null) {
																ouString += ou.getName() + ", ";
															}
														}
														
														valueDto.setConstraintValue(ouString.substring(0, ouString.length()-2));
													}
													else if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
														String[] ids = postponedConstraint.getValue().split(",");
														String itSystemsString = "";

														for (String id : ids) {
															ItSystem itSystem = itSystemService.getById(Integer.parseInt(id));
															if (itSystem != null) {
																itSystemsString += itSystem.getName() + ", ";
															}
														}
														
														valueDto.setConstraintValue(itSystemsString.substring(0, itSystemsString.length()-2));
													}
													else {
														valueDto.setConstraintValue(postponedConstraint.getValue());
													}
												}
												else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_SINGLE)) {
													ConstraintTypeValueSet valueSet = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> v.getConstraintKey().equals(postponedConstraint.getValue())).findAny().orElse(null);
													valueDto.setConstraintValue(valueSet == null ? "" : valueSet.getConstraintValue());
												}
												else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_MULTI)) {
													String[] keysArr = postponedConstraint.getValue().split(",");
													List<String> keys = Arrays.asList(keysArr);
													List<ConstraintTypeValueSet> valueSets = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> keys.contains(v.getConstraintKey())).collect(Collectors.toList());
													String valuesString = "";

													for (ConstraintTypeValueSet valueSet : valueSets) {
														valuesString += valueSet.getConstraintValue() + ", ";
													}
													
													valueDto.setConstraintValue(valuesString.substring(0, valuesString.length()-2));
												}
											}

											postponedConstraintValues.add(valueDto);
										}
									}
									
									if (!postponedConstraintValues.isEmpty()) {
										SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
										systemRoleAssignmentDTO.setSystemRoleName(systemRoleAssignment.getSystemRole().getName());
										systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);
										
										systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
									}
								}
							}

							dto.setSystemRoleAssignmentsDTOs(systemRoleAssignmentsDTOs);
						}
					}
				}
			}
			else if (roleType.equals("Rollebuket")) {
				RoleGroup rg = roleGroupService.getById(tbr.getRoleId());
				if (rg == null) {
					log.warn("Unknown RoleGroup: " + tbr.getRoleId());
					continue;
				}

				dto.setRoleName(rg.getName());
				dto.setRoleGroup(true);
			}
			else {
				log.warn("Unknown roleType: " + roleType);
				continue;
			}
			
			AttestationRolesDTOsToBeRemoved.add(dto);
		}
		
		// deal with orgUnit/title roles to be removed (or requested removed)
		for (AttestationConfirmUnitListDTO tE : toEmail) {
			AttestationConfirmShowDTO dto = new AttestationConfirmShowDTO();
			String uuid = tE.getUnitTitleUuid();

			String roleType = tE.getRoleType();
			if (roleType.equals("Jobfunktionsrolle")) {
				UserRole ur = userRoleService.getById(tE.getRoleId());
				if (ur == null) {
					log.warn("Unknown UserRole: " + tE.getRoleId());
					continue;
				}

				dto.setItSystemName(ur.getItSystem().getName());
				dto.setRoleName(ur.getName());
				tE.setItSystemName(dto.getItSystemName());
			}
			else if (roleType.equals("Rollebuket")) {
				RoleGroup rg = roleGroupService.getById(tE.getRoleId());
				if (rg == null) {
					log.warn("Unknown RoleGroup" + tE.getRoleId());
					continue;
				}

				dto.setRoleName(rg.getName());
				dto.setRoleGroup(true);
			}
			else {
				log.warn("Unknown roleType: " + roleType);
				continue;
			}

			if (tE.getAssignedThrough().equals(AssignedThrough.ORGUNIT)) {				
				OrgUnit ou = orgUnitService.getByUuid(uuid);
				if (ou == null) {
					log.warn("Unknown OrgUnit: " + uuid);
					continue;
				}

				// If assigned through a OU, check for excepted users and add to pdf
				if (roleType.equals("Jobfunktionsrolle")) {

					// Go through ou UserRoleAssignments, skip any that does not have excepted users and find the one with a matching roleId
					for (OrgUnitUserRoleAssignment roleAssignment : ou.getUserRoleAssignments()) {
						if (roleAssignment.isContainsExceptedUsers() && Objects.equals(roleAssignment.getUserRole().getId(), tE.getRoleId())) {
							// Found correct role and it has excepted users
							dto.setExceptedUsers(roleAssignment.getExceptedUsers()
									.stream()
									.map(user -> user.getName() + " (" + user.getUserId() + ")")
									.collect(Collectors.toList()));
						}
					}
				}
				else if (roleType.equals("Rollebuket")) {
					// Go through ou RoleGroupAssignments, skip any that does not have excepted users and find the one with a matching roleId
					for (OrgUnitRoleGroupAssignment roleGroupAssignment : ou.getRoleGroupAssignments()) {
						if (roleGroupAssignment.isContainsExceptedUsers() && Objects.equals(roleGroupAssignment.getRoleGroup().getId(), tE.getRoleId())) {
							// Found correct role and it has excepted users
							dto.setExceptedUsers(roleGroupAssignment.getExceptedUsers()
									.stream()
									.map(user -> user.getName() + " (" + user.getUserId() + ")")
									.collect(Collectors.toList()));
						}
					}
				}

				dto.setUserOrUnitName(ou.getName());
			}
			else if (tE.getAssignedThrough().equals(AssignedThrough.TITLE)) {
				Title t = titleService.getByUuid(uuid);
				if (t == null) {
					log.warn("Unknown title: " + uuid);
					continue;
				}

				dto.setUserOrUnitName(t.getName());
				dto.setTitle(true);
			}
			else {
				log.warn("Unknown assignedThrough: " + tE.getAssignedThrough().toString());
				continue;
			}

			AttestationRolesDTOsToEmail.add(dto);
		}

		// deal with personal roles approved
		for (AttestationConfirmPersonalListDTO ap : aprovedPersonal) {
			User user = userService.getByUuid(ap.getUserUuid());
			if (user == null) {
				log.warn("Unknown user: " + ap.getUserUuid());
				continue;
			}

			AttestationConfirmShowDTO dto = new AttestationConfirmShowDTO();
			dto.setUserOrUnitName(user.getName());
			dto.setAssignmentId(ap.getAssignmentId());
			dto.setFromPosition(ap.isFromPosition());

			String roleType = ap.getRoleType();
			if (roleType.equals("Jobfunktionsrolle")) {
				UserRole ur = userRoleService.getById(ap.getRoleId());
				if (ur == null) {
					log.warn("Unknown userRole: " + ap.getRoleId());
					continue;
				}

				dto.setItSystemName(ur.getItSystem().getName());
				dto.setRoleName(ur.getName());
				ap.setItSystemName(dto.getItSystemName());
				
				if (ap.getAssignedThrough().equals(AssignedThrough.DIRECT)) {
					if (ap.getAssignmentId() != null) {
						UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == ap.getAssignmentId()).findAny().orElse(null);

						if (userUserRoleAssignment != null) {
							List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();
							UserRole role = userUserRoleAssignment.getUserRole();

							if (role.isAllowPostponing()) {
								for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
									List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();

									for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
										if (constraintValue.isPostponed()) {
											SystemRoleAssignmentConstraintValueDTO valueDto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);
											PostponedConstraint postponedConstraint = userUserRoleAssignment.getPostponedConstraints().stream().filter(p -> p.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId() && p.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid())).findAny().orElse(null);

											if (postponedConstraint != null) {
												if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.REGEX)) {
													if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
														String[] uuids = postponedConstraint.getValue().split(",");
														String ouString = "";

														for (String ouUuid : uuids) {
															OrgUnit ou = orgUnitService.getByUuid(ouUuid);
															if (ou != null) {
																ouString += ou.getName() + ", ";
															}
														}
														
														valueDto.setConstraintValue(ouString.substring(0, ouString.length()-2));
													}
													else if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
														String[] ids = postponedConstraint.getValue().split(",");
														String itSystemsString = "";

														for (String id : ids) {
															ItSystem itSystem = itSystemService.getById(Integer.parseInt(id));
															if (itSystem != null) {
																itSystemsString += itSystem.getName() + ", ";
															}
														}
														
														valueDto.setConstraintValue(itSystemsString.substring(0, itSystemsString.length()-2));
													}
													else {
														valueDto.setConstraintValue(postponedConstraint.getValue());
													}
												}
												else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_SINGLE)) {
													ConstraintTypeValueSet valueSet = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> v.getConstraintKey().equals(postponedConstraint.getValue())).findAny().orElse(null);
													valueDto.setConstraintValue(valueSet == null ? "" : valueSet.getConstraintValue());
												}
												else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_MULTI)) {
													String[] keysArr = postponedConstraint.getValue().split(",");
													List<String> keys = Arrays.asList(keysArr);
													List<ConstraintTypeValueSet> valueSets = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> keys.contains(v.getConstraintKey())).collect(Collectors.toList());
													String valuesString = "";

													for (ConstraintTypeValueSet valueSet : valueSets) {
														valuesString += valueSet.getConstraintValue() + ", ";
													}
													
													valueDto.setConstraintValue(valuesString.substring(0, valuesString.length()-2));
												}
											}
											postponedConstraintValues.add(valueDto);
										}
									}
									
									if (!postponedConstraintValues.isEmpty()) {
										SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
										systemRoleAssignmentDTO.setSystemRoleName(systemRoleAssignment.getSystemRole().getName());
										systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);
										
										systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
									}
								}
							}

							dto.setSystemRoleAssignmentsDTOs(systemRoleAssignmentsDTOs);
						}
					}
				}
			}
			else if (roleType.equals("Rollebuket")) {
				RoleGroup rg = roleGroupService.getById(ap.getRoleId());
				if (rg == null) {
					log.warn("Unknown RoleGroup: " + ap.getRoleId());
					continue;
				}

				dto.setRoleName(rg.getName());
				dto.setRoleGroup(true);
			}
			else {
				log.warn("Unknown roleType: " + roleType);
				continue;
			}
			
			AttestationRolesDTOsAprovedPersonal.add(dto);
		}
		
		// deal with orgUnit/title approved roles
		for (AttestationConfirmUnitListDTO aU : aprovedUnit) {
			AttestationConfirmShowDTO dto = new AttestationConfirmShowDTO();
			String uuid = aU.getUnitTitleUuid();

			String roleType = aU.getRoleType();
			if (roleType.equals("Jobfunktionsrolle")) {
				UserRole ur = userRoleService.getById(aU.getRoleId());
				if (ur == null) {
					log.warn("Unknown UserRole: " + aU.getRoleId());
					continue;
				}

				dto.setItSystemName(ur.getItSystem().getName());
				dto.setRoleName(ur.getName());
				aU.setItSystemName(dto.getItSystemName());
			}
			else if (roleType.equals("Rollebuket")) {
				RoleGroup rg = roleGroupService.getById(aU.getRoleId());
				if (rg == null) {
					log.warn("Unknown RoleGroup: " + aU.getRoleId());
					continue;
				}

				dto.setRoleName(rg.getName());
				dto.setRoleGroup(true);
			}
			else {
				log.warn("Unknown roleType: " + roleType);
				continue;
			}

			if (aU.getAssignedThrough().equals(AssignedThrough.ORGUNIT)) {
				OrgUnit ou = orgUnitService.getByUuid(uuid);
				if (ou == null) {
					log.warn("Unknown OU: " + uuid);
					continue;
				}

				dto.setUserOrUnitName(ou.getName());
			}
			else if (aU.getAssignedThrough().equals(AssignedThrough.TITLE)) {
				Title t = titleService.getByUuid(uuid);
				if (t == null) {
					log.warn("Unknown title: " + uuid);
					continue;
				}
				
				dto.setUserOrUnitName(t.getName());
				dto.setTitle(true);
			}
			else {
				log.warn("Unknown assignedThrough: " + aU.getAssignedThrough().toString());
				continue;
			}

			AttestationRolesDTOsAprovedUnit.add(dto);
		}

		model.addAttribute("dtoToEmail", AttestationRolesDTOsToEmail);
		model.addAttribute("dtoToBeRemoved", AttestationRolesDTOsToBeRemoved);
		model.addAttribute("dtoAprovedPersonal", AttestationRolesDTOsAprovedPersonal);
		model.addAttribute("dtoAprovedUnit", AttestationRolesDTOsAprovedUnit);
		model.addAttribute("toEmail", toEmail);
		model.addAttribute("toBeRemoved", toBeRemoved);
		model.addAttribute("aprovedPersonal", aprovedPersonal);
		model.addAttribute("aprovedUnit", aprovedUnit);
		model.addAttribute("orgUnitUuid", confirmDTO.getOrgUnitUuid());

		return "manager/attestations_ou_confirm";
	}

	private List<AttestationConfirmPersonalListDTO> jsonDesializePersonal(String json) throws IOException, JsonParseException, JsonMappingException {
		var mapper = new ObjectMapper();
		var mapCollectionType = mapper.getTypeFactory().constructCollectionType(List.class, AttestationConfirmPersonalListDTO.class);

		return mapper.readValue(json, mapCollectionType);
	}

	private List<AttestationConfirmUnitListDTO> jsonDesializeUnit(String json) throws IOException, JsonParseException, JsonMappingException {
		var mapper = new ObjectMapper();
		var mapCollectionType = mapper.getTypeFactory().constructCollectionType(List.class, AttestationConfirmUnitListDTO.class);

		return mapper.readValue(json, mapCollectionType);
	}
}
