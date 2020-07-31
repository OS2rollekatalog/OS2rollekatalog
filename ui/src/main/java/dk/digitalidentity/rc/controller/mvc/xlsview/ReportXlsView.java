package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.view.document.AbstractXlsView;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;

public class ReportXlsView extends AbstractXlsView {
    private LocalDate filterDate;
    private List<HistoryItSystem> itSystems;
    private Map<String, HistoryUser> users;
    private List<HistoryTitle> titles;
    private Map<String, List<HistoryRoleAssignment>> userRoleAssignments;
    private Map<String, List<HistoryKleAssignment>> userKLEAssignments;
    private Map<String, HistoryOU> orgUnits;
    private Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments;
    private Map<String, List<HistoryOUKleAssignment>> ouKLEAssignments;
    private Map<Long, String> itSystemNameMapping;
    private SimpleDateFormat dateFormatter;
    private ResourceBundleMessageSource messageSource;
    private Locale locale;
    private CellStyle headerStyle;
    private CellStyle wrapStyle;

    @SuppressWarnings("unchecked")
    @Override
    protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {

        // Get data
        locale = (Locale) model.get("locale");
        filterDate = (LocalDate) model.get("filterDate");
        users = (Map<String, HistoryUser>) model.get("users");
        titles = (List<HistoryTitle>) model.get("titles");
        orgUnits = (Map<String, HistoryOU>) model.get("orgUnits");
        itSystems = (List<HistoryItSystem>) model.get("itSystems");
        messageSource = (ResourceBundleMessageSource) model.get("messagesBundle");
        ouKLEAssignments = (Map<String, List<HistoryOUKleAssignment>>) model.get("ouKLEAssignments");
        userKLEAssignments = (Map<String, List<HistoryKleAssignment>>) model.get("userKLEAssignments");
        ouRoleAssignments = (Map<String, List<HistoryOURoleAssignment>>) model.get("ouRoleAssignments");
        userRoleAssignments = (Map<String, List<HistoryRoleAssignment>>) model.get("userRoleAssignments");
        
        // Process data
        itSystemNameMapping = new HashMap<>();
        for (HistoryItSystem itSystem : itSystems) {
            for (HistoryUserRole userRole : itSystem.getHistoryUserRoles()) {
                itSystemNameMapping.put(userRole.getUserRoleId(), userRole.getUserRoleName());
            }
        }
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        // Setup shared resources
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);

        ReportForm reportForm = (ReportForm) model.get("reportForm");

        // Create Sheets
        createMasterDataSheet(workbook, reportForm);

		if (reportForm.isShowItSystems()) {
			createRoleSheet(workbook);
		}

		if (reportForm.isShowOUs()) {	    	
			if (reportForm.isShowUserRoles()) {
				createOURoleSheet(workbook);
			}
	    	
			if (reportForm.isShowKLE()) {
				createOUKLESheet(workbook);
			}
		}
		
		if (reportForm.isShowTitles()) {
			createTitlesSheet(workbook);
		}

		if (reportForm.isShowUsers()) {
			if (reportForm.isShowUserRoles()) {
				createUserRoleSheet(workbook, reportForm.isShowInactiveUsers());
			}
			
			if (reportForm.isShowKLE()) {
				createUserKLESheet(workbook, reportForm.isShowInactiveUsers());
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
        createCell(dateRow, 0, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")), null);

        Row filterDateRow = sheet.createRow(3);
        String dateFilterHeader = messageSource.getMessage("xls.report.masterdata.filter.date", null, locale);
        createCell(filterDateRow, 0, dateFilterHeader, headerStyle);
        createCell(filterDateRow, 1, filterDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), null);
    }

    private void createTitlesSheet(Workbook workbook) {
    	if (titles == null) {
    		return;
    	}

        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.titles.sheet.title", null, locale));
		
        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.report.titles.uuid");
        headers.add("xls.report.titles.name");

        createHeaderRow(sheet, headers);

        int row = 1;
        for (HistoryTitle title : titles) {
            Row dataRow = sheet.createRow(row++);

            createCell(dataRow, 0, title.getTitleUuid(), null);
            createCell(dataRow, 1, title.getTitleName(), null);
        }
	}

    private void createRoleSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.roles.sheet.title", null, locale));

        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.report.roles.it.system.name");
        headers.add("xls.report.roles.user.role.name");
        headers.add("xls.report.roles.user.role.description");
        headers.add("xls.report.roles.system.role.name");
        headers.add("xls.report.roles.constraint");

        createHeaderRow(sheet, headers);

        int row = 1;
        for (HistoryItSystem itSystem : itSystems) {
            for (HistoryUserRole userRole : itSystem.getHistoryUserRoles()) {
                for (HistorySystemRoleAssignment systemRoleAssignment : userRole.getHistorySystemRoleAssignments()) {
                    Row dataRow = sheet.createRow(row++);
                    int column = 0;
                    createCell(dataRow, column++, itSystem.getItSystemName(), null);
                    createCell(dataRow, column++, userRole.getUserRoleName(), null);
                    createCell(dataRow, column++, userRole.getUserRoleDescription(), null);
                    createCell(dataRow, column++, systemRoleAssignment.getSystemRoleName(), null);

                    StringBuilder constraintCell = new StringBuilder();
                    for (HistorySystemRoleAssignmentConstraint constraint : systemRoleAssignment.getHistoryConstraints()) {
                    	switch (constraint.getConstraintValueType()) {
                    		case EXTENDED_INHERITED:
                                if ("KLE".equals(constraint.getConstraintName())) {
                                    constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.kle.extended", null, locale) + "\n");
                                }
                                else if ("Organisation".equals(constraint.getConstraintName())) {
                                    constraintCell.append(constraint.getConstraintName() + " = " + messageSource.getMessage("html.constraint.organisation.extended", null, locale) + "\n");
                                }
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
										default:
											break;
                    				}
                                }
                    			break;
                    		case VALUE:
                    			constraintCell.append(constraint.getConstraintName() + " = " + constraint.getConstraintValue() + "\n");
                    			break;
                    	}

                        if (constraintCell.length() == 0) {
                            constraintCell.append(messageSource.getMessage("xls.constraint.error", null, locale) + "\n");
                        }
                    }
                    createCell(dataRow, column++, constraintCell.toString(), null);

                    if (systemRoleAssignment.getHistoryConstraints() != null) {
                        dataRow.setHeight((short) (dataRow.getHeight() * (1 + systemRoleAssignment.getHistoryConstraints().size())));
                    }

                }
            }
        }
    }

    private void createOURoleSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.ou.roles.sheet.title", null, locale));

        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.report.ou.roles.ou.name");
        headers.add("xls.role.name");
        headers.add("xls.role.it.system");
        headers.add("xls.role.assigned.by");
        headers.add("xls.role.date");
        headers.add("xls.role.assigned.through");

        createHeaderRow(sheet, headers);

        int row = 1;
        for (Map.Entry<String, List<HistoryOURoleAssignment>> entry : ouRoleAssignments.entrySet()) {
            String ouName = orgUnits.get(entry.getKey()).getOuName();
            List<HistoryOURoleAssignment> ouRoleAssignments = entry.getValue();
            for (HistoryOURoleAssignment ouRoleAssignment : ouRoleAssignments) {

                // Get ItSystem by id
                Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == ouRoleAssignment.getRoleItSystemId()).findFirst();
                String itSystem = "";
                if (first.isPresent()) {
                    HistoryItSystem historyItSystem = first.get();
                    itSystem = historyItSystem.getItSystemName();
                }

                // Creating assigned by
                String assignedBy = ouRoleAssignment.getAssignedByName() + " (" + ouRoleAssignment.getAssignedByUserId() + ")";

                // Creating assinged Through string
                String assignedThroughStr = "";
                switch (ouRoleAssignment.getAssignedThroughType()) {
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

                if (!StringUtils.isEmpty(ouRoleAssignment.getAssignedThroughName())) {
                    assignedThroughStr += ": " + ouRoleAssignment.getAssignedThroughName();
                }

                Row dataRow = sheet.createRow(row++);
                int column = 0;
                createCell(dataRow, column++, ouName, null);
                createCell(dataRow, column++, itSystemNameMapping.get(ouRoleAssignment.getRoleId()), null);
                createCell(dataRow, column++, itSystem, null);
                createCell(dataRow, column++, assignedBy, null);
                createCell(dataRow, column++, dateFormatter.format(ouRoleAssignment.getAssignedWhen()), null);
                createCell(dataRow, column++, assignedThroughStr, null);
            }
        }
    }

    private void createOUKLESheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.ou.kle.sheet.title", null, locale));

        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.report.ou.kle.ou.name");
        headers.add("xls.report.ou.kle.assignment.type");
        headers.add("xls.report.ou.kle.kle.values");

        createHeaderRow(sheet, headers);

        int row = 1;
        for (Map.Entry<String, List<HistoryOUKleAssignment>> entry : ouKLEAssignments.entrySet()) {
            String ouName = orgUnits.get(entry.getKey()).getOuName();
            for (HistoryOUKleAssignment ouKleAssignment : entry.getValue()) {
                Row dataRow = sheet.createRow(row++);

                String assignmentType = ouKleAssignment.getAssignmentType();
                if (Objects.equals(assignmentType, "INTEREST")) {
                    assignmentType = "Opgaveansvar";
                } else if (Objects.equals(assignmentType, "PERFORMING")) {
                    assignmentType = "Indsigtsbehov";
                }

                int column = 0;
                createCell(dataRow, column++, ouName, null);
                createCell(dataRow, column++, assignmentType, null);
                createCell(dataRow, column++, ouKleAssignment.getKleValues(), null);
            }
        }
    }

    private void createUserRoleSheet(Workbook workbook, boolean showInactiveUsers) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.user.roles.sheet.title", null, locale));

        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.user.name");
        headers.add("xls.user.id");
        headers.add("xls.user.active");
        headers.add("xls.role.name");
        headers.add("xls.role.it.system");
        headers.add("xls.role.assigned.by");
        headers.add("xls.role.date");
        headers.add("xls.role.assigned.through");

        createHeaderRow(sheet, headers);

        int row = 1;
        for (Map.Entry<String, List<HistoryRoleAssignment>> entry : userRoleAssignments.entrySet()) {
            HistoryUser user = users.get(entry.getKey());

            // Skip inactive users if showInactive users = false
			if (!showInactiveUsers && !user.isUserActive()) {
				continue;
			}

            String userName = user.getUserName();
            String userId = user.getUserUserId();
            boolean userActive = user.isUserActive();

            List<HistoryRoleAssignment> roleAssignments = entry.getValue();
            for (HistoryRoleAssignment roleAssignment : roleAssignments) {
                
            	// Get ItSystem by id
                Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == roleAssignment.getRoleItSystemId()).findFirst();
                String itSystem = "";
                if (first.isPresent()) {
                    HistoryItSystem historyItSystem = first.get();
                    itSystem = historyItSystem.getItSystemName();
                }

                String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";

                // Creating assigned Through string
                String assignedThroughStr = "";
                switch (roleAssignment.getAssignedThroughType()) {
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

                if (!StringUtils.isEmpty(roleAssignment.getAssignedThroughName())) {
                    assignedThroughStr += ": " + roleAssignment.getAssignedThroughName();
                }

                Row dataRow = sheet.createRow(row++);
                int column = 0;
                createCell(dataRow, column++, userName, null);
                createCell(dataRow, column++, userId, null);
                createCell(dataRow, column++, userActive ? "aktiv" : "inaktiv", null);
                createCell(dataRow, column++, itSystemNameMapping.get(roleAssignment.getRoleId()), null);
                createCell(dataRow, column++, itSystem, null);
                createCell(dataRow, column++, assignedBy, null);
                createCell(dataRow, column++, dateFormatter.format(roleAssignment.getAssignedWhen()), null);
                createCell(dataRow, column++, assignedThroughStr, null);
            }
        }
    }

    private void createUserKLESheet(Workbook workbook, boolean showInactiveUsers) {
        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.user.kle.sheet.title", null, locale));

        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.user.name");
        headers.add("xls.user.id");
        headers.add("xls.user.active");
        headers.add("xls.report.user.kle.assignment.type");
        headers.add("xls.report.user.kle.kle.values");

        createHeaderRow(sheet, headers);

        int row = 1;
        for (Map.Entry<String, List<HistoryKleAssignment>> entry : userKLEAssignments.entrySet()) {
            HistoryUser user = users.get(entry.getKey());

            // Skip inactive users if showInactive users = false
            if (!showInactiveUsers && !user.isUserActive()) { continue; }

            String userName = user.getUserName();
            String userId = user.getUserUserId();
            boolean userActive = user.isUserActive();

            for (HistoryKleAssignment kleAssignment : entry.getValue()) {

                String assignmentType = kleAssignment.getAssignmentType();
                if (Objects.equals(assignmentType, "INTEREST")) {
                    assignmentType = "Opgaveansvar";
                } else if (Objects.equals(assignmentType, "PERFORMING")) {
                    assignmentType = "Indsigtsbehov";
                }


                Row dataRow = sheet.createRow(row++);
                int column = 0;
                createCell(dataRow, column++, userName, null);
                createCell(dataRow, column++, userId, null);
                createCell(dataRow, column++, userActive ? "aktiv" : "inaktiv", null);
                createCell(dataRow, column++, assignmentType, null);
                createCell(dataRow, column++, kleAssignment.getKleValues(), null);
            }
        }
    }

    private static void createCell(Row header, int column, String value, CellStyle style) {
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
}
