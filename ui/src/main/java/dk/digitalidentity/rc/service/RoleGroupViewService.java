package dk.digitalidentity.rc.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.Column;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.datatables.mapping.Order;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleForRoleGroupViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleForRoleGroupView;

@Service
public class RoleGroupViewService {

	@Autowired
	private UserRoleForRoleGroupViewDao userRoleForRoleGroupDao;
	
	public DataTablesOutput<UserRoleForRoleGroupView> findAllForRoleGroup(DataTablesInput input, long rolegroupId) {
		// Get raw data
		List<Object[]> rawResults = userRoleForRoleGroupDao.findRawUserRolesForRoleGroup(rolegroupId);
		long totalCount = userRoleForRoleGroupDao.countUserRolesForRoleGroup();
		
		// Convert to entities
		List<UserRoleForRoleGroupView> entities = rawResults.stream()
			.map(row -> new UserRoleForRoleGroupView(
				((Number) row[0]).longValue(),
				(String) row[1],
				(String) row[2], 
				(String) row[3],
				rolegroupId,
				((Number) row[5]).intValue() == 1,
				((Number) row[6]).intValue() == 1
			))
			.collect(Collectors.toList());
		
		// Apply DataTables processing
		List<UserRoleForRoleGroupView> filteredData = applyFiltering(entities, input);
		List<UserRoleForRoleGroupView> sortedData = applySorting(filteredData, input);
		List<UserRoleForRoleGroupView> pagedData = applyPaging(sortedData, input);
		
		// Build DataTablesOutput
		DataTablesOutput<UserRoleForRoleGroupView> output = new DataTablesOutput<>();
		output.setData(pagedData);
		output.setRecordsTotal(totalCount);
		output.setRecordsFiltered(filteredData.size());
		output.setDraw(input.getDraw());
		
		return output;
	}
	
	private List<UserRoleForRoleGroupView> applyFiltering(List<UserRoleForRoleGroupView> data, DataTablesInput input) {
		// Global search
		String globalSearch = input.getSearch() != null ? input.getSearch().getValue() : null;
		if (globalSearch != null && !globalSearch.trim().isEmpty()) {
			String searchTerm = globalSearch.toLowerCase().trim();
			data = data.stream()
				.filter(item -> matchesGlobalSearch(item, searchTerm))
				.collect(Collectors.toList());
		}
		
		// Column-specific searches
		if (input.getColumns() != null) {
			for (int i = 0; i < input.getColumns().size(); i++) {
				Column column = input.getColumns().get(i);
				if (column.getSearch() != null && !column.getSearch().getValue().isEmpty()) {
					String columnSearchTerm = column.getSearch().getValue().toLowerCase().trim();
					final int columnIndex = i;
					data = data.stream()
						.filter(item -> matchesColumnSearch(item, columnIndex, columnSearchTerm))
						.collect(Collectors.toList());
				}
			}
		}
		
		return data;
	}
	
	private boolean matchesGlobalSearch(UserRoleForRoleGroupView item, String searchTerm) {
		return (item.getName() != null && item.getName().toLowerCase().contains(searchTerm)) ||
			   (item.getDescription() != null && item.getDescription().toLowerCase().contains(searchTerm)) ||
			   (item.getItSystemName() != null && item.getItSystemName().toLowerCase().contains(searchTerm));
	}
	
	private boolean matchesColumnSearch(UserRoleForRoleGroupView item, int columnIndex, String searchTerm) {
		switch (columnIndex) {
			case 0: // selected
				String selectedStr = item.getSelected() != null && item.getSelected() ? "true" : "false";
				return selectedStr.contains(searchTerm);
			case 1: // name
				return item.getName() != null && item.getName().toLowerCase().contains(searchTerm);
			case 2: // itSystemName
			    return item.getItSystemName() != null && item.getItSystemName().toLowerCase().contains(searchTerm);
			case 3: // description  
				return item.getDescription() != null && item.getDescription().toLowerCase().contains(searchTerm);
			case 4: // readOnly
				String readOnlyStr = item.getReadOnly() != null && item.getReadOnly() ? "true" : "false";
				return readOnlyStr.contains(searchTerm);
			default:
				return true;
		}
	}
	
	private List<UserRoleForRoleGroupView> applySorting(List<UserRoleForRoleGroupView> data, DataTablesInput input) {
		if (input.getOrder() == null || input.getOrder().isEmpty()) {
			// Default sorting by name
			return data.stream()
				.sorted((a, b) -> compareStrings(a.getName(), b.getName()))
				.collect(Collectors.toList());
		}
		
		return data.stream()
			.sorted((a, b) -> {
				for (Order order : input.getOrder()) {
					int columnIndex = order.getColumn();
					String direction = order.getDir();
					
					int comparison = compareByColumn(a, b, columnIndex);
					if (comparison != 0) {
						return "desc".equalsIgnoreCase(direction) ? -comparison : comparison;
					}
				}
				return 0;
			})
			.collect(Collectors.toList());
	}
	
	private int compareByColumn(UserRoleForRoleGroupView a, UserRoleForRoleGroupView b, int columnIndex) {
		switch (columnIndex) {
			case 0: // selected
				return compareBooleans(a.getSelected(), b.getSelected());
			case 1: // name
				return compareStrings(a.getName(), b.getName());
			case 2: // itSystemName
			    return compareStrings(a.getItSystemName(), b.getItSystemName());
			case 3: // description
				return compareStrings(a.getDescription(), b.getDescription());
			case 4: // readOnly
				return compareBooleans(a.getReadOnly(), b.getReadOnly());
			default:
				return 0;
		}
	}
	
	private int compareStrings(String a, String b) {
		if (a == null && b == null) return 0;
		if (a == null) return -1;
		if (b == null) return 1;
		return a.compareToIgnoreCase(b);
	}
	
	private int compareBooleans(Boolean a, Boolean b) {
		if (a == null && b == null) return 0;
		if (a == null) return -1;
		if (b == null) return 1;
		return Boolean.compare(a, b);
	}
	
	private List<UserRoleForRoleGroupView> applyPaging(List<UserRoleForRoleGroupView> data, DataTablesInput input) {
		int start = input.getStart();
		int length = input.getLength();
		
		if (length == -1) {
			// Show all records
			return data.stream().skip(start).collect(Collectors.toList());
		}
		
		return data.stream()
			.skip(start)
			.limit(length)
			.collect(Collectors.toList());
	}
}