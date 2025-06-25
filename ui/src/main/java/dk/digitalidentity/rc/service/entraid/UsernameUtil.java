package dk.digitalidentity.rc.service.entraid;

import com.microsoft.graph.models.User;
import dk.digitalidentity.rc.config.model.AzureUsernameField;

public class UsernameUtil {

	public static boolean matchesUsername(User user, String usernameToMatch, AzureUsernameField field) {
		if (usernameToMatch == null || user == null) return false;

		return switch (field) {
			case MAIL_NICKNAME -> usernameToMatch.equalsIgnoreCase(user.getMailNickname());
			case SAMACCOUNT_NAME -> usernameToMatch.equalsIgnoreCase(user.getOnPremisesSamAccountName());
			case UPN -> usernameToMatch.equalsIgnoreCase(user.getUserPrincipalName());
		};
	}

	public static String getUsernameFromUser(User user, AzureUsernameField field) {
		if (user == null) return null;

		return switch (field) {
			case MAIL_NICKNAME -> user.getMailNickname();
			case SAMACCOUNT_NAME -> user.getOnPremisesSamAccountName();
			case UPN -> user.getUserPrincipalName();
		};
	}

	public static String getGraphFieldName(AzureUsernameField field) {
		return switch (field) {
			case MAIL_NICKNAME -> "mailNickname";
			case SAMACCOUNT_NAME -> "onPremisesSamAccountName";
			case UPN -> "userPrincipalName";
		};
	}
}
