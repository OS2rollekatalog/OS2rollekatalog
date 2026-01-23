package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApproverOptionService {
	private final SettingsService settingsService;
	private final MessageSource messageSource;

	public String getApproverOptionsAsString(Collection<ApprovableBy> approverOptions) {
		List<String> approvableByList = approverOptions.stream()
			.map(approvableBy -> messageSource.getMessage(approvableBy.getMessage(), null, Locale.getDefault()))
			.sorted()
			.toList();

		if (approvableByList.contains(messageSource.getMessage(ApprovableBy.AUTOMATIC.getMessage(), null, Locale.getDefault()))) {
			return messageSource.getMessage(ApprovableBy.AUTOMATIC.getMessage(), null, Locale.getDefault());
		}

		if (approvableByList.size() == 1) {
			return approvableByList.getFirst();
		}

		StringBuilder result = new StringBuilder();
		for (int i = 0; i < approvableByList.size(); i++) {
			if (i < approvableByList.size() - 2) {
				//if any other than the two last, comma seperate
				result.append(approvableByList.get(i))
					.append(", ");
			} else if ((i < approvableByList.size() - 1)) {
				//if next to last, just append
				result.append(approvableByList.get(i));
			} else {
				//if last, prepend "eller"
				result.append(" eller ")
					.append(approvableByList.get(i));
			}
		}
		return result.toString();
	}


	/**
	 * Helper method for getting the approval permission for a userrole, taking into account the INHERIT value
	 *
	 * @param userRole
	 * @return Nearest relevant ApprovalOption from the given userrole
	 */
	public List<ApprovableBy> getInheritedApproverOption(UserRole userRole) {
		List<ApprovableBy> relevantOption = userRole.getApproverPermission();
		ItSystem itSystem = userRole.getItSystem();
		if (relevantOption.contains(ApprovableBy.INHERIT)) {
			if (itSystem != null && itSystem.getApproverPermission() != null && !itSystem.getApproverPermission().contains(ApprovableBy.INHERIT)) {
				relevantOption = itSystem.getApproverPermission();
			} else {
				relevantOption = settingsService.getRolerequestApprover();
			}
		}
		return relevantOption;
	}

	/**
	 * Helper method for getting approval permission for a roleGroup, taking into account the INHERIT value
	 *
	 * @param roleGroup
	 * @return Nearest relevant ApprovalOption from the given roleGroup
	 */
	public List<ApprovableBy> getInheritedApproverOption(RoleGroup roleGroup) {
		List<ApprovableBy> relevantOption = roleGroup.getApproverPermission();
		if (relevantOption.contains(ApprovableBy.INHERIT)) {
			relevantOption = settingsService.getRolerequestApprover();
		}
		return relevantOption;
	}

	public List<RequestableBy> getInheritedRequesterPermission(UserRole userRole) {
		List<RequestableBy> relevantOption = userRole.getRequesterPermission();
		ItSystem itSystem = userRole.getItSystem();
		if (relevantOption.contains(RequestableBy.INHERIT)) {
			if (itSystem != null && itSystem.getRequesterPermission() != null && !itSystem.getRequesterPermission().contains(RequestableBy.INHERIT)) {
				relevantOption = itSystem.getRequesterPermission();
			} else {
				relevantOption = settingsService.getRolerequestRequester();
			}
		}
		return relevantOption;
	}

	public List<RequestableBy> getInheritedRequesterPermission(RoleGroup roleGroup) {
		List<RequestableBy> relevantOption = roleGroup.getRequesterPermission();
		if (relevantOption.contains(RequestableBy.INHERIT)) {
			relevantOption = settingsService.getRolerequestRequester();
		}
		return relevantOption;
	}

}
