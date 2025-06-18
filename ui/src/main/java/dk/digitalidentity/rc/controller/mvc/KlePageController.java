package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUTreeViewModel;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserListForm;
import dk.digitalidentity.rc.dao.KLEMappingDao;
import dk.digitalidentity.rc.dao.UserKLEMappingDao;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.KleAssignment;
import dk.digitalidentity.rc.util.StreamExtensions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireReadAccessRole
@Controller
public class KlePageController {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private KleService kleService;

	@Autowired
	private KLEMappingDao kleMappingDao;

	@Autowired
	private UserKLEMappingDao userKLEMappingDao;

	@Autowired
	private UserService userService;

	@Autowired
	private MessageSource messageSource;
	
	@Value("#{servletContext.contextPath}")
	private String servletContextPath;

	@Autowired
	private AccessConstraintService accessConstraintService;

	@GetMapping(value = "/ui/kle/ou")
	public String listOUWithKleMapping(Model model) {
		List<OUTreeViewModel> orgUnitDTOs = new ArrayList<>();

		for (OrgUnit orgUnit : orgUnitService.getAll()) {
			OUTreeViewModel dto = new OUTreeViewModel();
			dto.setText(
					orgUnit.getName()+
					"<em class=\"pull-right fa fa-fw fa-" + (kleService.getKleAssignments(orgUnit, KleType.INTEREST, true).size() > 0 ? "check-square-o" : "square-o") + "\"></em>" +
					"<em class=\"pull-right fa fa-fw fa-" + (kleService.getKleAssignments(orgUnit, KleType.PERFORMING, true).size() > 0 ? "check-square-o" : "square-o") + "\"></em>" 
					);
			dto.setId(orgUnit.getUuid());
			dto.setParent(orgUnit.getParent() != null ? orgUnit.getParent().getUuid() : "#");

			orgUnitDTOs.add(dto);
		}

		model.addAttribute("orgUnits", orgUnitDTOs);

		return "kle/ou-kle";
	}

	@GetMapping(value = "/ui/kle/mapped")
	public String listMappedKle(Model model) {
		Set<KleAssignment> mappedKle = new HashSet<>();

		for (OrgUnit orgUnit : orgUnitService.getAll()) {
			List<KleAssignment> kles = kleService.getKleAssignments(orgUnit, KleType.PERFORMING, false);

			mappedKle.addAll(kles);
		}

		model.addAttribute("kleMainGroupsList", kleService.getKleMainGroupList());
		model.addAttribute("klePerforming", mappedKle);

		return "kle/kle-mapped";
	}

	@GetMapping(value = "/ui/kle/assignment")
	public String listKleAssignment(Model model) {
		List<Kle> kles = kleService.findAll();

		List<KleDTO> kleDTOs = new ArrayList<>();
		for (Kle kle : kles) {
			KleDTO kleDTO = new KleDTO();
			kleDTO.setId(kle.getCode());
			kleDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());
			kleDTO.setText(kle.isActive() ? kle.getCode() + " " + kle.getName() : kle.getCode() + " " + kle.getName() + " [UDGÅET]");
			kleDTOs.add(kleDTO);
		}
		kleDTOs.sort(Comparator.comparing(KleDTO::getText));
		model.addAttribute("allKles", kleDTOs);

		return "kle/kle-assignment";
	}

	@GetMapping(value = "/ui/kle/{code}")
	public String viewKleAssignment(Model model, @PathVariable("code") String code, Locale locale) {
		Kle kle = kleService.getByCode(code);
		if (kle == null) {
			log.warn("Requested KLE number " + code + " doesn't exists.");

			return "redirect:/ui/kle/assignment";
		}

		// get the directly assigned kles to orgunits
		List<KLEMapping> mappings = kleMappingDao.findByCodeAndOrgUnitActiveTrue(code);

		// if we are looking at a xx.xx.xx KLE, also find those with access to xx.xx
		if (code.length() > 5) {
			mappings.addAll(kleMappingDao.findByCodeAndOrgUnitActiveTrue(code.substring(0, 5)));
		}
		
		// if we are looking at a xx.xx KLE, also find those with access to xx
		if (code.length() > 2) {
			mappings.addAll(kleMappingDao.findByCodeAndOrgUnitActiveTrue(code.substring(0, 2)));
		}

		// map to actual OrgUnits
		List<OrgUnit> allOrgUnits = mappings.stream().map(KLEMapping::getOrgUnit).collect(Collectors.toList());

		// for this OrgUnits that has inheritance flagged, also add all children
		for (OrgUnit orgUnit : new ArrayList<>(allOrgUnits)) {
			if (orgUnit.isInheritKle()) {
				List<OrgUnit> children = getAllChildren(orgUnit);
				allOrgUnits.addAll(children);
			}
		}

		// remove orgUnit duplicates
		allOrgUnits = allOrgUnits.stream().filter(StreamExtensions.distinctByKey(OrgUnit::getUuid)).collect(Collectors.toList());
		
		// get users from selected orgUnits
		List<User> users = allOrgUnits.stream().flatMap(ou -> userService.findByOrgUnit(ou).stream()).collect(Collectors.toList());

		// append users that have that KLE assigned directly
		
		List<UserKLEMapping> userMappings = userKLEMappingDao.findByCodeAndUserDeletedFalse(code);
		
		// if we are looking at a xx.xx.xx KLE, also find those with access to xx.xx
		if (code.length() > 5) {
			userMappings.addAll(userKLEMappingDao.findByCodeAndUserDeletedFalse(code.substring(0, 5)));
		}

		// if we are looking at a xx.xx KLE, also find those with access to xx
		if (code.length() > 2) {
			userMappings.addAll(userKLEMappingDao.findByCodeAndUserDeletedFalse(code.substring(0, 2)));
		}
		
		users.addAll(userMappings.stream().map(UserKLEMapping::getUser).collect(Collectors.toList()));

		// remove use duplicates
		users = users.stream().filter(StreamExtensions.distinctByKey(User::getUuid)).collect(Collectors.toList());

		String in = messageSource.getMessage("html.word.in", null, locale);

		users = accessConstraintService.filterUsersUserCanAccess(users, false);

		List<UserListForm> usersDTO = users.stream().map(u -> new UserListForm(u, servletContextPath, in)).collect(Collectors.toList());

		model.addAttribute("users", usersDTO);
		model.addAttribute("code", kle.getCode());
		model.addAttribute("name", kle.getName());
		model.addAttribute("orgUnits", allOrgUnits);

		return "kle/view";
	}

	private List<OrgUnit> getAllChildren(OrgUnit orgUnit) {
		List<OrgUnit> children = new ArrayList<OrgUnit>();

		for (OrgUnit unit : orgUnit.getChildren()) {
			if (orgUnitService.isActiveAndIncluded(unit)) {
				children.add(unit);
				children.addAll(getAllChildren(unit));
			}
		}

		return children;
	}
}
