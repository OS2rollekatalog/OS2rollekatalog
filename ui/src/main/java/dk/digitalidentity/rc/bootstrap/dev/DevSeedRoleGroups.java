package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedRoleGroups {

	private final RoleGroupService roleGroupService;
	private final UserRoleService userRoleService;

	public void seed(DevSeedItSystems.SeedResult itSystems) {
		log.info("Seeding role groups...");

		Map<Long, List<UserRole>> rolesByItSystemId = userRoleService.getAll().stream()
			.filter(ur -> itSystems.itSystemIdsByIdentifier().containsValue(ur.getItSystem().getId()))
			.collect(Collectors.groupingBy(ur -> ur.getItSystem().getId()));

		for (Map.Entry<Long, List<UserRole>> entry : rolesByItSystemId.entrySet()) {
			List<UserRole> allRoles = entry.getValue();
			String itSystemName = allRoles.get(0).getItSystem().getName();

			List<UserRole> groupA = new ArrayList<>();
			List<UserRole> groupB = new ArrayList<>();
			for (int i = 0; i < allRoles.size(); i++) {
				if (i % 2 == 0 && groupA.size() < 2) groupA.add(allRoles.get(i));
				else if (i % 2 == 1 && groupB.size() < 2) groupB.add(allRoles.get(i));
			}
			if (groupB.isEmpty()) groupB.addAll(groupA);

			createRoleGroup(itSystemName + " - Rollegruppe 1", groupA);
			createRoleGroup(itSystemName + " - Rollegruppe 2", groupB);
		}
	}

	private void createRoleGroup(String name, List<UserRole> userRoles) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setName(name);
		roleGroup.setDescription("Dev seed rollegruppe");
		roleGroup.setUserRoleAssignments(new ArrayList<>());

		for (UserRole userRole : userRoles) {
			RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
			assignment.setRoleGroup(roleGroup);
			assignment.setUserRole(userRole);
			assignment.setAssignedByName("Development Bootstrapper");
			assignment.setAssignedByUserId("dev-bootstrapper");
			assignment.setAssignedTimestamp(new Date());
			roleGroup.getUserRoleAssignments().add(assignment);
		}

		roleGroupService.save(roleGroup);
	}
}
