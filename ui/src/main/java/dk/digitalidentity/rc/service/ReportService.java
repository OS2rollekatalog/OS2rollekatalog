package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportService {
    
	@Autowired
	private MessageSource messageSource;

	@Autowired
	private HistoryService historyService;

	public Map<String, Object> getReportModel(ReportForm reportForm, Locale loc) {
		LocalDate localDate = LocalDate.parse(reportForm.getDate());
		List<String> ouFilter = reportForm.getOrgUnits();
		List<Long> itSystemFilter = reportForm.getItSystems();
		String manager = reportForm.getManager();

		List<HistoryItSystem> itSystems = historyService.getItSystems(localDate);
		List<HistoryTitle> titles = (reportForm.isShowTitles()) ? historyService.getTitles(localDate) : null;
		Map<String, HistoryOU> orgUnits = historyService.getOUs(localDate);
		Map<String, HistoryUser> users = historyService.getUsers(localDate);
		Map<String, List<HistoryKleAssignment>> userKleAssignments = historyService.getKleAssignments(localDate);
		Map<String, List<HistoryOUKleAssignment>> ouKleAssignments = historyService.getOUKleAssignments(localDate);
		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments;
		Map<String, List<HistoryRoleAssignment>> userRoleAssignments;

		// Filter on ItSystems if specified
		if (itSystemFilter != null && itSystemFilter.size() > 0) {
			itSystems = itSystems
					.stream()
					.filter(itSystem -> itSystemFilter.contains(itSystem.getItSystemId()))
					.collect(Collectors.toList());

			ouRoleAssignments = historyService.getOURoleAssignments(localDate, itSystemFilter);
			userRoleAssignments = historyService.getRoleAssignments(localDate, itSystemFilter);
		}
		else {
			ouRoleAssignments = historyService.getOURoleAssignments(localDate);
			userRoleAssignments = historyService.getRoleAssignments(localDate);
		}
		
		// Filter on manager if specified		
		if (!StringUtils.isEmpty(manager)) {
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
			}
		}

		// Filter OrgUnits if specified
		if (ouFilter != null && ouFilter.size() > 0) {
			List<String> finalOuFilter = ouFilter;

			// filter the retrieved role assignments and kle assignments
			orgUnits.keySet().removeIf(k -> !finalOuFilter.contains(k));
			ouRoleAssignments.keySet().removeIf(k -> !finalOuFilter.contains(k));
			ouKleAssignments.keySet().removeIf(k -> !finalOuFilter.contains(k));

			// Find all users in those filtered orgUnits
			List<String> userUUIDs = orgUnits
					.entrySet()
					.stream()
					.flatMap(entry -> entry.getValue().getUserUuids().stream())
					.collect(Collectors.toList());

			// Filter list of retrieved users
			users.keySet().removeIf(k -> !userUUIDs.contains(k));
			userRoleAssignments.keySet().removeIf(k -> !userUUIDs.contains(k));
			userKleAssignments.keySet().removeIf(k -> !userUUIDs.contains(k));
		}

		Map<String, Object> model = new HashMap<>();
		model.put("filterDate", localDate);
		model.put("itSystems", itSystems);

		model.put("orgUnits", orgUnits);
		model.put("ouRoleAssignments", ouRoleAssignments);
		model.put("ouKLEAssignments", ouKleAssignments);
		
		model.put("titles", titles);
		
		model.put("users", users);
		model.put("userRoleAssignments", userRoleAssignments);
		model.put("userKLEAssignments", userKleAssignments);
		model.put("reportForm", reportForm);

		// Locale specific text
		model.put("messagesBundle", messageSource);
		model.put("locale", loc);
		
		return model;
	}
}
