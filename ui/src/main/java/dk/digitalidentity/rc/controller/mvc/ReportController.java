package dk.digitalidentity.rc.controller.mvc;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ItSystemChoice;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserWithDuplicateRoleAssignmentDTO;
import dk.digitalidentity.rc.controller.mvc.xlsview.ReportXlsxView;
import dk.digitalidentity.rc.controller.mvc.xlsview.XlsView;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ReportType;
import dk.digitalidentity.rc.security.RequireReportAccessRole;
import dk.digitalidentity.rc.security.RequireTemplateAccessOrReportAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.HistoryService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.ReportService;
import dk.digitalidentity.rc.service.ReportTemplateService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RoleGroupAssignmentWithInfo;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentWithInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireReportAccessRole
@Controller
public class ReportController {

	@Autowired
	private ReportService reportService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private ReportTemplateService reportTemplateService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private UserService userService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private PositionService positionService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private MessageSource messageSource;

	@RequireTemplateAccessOrReportAccessRole
	@GetMapping("/ui/report/templates")
	public String templatesReport(Model model) {
		List<ReportTemplate> templates = new ArrayList<>();
		
		if (SecurityUtil.hasRole(Constants.ROLE_REPORT_ACCESS)) {
			templates = reportTemplateService.getAll();
		}
		else if (SecurityUtil.hasRole(Constants.ROLE_TEMPLATE_ACCESS)) {
			User currentUser = userService.getByUserId(SecurityUtil.getUserId());
			if (currentUser != null) {
				templates = reportTemplateService.getByUser(currentUser);
			}
		}

		model.addAttribute("reportTemplates", templates);

		return "reports/templates/list";
	}

	@GetMapping("/ui/report/assign/{id}")
	public String assignToUser(Model model, @PathVariable("id") Long templateId) {
		model.addAttribute("templateId", templateId);

		return "reports/assign";
	}

	@GetMapping("/ui/report/configure")
	public String configureReport(Model model) {
		LocalDate now = LocalDate.now();

		// default column choices
		ReportForm reportForm = new ReportForm();
		reportForm.setShowUsers(true);
		reportForm.setShowOUs(false);
		reportForm.setShowUserRoles(true);
		reportForm.setShowNegativeRoles(false);
		reportForm.setShowKLE(true);
		reportForm.setShowItSystems(true);
		reportForm.setShowInactiveUsers(false);
		reportForm.setShowSystemRoles(false);

		model.addAttribute("reportForm", reportForm);
		model.addAttribute("allItSystems", parseItSystems(now));
		model.addAttribute("allOrgUnits", parseOuTree(now));
		model.addAttribute("allManagers", historyService.getManagers(now));
		model.addAttribute("templateId", 0);

		return "reports/configure";
	}

	@GetMapping("/ui/report/configure/template/{id}")
	public String getFilterOptions(Model model, @PathVariable("id") Long id) {
		LocalDate now = LocalDate.now();
		ReportForm reportForm = new ReportForm();

		ReportTemplate reportTemplate = reportTemplateService.getById(id);
		if (reportTemplate != null) {
			reportForm.setShowUsers(reportTemplate.isShowUsers());
			reportForm.setShowOUs(reportTemplate.isShowOUs());
			reportForm.setShowUserRoles(reportTemplate.isShowUserRoles());
			reportForm.setShowNegativeRoles(reportTemplate.isShowNegativeRoles());
			reportForm.setShowKLE(reportTemplate.isShowKLE());
			reportForm.setShowItSystems(reportTemplate.isShowItSystems());
			reportForm.setShowInactiveUsers(reportTemplate.isShowInactiveUsers());
			reportForm.setManagerFilter(reportTemplate.getManagerFilter());
			reportForm.setUnitFilter((reportTemplate.getUnitFilter() != null) ? reportTemplate.getUnitFilter().split(",") : null);
			reportForm.setShowSystemRoles(reportTemplate.isShowSystemRoles());

			if (reportTemplate.getItsystemFilter() != null) {
				String[] stringIds = reportTemplate.getItsystemFilter().split(",");
				long ids[] = new long[stringIds.length];
				
				for (int i = 0; i < ids.length; i++) {
					ids[i] = Long.parseLong(stringIds[i]);
				}
				
				reportForm.setItsystemFilter(ids);
			}
			else {
				reportForm.setItsystemFilter(null);
			}
		}
		else {
			reportForm.setShowUsers(true);
			reportForm.setShowOUs(false);
			reportForm.setShowUserRoles(true);
			reportForm.setShowKLE(true);
			reportForm.setShowItSystems(true);
			reportForm.setShowInactiveUsers(false);
			reportForm.setShowSystemRoles(false);
		}

		model.addAttribute("reportForm", reportForm);
		model.addAttribute("allItSystems", parseItSystems(now));
		model.addAttribute("allOrgUnits", parseOuTree(now));
		model.addAttribute("allManagers", historyService.getManagers(now));
		model.addAttribute("templateId", id);

		return "reports/configure";
	}

	@GetMapping("/ui/report/configure/{date}")
	public String getFilterOptions(Model model, @PathVariable("date") String dateStr, @RequestParam("templateId") long templateId) {
		LocalDate date = null;
		try {
			date = LocalDate.parse(dateStr);
		}
		catch (DateTimeException e) {
			return "reports/fragments/filterOptionsFragment :: filterOptions";
		}
		
		if (LocalDate.now().isBefore(date)) {
			date = LocalDate.now();
		}

		ReportForm reportForm = new ReportForm();
		
		ReportTemplate reportTemplate = reportTemplateService.getById(templateId);
		if (reportTemplate != null) {
			reportForm.setShowUsers(reportTemplate.isShowUsers());
			reportForm.setShowOUs(reportTemplate.isShowOUs());
			reportForm.setShowUserRoles(reportTemplate.isShowUserRoles());
			reportForm.setShowNegativeRoles(reportTemplate.isShowNegativeRoles());
			reportForm.setShowKLE(reportTemplate.isShowKLE());
			reportForm.setShowItSystems(reportTemplate.isShowItSystems());
			reportForm.setShowInactiveUsers(reportTemplate.isShowInactiveUsers());
			reportForm.setManagerFilter(reportTemplate.getManagerFilter());
			reportForm.setUnitFilter((reportTemplate.getUnitFilter() != null) ? reportTemplate.getUnitFilter().split(",") : null);		
	
			if (reportTemplate.getItsystemFilter() != null) {
				String[] stringIds = reportTemplate.getItsystemFilter().split(",");
				long ids[] = new long[stringIds.length];
				
				for (int i = 0; i < ids.length; i++) {
					ids[i] = Long.parseLong(stringIds[i]);
				}
				
				reportForm.setItsystemFilter(ids);
			}
		}
		
		model.addAttribute("reportForm", reportForm);
		model.addAttribute("allItSystems", parseItSystems(date));
		model.addAttribute("allOrgUnits", parseOuTree(date));
		model.addAttribute("allManagers", historyService.getManagers(date));

		return "reports/fragments/filterOptionsFragment :: filterOptions";
	}

	@PostMapping(value = "/ui/report/download")
	public ModelAndView downloadReport(ReportForm reportForm, HttpServletResponse response, Locale loc) {
		Map<String, Object> model = reportService.getReportModel(reportForm, loc);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Rapport.xlsx\"");

		return new ModelAndView(new ReportXlsxView(), model);
	}

	@RequireTemplateAccessOrReportAccessRole
	@GetMapping(value = "/ui/report/download/template/{id}")
	public ModelAndView downloadReportFromTemplate(@PathVariable("id") Long id, HttpServletResponse response, Locale loc) {		
		List<ReportTemplate> templates = null;
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user != null) {
			templates = reportTemplateService.getByUser(user);
		}

		ReportTemplate template = null;
		if (templates != null) {
			Optional<ReportTemplate> oTemplate = templates.stream().filter(t -> t.getId() == id).findFirst();
			if (oTemplate.isPresent()) {
				template = oTemplate.get();
			}
		}
		
		if (template == null) {
			log.warn("No matching template");
			return new ModelAndView("redirect:/ui/report/templates");
		}

		ReportForm reportForm = new ReportForm();
		reportForm.setDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		reportForm.setItSystems(template.getItsystemFilter() != null ? Stream.of(template.getItsystemFilter().split(",")).map(Long::parseLong).collect(Collectors.toList()) : null);
		reportForm.setOrgUnits(template.getUnitFilter() != null ? (Stream.of(template.getUnitFilter().split(",")).collect(Collectors.toList())) : null);
		reportForm.setManager(template.getManagerFilter());
		reportForm.setShowInactiveUsers(template.isShowInactiveUsers());
		reportForm.setShowItSystems(template.isShowItSystems());
		reportForm.setShowKLE(template.isShowKLE());
		reportForm.setShowOUs(template.isShowOUs());
		reportForm.setShowUserRoles(template.isShowUserRoles());
		reportForm.setShowNegativeRoles(template.isShowNegativeRoles());
		reportForm.setShowUsers(template.isShowUsers());
		reportForm.setName(template.getName());

		Map<String, Object> model = reportService.getReportModel(reportForm, loc);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Rapport.xls\"");

		return new ModelAndView(new ReportXlsxView(), model);
	}
	
	// fragment
	@GetMapping(value = "/ui/report/time")
	public String timeReport() {
		return "reports/time";
	}

	@GetMapping("/ui/report/custom")
	public String customReports(Model model) {
		ReportType[] reports = ReportType.values();

		model.addAttribute("reports", reports);

		return "reports/custom";
	}

	@GetMapping("/ui/report/custom/{reportType}")
	public String getCustomReport(Model model, @PathVariable("reportType") ReportType report) {
		model.addAttribute("reportType", report);

		switch (report) {
			case USER_ROLE_WITH_SYSTEM_ROLE_THAT_COULD_BE_CONSTRAINT_BUT_ISNT:
				model.addAttribute("userRoles", generateUserRolesWithoutDataConstraintsReport());
				return "reports/custom/user_roles_without_data_constraint";
			case USER_ROLE_WITH_SENSITIVE_FLAG:
				model.addAttribute("userRoles", generateUserRolesWithSensitiveFlagReport());
				return "reports/custom/user_roles_with_sensitive_flag";
			case USERS_WITH_DUPLICATE_USERROLE_ASSIGNMENTS:
				model.addAttribute("users", generateUsersWithDuplicateRoleAssignmentsReport());
				return "reports/custom/users_with_duplicate_userrole_assignments";
			case USERS_WITH_DUPLICATE_ROLEGROUP_ASSIGNMENTS:
				model.addAttribute("users", generateUsersWithDuplicateRoleGroupAssignmentsReport());
				return "reports/custom/users_with_duplicate_rolegroup_assignments";
			case USER_ROLE_WITHOUT_ASSIGNMENTS:
				model.addAttribute("userRoles", generateUserRolesWithoutAssignmentsReport());
				return "reports/custom/user_roles_without_assignments";
			case USER_ROLE_WITHOUT_SYSTEM_ROLES:
				model.addAttribute("userRoles", generateUserRolesWithoutSystemRolesReport());
				return "reports/custom/user_roles_without_system_roles";
			case ITSYSTEMS_WITHOUT_ATTESTATION_RESPONSIBLE:
				model.addAttribute("itSystems", generateItSystemWithoutSystemResponsibleReport());
				return "reports/custom/itsystem_without_system_responsible";
			case ITSYSTEMS_WITHOUT_ATTESTATION:
				model.addAttribute("itSystems", generateItSystemWithoutAttestationReport());
				return "reports/custom/itsystem_without_attestation";
			case ITSYSTEM_ATTESTATION_RESPONSIBLE:
				model.addAttribute("users", generateItSystemAttestationResponsibleReport());
				return "reports/custom/itsystem_attestation_responsible";
			case ITSYSTEM_SYSTEM_OWNERS:
				model.addAttribute("users", generateItSystemSystemOwnerReport());
				return "reports/custom/itsystem_system_owner";
		}

		return "redirect:/ui/report/custom";
	}

	@GetMapping("/ui/report/custom/{reportType}/download")
	public ModelAndView downloadCustomReport(HttpServletResponse response, @PathVariable("reportType") ReportType report) {
		final Locale locale = LocaleContextHolder.getLocale();

		ArrayList<ArrayList<Object>> rows = new ArrayList<>();
		ArrayList<String> headers = new ArrayList<>();

		switch (report) {
		case USER_ROLE_WITH_SYSTEM_ROLE_THAT_COULD_BE_CONSTRAINT_BUT_ISNT: {
			headers.add(messageSource.getMessage("html.entity.userrole.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.type", null, locale));
			headers.add(messageSource.getMessage("html.entity.userrole.description", null, locale));

			List<UserRole> userRoles = generateUserRolesWithoutDataConstraintsReport();
			for (UserRole userRole : userRoles) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(userRole.getName());
				row.add(userRole.getItSystem().getName());
				row.add(userRole.getDescription());

				rows.add(row);
			}
		}
			break;
		case USER_ROLE_WITH_SENSITIVE_FLAG: {
			headers.add(messageSource.getMessage("html.entity.userrole.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.type", null, locale));
			headers.add(messageSource.getMessage("html.entity.userrole.description", null, locale));

			List<UserRole> userRoles = generateUserRolesWithSensitiveFlagReport();
			for (UserRole userRole : userRoles) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(userRole.getName());
				row.add(userRole.getItSystem().getName());
				row.add(userRole.getDescription());

				rows.add(row);
			}
		}
			break;

		case USERS_WITH_DUPLICATE_USERROLE_ASSIGNMENTS: {
			headers.add(messageSource.getMessage("html.entity.user.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.user.userId", null, locale));
			headers.add(messageSource.getMessage("html.entity.userrole.type", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem", null, locale));
			headers.add(messageSource.getMessage("html.page.report.duplicate.via", null, locale));

			List<UserWithDuplicateRoleAssignmentDTO> result = generateUsersWithDuplicateRoleAssignmentsReport();
			for (UserWithDuplicateRoleAssignmentDTO user : result) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(user.getName());
				row.add(user.getUserId());
				row.add(user.getUserRole().getName());
				row.add(user.getUserRole().getItSystem().getName());
				row.add(user.getMessage());

				rows.add(row);
			}
		}
			break;
		case USERS_WITH_DUPLICATE_ROLEGROUP_ASSIGNMENTS: {
			headers.add(messageSource.getMessage("html.entity.user.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.user.userId", null, locale));
			headers.add(messageSource.getMessage("html.entity.rolegroup.type", null, locale));
			headers.add(messageSource.getMessage("html.page.report.duplicate.via", null, locale));

			List<UserWithDuplicateRoleAssignmentDTO> result = generateUsersWithDuplicateRoleGroupAssignmentsReport();
			for (UserWithDuplicateRoleAssignmentDTO user : result) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(user.getName());
				row.add(user.getUserId());
				row.add(user.getRoleGroup().getName());
				row.add(user.getMessage());

				rows.add(row);
			}
		}
			break;
		case USER_ROLE_WITHOUT_ASSIGNMENTS: {
			headers.add(messageSource.getMessage("html.entity.userrole.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.type", null, locale));
			headers.add(messageSource.getMessage("html.entity.userrole.description", null, locale));

			List<UserRole> userRoles = generateUserRolesWithoutAssignmentsReport();
			for (UserRole userRole : userRoles) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(userRole.getName());
				row.add(userRole.getItSystem().getName());
				row.add(userRole.getDescription());

				rows.add(row);
			}

		}
			break;
		case USER_ROLE_WITHOUT_SYSTEM_ROLES: {
			headers.add(messageSource.getMessage("html.entity.userrole.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.type", null, locale));
			headers.add(messageSource.getMessage("html.entity.userrole.description", null, locale));

			List<UserRole> userRoles = generateUserRolesWithoutSystemRolesReport();
			for (UserRole userRole : userRoles) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(userRole.getName());
				row.add(userRole.getItSystem().getName());
				row.add(userRole.getDescription());

				rows.add(row);
			}

		}
			break;
		case ITSYSTEMS_WITHOUT_ATTESTATION_RESPONSIBLE: {
			headers.add(messageSource.getMessage("html.entity.itsystem.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.identifier", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.type", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.inactiveAttestationResponsible", null, locale));

			List<ItSystemWithoutSystemResponsibleReport> result = generateItSystemWithoutSystemResponsibleReport();
			for (ItSystemWithoutSystemResponsibleReport itsystem : result) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(itsystem.name);
				row.add(itsystem.identifier);
				row.add(messageSource.getMessage(itsystem.type, null, locale));
				row.add(itsystem.inactiveSystemResponsible ? "Ja" : "Nej");

				rows.add(row);
			}

		}
			break;
		case ITSYSTEMS_WITHOUT_ATTESTATION: {
			headers.add(messageSource.getMessage("html.entity.itsystem.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.identifier", null, locale));
			headers.add(messageSource.getMessage("html.entity.itsystem.type", null, locale));

			List<ItSystemWithoutAttestationReport> result = generateItSystemWithoutAttestationReport();
			for (ItSystemWithoutAttestationReport itsystem : result) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(itsystem.name);
				row.add(itsystem.identifier);
				row.add(messageSource.getMessage(itsystem.type, null, locale));

				rows.add(row);
			}
		}
			break;
		case ITSYSTEM_ATTESTATION_RESPONSIBLE: {
			headers.add(messageSource.getMessage("html.entity.itsystem.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.user.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.user.email", null, locale));

			List<ItSystemAttestationResponsibleRecord> result = generateItSystemAttestationResponsibleReport();
			for (ItSystemAttestationResponsibleRecord user : result) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(user.itSystemName);
				row.add(user.name);
				row.add(user.email);

				rows.add(row);
			}

		}
			break;
		case ITSYSTEM_SYSTEM_OWNERS: {
			headers.add(messageSource.getMessage("html.entity.itsystem.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.user.name", null, locale));
			headers.add(messageSource.getMessage("html.entity.user.email", null, locale));

			List<ItSystemSystemOwnerRecord> result = generateItSystemSystemOwnerReport();
			for (ItSystemSystemOwnerRecord user : result) {
				ArrayList<Object> row = new ArrayList<>();
				row.add(user.itSystemName);
				row.add(user.name);
				row.add(user.email);

				rows.add(row);
			}
		}
			break;
		}
		
		Map<String, Object> model = new HashMap<>();
		model.put("headers", headers);
		model.put("rows", rows);
		model.put("sheetName", "report");

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"report.xls\"");

		return new ModelAndView(new XlsView(), model);
	}

	record ItSystemSystemOwnerRecord(String itSystemName, String name, String email) {}
	private List<ItSystemSystemOwnerRecord> generateItSystemSystemOwnerReport() {
		List<ItSystemSystemOwnerRecord> result = new ArrayList<>();
		List<ItSystem> allSystems = itSystemService.getVisible();
		for (ItSystem system : allSystems) {
			if (system.getSystemOwner() != null) {
				result.add(new ItSystemSystemOwnerRecord(system.getName(), system.getSystemOwner().getName(), system.getSystemOwner().getEmail()));
			}
		}
		return result;
	}

	record ItSystemAttestationResponsibleRecord(String itSystemName, String name, String email) {}
	private List<ItSystemAttestationResponsibleRecord> generateItSystemAttestationResponsibleReport() {
		List<ItSystemAttestationResponsibleRecord> result = new ArrayList<>();
		List<ItSystem> allSystems = itSystemService.getVisible();
		for (ItSystem system : allSystems) {
			if (system.getAttestationResponsible() != null) {
				result.add(new ItSystemAttestationResponsibleRecord(system.getName(), system.getAttestationResponsible().getName(), system.getAttestationResponsible().getEmail()));
			}
		}
		return result;
	}

	record ItSystemWithoutAttestationReport(long id, String name, String identifier, String type) {}
	private List<ItSystemWithoutAttestationReport> generateItSystemWithoutAttestationReport() {
		List<ItSystemWithoutAttestationReport> result = new ArrayList<>();
		List<ItSystem> allSystems = itSystemService.getVisible();
		for (ItSystem system : allSystems) {
			if (system.isAttestationExempt()) {
				result.add(new ItSystemWithoutAttestationReport(system.getId(), system.getName(), system.getIdentifier(), system.getSystemType().getMessage()));
			}
		}
		return result;
	}

	record ItSystemWithoutSystemResponsibleReport(long id, String name, String identifier, String type, boolean inactiveSystemResponsible) {}
	private List<ItSystemWithoutSystemResponsibleReport> generateItSystemWithoutSystemResponsibleReport() {
		List<ItSystemWithoutSystemResponsibleReport> result = new ArrayList<>();
		List<ItSystem> allSystems = itSystemService.getVisible();
		for (ItSystem system : allSystems) {
			if (system.getAttestationResponsible() == null) {
				result.add(new ItSystemWithoutSystemResponsibleReport(system.getId(), system.getName(), system.getIdentifier(), system.getSystemType().getMessage(), false));
			}
			else if (system.getAttestationResponsible() != null && (system.getAttestationResponsible().isDeleted() || system.getAttestationResponsible().isDisabled())) {
				result.add(new ItSystemWithoutSystemResponsibleReport(system.getId(), system.getName(), system.getIdentifier(), system.getSystemType().getMessage(), true));
			}
		}
		return result;
	}

	private List<UserRole> generateUserRolesWithSensitiveFlagReport() {
		return userRoleService.getAllSensitiveRoles();
	}

	private List<UserRole> generateUserRolesWithoutDataConstraintsReport() {
		List<UserRole> userRoles = userRoleService.getAll();
		userRoles = userRoles.stream()
				//filter SystemRoles that have supported constraints and don't have constraints assigned
				.filter(ur -> ur.getSystemRoleAssignments().size() > 0 &&
							  ur.getSystemRoleAssignments().stream()
							  	.anyMatch(sra -> sra.getSystemRole().getSupportedConstraintTypes().size() > 0 &&
							  			  sra.getConstraintValues().isEmpty()))
				.collect(Collectors.toList());

		return userRoles;
	}

	private List<UserWithDuplicateRoleAssignmentDTO> generateUsersWithDuplicateRoleAssignmentsReport() {
		List<UserWithDuplicateRoleAssignmentDTO> result = new ArrayList<>();
		
		// heavy lookup performed here
		Map<User, List<UserRoleAssignmentWithInfo>> usersWithRoleAssignments = userService.getUsersWithRoleAssignments();

		for (User user : usersWithRoleAssignments.keySet()) {
			
			List<UserRoleAssignmentWithInfo> assignments = usersWithRoleAssignments.get(user);
			if (assignments == null || assignments.size() == 0) {
				continue;
			}
			
			List<UserRoleAssignmentWithInfo> directAssignments = assignments.stream().filter(a -> a.getAssignedThroughInfo() == null).collect(Collectors.toList());
			if (directAssignments == null || directAssignments.size() == 0) {
				continue;
			}

			for (UserRoleAssignmentWithInfo directAssignment : directAssignments) {
				for (UserRoleAssignmentWithInfo assignment : assignments) {
					// ignore direct assignments
					if (assignment.getAssignedThroughInfo() == null) {
						continue;
					}

					// is this UserRole also assigned indirectly?
					if (assignment.getUserRole().getId() == directAssignment.getUserRole().getId()) {
						UserWithDuplicateRoleAssignmentDTO entry = new UserWithDuplicateRoleAssignmentDTO();
						entry.setMessage(assignment.getAssignedThroughInfo().getMessage());
						entry.setName(user.getName());
						entry.setUserId(user.getUserId());
						entry.setUuid(user.getUuid());
						entry.setUserRole(assignment.getUserRole());

						result.add(entry);
					}
				}
			}
		}
		
		return result;
	}

	private List<UserWithDuplicateRoleAssignmentDTO> generateUsersWithDuplicateRoleGroupAssignmentsReport() {
		List<UserWithDuplicateRoleAssignmentDTO> result = new ArrayList<>();
		
		// heavy lookup performed here
		Map<User, List<RoleGroupAssignmentWithInfo>> usersWithRoleGroupAssignments = userService.getUsersWithRoleGroupAssignments();

		for (User user : usersWithRoleGroupAssignments.keySet()) {
			List<RoleGroupAssignmentWithInfo> assignments = usersWithRoleGroupAssignments.get(user);
			if (assignments == null || assignments.isEmpty()) {
				continue;
			}
			
			List<RoleGroupAssignmentWithInfo> directAssignments = assignments.stream().filter(a -> a.getAssignedThroughInfo() == null).collect(Collectors.toList());
			if (directAssignments == null || directAssignments.isEmpty()) {
				continue;
			}
			
			for (RoleGroupAssignmentWithInfo directAssignment : directAssignments) {
				for (RoleGroupAssignmentWithInfo assignment : assignments) {
					// ignore direct assignments
					if (assignment.getAssignedThroughInfo() == null) {
						continue;
					}
					
					// is this RoleGroup also assigned indirectly?
					if (assignment.getRoleGroup().getId() == directAssignment.getRoleGroup().getId()) {
						UserWithDuplicateRoleAssignmentDTO entry = new UserWithDuplicateRoleAssignmentDTO();
						entry.setMessage(assignment.getAssignedThroughInfo().getMessage());
						entry.setName(user.getName());
						entry.setUserId(user.getUserId());
						entry.setUuid(user.getUuid());
						entry.setRoleGroup(assignment.getRoleGroup());

						result.add(entry);
					}
				}
			}
		}
		
		return result;
	}

	private List<UserRole> generateUserRolesWithoutAssignmentsReport() {
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		List<UserRole> userRoles = userRoleService.getAll();

		// bulk load once
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		List<User> users = userService.getAll();
		List<Position> positions = positionService.getAll();
		
		// find all assigned roleGroups
		List<RoleGroup> assignedRoleGroups = roleGroups.stream()
				.filter(ur ->
					orgUnits.stream().anyMatch(ou -> ou.getRoleGroupAssignments().stream().anyMatch(ura -> ura.getRoleGroup().getId() == ur.getId())) ||
					users.stream().anyMatch(u -> u.getRoleGroupAssignments().stream().anyMatch(ura -> ura.getRoleGroup().getId() == ur.getId())) ||
					positions.stream().anyMatch(p -> p.getRoleGroupAssignments().stream().anyMatch(ura -> ura.getRoleGroup().getId() == ur.getId()))
				)
				.collect(Collectors.toList());

		// negative lookups to speed up the process
		List<UserRole> userRolesNotInReport = userRoles.stream()
				.filter(ur ->
					orgUnits.stream().anyMatch(ou -> ou.getUserRoleAssignments().stream().anyMatch(ura -> ura.getUserRole().getId() == ur.getId())) ||
					users.stream().anyMatch(u -> u.getUserRoleAssignments().stream().anyMatch(ura -> ura.getUserRole().getId() == ur.getId())) ||
					positions.stream().anyMatch(p -> p.getUserRoleAssignments().stream().anyMatch(ura -> ura.getUserRole().getId() == ur.getId())) ||
					assignedRoleGroups.stream().anyMatch(rg -> rg.getUserRoleAssignments().stream().anyMatch(ura -> ura.getUserRole().getId() == ur.getId()))
				)
				.collect(Collectors.toList());

		return userRoles.stream().filter(u -> userRolesNotInReport.stream().noneMatch(ur -> ur.getId() == u.getId())).collect(Collectors.toList());
	}

	private List<UserRole> generateUserRolesWithoutSystemRolesReport() {
		List<UserRole> userRoles = userRoleService.getAll();
		userRoles = userRoles.stream()
				.filter(ur -> ur.getSystemRoleAssignments().size() == 0)
				.collect(Collectors.toList());

		return userRoles;
	}

	private List<ItSystemChoice> parseItSystems(LocalDate localDate) {
		final List<HistoryItSystem> itSystems = historyService.getItSystems(localDate);
		return itSystems.stream()
                .filter(its -> !its.isItSystemHidden())
                .map(ItSystemChoice::new)
				.sorted((sys1, sys2) -> sys1.getItSystemName().compareToIgnoreCase(sys2.getItSystemName()))
				.collect(Collectors.toList());
	}

	private List<OUListForm> parseOuTree(LocalDate localDate) {
		return historyService
				.getOUs(localDate)
				.values()
				.stream()
				.map(OUListForm::new)
				.collect(Collectors.toList());
	}
}
