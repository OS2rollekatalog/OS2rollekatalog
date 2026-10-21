package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.service.report.ReportColumnDefinitions;
import dk.digitalidentity.rc.service.report.ReportColumnDefinitions.ReportColumn;

import dk.digitalidentity.rc.dao.history.model.HistoryFunction;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OuRoleAssignmentReportRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRole;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.ReportService;
import dk.digitalidentity.rc.util.OrganisationConstraintUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReportXlsxView extends AbstractXlsxStreamingViewWrapper {
    private static final Set<String> OU_ROLE_WRAP_KEYS = Set.of("exceptedUsers", "titles", "negativeTitles", "functions");

    private LocalDate filterDate;
    private LocalDate queryDate;
    private ReportForm reportForm;
    private List<HistoryItSystem> itSystems;
    private List<HistorySystemRole> systemRoles;
    private Map<String, HistoryUser> users;
    private List<HistoryTitle> titles;
    private List<HistoryFunction> functions;
    private Map<String, HistoryTitle> titleMap;
	private Map<String, HistoryFunction> functionMap;
    private Map<String, List<HistoryKleAssignment>> userKLEAssignments;
    private Map<String, HistoryOU> orgUnits;
    private Map<String, HistoryOU> allOrgUnits;
    private Map<String, List<OuRoleAssignmentReportRow>> ouRoleAssignments;
    private Map<String, List<HistoryOUKleAssignment>> ouKLEAssignments;
    private Map<Long, String> itSystemNameMapping;
    private DateTimeFormatter localDateFormatter;
    private ResourceBundleMessageSource messageSource;
    private Locale locale;
    private CellStyle headerStyle;
    private CellStyle wrapStyle;
    private ReportService reportService;
    private OrgUnitService orgUnitService;
    private ItSystemService itSystemService;
    private OrganisationConstraintUtil organisationConstraintUtil;
    private List<HistoryOUUser> ouUsers;

    @SuppressWarnings("unchecked")
    @Override
    protected void buildExcelDocument(Map<String, ?> model, DisposableSXSSFWorkbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {

        // Get data
        locale = (Locale) model.get("locale");
        reportService = (ReportService) model.get("reportService");
        orgUnitService = (OrgUnitService) model.get("orgUnitService");
        itSystemService = (ItSystemService) model.get("itSystemService");
        organisationConstraintUtil = (OrganisationConstraintUtil) model.get("organisationConstraintUtil");
        filterDate = (LocalDate) model.get("filterDate");
        queryDate = (LocalDate) model.get("queryDate");
        users = (Map<String, HistoryUser>) model.get("users");
        titles = (List<HistoryTitle>) model.get("titles");
        functions = (List<HistoryFunction>) model.get("functions");
        orgUnits = (Map<String, HistoryOU>) model.get("orgUnits");
        allOrgUnits = (Map<String, HistoryOU>) model.get("allOrgUnits");
        itSystems = (List<HistoryItSystem>) model.get("itSystems");
        systemRoles = (List<HistorySystemRole>) model.get("systemRoles");
        messageSource = (ResourceBundleMessageSource) model.get("messagesBundle");
        ouKLEAssignments = (Map<String, List<HistoryOUKleAssignment>>) model.get("ouKLEAssignments");
        userKLEAssignments = (Map<String, List<HistoryKleAssignment>>) model.get("userKLEAssignments");
        ouRoleAssignments = (Map<String, List<OuRoleAssignmentReportRow>>) model.get("ouRoleAssignments");
		ouUsers = (List<HistoryOUUser>) model.get("ouUsers");

		if (titles != null) {
			titleMap = titles.stream().collect(Collectors.toMap(HistoryTitle::getTitleUuid, Function.identity()));
		}
		else {
			titleMap = new HashMap<>();
		}

		if (functions != null) {
			functionMap = functions.stream().collect(Collectors.toMap(HistoryFunction::getFunctionUuid, Function.identity()));
		}
		else {
			functionMap = new HashMap<>();
		}

		// Process data
        itSystemNameMapping = new HashMap<>();
        for (HistoryItSystem itSystem : itSystems) {
            for (HistoryUserRole userRole : itSystem.getHistoryUserRoles()) {
                itSystemNameMapping.put(userRole.getUserRoleId(), userRole.getUserRoleName());
            }
        }
        localDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Setup shared resources
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);

        reportForm = (ReportForm) model.get("reportForm");

        // Create Sheets
        createMasterDataSheet(workbook, reportForm);

        Map<String, List<String>> selectedColumns = reportForm.getSelectedColumns();

        if (reportForm.isShowUsers() && !ReportColumnDefinitions.isExplicitlyEmpty(ReportColumnDefinitions.USERS, selectedColumns)) {
			createUsersSheet(workbook);
        }

		if (reportForm.isShowItSystems() && !ReportColumnDefinitions.isExplicitlyEmpty(ReportColumnDefinitions.ITSYSTEMS, selectedColumns)) {
			createRoleSheet(workbook);
		}

		if (reportForm.isShowOUs()) {
			if (reportForm.isShowUserRoles() && !ReportColumnDefinitions.isExplicitlyEmpty(ReportColumnDefinitions.OU_ROLES, selectedColumns)) {
				createOURoleSheet(workbook);
			}

			if (!ReportColumnDefinitions.isExplicitlyEmpty(ReportColumnDefinitions.OU_KLE, selectedColumns)) {
				createOUKLESheet(workbook);
			}
		}

        if (reportForm.isShowUserRoles() && !ReportColumnDefinitions.isExplicitlyEmpty(ReportColumnDefinitions.USER_ROLES, selectedColumns)) {
            createUserRoleSheet(workbook, reportForm.isShowInactiveUsers());
        }

        if (reportForm.isShowNegativeRoles()) {
            createNegativeUserRoleSheet(workbook, reportForm.isShowInactiveUsers());
        }

		if (reportForm.isShowUsers() && !ReportColumnDefinitions.isExplicitlyEmpty(ReportColumnDefinitions.USER_KLE, selectedColumns)) {
			createUserKLESheet(workbook, reportForm.isShowInactiveUsers());
		}

		if (reportForm.isShowSystemRoles() && !ReportColumnDefinitions.isExplicitlyEmpty(ReportColumnDefinitions.SYSTEM_ROLES, selectedColumns)) {
			createSystemRolesSheet(workbook);
		}

		if (reportForm.isShowOrgUnitAllocations()) {
			createOrgUnitAllocationSheet(workbook);
		}
    }

	private void createSystemRolesSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.systemRoles.sheet.title", null, locale));

		List<ReportColumn> columns = ReportColumnDefinitions.resolveColumns(ReportColumnDefinitions.SYSTEM_ROLES, reportForm.getSelectedColumns());
		createHeaderRow(sheet, columns.stream().map(ReportColumn::messageKey).toList());

		int row = 1;
		for (HistorySystemRole entry : systemRoles) {
			Row dataRow = sheet.createRow(row++);

			Map<String, String> values = new LinkedHashMap<>();
			values.put("id", String.valueOf(entry.getSystemRoleId()));
			values.put("itSystem", entry.getHistoryItSystem().getItSystemName());
			values.put("name", entry.getSystemRoleName());
			values.put("description", entry.getSystemRoleDescription());

			int column = 0;
			for (ReportColumn col : columns) {
				createCell(dataRow, column++, values.get(col.key()), null);
			}
		}
	}

	private void createUsersSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.users.sheet.title", null, locale));

		List<ReportColumn> columns = ReportColumnDefinitions.resolveColumns(ReportColumnDefinitions.USERS, reportForm.getSelectedColumns());
		createHeaderRow(sheet, columns.stream().map(ReportColumn::messageKey).toList());

		CellStyle wrapCellStyle = workbook.createCellStyle();
		wrapCellStyle.setWrapText(true);
		String in = messageSource.getMessage("html.word.in", null, locale);

        int row = 1;
        for (HistoryUser entry : users.values()) {
            Row dataRow = sheet.createRow(row++);

    		List<HistoryOUUser> entryOUUsers = ouUsers.stream().filter(h -> h.getUserUuid().equals(entry.getUserUuid())).toList();
    		List<String> titleStrings = new ArrayList<>();
    		for (HistoryOUUser ouUser : entryOUUsers) {
				if (!Boolean.TRUE.equals(ouUser.getHasPosition())) {
					continue;
				}

    			if (ouUser.getTitleUuid() == null) {
    				titleStrings.add("• " + ouUser.getHistoryOU().getOuName());
    			}
    			else {
    				HistoryTitle title = titles.stream().filter(t -> t.getTitleUuid().equals(ouUser.getTitleUuid())).findAny().orElse(null);

    				if (title == null) {
    					titleStrings.add("• " + ouUser.getHistoryOU().getOuName());
    				}
    				else {
    					titleStrings.add("• " + title.getTitleName() + " (" + title.getTitleUuid() + ") " + in +  " " + ouUser.getHistoryOU().getOuName());
    				}
    			}
    		}

			Map<String, String> values = new LinkedHashMap<>();
			values.put("uuid", entry.getUserUuid());
			values.put("name", entry.getUserName());
			values.put("userId", entry.getUserUserId());
			values.put("positions", titleStrings.stream().collect(Collectors.joining("\n")));

			int column = 0;
			for (ReportColumn col : columns) {
				CellStyle style = "positions".equals(col.key()) ? wrapCellStyle : null;
				createCell(dataRow, column++, values.get(col.key()), style);
			}
        }
	}

	private void createMasterDataSheet(Workbook workbook, ReportForm reportForm) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.masterdata.sheet.title", null, locale));

        // Date and time report was generated
        Row headerRow = sheet.createRow(0);
        String dateNowHeader = messageSource.getMessage("xls.report.masterdata.date.now", null, locale);
        createCell(headerRow, 0, dateNowHeader, headerStyle);

        Row dateRow = sheet.createRow(1);
        createCell(dateRow, 0, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), null);

        Row filterDateRow = sheet.createRow(3);
        String dateFilterHeader = messageSource.getMessage("xls.report.masterdata.filter.date", null, locale);
        createCell(filterDateRow, 0, dateFilterHeader, headerStyle);
        createCell(filterDateRow, 1, filterDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), null);

    }

    private void createRoleSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.roles.sheet.title", null, locale));

        List<ReportColumn> columns = ReportColumnDefinitions.resolveColumns(ReportColumnDefinitions.ITSYSTEMS, reportForm.getSelectedColumns());
        createHeaderRow(sheet, columns.stream().map(ReportColumn::messageKey).toList());
        boolean includeConstraint = columns.stream().anyMatch(col -> "constraint".equals(col.key()));

        int row = 1;
        for (HistoryItSystem itSystem : itSystems) {
            for (HistoryUserRole userRole : itSystem.getHistoryUserRoles()) {
                for (HistorySystemRoleAssignment systemRoleAssignment : userRole.getHistorySystemRoleAssignments()) {
                    Row dataRow = sheet.createRow(row++);

                    StringBuilder constraintCell = new StringBuilder();
                    for (HistorySystemRoleAssignmentConstraint constraint : systemRoleAssignment.getHistoryConstraints()) {
                    	switch (constraint.getConstraintValueType()) {
	                		case READ_AND_WRITE:
	                            if ("KLE".equals(constraint.getConstraintName())) {
	                                constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.kle.read_and_write", null, locale) + "\n");
	                            }
	                            break;
                    		case EXTENDED_INHERITED:
                                if ("KLE".equals(constraint.getConstraintName())) {
                                    constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.kle.extended", null, locale) + "\n");
                                }
                                else if ("Organisation".equals(constraint.getConstraintName())) {
                                    constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.extended", null, locale) + "\n");
                                }
                                break;
                    		case INHERITED_FROM_MANAGER_ROLE:
                    			constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.inherited_from_manager_role", null, locale) + "\n");
                    			break;
                    		case EXTENDED_INHERITED_FROM_MANAGER_ROLE:
                    			constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.extended_inherited_from_manager_role", null, locale) + "\n");
                    			break;
                    		case INHERITED_FROM_FUNCTIONS:
                    			constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.inherited_from_functions", null, locale) + "\n");
                    			break;
                    		case EXTENDED_INHERITED_FROM_FUNCTIONS:
                    			constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.extended_inherited_from_functions", null, locale) + "\n");
                    			break;
                    		case INHERITED:
                                if ("KLE".equals(constraint.getConstraintName())) {
                                    constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.kle.inherited", null, locale) + "\n");
                                }
                                else if ("Organisation".equals(constraint.getConstraintName())) {
                                    constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.inherited", null, locale) + "\n");
                                }
                                break;
                    		case LEVEL_1:
                    		case LEVEL_2:
                    		case LEVEL_3:
                    		case LEVEL_4:
							case LEVEL_5:
							case LEVEL_6:
                    			if ("Organisation".equals(constraint.getConstraintName())) {
                    				switch (constraint.getConstraintValueType()) {
										case LEVEL_1:
											constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.level.1", null, locale) + "\n");
											break;
										case LEVEL_2:
											constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.level.2", null, locale) + "\n");
											break;
										case LEVEL_3:
											constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.level.3", null, locale) + "\n");
											break;
										case LEVEL_4:
											constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.level.4", null, locale) + "\n");
											break;
										case LEVEL_5:
											constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.level.5", null, locale) + "\n");
											break;
										case LEVEL_6:
											constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.level.6", null, locale) + "\n");
											break;
										default:
											break;
                    				}
                                }
                    			break;
                            case SELECTED_INHERITED:
                    		case VALUE:
                    			String constraintValue = constraint.getConstraintValue();

								List<String> values;
								String[] constraintValues;
								// TODO: not super robust, but as the history table only stores names, and not ID's, this is
								//       what is currently possible
								switch (constraint.getConstraintName()) {
									case "It-system":
										values = new ArrayList<>();
                                        constraintValues = constraint.getConstraintValueType().equals(ConstraintValueType.VALUE) ? (constraintValue == null ? new String[0] : constraintValue.split(",")) : organisationConstraintUtil.getOrganisationConstraintUuids(constraintValue).toArray(String[]::new);
										for (String id : constraintValues) {
                                            if (id.isEmpty()) {
                                                continue;
                                            }
											var cvItSystem = itSystemService.getById(Long.parseLong(id));
											if (cvItSystem == null) {
												values.add(id);
											}
											else {
												values.add(cvItSystem.getName() + "(" + id + ")");
											}
										}
										constraintValue = values.stream().collect(Collectors.joining(", "));
										break;
									case "Organisation":
									case "Enhed":
										values = new ArrayList<>();
                                        constraintValues = constraint.getConstraintValueType().equals(ConstraintValueType.VALUE) ? (constraintValue == null ? new String[0] : constraintValue.split(",")) : organisationConstraintUtil.getOrganisationConstraintUuids(constraintValue).toArray(String[]::new);
										for (String uuid : constraintValues) {
											var cvOrgUnit = orgUnitService.getByUuid(uuid);
											if (cvOrgUnit == null) {
												values.add(uuid);
											}
											else {
												values.add(cvOrgUnit.getName() + "(" + uuid + ")");
											}
										}
										constraintValue = values.stream().collect(Collectors.joining(", "));
										break;
									default:
										break;
								}
								constraintCell.append(constraint.getConstraintName() + " = " + constraintValue + "\n");
								break;
							case POSTPONED:
								constraintCell.append("Udskudt");
								break;
                    	}

                        if (constraintCell.length() == 0) {
                            constraintCell.append(messageSource.getMessage("xls.constraint.error", null, locale) + "\n");
                        }
                    }

                    Map<String, String> values = new LinkedHashMap<>();
                    values.put("itSystemName", itSystem.getItSystemName());
                    values.put("userRoleName", userRole.getUserRoleName());
                    values.put("userRoleDescription", userRole.getUserRoleDescription());
                    values.put("systemRoleName", systemRoleAssignment.getSystemRoleName());
                    values.put("systemRoleWeight", String.valueOf(itSystem.getHistorySystemRoles().stream()
                            .filter(historySystemRole -> historySystemRole.getSystemRoleId() == systemRoleAssignment.getSystemRoleId())
                            .map(HistorySystemRole::getWeight)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(1L)));
                    values.put("constraint", constraintCell.toString());

                    int column = 0;
                    for (ReportColumn col : columns) {
                        createCell(dataRow, column++, values.get(col.key()), null);
                    }

                    if (includeConstraint && systemRoleAssignment.getHistoryConstraints() != null) {
                        dataRow.setHeight((short) (dataRow.getHeight() * (1 + systemRoleAssignment.getHistoryConstraints().size())));
                    }
                }
            }
        }
    }

    //TODO this still needs fixing
    private void createOURoleSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.ou.roles.sheet.title", null, locale));

        List<ReportColumn> columns = ReportColumnDefinitions.resolveColumns(ReportColumnDefinitions.OU_ROLES, reportForm.getSelectedColumns());
        createHeaderRow(sheet, columns.stream().map(ReportColumn::messageKey).toList());

        int row = 1;
        for (Map.Entry<String, List<OuRoleAssignmentReportRow>> entry : ouRoleAssignments.entrySet()) {
            HistoryOU ou = orgUnits.get(entry.getKey());
            if (ou == null) {
                // this OU has been filtered out, so skip
                continue;
            }

            String ouName = ou.getOuName();
            List<OuRoleAssignmentReportRow> assignments = entry.getValue();

            for (OuRoleAssignmentReportRow ouRoleAssignment : assignments) {

                String itSystem = ouRoleAssignment.getItSystemName() != null ? ouRoleAssignment.getItSystemName() : "";

                // Creating assigned by
                String assignedBy = ouRoleAssignment.getAssignedByName() + " (" + ouRoleAssignment.getAssignedByUserId() + ")";

                // Creating assigned Through string
                String assignedThroughStr = "";
                switch (ouRoleAssignment.getAssignedThroughType()) {
                    case null:
                    case DIRECT:
                    case ROLEGROUP:
                        assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.direct", null, locale);
                        break;
                    case POSITION:
                        assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.position", null, locale);
                        break;
                    case ORGUNIT:
                        assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.orgunit", null, locale);
                        break;
                    case TITLE:
                        assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.title", null, locale);
                        break;
                }
                if (StringUtils.hasLength(ouRoleAssignment.getAssignedThroughName())) {
                    assignedThroughStr += ": " + ouRoleAssignment.getAssignedThroughName();
                }

                // Collect exclusions
                String exceptedUsersStr = joinExcludedUsers(ouRoleAssignment.getExceptedUserUuids());
                String titlesStr = ouRoleAssignment.getSelectedTitleUuids().stream()
                    .map(titleUuid -> titleMap.containsKey(titleUuid) ? titleMap.get(titleUuid).getTitleName() : titleUuid)
                    .collect(Collectors.joining("\n"));
                String excludedTitlesStr = ouRoleAssignment.getExcludedTitleUuids().stream()
                    .map(titleUuid -> titleMap.containsKey(titleUuid) ? titleMap.get(titleUuid).getTitleName() : titleUuid)
                    .collect(Collectors.joining("\n"));
                String functionsStr = ouRoleAssignment.getFunctionUuids().stream()
                    .map(functionUuid -> functionMap.containsKey(functionUuid) ? functionMap.get(functionUuid).getFunctionName() : functionUuid)
                    .collect(Collectors.joining("\n"));

				String managerAndSubstitutesText = null;
				if (ouRoleAssignment.isManager()) {
					if (ouRoleAssignment.isSubstitutes()) {
						managerAndSubstitutesText = "xls.report.ou.roles.manager.type.managerAndSubstitutes";
					} else {
						managerAndSubstitutesText = "xls.report.ou.roles.manager.type.manager";
					}
				}

                Row dataRow = sheet.createRow(row++);

                Map<String, String> values = new LinkedHashMap<>();
                values.put("ouName", ouName);
                values.put("ouUuid", entry.getKey());
                values.put("roleName", itSystemNameMapping.get(ouRoleAssignment.getRoleId()));
                values.put("itSystem", itSystem);
                values.put("assignedBy", assignedBy);
                values.put("startDate", bestStartDate(ouRoleAssignment.getAssignedWhen(), ouRoleAssignment.getStartDate(), localDateFormatter));
                values.put("stopDate", formatLocalDateTime(atEndOfDay(ouRoleAssignment.getStopDate())));
                values.put("assignedThrough", assignedThroughStr);
                values.put("managerType", managerAndSubstitutesText == null ? "" : messageSource.getMessage(managerAndSubstitutesText, null, locale));
                values.put("exceptedUsers", exceptedUsersStr);
                values.put("titles", titlesStr);
                values.put("negativeTitles", excludedTitlesStr);
                values.put("functions", functionsStr);

                int column = 0;
                for (ReportColumn col : columns) {
                    createCell(dataRow, column++, values.get(col.key()), OU_ROLE_WRAP_KEYS.contains(col.key()) ? wrapStyle : null);
                }
            }
        }
    }


    private void createOUKLESheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.ou.kle.sheet.title", null, locale));

        List<ReportColumn> columns = ReportColumnDefinitions.resolveColumns(ReportColumnDefinitions.OU_KLE, reportForm.getSelectedColumns());
        createHeaderRow(sheet, columns.stream().map(ReportColumn::messageKey).toList());

        int row = 1;
        for (HistoryOU ou : orgUnits.values()) {
        	List<HistoryOUKleAssignment> entries = ouKLEAssignments.get(ou.getOuUuid());
        	if (entries == null) {
        		continue;
        	}

            String ouName = ou.getOuName();
            for (HistoryOUKleAssignment ouKleAssignment : entries) {
                Row dataRow = sheet.createRow(row++);

                String assignmentType = ouKleAssignment.getAssignmentType();
                if (Objects.equals(assignmentType, "INTEREST")) {
                    assignmentType = "Indsigtsbehov";
                }
                else if (Objects.equals(assignmentType, "PERFORMING")) {
                    assignmentType = "Opgaveansvar";
                }

                Map<String, String> values = new LinkedHashMap<>();
                values.put("ouName", ouName);
                values.put("assignmentType", assignmentType);
                values.put("kleValues", ouKleAssignment.getKleValues());

                int column = 0;
                for (ReportColumn col : columns) {
                    createCell(dataRow, column++, values.get(col.key()), null);
                }
            }
        }
    }

    private void createUserRoleSheet(Workbook workbook, boolean showInactiveUsers) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.user.roles.sheet.title", null, locale));

        List<ReportColumn> columns = ReportColumnDefinitions.resolveColumns(ReportColumnDefinitions.USER_ROLES, reportForm.getSelectedColumns());
        createHeaderRow(sheet, columns.stream().map(ReportColumn::messageKey).toList());

        Set<String> allowedDisplayOuUuids = buildAllowedDisplayOuUuids();
        Set<String> allowedResponsibleOuUuids = buildAllowedResponsibleOuUuids();
        Set<String> allowedUserUuids = (allowedDisplayOuUuids != null || allowedResponsibleOuUuids != null) ? users.keySet() : null;
        List<Long> itSystemFilter = reportForm.getItSystems();
        Collection<Long> itSystemIds = (itSystemFilter != null && !itSystemFilter.isEmpty()) ? itSystemFilter : null;

        AtomicInteger row = new AtomicInteger(1);
        reportService.streamUserRoleAssignmentReportEntries(
            queryDate, filterDate, allowedUserUuids, allowedDisplayOuUuids, allowedResponsibleOuUuids, itSystemIds,
            users, allOrgUnits, itSystems, locale, showInactiveUsers, true, entry -> {
                Row dataRow = sheet.createRow(row.getAndIncrement());

                Map<String, String> values = new LinkedHashMap<>();
                values.put("userName", entry.getUserName());
                values.put("userId", entry.getUserId());
                values.put("employeeId", entry.getEmployeeId());
                values.put("ouName", entry.getOrgUnitName());
                values.put("ouUuid", entry.getOrgUnitUUID());
                values.put("userActive", entry.isUserActive() ? "aktiv" : "inaktiv");
                values.put("roleName", itSystemNameMapping.get(entry.getRoleId()));
                values.put("itSystem", entry.getItSystem());
                values.put("systemRoleWeight", "" + entry.getSystemRoleWeight());
                values.put("weightResult", "" + entry.getItSystemResultWeight());
                values.put("assignedBy", entry.getAssignedBy());
                values.put("startDate", bestStartDate(entry.getAssignedWhen(), entry.getStartDate(), localDateFormatter));
                values.put("stopDate", formatLocalDateTime(atEndOfDay(entry.getStopDate())));
                values.put("assignedThrough", entry.getAssignedThrough());
                values.put("postponedConstraints", entry.getPostponedConstraints());

                int column = 0;
                for (ReportColumn col : columns) {
                    createCell(dataRow, column++, values.get(col.key()), null);
                }
            });
    }

    private void createNegativeUserRoleSheet(Workbook workbook, boolean showInactiveUsers) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.user.roles.sheet.negativetitle", null, locale));

        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.user.name");
        headers.add("xls.user.id");
        headers.add("xls.role.employee.id");
        headers.add("xls.role.ou.name");
        headers.add("xls.role.ou.uuid");
        headers.add("xls.user.active");
        headers.add("xls.role.name");
        headers.add("xls.role.it.system");
        headers.add("xls.role.assigned.by");
        headers.add("xls.role.start_date");
        headers.add("xls.role.stop_date");
        headers.add("xls.role.assigned.through");
        headers.add("xls.role.postponedconstraints"); // TODO can be removed - not possible on OU role assignments (negative assignment = ou assignment)

        createHeaderRow(sheet, headers);

        Set<String> allowedDisplayOuUuids = buildAllowedDisplayOuUuids();
        Set<String> allowedResponsibleOuUuids = buildAllowedResponsibleOuUuids();
        Set<String> allowedUserUuids = (allowedDisplayOuUuids != null || allowedResponsibleOuUuids != null) ? users.keySet() : null;
        List<Long> itSystemFilter = reportForm.getItSystems();
        Collection<Long> itSystemIds = (itSystemFilter != null && !itSystemFilter.isEmpty()) ? itSystemFilter : null;

        AtomicInteger row = new AtomicInteger(1);
        reportService.streamNegativeUserRoleAssignmentReportEntries(
            queryDate, filterDate, allowedUserUuids, allowedDisplayOuUuids, allowedResponsibleOuUuids, itSystemIds,
            users, allOrgUnits, locale, showInactiveUsers, entry -> {
                Row dataRow = sheet.createRow(row.getAndIncrement());
                int column = 0;

                createCell(dataRow, column++, entry.getUserName(), null);
                createCell(dataRow, column++, entry.getUserId(), null);
                createCell(dataRow, column++, entry.getEmployeeId(), null);
                createCell(dataRow, column++, entry.getOrgUnitName(), null);
                createCell(dataRow, column++, entry.getOrgUnitUUID(), null);
                createCell(dataRow, column++, entry.isUserActive() ? "aktiv" : "inaktiv", null);
                createCell(dataRow, column++, itSystemNameMapping.get(entry.getRoleId()), null);
                createCell(dataRow, column++, entry.getItSystem(), null);
                createCell(dataRow, column++, entry.getAssignedBy(), null);
                createCell(dataRow, column++, bestStartDate(entry.getAssignedWhen(), entry.getStartDate(), localDateFormatter), null);
                createCell(dataRow, column++, formatLocalDateTime(atEndOfDay(entry.getStopDate())), null);
                createCell(dataRow, column++, entry.getAssignedThrough(), null);
                createCell(dataRow, column++, entry.getPostponedConstraints(), null);
            });
    }

    /**
     * Returns the set of allowed OU UUIDs when the org-unit filter is active,
     * or null if not. Matched against each row's displayed OU.
     */
    private Set<String> buildAllowedDisplayOuUuids() {
        List<String> ouFilter = reportForm.getOrgUnits();
        if (ouFilter != null && !ouFilter.isEmpty()) {
            return orgUnits.keySet();
        }
        return null;
    }

    /**
     * Returns the set of allowed OU UUIDs when the manager filter (reportForm.getManager()) is active,
     * or null if not. Matched against each assignment's responsibleOUUuid..
     */
    private Set<String> buildAllowedResponsibleOuUuids() {
        String manager = reportForm.getManager();
        if (StringUtils.hasLength(manager)) {
            return orgUnits.keySet();
        }
        return null;
    }

    private void createUserKLESheet(Workbook workbook, boolean showInactiveUsers) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.user.kle.sheet.title", null, locale));

        List<ReportColumn> columns = ReportColumnDefinitions.resolveColumns(ReportColumnDefinitions.USER_KLE, reportForm.getSelectedColumns());
        createHeaderRow(sheet, columns.stream().map(ReportColumn::messageKey).toList());

        // enhance existing KLE assignments from OU assignments
        for (HistoryOU ou : allOrgUnits.values()) {
        	// skip OUs without any users
        	if (ou.getUsers() == null || ou.getUsers().size() == 0) {
        		continue;
        	}

        	// skip OUs without KLE assignments
        	List<HistoryOUKleAssignment> ouKleAssignments = ouKLEAssignments.get(ou.getOuUuid());
        	if (ouKleAssignments == null || ouKleAssignments.size() == 0) {
        		continue;
        	}

        	for (HistoryOUUser ouUser : ou.getUsers()) {
        		// minor optimization - no reason to map extra KLE for users that are not included in the report
        		if (users.get(ouUser.getUserUuid()) == null) {
        			continue;
        		}

				if (!Boolean.TRUE.equals(ouUser.getHasPosition())) {
					continue;
				}

        		List<HistoryKleAssignment> existingUserAssignments = userKLEAssignments.get(ouUser.getUserUuid());
        		if (existingUserAssignments == null) {
        			existingUserAssignments = new ArrayList<HistoryKleAssignment>();
        			userKLEAssignments.put(ouUser.getUserUuid(), existingUserAssignments);
        		}

        		for (HistoryOUKleAssignment ouKleAssignment : ouKleAssignments) {
    				HistoryKleAssignment assignment = new HistoryKleAssignment(ouUser.getUserUuid(), ouKleAssignment.getAssignmentType(), ouKleAssignment.getKleValues(), ou.getOuName());
    				existingUserAssignments.add(assignment);
        		}
        	}
        }

        int row = 1;
        for (HistoryUser user : users.values()) {
        	List<HistoryKleAssignment> entries = userKLEAssignments.get(user.getUserUuid());

            // Skip inactive users if showInactive users = false
			if (entries == null || (!showInactiveUsers && !user.isUserActive())) {
				continue;
			}

            String userName = user.getUserName();
            String userId = user.getUserUserId();
            boolean userActive = user.isUserActive();

            List<HistoryKleAssignment> interestAssignments = entries.stream().filter(e -> Objects.equals(e.getAssignmentType(), "INTEREST")).collect(Collectors.toList());
            List<HistoryKleAssignment> performingAssignments = entries.stream().filter(e -> Objects.equals(e.getAssignmentType(), "PERFORMING")).collect(Collectors.toList());

            // duplicated code *sigh*

            Set<String> codeSum = new HashSet<>();
            for (HistoryKleAssignment kleAssignment : interestAssignments) {
                String source = StringUtils.hasLength(kleAssignment.getInheritedFrom()) ? "Ansat: " + kleAssignment.getInheritedFrom() : "Personlige";
                writeUserKleRow(sheet, row++, columns, userName, userId, userActive, "Indsigtsbehov", kleAssignment.getKleValues(), source);

                String[] kleCodes = kleAssignment.getKleValues().split(",");
                for (String kleCode : kleCodes) {
                	codeSum.add(kleCode);
                }
            }

            // output sum of above rows
            if (codeSum.size() > 0) {
                writeUserKleRow(sheet, row++, columns, userName, userId, userActive, "Indsigtsbehov", String.join(",", codeSum), "Summen");
            }

            codeSum = new HashSet<>();
            for (HistoryKleAssignment kleAssignment : performingAssignments) {
                String source = StringUtils.hasLength(kleAssignment.getInheritedFrom()) ? "Ansat: " + kleAssignment.getInheritedFrom() : "Personlige";
                writeUserKleRow(sheet, row++, columns, userName, userId, userActive, "Opgaveansvar", kleAssignment.getKleValues(), source);

                String[] kleCodes = kleAssignment.getKleValues().split(",");
                for (String kleCode : kleCodes) {
                	codeSum.add(kleCode);
                }
            }

            // output sum of above rows
            if (codeSum.size() > 0) {
                writeUserKleRow(sheet, row++, columns, userName, userId, userActive, "Opgaveansvar", String.join(",", codeSum), "Summen");
            }
        }
    }

    private void writeUserKleRow(Sheet sheet, int rowIndex, List<ReportColumn> columns, String userName, String userId,
            boolean userActive, String assignmentType, String kleValues, String source) {
        Row dataRow = sheet.createRow(rowIndex);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("userName", userName);
        values.put("userId", userId);
        values.put("userActive", userActive ? "aktiv" : "inaktiv");
        values.put("assignmentType", assignmentType);
        values.put("kleValues", kleValues);
        values.put("source", source);

        int column = 0;
        for (ReportColumn col : columns) {
            createCell(dataRow, column++, values.get(col.key()), null);
        }
    }

	private void createOrgUnitAllocationSheet(final Workbook workbook) {
		new OrgUnitAllocationSheetWriter(messageSource, locale, headerStyle, wrapStyle, users, titleMap, orgUnits, ouRoleAssignments)
			.write(workbook);
	}

	private String joinExcludedUsers(final List<String> userUuids) {
		if (userUuids == null) {
			return "";
		}
		return userUuids.stream()
			.map(userUuid -> users.containsKey(userUuid)
				? users.get(userUuid).getUserName() + " (" + users.get(userUuid).getUserUserId() + ")"
				: userUuid)
			.collect(Collectors.joining("\n"));
	}

    static void createCell(Row header, int column, String value, CellStyle style) {
        if (value != null && value.length() > 32767) {
			value = value.substring(0, 32767 - 3) + "...";
		}

		Cell cell = header.createCell(column);
        cell.setCellValue(value);

        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void createHeaderRow(Sheet sheet, List<String> headers) {
        Row headerRow = sheet.createRow(0);

        int column = 0;
        for (String header : headers) {
            String localeSpecificHeader = messageSource.getMessage(header, null, locale);
            createCell(headerRow, column++, localeSpecificHeader, headerStyle);
        }
    }

    private static String bestStartDate(final LocalDateTime assigned, final LocalDate startDate, DateTimeFormatter localDateFormatter) {
        if (startDate != null) {
            return startDate.atStartOfDay().format(localDateFormatter);
        } else if (assigned != null) {
        	return assigned.format(localDateFormatter);
        }

        return "";
    }

    private String formatLocalDateTime(final LocalDateTime when) {
        if (when == null) {
            return null;
        }
        return localDateFormatter.format(when);
    }

    private static LocalDateTime atEndOfDay(final LocalDate when) {
        if (when == null) {
            return null;
        }
        return when.plusDays(1).atStartOfDay().minusSeconds(1);
    }

	@Override
	protected String getFilename() {
		return "Rapport.xlsx";
	}
}
