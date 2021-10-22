package dk.digitalidentity.rc.dao.model.enums;
import lombok.Getter;

@Getter
public enum ReportType {
	USER_ROLE_WITHOUT_ASSIGNMENTS("html.report.user_roles_without_assignments"),
	USER_ROLE_WITHOUT_SYSTEM_ROLES("html.report.user_roles_without_system_roles"),
	USER_ROLE_WITH_SYSTEM_ROLE_THAT_COULD_BE_CONSTRAINT_BUT_ISNT("html.report.user_roles_w_sys_role_but_no_constraint"),
	USER_ROLE_WITH_SENSITIVE_FLAG("html.report.user_roles_with_sensitive_flag"),
	USERS_WITH_DUPLICATE_USERROLE_ASSIGNMENTS("html.report.users_with_duplicate_userrole_assignments"),
	USERS_WITH_DUPLICATE_ROLEGROUP_ASSIGNMENTS("html.report.users_with_duplicate_rolegroup_assignments");

	private String title;

	private ReportType(String title) {
		this.title = title;
	}
}
