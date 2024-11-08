package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithNegativeTitles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentReportEntry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportService {
	@Autowired
	private HistoryOUDao historyOUDao;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private ItSystemService itSytemService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private OrgUnitDao orgUnitDao;

	// used to be part of ReportXlsView code, but moved here because our ManualRolesService also
	// needs these computations
	public List<UserRoleAssignmentReportEntry> getUserRoleAssignmentReportEntries(
			Map<String, HistoryUser> users,
			Map<String, HistoryOU> orgUnits,
			List<HistoryItSystem> itSystems,
			Map<String, List<HistoryRoleAssignment>> userRoleAssignments,
			Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments,
			Map<String, List<HistoryOURoleAssignmentWithExceptions>> ouRoleAssignmentsWithExceptions,
			Map<String, List<HistoryOURoleAssignmentWithTitles>> titleRoleAssignments,
			Map<Long, String> itSystemNameMapping,
			Locale locale,
			boolean showInactiveUsers,
			boolean onlyResponsibleOU) {
        
		List<UserRoleAssignmentReportEntry> result = new ArrayList<>();
		
        for (Map.Entry<String, List<HistoryRoleAssignment>> entry : userRoleAssignments.entrySet()) {
			HistoryUser user = users.get(entry.getKey());

			// Skip inactive users if showInactive users = false
			if (!showInactiveUsers && !user.isUserActive()) {
				continue;
			}

			List<HistoryRoleAssignment> roleAssignments = entry.getValue();

			for (HistoryRoleAssignment roleAssignment : roleAssignments) {
				if (onlyResponsibleOU && roleAssignment.getOrgUnitUuid() != null) {
					HistoryOU ou = orgUnits.get(roleAssignment.getOrgUnitUuid());
					addRow(itSystems, locale, result, user, roleAssignment, ou);
				}
				else {
					List<HistoryOU> positions = getPositions(orgUnits, user);
					
					for (HistoryOU position : positions) {
						addRow(itSystems, locale, result, user, roleAssignment, position);
					}
				}
			}
        }

        for (HistoryOU ou : orgUnits.values()) {
        	if (ou.getUsers() == null) {
        		continue;
        	}
        	

        	List<HistoryOURoleAssignment> ouAssignments = ouRoleAssignments.get(ou.getOuUuid());
        	for (HistoryOUUser historyOuUser : ou.getUsers()) {
                HistoryUser user = users.get(historyOuUser.getUserUuid());
                if (user == null) {
                	continue;
                }
                
                // Skip inactive users if showInactive users = false
    			if (!showInactiveUsers && !user.isUserActive()) {
    				continue;
    			}

				// The users position has doNotInherit set, so the user does not get any roles from this OU.
				if (historyOuUser.getDoNotInherit()) {
					continue;
				}

        		List<HistoryOURoleAssignmentWithTitles> userTitleAssignments = null;
        		
        		String titleUuid = historyOuUser.getTitleUuid();
        		if (titleUuid != null) {
        			userTitleAssignments = titleRoleAssignments.get(ou.getOuUuid());
        			if (userTitleAssignments != null) {
        				userTitleAssignments = userTitleAssignments.stream().filter(t -> t.getTitleUuids().contains(historyOuUser.getTitleUuid())).collect(Collectors.toList());
        			}
        		}

        		// ok, time to generate records


				long domainId = user.getDomainId();
				String userName = user.getUserName();
                String userId = user.getUserUserId();
                String employeeId = user.getUserExtUuid();
                boolean userActive = user.isUserActive();
                
                if (ouAssignments != null) {
	                for (HistoryOURoleAssignment roleAssignment : ouAssignments) {
	                    
	                	// Get ItSystem by id
	                    Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == roleAssignment.getRoleItSystemId()).findFirst();
	                    String itSystem = "";
	                    if (first.isPresent()) {
	                        HistoryItSystem historyItSystem = first.get();
	                        itSystem = historyItSystem.getItSystemName();
	                    }

	                    String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";	
	                    String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.orgunit", null, locale) + ": " + ((roleAssignment.getAssignedThroughName() != null) ? roleAssignment.getAssignedThroughName() : ou.getOuName());

	                    UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
						row.setDomainId(domainId);
	                    row.setUserName(userName);
	                    row.setUserId(userId);
		                row.setEmployeeId(employeeId);
		                row.setOrgUnitName(ou.getOuName());
		                row.setOrgUnitUUID(ou.getOuUuid());
	                    row.setUserActive(userActive);
	                    row.setRoleId(roleAssignment.getRoleId());
	                    row.setItSystem(itSystem);
	                    row.setAssignedBy(assignedBy);
	                    row.setAssignedWhen(roleAssignment.getAssignedWhen());
	                    row.setAssignedThrough(assignedThroughStr);
						row.setStartDate(roleAssignment.getStartDate());
						row.setStopDate(roleAssignment.getStopDate());
	                    result.add(row);
	                }
                }
                
                if (userTitleAssignments != null) {
	                for (HistoryOURoleAssignmentWithTitles roleAssignment : userTitleAssignments) {
	                	
	                	// Get ItSystem by id
	                    Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == roleAssignment.getRoleItSystemId()).findFirst();
	                    String itSystem = "";
	                    if (first.isPresent()) {
	                        HistoryItSystem historyItSystem = first.get();
	                        itSystem = historyItSystem.getItSystemName();
	                    }
	
	                    String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";
	                    	                    
	                    String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.title", null, locale) + ": " + ou.getOuName();

	                    UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
						row.setDomainId(domainId);
	                    row.setUserName(userName);
	                    row.setUserId(userId);
		                row.setEmployeeId(employeeId);
		                row.setOrgUnitName(ou.getOuName());
		                row.setOrgUnitUUID(ou.getOuUuid());
	                    row.setUserActive(userActive);
	                    row.setRoleId(roleAssignment.getRoleId());
	                    row.setItSystem(itSystem);
	                    row.setAssignedBy(assignedBy);
	                    row.setAssignedWhen(roleAssignment.getAssignedWhen());
	                    row.setAssignedThrough(assignedThroughStr);
						row.setStartDate(roleAssignment.getStartDate());
						row.setStopDate(roleAssignment.getStopDate());
	                    result.add(row);
	                }
                }
        	}
        }

        // Add assignments from OUs, with exceptions
        for (Map.Entry<String, List<HistoryOURoleAssignmentWithExceptions>> entry : ouRoleAssignmentsWithExceptions.entrySet()) {
            List<HistoryOURoleAssignmentWithExceptions> ouRoleAssignmentWithExceptions = entry.getValue();
        	HistoryOU orgUnit = orgUnits.get(entry.getKey());


            for (HistoryOURoleAssignmentWithExceptions ouRoleAssignment : ouRoleAssignmentWithExceptions) {

                // Get ItSystem by id
                Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == ouRoleAssignment.getRoleItSystemId()).findFirst();
                String itSystem = "";
                if (first.isPresent()) {
                    HistoryItSystem historyItSystem = first.get();
                    itSystem = historyItSystem.getItSystemName();
                }

                String assignedBy = ouRoleAssignment.getAssignedByName() + " (" + ouRoleAssignment.getAssignedByUserId() + ")";
                String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.orgunit", null, locale) + ": " + orgUnit.getOuName();

                for (HistoryOUUser user : orgUnit.getUsers()) {
					// The users position has doNotInherit set, so the user does not get any roles from this OU.
					if (user.getDoNotInherit()) {
						continue;
					}
                	if (ouRoleAssignment.getUserUuids().contains(user.getUserUuid())) {
                		continue;
                	}
                	
                	if (!users.containsKey(user.getUserUuid())) {
                		log.warn("Excepted user with uuid=" + user.getUserUuid() + " does not exist");
                		continue;
                	}

                	HistoryUser actualUser = users.get(user.getUserUuid());

	                UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
					row.setDomainId(actualUser.getDomainId());
	                row.setUserName(actualUser.getUserName());
	                row.setUserId(actualUser.getUserUserId());
	                row.setEmployeeId(actualUser.getUserExtUuid());
	                row.setOrgUnitName(orgUnit.getOuName());
	                row.setOrgUnitUUID(orgUnit.getOuUuid());
	                row.setUserActive(actualUser.isUserActive());
	                row.setRoleId(ouRoleAssignment.getRoleId());
	                row.setItSystem(itSystem);
	                row.setAssignedBy(assignedBy);
	                row.setAssignedWhen(ouRoleAssignment.getAssignedWhen());
	                row.setAssignedThrough(assignedThroughStr);
					row.setStartDate(ouRoleAssignment.getStartDate());
					row.setStopDate(ouRoleAssignment.getStopDate());
	                result.add(row);
                }
            }
        }
        
        return result;
	}

	public List<UserRoleAssignmentReportEntry> getNegativeUserRoleAssignmentReportEntries(
			Map<String, HistoryUser> users,
			Map<String, HistoryOU> orgUnits,
			List<HistoryItSystem> itSystems,
			Map<String, List<HistoryOURoleAssignmentWithNegativeTitles>> negativeTitleRoleAssignments,
			Locale locale,
			boolean showInactiveUsers) {

		List<UserRoleAssignmentReportEntry> result = new ArrayList<>();

        List<HistoryOURoleAssignmentWithNegativeTitles> negativeAssignments = negativeTitleRoleAssignments.values().stream().flatMap(List::stream).toList();

		for (HistoryOU ou : orgUnits.values()) {
			if (ou.getUsers() == null || ou.getUsers().isEmpty()) {
				continue;
			}

			List<HistoryOURoleAssignmentWithNegativeTitles> negativeAssignmentsForOU = negativeAssignments.stream().filter(nt -> uuidOfUnitAndParents(ou, LocalDate.now()).contains(nt.getOuUuid())).toList();
			if(negativeAssignmentsForOU == null) {
				continue;
			}
			else if (negativeAssignmentsForOU.isEmpty()) {
				continue;
			}

			for (HistoryOUUser historyOuUser : ou.getUsers()) {
				HistoryUser user = users.get(historyOuUser.getUserUuid());
				if (user == null) {
					continue;
				}

				// Skip inactive users if showInactive users = false
				if (!showInactiveUsers && !user.isUserActive()) {
					continue;
				}

				// The users position has doNotInherit set, so the user does not get any roles from this OU.
				if (historyOuUser.getDoNotInherit()) {
					continue;
				}

				List<HistoryOURoleAssignmentWithNegativeTitles> userNegativeTitleAssignments = null;
				String titleUuid = historyOuUser.getTitleUuid();
				if (titleUuid != null) {
					userNegativeTitleAssignments = negativeAssignmentsForOU;
					if (!userNegativeTitleAssignments.isEmpty()) {
						userNegativeTitleAssignments = userNegativeTitleAssignments.stream().filter(t -> !t.getTitleUuids().contains(historyOuUser.getTitleUuid())).toList();
					}
				}

				// ok, time to generate records
				long domainId = user.getDomainId();
				String userName = user.getUserName();
				String userId = user.getUserUserId();
				String employeeId = user.getUserExtUuid();
				boolean userActive = user.isUserActive();

				if (userNegativeTitleAssignments != null) {
					for (HistoryOURoleAssignmentWithNegativeTitles roleAssignment : userNegativeTitleAssignments) {

						// Get ItSystem by id
						Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == roleAssignment.getRoleItSystemId()).findFirst();
						String itSystem = "";
						if (first.isPresent()) {
							HistoryItSystem historyItSystem = first.get();
							itSystem = historyItSystem.getItSystemName();
						}

						String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";
						String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.negativetitle", null, locale) + ": " + (ou.getOuName());

						UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
						row.setDomainId(domainId);
						row.setUserName(userName);
						row.setUserId(userId);
						row.setEmployeeId(employeeId);
						row.setOrgUnitName(ou.getOuName());
						row.setOrgUnitUUID(ou.getOuUuid());
						row.setUserActive(userActive);
						row.setRoleId(roleAssignment.getRoleId());
						row.setItSystem(itSystem);
						row.setAssignedBy(assignedBy);
						row.setAssignedWhen(roleAssignment.getAssignedWhen());
						row.setAssignedThrough(assignedThroughStr);
						row.setStartDate(roleAssignment.getStartDate());
						row.setStopDate(roleAssignment.getStopDate());
						result.add(row);
					}
				}

			}
		}
		return result;
	}

	private List<String> uuidOfUnitAndParents(HistoryOU ou, LocalDate localDate) {
		Set<String> uuidSet = new HashSet<>();
		uuidSet.add(ou.getOuUuid());
		HistoryOU parent;
		if(ou.getOuParentUuid() == null || ou.getOuParentUuid().isEmpty()) {
			return uuidSet.stream().toList();
		}
		else{
			parent = historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(localDate, ou.getOuParentUuid());
		}
		if(parent != null) {
			uuidSet.addAll(uuidOfUnitAndParents(parent, localDate));
		}
		return uuidSet.stream().toList();
	}



	private void addRow(List<HistoryItSystem> itSystems, Locale locale, List<UserRoleAssignmentReportEntry> result,
						HistoryUser user, HistoryRoleAssignment roleAssignment, HistoryOU ou) {
		// Get ItSystem by id
		Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> itSystem.getItSystemId() == roleAssignment.getRoleItSystemId()).findFirst();
		String itSystem = "";
		if (first.isPresent()) {
			HistoryItSystem historyItSystem = first.get();
			itSystem = historyItSystem.getItSystemName();
		}

		String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";

		// Creating assigned Through string
		String assignedThroughMessageCode = switch (roleAssignment.getAssignedThroughType()) {
			case DIRECT -> "xls.role.assigned.trough.type.direct";
			case ROLEGROUP -> "xls.role.assigned.trough.type.direct_group";
			case POSITION -> "xls.role.assigned.trough.type.position";
			case ORGUNIT -> "xls.role.assigned.trough.type.orgunit";
			case TITLE -> "xls.role.assigned.trough.type.title";
		};
		String assignedThroughStr = messageSource.getMessage(assignedThroughMessageCode, null, locale);

		if (StringUtils.hasLength(roleAssignment.getAssignedThroughName())) {
			assignedThroughStr += ": " + roleAssignment.getAssignedThroughName();
		}

		String postponedConstraints = null;
		
		if (roleAssignment.getPostponedConstraints() != null) {
			StringBuilder sb = new StringBuilder();
			String regex = "Organisation: ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})";
			Pattern pattern = Pattern.compile(regex);
			for (String line : roleAssignment.getPostponedConstraints().split("\\n")) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.matches()) {
					String orgUuid = matcher.group(1);
					OrgUnit orgUnit = orgUnitDao.findById(orgUuid).orElse(null);
					if (orgUnit != null) {
						sb.append("Organisation: " + orgUnit.getName() + "\n");
					}
				} else {
					sb.append(line + "\n");
				}
			}
			postponedConstraints = sb.toString();
		}

		UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
		row.setDomainId(user.getDomainId());
		row.setUserName(user.getUserName());
		row.setUserId(user.getUserUserId());
		row.setEmployeeId(user.getUserExtUuid());
		row.setOrgUnitName((ou != null) ? ou.getOuName() : "<ukendt enhed>");
		row.setOrgUnitUUID((ou != null) ? ou.getOuUuid() : "");
		row.setUserActive(user.isUserActive());
		row.setRoleId(roleAssignment.getRoleId());
		row.setItSystem(itSystem);
		row.setAssignedBy(assignedBy);
		row.setAssignedWhen(roleAssignment.getAssignedWhen());
		row.setAssignedThrough(assignedThroughStr);
		row.setPostponedConstraints(postponedConstraints);
		row.setStartDate(roleAssignment.getStartDate());
		row.setStopDate(roleAssignment.getStopDate());
		result.add(row);
	}

	private List<HistoryOU> getPositions(Map<String, HistoryOU> orgUnits, HistoryUser user) {
		return orgUnits.values().stream().filter( ou -> ou.getUsers().stream().anyMatch(ouUser -> Objects.equals(ouUser.getUserUuid(), user.getUserUuid()))).collect(Collectors.toList());
	}

	public Map<String, Object> getReportModel(ReportForm reportForm, Locale loc) {
		LocalDate localDate = (reportForm.getDate() == null || !StringUtils.hasLength(reportForm.getDate())) ? LocalDate.now() : LocalDate.parse(reportForm.getDate());
		List<String> ouFilter = reportForm.getOrgUnits();
		List<Long> itSystemFilter = reportForm.getItSystems();
		String manager = reportForm.getManager();

		List<HistoryItSystem> itSystems = historyService.getItSystems(localDate);
		List<HistoryTitle> titles = historyService.getTitles(localDate);
		Map<String, HistoryOU> orgUnits = historyService.getOUs(localDate);
		Map<String, HistoryOU> allOrgUnits = new HashMap<>(orgUnits);
		Map<String, HistoryUser> users = historyService.getUsers(localDate);
		Map<String, List<HistoryKleAssignment>> userKleAssignments = historyService.getKleAssignments(localDate);
		Map<String, List<HistoryOUKleAssignment>> ouKleAssignments = historyService.getOUKleAssignments(localDate);
		Map<String, List<HistoryOURoleAssignmentWithTitles>> titleRoleAssignments;
		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments;
		Map<String, List<HistoryOURoleAssignmentWithExceptions>> ouRoleAssignmentsWithExceptions;
		Map<String, List<HistoryRoleAssignment>> userRoleAssignments;
		Map<String, List<HistoryOURoleAssignmentWithNegativeTitles>> negativeRoleAssignments;

		
		List<HistoryOUUser> historyOUUsers = orgUnits
				.entrySet()
				.stream()
				.flatMap(entry -> entry.getValue().getUsers().stream())
				.collect(Collectors.toList());

		// Filter on ItSystems if specified
		if (itSystemFilter != null && itSystemFilter.size() > 0) {
			itSystems = itSystems
					.stream()
					.filter(itSystem -> itSystemFilter.contains(itSystem.getItSystemId()))
					.collect(Collectors.toList());
			
			ouRoleAssignments = historyService.getOURoleAssignments(localDate, itSystemFilter);
			userRoleAssignments = historyService.getRoleAssignments(localDate, itSystemFilter);
			negativeRoleAssignments = historyService.getOURoleAssignmentsWithNegativeTitles(localDate, itSystemFilter);
			titleRoleAssignments = historyService.getOURoleAssignmentsWithTitles(localDate, itSystemFilter);
			ouRoleAssignmentsWithExceptions = historyService.getOURoleAssignmentsWithExceptions(localDate, itSystemFilter);
		}
		else {
			ouRoleAssignments = historyService.getOURoleAssignments(localDate);
			userRoleAssignments = historyService.getRoleAssignments(localDate);
			negativeRoleAssignments = historyService.getOURoleAssignmentsWithNegativeTitles(localDate);
			titleRoleAssignments = historyService.getOURoleAssignmentsWithTitles(localDate);
			ouRoleAssignmentsWithExceptions = historyService.getOURoleAssignmentsWithExceptions(localDate);
		}
		
		// TODO: det er uheldigt at vi filtrerer OU'ere væk... vi skal bruge dem alle sammen, så måske sende både ALLE OU'ere og de filtrerede med rundt (hvis det er relevant),
		//       da vi nu skal nedarve fra dem... og det er lige så fjollet hvis vi fjerner rettigheder, da vi også skal bruge dem... hmmm
		
		// Filter on manager if specified		
		if (StringUtils.hasLength(manager)) {
			ouFilter = orgUnits
					.entrySet()
					.stream()
					.filter(entry -> java.util.Objects.equals(manager, entry.getValue().getOuManagerUuid()))
					.map(entry -> entry.getKey())
					.collect(Collectors.toList());

			if (ouFilter.size() == 0) {
				log.warn("Unknown manager: " + manager);

				ouFilter = null;
				itSystems = new ArrayList<>();
				orgUnits = new HashMap<>();
				users = new HashMap<>();
				userKleAssignments = new HashMap<>();
				ouKleAssignments = new HashMap<>();
				ouRoleAssignments = new HashMap<>();
				userRoleAssignments = new HashMap<>();
				negativeRoleAssignments = new HashMap<>();
			}
		}

		// Filter OrgUnits if specified
		if (ouFilter != null && ouFilter.size() > 0) {
			List<String> finalOuFilter = ouFilter;

			// filter the retrieved role assignments and kle assignments
			orgUnits.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));
			ouRoleAssignments.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));
			ouRoleAssignmentsWithExceptions.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));
			// doubt we need any filtering actually - but filtering here breaks the report, so lets not do that
//			ouKleAssignments.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));

			// Find all users in those filtered orgUnits
			List<HistoryOUUser> ouUsers = orgUnits
					.entrySet()
					.stream()
					.flatMap(entry -> entry.getValue().getUsers().stream())
					.collect(Collectors.toList());

			List<String> userUUIDs = ouUsers
					.stream()
					.map(u -> u.getUserUuid())
					.collect(Collectors.toList());

			// Filter list of retrieved users
			users.keySet().removeIf(k -> !userUUIDs.contains(k));
			userRoleAssignments.entrySet().removeIf(e -> !userUUIDs.contains(e.getKey()));
			userKleAssignments.entrySet().removeIf(e -> !userUUIDs.contains(e.getKey()));

			// filter out role assignments where responsible ou is not in ouFilter
			for (Map.Entry<String, List<HistoryRoleAssignment>> entry : userRoleAssignments.entrySet()) {
				entry.getValue().removeIf(v -> !finalOuFilter.contains(v.getOrgUnitUuid()));
			}
		}

		Map<String, Object> model = new HashMap<>();
		model.put("filterDate", localDate);
		model.put("itSystems", itSystems);

		model.put("orgUnits", orgUnits);
		model.put("allOrgUnits", allOrgUnits);
		model.put("ouRoleAssignments", ouRoleAssignments);
		model.put("ouRoleAssignmentsWithExceptions", ouRoleAssignmentsWithExceptions);
		model.put("ouKLEAssignments", ouKleAssignments);
		
		model.put("titles", titles);
		model.put("titleRoleAssignments", titleRoleAssignments);
		
		model.put("users", users);
		model.put("userRoleAssignments", userRoleAssignments);
		model.put("negativeRoleAssignments", negativeRoleAssignments);
		model.put("userKLEAssignments", userKleAssignments);
		model.put("reportForm", reportForm);
		model.put("reportService", this);
		model.put("itSystemService", itSytemService);
		model.put("orgUnitService", orgUnitService);
		
		model.put("ouUsers", historyOUUsers);

		// Locale specific text
		model.put("messagesBundle", messageSource);
		model.put("locale", loc);
		
		return model;
	}
}
