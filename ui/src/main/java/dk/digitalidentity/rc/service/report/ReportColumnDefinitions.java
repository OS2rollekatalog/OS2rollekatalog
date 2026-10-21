package dk.digitalidentity.rc.service.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines columns for each report sheet and their order.
 */
public final class ReportColumnDefinitions {

	public record ReportColumn(String key, String messageKey, boolean defaultSelected) {
	}

	// These identify a content type in saved templates (DB) and the browser's localStorage,
	// so they should not be renamed without consideration.
	public static final String USERS = "ShowUsers";
	public static final String OU_ROLES = "ShowOUs";
	public static final String OU_KLE = "ShowOUKLE";
	public static final String USER_ROLES = "ShowUserRoles";
	public static final String USER_KLE = "ShowKLE";
	public static final String ITSYSTEMS = "ShowItSystems";
	public static final String SYSTEM_ROLES = "ShowSystemRoles";

	private static final Map<String, List<ReportColumn>> DEFINITIONS = buildDefinitions();

	private ReportColumnDefinitions() {
	}

	public static Map<String, List<ReportColumn>> all() {
		return DEFINITIONS;
	}

	public static List<ReportColumn> columnsFor(String contentTypeKey) {
		return DEFINITIONS.getOrDefault(contentTypeKey, List.of());
	}

	/**
	 * Picks which columns a sheet should have.
	 * <p>
	 * Falls back to all columns if nothing was selected, or if none of the selected keys are
	 * valid for this sheet (e.g. a typo) — a sheet should never end up with zero columns.
	 * <p>
	 * Selected columns keep the order defined above, not submission order.
	 */
	public static List<ReportColumn> resolveColumns(String contentTypeKey, Map<String, List<String>> selected) {
		List<ReportColumn> allColumns = columnsFor(contentTypeKey);

		if (selected == null) {
			return allColumns;
		}

		List<String> selectedKeys = selected.get(contentTypeKey);
		if (selectedKeys == null || selectedKeys.isEmpty()) {
			return allColumns;
		}

		List<ReportColumn> filtered = allColumns.stream()
			.filter(column -> selectedKeys.contains(column.key()))
			.toList();

		return filtered.isEmpty() ? allColumns : filtered;
	}

	/**
	 * True when the caller explicitly selected zero columns for this content type (as opposed to
	 * not mentioning it at all, which means "use all columns" for backward compatibility). The
	 * caller should treat this as "exclude this sheet", the same as its checkbox being unchecked.
	 */
	public static boolean isExplicitlyEmpty(String contentTypeKey, Map<String, List<String>> selected) {
		if (selected == null || !selected.containsKey(contentTypeKey)) {
			return false;
		}

		List<String> selectedKeys = selected.get(contentTypeKey);
		return selectedKeys != null && selectedKeys.isEmpty();
	}

	// Column definitions per sheet.
	private static Map<String, List<ReportColumn>> buildDefinitions() {
		Map<String, List<ReportColumn>> definitions = new LinkedHashMap<>();

		definitions.put(USERS, List.of(
			new ReportColumn("uuid", "xls.report.users.uuid", true),
			new ReportColumn("name", "xls.report.users.name", true),
			new ReportColumn("userId", "xls.report.users.userId", true),
			new ReportColumn("positions", "xls.report.users.positions", true)
		));

		definitions.put(OU_ROLES, List.of(
			new ReportColumn("ouName", "xls.report.ou.roles.ou.name", true),
			new ReportColumn("ouUuid", "xls.report.ou.roles.ou.uuid", true),
			new ReportColumn("roleName", "xls.role.name", true),
			new ReportColumn("itSystem", "xls.role.it.system", true),
			new ReportColumn("assignedBy", "xls.role.assigned.by", true),
			new ReportColumn("startDate", "xls.role.start_date", true),
			new ReportColumn("stopDate", "xls.role.stop_date", true),
			new ReportColumn("assignedThrough", "xls.role.assigned.through", true),
			new ReportColumn("managerType", "xls.report.ou.roles.manager.type", true),
			new ReportColumn("exceptedUsers", "xls.report.ou.roles.exceptedusers", true),
			new ReportColumn("titles", "xls.report.ou.roles.titles", true),
			new ReportColumn("negativeTitles", "xls.report.ou.roles.titles.negative", true),
			new ReportColumn("functions", "xls.report.ou.roles.functions", true)
		));

		definitions.put(OU_KLE, List.of(
			new ReportColumn("ouName", "xls.report.ou.kle.ou.name", true),
			new ReportColumn("assignmentType", "xls.report.ou.kle.assignment.type", true),
			new ReportColumn("kleValues", "xls.report.ou.kle.kle.values", true)
		));

		definitions.put(USER_ROLES, List.of(
			new ReportColumn("userName", "xls.user.name", true),
			new ReportColumn("userId", "xls.user.id", true),
			new ReportColumn("employeeId", "xls.role.employee.id", true),
			new ReportColumn("ouName", "xls.role.ou.name", true),
			new ReportColumn("ouUuid", "xls.role.ou.uuid", true),
			new ReportColumn("userActive", "xls.user.active", true),
			new ReportColumn("roleName", "xls.role.name", true),
			new ReportColumn("itSystem", "xls.role.it.system", true),
			new ReportColumn("systemRoleWeight", "xls.role.it.system_role_weight", true),
			new ReportColumn("weightResult", "xls.role.it.weight_result", true),
			new ReportColumn("assignedBy", "xls.role.assigned.by", true),
			new ReportColumn("startDate", "xls.role.start_date", true),
			new ReportColumn("stopDate", "xls.role.stop_date", true),
			new ReportColumn("assignedThrough", "xls.role.assigned.through", true),
			new ReportColumn("postponedConstraints", "xls.role.postponedconstraints", true)
		));

		definitions.put(USER_KLE, List.of(
			new ReportColumn("userName", "xls.user.name", true),
			new ReportColumn("userId", "xls.user.id", true),
			new ReportColumn("userActive", "xls.user.active", true),
			new ReportColumn("assignmentType", "xls.report.user.kle.assignment.type", true),
			new ReportColumn("kleValues", "xls.report.user.kle.kle.values", true),
			new ReportColumn("source", "xls.report.user.kle.source", true)
		));

		definitions.put(ITSYSTEMS, List.of(
			new ReportColumn("itSystemName", "xls.report.roles.it.system.name", true),
			new ReportColumn("userRoleName", "xls.report.roles.user.role.name", true),
			new ReportColumn("userRoleDescription", "xls.report.roles.user.role.description", true),
			new ReportColumn("systemRoleName", "xls.report.roles.system.role.name", true),
			new ReportColumn("systemRoleWeight", "xls.report.roles.system.role.weight", true),
			new ReportColumn("constraint", "xls.report.roles.constraint", true)
		));

		definitions.put(SYSTEM_ROLES, List.of(
			new ReportColumn("id", "xls.report.systemRoles.id", true),
			new ReportColumn("itSystem", "xls.report.systemRoles.itSystem", true),
			new ReportColumn("name", "xls.report.systemRoles.name", true),
			new ReportColumn("description", "xls.report.systemRoles.description", true)
		));

		return definitions;
	}
}
