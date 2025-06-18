package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApproverOptionService {
	private final SettingsService settingsService;
	private final MessageSource messageSource;

	public String getApproverOptionsAsString(ApproverOption approverOption, ItSystem itSystem) {
		ApproverOption relevantOption = approverOption;
		if (relevantOption.equals(ApproverOption.INHERIT)) {
			if (itSystem != null && itSystem.getApproverPermission() != null && !itSystem.getApproverPermission().equals(ApproverOption.INHERIT)) {
				relevantOption = itSystem.getApproverPermission();
			} else {
				relevantOption = settingsService.getRolerequestApprover();
			}
		}
		List<String> approvableByList = relevantOption.getApproverPermissions().stream()
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
	public ApproverOption getInheritedApproverOption(UserRole userRole) {
		ApproverOption relevantOption = userRole.getApproverPermission();
		ItSystem itSystem = userRole.getItSystem();
		if (relevantOption.equals(ApproverOption.INHERIT)) {
			if (itSystem != null && itSystem.getApproverPermission() != null && !itSystem.getApproverPermission().equals(ApproverOption.INHERIT)) {
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
	public ApproverOption getInheritedApproverOption(RoleGroup roleGroup) {
		ApproverOption relevantOption = roleGroup.getApproverPermission();
		if (relevantOption.equals(ApproverOption.INHERIT)) {
			relevantOption = settingsService.getRolerequestApprover();
		}
		return relevantOption;
	}

	public RequesterOption getInheritedRequesterPermission(UserRole userRole) {
		RequesterOption relevantOption = userRole.getRequesterPermission();
		ItSystem itSystem = userRole.getItSystem();
		if (relevantOption.equals(RequesterOption.INHERIT)) {
			if (itSystem != null && itSystem.getRequesterPermission() != null && !itSystem.getRequesterPermission().equals(RequesterOption.INHERIT)) {
				relevantOption = itSystem.getRequesterPermission();
			} else {
				relevantOption = settingsService.getRolerequestRequester();
			}
		}
		return relevantOption;
	}

	public RequesterOption getInheritedRequesterPermission(RoleGroup roleGroup) {
		RequesterOption relevantOption = roleGroup.getRequesterPermission();
		if (relevantOption.equals(RequesterOption.INHERIT)) {
			relevantOption = settingsService.getRolerequestRequester();
		}
		return relevantOption;
	}

}
