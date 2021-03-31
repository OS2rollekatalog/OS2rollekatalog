package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.TitleRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.TitleUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.SecurityUtil;

@Service
public class TitleService {

	@Autowired
	private TitleDao titleDao;

	public Title save(Title title) {
		return titleDao.save(title);
	}

	public void save(List<Title> list) {
		titleDao.saveAll(list);
	}

	public Title getByUuid(String uuid) {
		return titleDao.getByUuidAndActiveTrue(uuid);
	}
	
	public List<Title> getAll() {
		return titleDao.getByActiveTrue();
	}
	
	@SuppressWarnings("deprecation")
	public List<Title> getAllIncludingInactive() {
		return titleDao.findAll();
	}

	public List<Title> getAllWithRole(UserRole userRole) {
		return titleDao.findByUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(userRole, false);
	}

	public List<Title> getAllWithRoleGroup(RoleGroup roleGroup) {
		return titleDao.findByRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false);
	}

	@AuditLogIntercepted
	public boolean addUserRole(Title title, UserRole role, String[] ouUuids, LocalDate startDate, LocalDate stopDate) {
		if (role.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan tildele Rollekatalog roller");
		}

		List<String> ouUuidsAsList = Arrays.asList(ouUuids);
		
		// existing assignment modification
		for (TitleUserRoleAssignment assignment : title.getUserRoleAssignments()) {
			if (assignment.getUserRole().getId() == role.getId()) {
				boolean changes = false;

				// find those to add
				for (String ouUuid : ouUuidsAsList) {
					if (!assignment.getOuUuids().contains(ouUuid)) {
						assignment.getOuUuids().add(ouUuid);
						changes = true;
					}
				}
				
				// find those to remove
				for (Iterator<String> iterator = assignment.getOuUuids().iterator(); iterator.hasNext();) {
					String ouUuid = iterator.next();
					
					if (!ouUuidsAsList.contains(ouUuid)) {
						iterator.remove();
						changes = true;
					}
				}

				startDate = (startDate == null || LocalDate.now().equals(startDate)) ? null : startDate;
				if (!Objects.equals(startDate, assignment.getStartDate()) || !Objects.equals(stopDate, assignment.getStopDate())) {
					assignment.setStartDate(startDate);
					assignment.setStopDate(stopDate);
					assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
					
					changes = true;
				}

				return changes;
			}
		}

		TitleUserRoleAssignment assignment = new TitleUserRoleAssignment();
		assignment.setTitle(title);
		assignment.setUserRole(role);
		assignment.setAssignedByName(SecurityUtil.getUserFullname());
		assignment.setAssignedByUserId(SecurityUtil.getUserId());
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		assignment.setOuUuids(new ArrayList<>());
		assignment.getOuUuids().addAll(ouUuidsAsList);
		title.getUserRoleAssignments().add(assignment);

		return true;
	}
	
	@AuditLogIntercepted
	public boolean removeUserRole(Title title, UserRole role, OrgUnit ou) {
		if (role.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan fjerne Rollekatalog roller");
		}

		if (title.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(role)) {

			for (Iterator<TitleUserRoleAssignment> iterator = title.getUserRoleAssignments().iterator(); iterator.hasNext();) {
				TitleUserRoleAssignment userRoleAssignment = iterator.next();
				
				if (userRoleAssignment.getUserRole().equals(role)) {
					userRoleAssignment.getOuUuids().remove(ou.getUuid());
					if (userRoleAssignment.getOuUuids().size() == 0) {
						iterator.remove();
					}
					
					break;
				}
			}

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean addRoleGroup(Title title, RoleGroup roleGroup, String[] ouUuids, LocalDate startDate, LocalDate stopDate) {
		List<String> ouUuidsAsList = Arrays.asList(ouUuids);

		for (TitleRoleGroupAssignment assignment : title.getRoleGroupAssignments()) {
			if (assignment.getRoleGroup().getId() == roleGroup.getId()) {
				boolean changes = false;

				// find those to add
				for (String ouUuid : ouUuidsAsList) {
					if (!assignment.getOuUuids().contains(ouUuid)) {
						assignment.getOuUuids().add(ouUuid);
						changes = true;
					}
				}
				
				// find those to remove
				for (Iterator<String> iterator = assignment.getOuUuids().iterator(); iterator.hasNext();) {
					String ouUuid = iterator.next();
					
					if (!ouUuidsAsList.contains(ouUuid)) {
						iterator.remove();
						changes = true;
					}
				}
				
				startDate = (startDate == null || LocalDate.now().equals(startDate)) ? null : startDate;
				if (!Objects.equals(startDate, assignment.getStartDate()) || !Objects.equals(stopDate, assignment.getStopDate())) {
					assignment.setStartDate(startDate);
					assignment.setStopDate(stopDate);
					assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
					
					changes = true;
				}

				return changes;
			}
		}

		TitleRoleGroupAssignment assignment = new TitleRoleGroupAssignment();
		assignment.setTitle(title);
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignedByName(SecurityUtil.getUserFullname());
		assignment.setAssignedByUserId(SecurityUtil.getUserId());
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		assignment.setOuUuids(new ArrayList<>());
		assignment.getOuUuids().addAll(ouUuidsAsList);
		title.getRoleGroupAssignments().add(assignment);

		return true;
	}

	@AuditLogIntercepted
	public boolean removeRoleGroup(Title title, RoleGroup roleGroup, OrgUnit ou) {
		if (title.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {

			for (Iterator<TitleRoleGroupAssignment> iterator = title.getRoleGroupAssignments().iterator(); iterator.hasNext();) {
				TitleRoleGroupAssignment roleGroupAssignment = iterator.next();
								
				if (roleGroupAssignment.getRoleGroup().equals(roleGroup)) {
					roleGroupAssignment.getOuUuids().remove(ou.getUuid());
					if (roleGroupAssignment.getOuUuids().size() == 0) {
						iterator.remove();
					}
					
					break;
				}
			}

			return true;
		}

		return false;
	}
}
