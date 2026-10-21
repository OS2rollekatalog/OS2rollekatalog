package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
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
	private final MessageSource messageSource;

	public String getApproverOptionsAsString(ApproverPermission approverPermission) {
		return getApproverOptionsAsString(approverPermission.getApprovableBy());
	}

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
}
