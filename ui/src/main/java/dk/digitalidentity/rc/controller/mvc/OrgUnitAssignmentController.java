package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import dk.digitalidentity.rc.controller.rest.orgunit.assign.OuAssignmentReceiptService;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.OuAssignmentService;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.OuAssignmentService.ExistingAssignment;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.OuAssignmentStep;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.RoleType;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeFields;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeChoice;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.TitleMode;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.projections.UserUuidNameAndUserId;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.FunctionService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.util.Failure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireControllerPermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
@RequiredArgsConstructor
@Controller
@RequestMapping("/ou/{ouUuid}")
public class OrgUnitAssignmentController {

	private static final List<OuAssignmentStep> INITIAL_STEPS = List.of(OuAssignmentStep.SELECT_SCOPE, OuAssignmentStep.RECEIPT);

	private final OrgUnitService orgUnitService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final FunctionService functionService;
	private final PositionService positionService;
	private final OuAssignmentReceiptService receiptService;
	private final OuAssignmentService ouAssignmentService;
	private final MessageSource messageSource;
	private final UserPermissionContext userPermissionContext;

	/** basic information context for a ou assignment wizard. */
	public record WizardTarget(String ouUuid, RoleType roleType, Long roleId, Optional<Long> assignmentId) {
	}

	/** Current context of a ou assignment made in the wizard. */
	public record WizardContext(WizardTarget target, ScopeChoice scopeChoice, ScopeFields scopeFields) {
	}

	public enum WizardStepState {
		COMPLETED, CURRENT, UPCOMING
	}

	public record WizardStep(OuAssignmentStep id, WizardStepState state, String backUrl) {
	}

	public record RoleName(String name) {
	}

	/** The accumulated wizard context echoed back with every non-SELECT_SCOPE step submission. */
	public record StepSubmission(String currentStepId, ScopeChoice scopeChoice, ScopeFields scopeFields) {
		WizardContext context(WizardTarget target) {
			return new WizardContext(target, scopeChoice, scopeFields);
		}
	}

	@GetMapping("userrole/{roleID}")
	public String assignModalForUserRole(Model model, @PathVariable String ouUuid, @PathVariable Long roleID) {
		return assignModal(model, ouUuid, RoleType.USERROLE, roleID, userRoleService.findProjectionById(roleID, RoleName.class));
	}

	@GetMapping("rolegroup/{roleID}")
	public String assignModalForRoleGroup(Model model, @PathVariable String ouUuid, @PathVariable Long roleID) {
		return assignModal(model, ouUuid, RoleType.ROLEGROUP, roleID, roleGroupService.findProjectionById(roleID, RoleName.class));
	}

	@GetMapping("userrole/{roleID}/assignment/{assignmentId}")
	public String editModalForUserRole(Model model, @PathVariable String ouUuid, @PathVariable Long roleID, @PathVariable long assignmentId) {
		requireOrgUnitWithinConstraints(ouUuid, RoleType.USERROLE, roleID);
		requireAssignableRole(ouUuid, RoleType.USERROLE, roleID);

		ExistingAssignment existing = ouAssignmentService.findExisting(RoleType.USERROLE, ouUuid, roleID, assignmentId)
				.orElseThrow(() -> Failure.notFound(log, "Assignment {} was not found, or its scope matches no assignment rule (ouUuid={}, roleType=userrole, roleId={})", assignmentId, ouUuid, roleID));

		return editModal(model, ouUuid, RoleType.USERROLE, roleID, assignmentId, existing,
				userRoleService.findProjectionById(roleID, RoleName.class));
	}

	@GetMapping("rolegroup/{roleID}/assignment/{assignmentId}")
	public String editModalForRoleGroup(Model model, @PathVariable String ouUuid, @PathVariable Long roleID, @PathVariable long assignmentId) {
		requireOrgUnitWithinConstraints(ouUuid, RoleType.ROLEGROUP, roleID);
		requireAssignableRole(ouUuid, RoleType.ROLEGROUP, roleID);

		ExistingAssignment existing = ouAssignmentService.findExisting(RoleType.ROLEGROUP, ouUuid, roleID, assignmentId)
				.orElseThrow(() -> Failure.notFound(log, "Assignment {} was not found, or its scope matches no assignment rule (ouUuid={}, roleType=rolegroup, roleId={})", assignmentId, ouUuid, roleID));

		return editModal(model, ouUuid, RoleType.ROLEGROUP, roleID, assignmentId, existing,
				roleGroupService.findProjectionById(roleID, RoleName.class));
	}

	@PostMapping({"{roleType}/{roleId}/assign/step/SELECT_SCOPE", "{roleType}/{roleId}/assignment/{assignmentId}/step/SELECT_SCOPE"})
	public String resolveScope(Model model, @PathVariable String ouUuid, @PathVariable RoleType roleType,
			@PathVariable Long roleId, @PathVariable Optional<Long> assignmentId, @RequestBody ScopeChoice choice) {
		requireOrgUnitWithinConstraints(ouUuid, roleType, roleId);

		WizardTarget target = new WizardTarget(ouUuid, roleType, roleId, assignmentId);

		return advanceToNextStep(model, new WizardContext(target, choice, ScopeFields.empty()), OuAssignmentStep.SELECT_SCOPE);
	}

	@PostMapping({"{roleType}/{roleId}/assign/step/{stepId}", "{roleType}/{roleId}/assignment/{assignmentId}/step/{stepId}"})
	public String submitStep(Model model, @PathVariable String ouUuid, @PathVariable RoleType roleType,
			@PathVariable Long roleId, @PathVariable Optional<Long> assignmentId, @PathVariable String stepId,
			@RequestBody StepSubmission body) {
		requireOrgUnitWithinConstraints(ouUuid, roleType, roleId);

		OuAssignmentStep currentStepId = OuAssignmentStep.fromId(stepId)
				.orElseThrow(() -> Failure.illegalArgument(log, "Unknown wizard step id {} (ouUuid={}, roleType={}, roleId={})", stepId, ouUuid, roleType, roleId));

		WizardContext context = body.context(new WizardTarget(ouUuid, roleType, roleId, assignmentId));
		List<String> errors = validateStep(currentStepId, context);

		if (!errors.isEmpty()) {
			log.debug("Wizard step {} rejected: {} (scopeChoice={}, scopeFields={})",
					currentStepId, errors, context.scopeChoice(), context.scopeFields());

			return rerenderStep(model, context, currentStepId, errors);
		}

		return advanceToNextStep(model, context, currentStepId);
	}

	@PostMapping({"{roleType}/{roleId}/assign/step/{stepId}/back/{targetStepId}", "{roleType}/{roleId}/assignment/{assignmentId}/step/{stepId}/back/{targetStepId}"})
	public String navigateBack(Model model, @PathVariable String ouUuid, @PathVariable RoleType roleType,
			@PathVariable Long roleId, @PathVariable Optional<Long> assignmentId, @PathVariable String stepId,
			@PathVariable String targetStepId, @RequestBody StepSubmission body) {
		requireOrgUnitWithinConstraints(ouUuid, roleType, roleId);

		OuAssignmentStep currentStep = OuAssignmentStep.fromId(stepId)
				.orElseThrow(() -> Failure.illegalArgument(log, "Unknown wizard step id {} (ouUuid={}, roleType={}, roleId={})", stepId, ouUuid, roleType, roleId));
		OuAssignmentStep targetStep = OuAssignmentStep.fromId(targetStepId)
				.orElseThrow(() -> Failure.illegalArgument(log, "Unknown wizard target step id {} (ouUuid={}, roleType={}, roleId={})", targetStepId, ouUuid, roleType, roleId));

		WizardContext context = body.context(new WizardTarget(ouUuid, roleType, roleId, assignmentId));
		List<OuAssignmentStep> steps = stepSequenceFor(context.scopeChoice());
		int targetIndex = steps.indexOf(targetStep);

		if (targetIndex < 0 || targetIndex >= steps.indexOf(currentStep)) {
			throw Failure.illegalArgument(log, "Cannot navigate back from {} to {} in sequence {}", currentStep, targetStep, steps);
		}

		return renderStep(model, context, targetStep, true);
	}

	private String assignModal(Model model, String ouUuid, RoleType roleType, Long roleId, Optional<RoleName> roleName) {
		requireOrgUnitWithinConstraints(ouUuid, roleType, roleId);
		requireAssignableRole(ouUuid, roleType, roleId);

		addCommonModalAttributes(model, ouUuid, roleType, roleId, roleName);

		showInitialStep(model, new WizardTarget(ouUuid, roleType, roleId, Optional.empty()));

		return "ous/assign/modal :: ouAssignmentModalContent";
	}

	/**
	 * Opens the wizard already seeded with an existing assignment's scope
	 */
	private String editModal(Model model, String ouUuid, RoleType roleType, Long roleId, long assignmentId,
			ExistingAssignment existing, Optional<RoleName> roleName) {
		addCommonModalAttributes(model, ouUuid, roleType, roleId, roleName);

		model.addAttribute("scopeChoice", existing.scopeChoice());
		model.addAttribute("scopeLocked", true);

		WizardTarget target = new WizardTarget(ouUuid, roleType, roleId, Optional.of(assignmentId));
		addStepsToModel(model, target, stepSequenceFor(existing.scopeChoice()), OuAssignmentStep.SELECT_SCOPE);

		return "ous/assign/modal :: ouAssignmentModalContent";
	}

	private void addCommonModalAttributes(Model model, String ouUuid, RoleType roleType, Long roleId, Optional<RoleName> roleName) {
		record OuName(String name){}
		OuName ouName = orgUnitService.findProjectionByUuid(ouUuid, OuName.class).stream()
			.findAny()
			.orElseThrow(() -> Failure.notFound(log, "Org unit {} was not found opening the assignment wizard", ouUuid));
		model.addAttribute("ouName", ouName.name);

		model.addAttribute("roleName", roleName
			.orElseThrow(() -> Failure.notFound(log, "Role {} {} was not found (ouUuid={})", roleType, roleId, ouUuid)).name);

		model.addAttribute("scopeRules", ScopeRules.constraintsByTarget());
	}

	private void requireOrgUnitWithinConstraints(String ouUuid, RoleType roleType, Long roleId) {
		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN);

		if (!SecurityUtil.hasDirectAdminRole() && !assignConstraint.allowsOrgunit(ouUuid)) {
			throw Failure.accessDenied(log, "Role {} {} may not be assigned in org unit {} by {}",
					roleType, roleId, ouUuid, SecurityUtil.getUserId());
		}
	}

	private void requireAssignableRole(String ouUuid, RoleType roleType, Long roleId) {
		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN);

		boolean allowed = switch (roleType) {
			case USERROLE -> userRoleService.getOptionalById(roleId)
					.map(role -> userRoleService.isUserRoleAssignable(role, true, assignConstraint))
					.orElseThrow(() -> Failure.notFound(log, "Role {} {} was not found (ouUuid={})", roleType, roleId, ouUuid));
			case ROLEGROUP -> roleGroupService.getOptionalById(roleId)
					.map(roleGroup -> userRoleService.isRoleGroupAssignable(roleGroup, true, assignConstraint))
					.orElseThrow(() -> Failure.notFound(log, "Role {} {} was not found (ouUuid={})", roleType, roleId, ouUuid));
		};

		if (!allowed) {
			throw Failure.accessDenied(log, "Role {} {} may not be assigned in org unit {} by {}",
					roleType, roleId, ouUuid, SecurityUtil.getUserId());
		}
	}


	/**
	 * The authority on whether a step's answer may be accepted
	 * @return the messages the user must act on, empty when the answer is acceptable
	 */
	private List<String> validateStep(OuAssignmentStep stepId, WizardContext context) {
		ScopeFields fields = Optional.ofNullable(context.scopeFields())
				.orElseThrow(() -> Failure.illegalArgument(log, "Step {} was submitted without scope fields (scopeChoice={})", stepId, context.scopeChoice()));

		return switch (stepId) {
			case TITLES -> requireSelection(fields.titleUuids(), "html.page.ou.assign.titles.required");
			case FUNCTIONS -> requireSelection(fields.functionUuids(), "html.page.ou.assign.functions.required");
			case EXCEPTED_USERS -> requireSelection(fields.exceptedUserUuids(), "html.page.ou.assign.exceptedUsers.required");
			case EXCEPTED_OUS -> requireSelection(fields.exceptedOuUuids(), "html.page.ou.assign.exceptedOus.required");
			case SELECT_SCOPE, MANAGER, RECEIPT -> List.of();
		};
	}

	private List<String> requireSelection(Set<String> selected, String messageKey) {
		return selected == null || selected.isEmpty()
				? List.of(messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale()))
				: List.of();
	}

	/** Re-derived from the choice on every submission - never trusted from the client. */
	private static List<OuAssignmentStep> stepSequenceFor(ScopeChoice choice) {
		return buildStepSequence(ScopeRules.stepsFor(choice)
				.orElseThrow(() -> Failure.illegalArgument(log, "Scope choice {} could not be resolved to a step sequence", choice)));
	}

	/**
	 * Looks up the assignment being edited.
	 */
	private Optional<ExistingAssignment> findExisting(WizardContext context) {
		WizardTarget target = context.target();

		return target.assignmentId()
				.flatMap(id -> ouAssignmentService.findExisting(target.roleType(), id));
	}

	private static Set<String> selectedUuids(WizardContext context, Function<ScopeFields, Set<String>> field,
			Optional<ExistingAssignment> persisted) {
		Set<String> answered = Optional.ofNullable(context.scopeFields()).map(field).orElse(Set.of());

		return answered != null && !answered.isEmpty()
				? answered
				: persisted.map(ExistingAssignment::scopeFields).map(field).orElse(Set.of());
	}

	private void loadTitlesStepData(Model model, WizardContext context) {
		String ouUuid = context.target().ouUuid();
		TitleMode titleMode = ScopeRules.titleModeFor(context.scopeChoice())
			.orElseThrow(() -> Failure.illegalArgument(log, "Scope choice {} implies no title mode, but the TITLES step was reached", context.scopeChoice()));
		OrgUnit orgUnit = orgUnitService.getOptionalByUuid(ouUuid)
			.orElseThrow(() -> Failure.notFound(log, "Org unit {} was not found loading TITLES step data", ouUuid));
		List<Title> titles = orgUnitService.getRelevantTitles(orgUnit, context.scopeChoice().inherit());

		model.addAttribute("titleMode", titleMode.name());
		model.addAttribute("titles", titles);
		model.addAttribute("selectedTitleUuids", selectedUuids(context, ScopeFields::titleUuids, findExisting(context)));
	}

	private void loadFunctionsStepData(Model model, WizardContext context) {
		List<dk.digitalidentity.rc.dao.model.Function> functions = functionService.getAllActive();

		model.addAttribute("functions", functions);
		model.addAttribute("selectedFunctionUuids", selectedUuids(context, ScopeFields::functionUuids, findExisting(context)));
	}

	private void loadExceptedUsersStepData(Model model, WizardContext context) {
		String ouUuid = context.target().ouUuid();
		OrgUnit orgUnit = orgUnitService.getOptionalByUuid(ouUuid)
			.orElseThrow(() -> Failure.notFound(log, "Org unit {} was not found loading EXCEPTED_USERS step data", ouUuid));
		List<UserUuidNameAndUserId> candidates = positionService.findUserDetailsByOrgUnit(orgUnit);

		model.addAttribute("exceptedUserCandidates", candidates);
		model.addAttribute("selectedUserUuids", selectedUuids(context, ScopeFields::exceptedUserUuids, findExisting(context)));
	}

	private void loadExceptedOusStepData(Model model, WizardContext context) {
		String ouUuid = context.target().ouUuid();

		model.addAttribute("exceptedOuCandidates", orgUnitService.findDescendantTree(ouUuid)
				.orElseThrow(() -> Failure.notFound(log, "Org unit {} was not found loading EXCEPTED_OUS step data", ouUuid)));
		model.addAttribute("selectedExceptedOuUuids", selectedUuids(context, ScopeFields::exceptedOuUuids, findExisting(context)));
	}

	/** A revisited step was already answered, so the echoed answer wins over the persisted one. */
	private void loadManagerStepData(Model model, WizardContext context, boolean revisit) {
		Optional<ScopeFields> source = revisit
				? Optional.ofNullable(context.scopeFields())
				: findExisting(context).map(ExistingAssignment::scopeFields);

		model.addAttribute("selectedIncludeSubstitutes", source.map(ScopeFields::includeSubstitutes).orElse(null));
	}

	private void loadSelectScopeStepData(Model model, WizardContext context) {
		model.addAttribute("scopeRules", ScopeRules.constraintsByTarget());
		model.addAttribute("scopeLocked", context.target().assignmentId().isPresent());
		model.addAttribute("scopeChoice", findExisting(context)
				.map(ExistingAssignment::scopeChoice)
				.orElse(context.scopeChoice()));
	}

	/** The receipt renders one read model, so an unusable scope choice is rejected here. */
	private void loadReceiptStepData(Model model, WizardContext context) {
		WizardTarget target = context.target();

		model.addAttribute("receipt", receiptService
				.summarise(target.ouUuid(), target.roleType(), target.roleId(), context.scopeChoice(), context.scopeFields())
				.orElseThrow(() -> Failure.illegalArgument(log, "Receipt could not be summarised (ouUuid={}, roleType={}, roleId={}, scopeChoice={}, scopeFields={})",
						target.ouUuid(), target.roleType(), target.roleId(), context.scopeChoice(), context.scopeFields())));

		Optional<ExistingAssignment> existing = findExisting(context);
		model.addAttribute("selectedStartDate", existing.map(ExistingAssignment::startDate).orElse(null));
		model.addAttribute("selectedStopDate", existing.map(ExistingAssignment::stopDate).orElse(null));
		model.addAttribute("selectedCaseNumber", existing.map(ExistingAssignment::caseNumber).orElse(null));
	}

	private static void showInitialStep(Model model, WizardTarget target) {
		addStepsToModel(model, target, INITIAL_STEPS, OuAssignmentStep.SELECT_SCOPE);
	}

	/**
	 * Determines the next step, loads the nessecary data and renders the view
	 */
	private String advanceToNextStep(Model model, WizardContext context, OuAssignmentStep currentStepId) {
		WizardTarget target = context.target();

		log.debug("Advancing wizard from {} (ouUuid={}, roleType={}, roleId={}, assignmentId={}, scopeChoice={})",
				currentStepId, target.ouUuid(), target.roleType(), target.roleId(),
				target.assignmentId().map(String::valueOf).orElse("none"), context.scopeChoice());

		return renderStep(model, context, stepAfter(stepSequenceFor(context.scopeChoice()), currentStepId), false);
	}

	/**
	 * Renders the step the user is already on, including the validation messages, if any.
	 */
	private String rerenderStep(Model model, WizardContext context, OuAssignmentStep currentStepId, List<String> errors) {
		model.addAttribute("stepErrors", errors);

		return renderStep(model, context, currentStepId, false);
	}

	private String renderStep(Model model, WizardContext context, OuAssignmentStep stepId, boolean revisit) {
		List<OuAssignmentStep> steps = stepSequenceFor(context.scopeChoice());

		loadStepData(model, context, stepId, revisit);
		addStepsToModel(model, context.target(), steps, stepId);

		return "ous/assign/fragments/wizardBody :: wizardBody";
	}

	/** Loads whatever server-side data the step needs; steps with none are a no-op. */
	private void loadStepData(Model model, WizardContext context, OuAssignmentStep stepId, boolean revisit) {
		switch (stepId) {
			case TITLES -> loadTitlesStepData(model, context);
			case FUNCTIONS -> loadFunctionsStepData(model, context);
			case EXCEPTED_USERS -> loadExceptedUsersStepData(model, context);
			case EXCEPTED_OUS -> loadExceptedOusStepData(model, context);
			case MANAGER -> loadManagerStepData(model, context, revisit);
			case RECEIPT -> loadReceiptStepData(model, context);
			case SELECT_SCOPE -> loadSelectScopeStepData(model, context);
		}
	}

	private static OuAssignmentStep stepAfter(List<OuAssignmentStep> steps, OuAssignmentStep currentStepId) {
		int nextIndex = steps.indexOf(currentStepId) + 1;
		if (nextIndex <= 0 || nextIndex >= steps.size()) {
			throw Failure.illegalArgument(log, "No step follows {} in sequence {}", currentStepId, steps);
		}
		return steps.get(nextIndex);
	}

	private static void addStepsToModel(Model model, WizardTarget target, List<OuAssignmentStep> steps,
			OuAssignmentStep currentStepId) {
		String submitUrl = stepSubmitUrl(target, currentStepId);
		List<WizardStep> wizardSteps = toWizardSteps(steps, currentStepId, submitUrl);
		int currentIndex = steps.indexOf(currentStepId);

		model.addAttribute("currentStepId", currentStepId.name());
		model.addAttribute("wizardSteps", wizardSteps);
		model.addAttribute("stepSubmitUrl", submitUrl);
		model.addAttribute("previousStepBackUrl", currentIndex > 0 ? wizardSteps.get(currentIndex - 1).backUrl() : null);
		model.addAttribute("finalStep", steps.getLast() == currentStepId);
	}

	/** Where the rendered step posts its own answer. Determines edit or create based on assignment id presence */
	private static String stepSubmitUrl(WizardTarget target, OuAssignmentStep currentStepId) {
		return target.assignmentId()
				.map(id -> "/ou/%s/%s/%d/assignment/%d/step/%s".formatted(target.ouUuid(), target.roleType().name().toLowerCase(), target.roleId(), id, currentStepId.name()))
				.orElseGet(() -> "/ou/%s/%s/%d/assign/step/%s".formatted(target.ouUuid(), target.roleType().name().toLowerCase(), target.roleId(), currentStepId.name()));
	}

	private static List<WizardStep> toWizardSteps(List<OuAssignmentStep> steps, OuAssignmentStep currentStepId, String submitUrl) {
		int currentIndex = steps.indexOf(currentStepId);

		List<WizardStep> wizardSteps = new ArrayList<>();
		for (int index = 0; index < steps.size(); index++) {
			OuAssignmentStep step = steps.get(index);
			WizardStepState state = stateOf(index, currentIndex);
			String backUrl = state == WizardStepState.COMPLETED ? submitUrl + "/back/" + step.name() : null;

			wizardSteps.add(new WizardStep(step, state, backUrl));
		}
		return wizardSteps;
	}

	private static WizardStepState stateOf(int index, int currentIndex) {
		return switch (Integer.compare(index, currentIndex)) {
			case -1 -> WizardStepState.COMPLETED;
			case 0 -> WizardStepState.CURRENT;
			default -> WizardStepState.UPCOMING;
		};
	}

	private static List<OuAssignmentStep> buildStepSequence(List<OuAssignmentStep> middleSteps) {
		List<OuAssignmentStep> steps = new ArrayList<>();
		steps.add(OuAssignmentStep.SELECT_SCOPE);
		steps.addAll(middleSteps);
		steps.add(OuAssignmentStep.RECEIPT);
		return steps;
	}
}
