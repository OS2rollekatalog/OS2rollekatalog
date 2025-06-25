package dk.digitalidentity.rc.controller.mvc.xlsview;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithNegativeTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRole;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.ReportService;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentReportEntry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReportXlsxView extends AbstractXlsxStreamingView {
    private LocalDate filterDate;
    private List<HistoryItSystem> itSystems;
    private List<HistorySystemRole> systemRoles;
    private Map<String, HistoryUser> users;
    private List<HistoryTitle> titles;
    private Map<String, HistoryTitle> titleMap;
    private Map<String, List<HistoryRoleAssignment>> userRoleAssignments;
    private Map<String, List<HistoryKleAssignment>> userKLEAssignments;
    private Map<String, HistoryOU> orgUnits;
    private Map<String, HistoryOU> allOrgUnits;
    private Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments;
    private Map<String, List<HistoryOURoleAssignmentWithExceptions>> ouRoleAssignmentsWithExceptions;
    private Map<String, List<HistoryOUKleAssignment>> ouKLEAssignments;
    private Map<String, List<HistoryOURoleAssignmentWithTitles>> titleRoleAssignments;
    private Map<String, List<HistoryOURoleAssignmentWithNegativeTitles>> negativeRoleAssignments;
    private Map<Long, String> itSystemNameMapping;
    private SimpleDateFormat dateFormatter;
    private ResourceBundleMessageSource messageSource;
    private Locale locale;
    private CellStyle headerStyle;
    private CellStyle wrapStyle;
    private ReportService reportService;
    private OrgUnitService orgUnitService;
    private ItSystemService itSystemService;
    private List<HistoryOUUser> ouUsers;

    @SuppressWarnings("unchecked")
    @Override
    protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {

        // Get data
        locale = (Locale) model.get("locale");
        reportService = (ReportService) model.get("reportService");
        orgUnitService = (OrgUnitService) model.get("orgUnitService");
        itSystemService = (ItSystemService) model.get("itSystemService");
        filterDate = (LocalDate) model.get("filterDate");
        users = (Map<String, HistoryUser>) model.get("users");
        titles = (List<HistoryTitle>) model.get("titles");
        orgUnits = (Map<String, HistoryOU>) model.get("orgUnits");
        allOrgUnits = (Map<String, HistoryOU>) model.get("allOrgUnits");
        itSystems = (List<HistoryItSystem>) model.get("itSystems");
        systemRoles = (List<HistorySystemRole>) model.get("systemRoles");
        messageSource = (ResourceBundleMessageSource) model.get("messagesBundle");
        ouKLEAssignments = (Map<String, List<HistoryOUKleAssignment>>) model.get("ouKLEAssignments");
        userKLEAssignments = (Map<String, List<HistoryKleAssignment>>) model.get("userKLEAssignments");
        ouRoleAssignments = (Map<String, List<HistoryOURoleAssignment>>) model.get("ouRoleAssignments");
        ouRoleAssignmentsWithExceptions = (Map<String, List<HistoryOURoleAssignmentWithExceptions>>) model.get("ouRoleAssignmentsWithExceptions");
        userRoleAssignments = (Map<String, List<HistoryRoleAssignment>>) model.get("userRoleAssignments");
		titleRoleAssignments = (Map<String, List<HistoryOURoleAssignmentWithTitles>>) model.get("titleRoleAssignments");
        negativeRoleAssignments = (Map<String, List<HistoryOURoleAssignmentWithNegativeTitles>>) model.get("negativeRoleAssignments");
		ouUsers = (List<HistoryOUUser>) model.get("ouUsers");

		if (titles != null) {
			titleMap = titles.stream().collect(Collectors.toMap(HistoryTitle::getTitleUuid, Function.identity()));
		}
		else {
			titleMap = new HashMap<>();
		}
		
		// Process data
        itSystemNameMapping = new HashMap<>();
        for (HistoryItSystem itSystem : itSystems) {
            for (HistoryUserRole userRole : itSystem.getHistoryUserRoles()) {
                itSystemNameMapping.put(userRole.getUserRoleId(), userRole.getUserRoleName());
            }
        }
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
        
        if (reportForm.isShowUsers()) {
			createUsersSheet(workbook);
        }

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

        if (reportForm.isShowUserRoles()) {
            createUserRoleSheet(workbook, reportForm.isShowInactiveUsers());
        }

        if (reportForm.isShowNegativeRoles()) {
            createNegativeUserRoleSheet(workbook, reportForm.isShowInactiveUsers());
        }

		if (reportForm.isShowUsers()) {
			if (reportForm.isShowKLE()) {
				createUserKLESheet(workbook, reportForm.isShowInactiveUsers());
			}
		}

		if (reportForm.isShowSystemRoles()) {
			createSystemRolesSheet(workbook);
		}
    }

	private void createSystemRolesSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.systemRoles.sheet.title", null, locale));
		ArrayList<String> headers = new ArrayList<>();
		headers.add("xls.report.systemRoles.id");
		headers.add("xls.report.systemRoles.itSystem");
		headers.add("xls.report.systemRoles.name");
		headers.add("xls.report.systemRoles.description");

		createHeaderRow(sheet, headers);

		int row = 1;
		for (HistorySystemRole entry : systemRoles) {
			Row dataRow = sheet.createRow(row++);
			int column = 0;

			createCell(dataRow, column++, String.valueOf(entry.getSystemRoleId()), null);
			createCell(dataRow, column++, entry.getHistoryItSystem().getItSystemName(), null);
			createCell(dataRow, column++, entry.getSystemRoleName(), null);
			createCell(dataRow, column++, entry.getSystemRoleDescription(), null);
		}
	}

	private void createUsersSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.users.sheet.title", null, locale));
		ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.report.users.uuid");
        headers.add("xls.report.users.name");
        headers.add("xls.report.users.userId");
        headers.add("xls.report.users.positions");

        createHeaderRow(sheet, headers);
        
        int row = 1;
        for (HistoryUser entry : users.values()) {
            Row dataRow = sheet.createRow(row++);
            int column = 0;
            
            createCell(dataRow, column++, entry.getUserUuid(), null);
            createCell(dataRow, column++, entry.getUserName(), null);
            createCell(dataRow, column++, entry.getUserUserId(), null);

            CellStyle cs = workbook.createCellStyle();
    		cs.setWrapText(true);
    		String in = messageSource.getMessage("html.word.in", null, locale);

    		List<HistoryOUUser> entryOUUsers = ouUsers.stream().filter(h -> h.getUserUuid().equals(entry.getUserUuid())).toList();
    		List<String> titleStrings = new ArrayList<>();
    		for (HistoryOUUser ouUser : entryOUUsers) {
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
            createCell(dataRow, column++, titleStrings.stream().collect(Collectors.joining("\n")), cs);
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

        ArrayList<String> headers = new ArrayList<>();
        headers.add("xls.report.roles.it.system.name");
        headers.add("xls.report.roles.user.role.name");
        headers.add("xls.report.roles.user.role.description");
        headers.add("xls.report.roles.system.role.name");
        headers.add("xls.report.roles.system.role.weight");
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
                    createCell(dataRow, column++, String.valueOf(itSystem.getHistorySystemRoles().stream()
                            .filter(historySystemRole -> historySystemRole.getSystemRoleId() == systemRoleAssignment.getSystemRoleId())
                            .map(HistorySystemRole::getWeight)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(1L)), null);
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
                    		case VALUE:
                    			String constraintValue = constraint.getConstraintValue();
                    			
								List<String> values;
								String[] constraintValues;
								// TODO: not super robust, but as the history table only stores names, and not ID's, this is
								//       what is currently possible
								switch (constraint.getConstraintName()) {
									case "It-system":
										values = new ArrayList<>();
										constraintValues = constraintValue.split(",");
										for (String id : constraintValues) {
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
										constraintValues = constraintValue.split(",");
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
        headers.add("xls.report.ou.roles.ou.uuid");
        headers.add("xls.role.name");
        headers.add("xls.role.it.system");
        headers.add("xls.role.assigned.by");
        headers.add("xls.role.start_date");
        headers.add("xls.role.stop_date");
        headers.add("xls.role.assigned.through");
        headers.add("xls.report.ou.roles.exceptedusers");
        headers.add("xls.report.ou.roles.titles");

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

                // Creating assigned Through string
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

                if (StringUtils.hasLength(ouRoleAssignment.getAssignedThroughName())) {
                    assignedThroughStr += ": " + ouRoleAssignment.getAssignedThroughName();
                }

                Row dataRow = sheet.createRow(row++);
                int column = 0;
                createCell(dataRow, column++, ouName, null);
                createCell(dataRow, column++, entry.getKey(), null);
                createCell(dataRow, column++, itSystemNameMapping.get(ouRoleAssignment.getRoleId()), null);
                createCell(dataRow, column++, itSystem, null);
                createCell(dataRow, column++, assignedBy, null);
                createCell(dataRow, column++, dateFormatter.format(bestStartDate(ouRoleAssignment.getAssignedWhen(), ouRoleAssignment.getStartDate())), null);
                createCell(dataRow, column++, formatLocalDateTime(atEndOfDay(ouRoleAssignment.getStopDate())), null);
                createCell(dataRow, column++, assignedThroughStr, null);
            }
        }

        // Add assignments with exceptions
        for (Map.Entry<String, List<HistoryOURoleAssignmentWithExceptions>> entry : ouRoleAssignmentsWithExceptions.entrySet()) {
        	HistoryOU ou = orgUnits.get(entry.getKey());
        	if (ou == null) {
        		// this OU has been filtered out, so skip
        		continue;
        	}
        	
            String ouName = ou.getOuName();
            List<HistoryOURoleAssignmentWithExceptions> ouRoleAssignments = entry.getValue();

            for (HistoryOURoleAssignmentWithExceptions ouRoleAssignment : ouRoleAssignments) {

                // Get ItSystem by id
                Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == ouRoleAssignment.getRoleItSystemId()).findFirst();
                String itSystem = "";
                if (first.isPresent()) {
                    HistoryItSystem historyItSystem = first.get();
                    itSystem = historyItSystem.getItSystemName();
                }

                // Creating assigned by
                String assignedBy = ouRoleAssignment.getAssignedByName() + " (" + ouRoleAssignment.getAssignedByUserId() + ")";

                // Creating Excepted users string
                StringBuilder exceptionStr = new StringBuilder();
                for (String userUuid : ouRoleAssignment.getUserUuids()) {
                	if (exceptionStr.length() > 0) {
                		exceptionStr.append("\n");
                	}
                    exceptionStr.append(users.containsKey(userUuid) ? users.get(userUuid).getUserName() + " (" + users.get(userUuid).getUserUserId() + ")" : userUuid + "");
                }

                Row dataRow = sheet.createRow(row++);
                int column = 0;
                createCell(dataRow, column++, ouName, null);
                createCell(dataRow, column++, entry.getKey(), null);
                createCell(dataRow, column++, itSystemNameMapping.get(ouRoleAssignment.getRoleId()), null);
                createCell(dataRow, column++, itSystem, null);
                createCell(dataRow, column++, assignedBy, null);
                createCell(dataRow, column++, dateFormatter.format(bestStartDate(ouRoleAssignment.getAssignedWhen(), ouRoleAssignment.getStartDate())), null);
                createCell(dataRow, column++, formatLocalDateTime(atEndOfDay(ouRoleAssignment.getStopDate())), null);
                createCell(dataRow, column++, messageSource.getMessage("xls.role.assigned.trough.type.direct", null, locale), null);
                createCell(dataRow, column++, exceptionStr.toString(), wrapStyle);
            }
        }
        
        // Add assignments with titles
        for (Entry<String, List<HistoryOURoleAssignmentWithTitles>> entry : titleRoleAssignments.entrySet()) {
        	HistoryOU ou = orgUnits.get(entry.getKey());
        	if (ou == null) {
        		// this OU has been filtered out, so skip
        		continue;
        	}

        	String ouName = orgUnits.get(entry.getKey()).getOuName();
            List<HistoryOURoleAssignmentWithTitles> ouRoleAssignments = entry.getValue();

            for (HistoryOURoleAssignmentWithTitles ouRoleAssignment : ouRoleAssignments) {

                // Get ItSystem by id
                Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == ouRoleAssignment.getRoleItSystemId()).findFirst();
                String itSystem = "";
                if (first.isPresent()) {
                    HistoryItSystem historyItSystem = first.get();
                    itSystem = historyItSystem.getItSystemName();
                }

                // Creating assigned by
                String assignedBy = ouRoleAssignment.getAssignedByName() + " (" + ouRoleAssignment.getAssignedByUserId() + ")";

                // Creating title string
                StringBuilder titleString = new StringBuilder();
                for (String titleUuid : ouRoleAssignment.getTitleUuids()) {
                	if (titleString.length() > 0) {
                		titleString.append("\n");
                	}

                    titleString.append(titleMap.containsKey(titleUuid) ? titleMap.get(titleUuid).getTitleName() : titleUuid + "");
                }

				// Creating assigned Through string
				String assignedThroughStr = "";
				switch (ouRoleAssignment.getAssignedThroughType()) {
					case DIRECT:
						assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.direct", null, locale);
						break;
					case ROLEGROUP:
						assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.direct_group", null, locale);
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

                Row dataRow = sheet.createRow(row++);
                int column = 0;
                createCell(dataRow, column++, ouName, null);
                createCell(dataRow, column++, entry.getKey(), null);
                createCell(dataRow, column++, itSystemNameMapping.get(ouRoleAssignment.getRoleId()), null);
                createCell(dataRow, column++, itSystem, null);
                createCell(dataRow, column++, assignedBy, null);
                createCell(dataRow, column++, dateFormatter.format(bestStartDate(ouRoleAssignment.getAssignedWhen(), ouRoleAssignment.getStartDate())), null);
                createCell(dataRow, column++, formatLocalDateTime(atEndOfDay(ouRoleAssignment.getStopDate())), null);
                createCell(dataRow, column++, assignedThroughStr, null);
                createCell(dataRow, column++, "", null);
                createCell(dataRow, column++, titleString.toString(), wrapStyle);
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
        headers.add("xls.role.employee.id");
        headers.add("xls.role.ou.name");
        headers.add("xls.role.ou.uuid");
        headers.add("xls.user.active");
        headers.add("xls.role.name");
        headers.add("xls.role.it.system");
        headers.add("xls.role.it.system_role_weight");
        headers.add("xls.role.it.weight_result");
        headers.add("xls.role.assigned.by");
        headers.add("xls.role.start_date");
        headers.add("xls.role.stop_date");
        headers.add("xls.role.assigned.through");
        headers.add("xls.role.postponedconstraints");

        createHeaderRow(sheet, headers);

        List<UserRoleAssignmentReportEntry> userRoleAssignmentReportEntry = reportService.getUserRoleAssignmentReportEntries(users, allOrgUnits, itSystems, userRoleAssignments, ouRoleAssignments, negativeRoleAssignments, ouRoleAssignmentsWithExceptions, titleRoleAssignments, itSystemNameMapping, locale, showInactiveUsers, true);
        
        int row = 1;
        for (UserRoleAssignmentReportEntry entry : userRoleAssignmentReportEntry) {
            Row dataRow = sheet.createRow(row++);
            int column = 0;
            
            createCell(dataRow, column++, entry.getUserName(), null);
            createCell(dataRow, column++, entry.getUserId(), null);
            createCell(dataRow, column++, entry.getEmployeeId(), null);
            createCell(dataRow, column++, entry.getOrgUnitName(), null);
            createCell(dataRow, column++, entry.getOrgUnitUUID(), null);
            createCell(dataRow, column++, entry.isUserActive() ? "aktiv" : "inaktiv", null);
            createCell(dataRow, column++, itSystemNameMapping.get(entry.getRoleId()), null);
            createCell(dataRow, column++, entry.getItSystem(), null);
            createCell(dataRow, column++, "" + entry.getSystemRoleWeight(), null);
            createCell(dataRow, column++, "" + entry.getItSystemResultWeight(), null);
            createCell(dataRow, column++, entry.getAssignedBy(), null);
            createCell(dataRow, column++, dateFormatter.format(bestStartDate(entry.getAssignedWhen(), entry.getStartDate())), null);
            createCell(dataRow, column++, formatLocalDateTime(atEndOfDay(entry.getStopDate())), null);
            createCell(dataRow, column++, entry.getAssignedThrough(), null);
            createCell(dataRow, column++, entry.getPostponedConstraints(), null);
        }
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
        headers.add("xls.role.postponedconstraints");

        createHeaderRow(sheet, headers);

        List<UserRoleAssignmentReportEntry> userRoleAssignmentReportEntry = reportService.getNegativeUserRoleAssignmentReportEntries(users, allOrgUnits, itSystems, negativeRoleAssignments, locale, showInactiveUsers, true);

        int row = 1;
        for (UserRoleAssignmentReportEntry entry : userRoleAssignmentReportEntry) {
            Row dataRow = sheet.createRow(row++);
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
            createCell(dataRow, column++, dateFormatter.format(bestStartDate(entry.getAssignedWhen(), entry.getStartDate())), null);
            createCell(dataRow, column++, formatLocalDateTime(atEndOfDay(entry.getStopDate())), null);
            createCell(dataRow, column++, entry.getAssignedThrough(), null);
            createCell(dataRow, column++, entry.getPostponedConstraints(), null);
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
        headers.add("xls.report.user.kle.source");

        createHeaderRow(sheet, headers);

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
                Row dataRow = sheet.createRow(row++);
                int column = 0;

                createCell(dataRow, column++, userName, null);
                createCell(dataRow, column++, userId, null);
                createCell(dataRow, column++, userActive ? "aktiv" : "inaktiv", null);
                createCell(dataRow, column++, "Indsigtsbehov", null);
                createCell(dataRow, column++, kleAssignment.getKleValues(), null);

                if (StringUtils.hasLength(kleAssignment.getInheritedFrom())) {
                	createCell(dataRow, column++, "Ansat: " + kleAssignment.getInheritedFrom(), null);
                }
                else {
                	createCell(dataRow, column++, "Personlige", null);
                }
                
                String[] kleCodes = kleAssignment.getKleValues().split(",");
                for (String kleCode : kleCodes) {
                	codeSum.add(kleCode);
                }
            }
            
            // output sum of above rows
            if (codeSum.size() > 0) {
                Row dataRow = sheet.createRow(row++);
                int column = 0;

                createCell(dataRow, column++, userName, null);
                createCell(dataRow, column++, userId, null);
                createCell(dataRow, column++, userActive ? "aktiv" : "inaktiv", null);
                createCell(dataRow, column++, "Indsigtsbehov", null);
                createCell(dataRow, column++, String.join(",", codeSum), null);
               	createCell(dataRow, column++, "Summen", null);
            }

            codeSum = new HashSet<>();
            for (HistoryKleAssignment kleAssignment : performingAssignments) {
                Row dataRow = sheet.createRow(row++);
                int column = 0;

                createCell(dataRow, column++, userName, null);
                createCell(dataRow, column++, userId, null);
                createCell(dataRow, column++, userActive ? "aktiv" : "inaktiv", null);
                createCell(dataRow, column++, "Opgaveansvar", null);
                createCell(dataRow, column++, kleAssignment.getKleValues(), null);

                if (StringUtils.hasLength(kleAssignment.getInheritedFrom())) {
                	createCell(dataRow, column++, "Ansat: " + kleAssignment.getInheritedFrom(), null);
                }
                else {
                	createCell(dataRow, column++, "Personlige", null);
                }
                
                String[] kleCodes = kleAssignment.getKleValues().split(",");
                for (String kleCode : kleCodes) {
                	codeSum.add(kleCode);
                }
            }
            
            // output sum of above rows
            if (codeSum.size() > 0) {
                Row dataRow = sheet.createRow(row++);
                int column = 0;

                createCell(dataRow, column++, userName, null);
                createCell(dataRow, column++, userId, null);
                createCell(dataRow, column++, userActive ? "aktiv" : "inaktiv", null);
                createCell(dataRow, column++, "Opgaveansvar", null);
                createCell(dataRow, column++, String.join(",", codeSum), null);
               	createCell(dataRow, column++, "Summen", null);
            }
        }
    }

    private static void createCell(Row header, int column, String value, CellStyle style) {
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

    private static Date bestStartDate(final Date assigned, final LocalDate startDate) {
        if (startDate != null) {
            return java.sql.Timestamp.valueOf(atStartOfDay(startDate));
        }
        return assigned;
    }

    private String formatLocalDateTime(final LocalDateTime when) {
        if (when == null) {
            return null;
        }
        return dateFormatter.format(java.sql.Timestamp.valueOf(when));
    }

    private static LocalDateTime atStartOfDay(final LocalDate when) {
        return when == null ? null : when.atStartOfDay();
    }

    private static LocalDateTime atEndOfDay(final LocalDate when) {
        if (when == null) {
            return null;
        }
        return when.plusDays(1).atStartOfDay().minusSeconds(1);
    }
}
