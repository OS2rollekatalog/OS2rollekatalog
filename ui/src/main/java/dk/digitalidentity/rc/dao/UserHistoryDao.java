package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserHistory;
import dk.digitalidentity.rc.dao.model.enums.EntityType;

@Component
public class UserHistoryDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@SuppressWarnings("deprecation")
	public List<UserHistory> getDirectUserRoleHistory(User user) {
		return jdbcTemplate.query(
			"SELECT a.timestamp, a.username, a.event_type, u.name, i.name AS system_name, a.secondary_entity_id as role_id" +
			"  FROM audit_log a" +
			"  LEFT OUTER JOIN user_roles u ON a.secondary_entity_id = u.id" +
			"  LEFT OUTER JOIN it_systems i ON i.id = u.it_system_id" +
			" WHERE a.entity_type = 'USER'" +
			"   AND a.entity_id = ?" +
			"   AND a.event_type in ('ASSIGN_USER_ROLE', 'REMOVE_USER_ROLE')",
			new Object[] { user.getUuid() },
			new UserHistoryRowMapper(EntityType.USERROLE)
		);
	}
	
	@SuppressWarnings("deprecation")
	public List<UserHistory> getPositionUserRoleHistory(User user) {
		return jdbcTemplate.query(
			"SELECT a.timestamp, a.username, a.event_type, u.name, i.name AS system_name, a.secondary_entity_id as role_id" +
			"  FROM audit_log a" +
			"  LEFT OUTER JOIN user_roles u ON a.secondary_entity_id = u.id" +
			"  LEFT OUTER JOIN it_systems i ON i.id = u.it_system_id" + 
			"  JOIN positions p ON a.entity_id = p.id" +
			" WHERE a.entity_type = 'POSITION'" +
			"  AND p.user_uuid = ?" +
			"  AND a.event_type in ('ASSIGN_USER_ROLE', 'REMOVE_USER_ROLE')",
			new Object[] { user.getUuid() },
			new UserHistoryRowMapper(EntityType.USERROLE)
		);
	}
	
	@SuppressWarnings("deprecation")
	public List<UserHistory> getDirectRoleGroupHistory(User user) {
		return jdbcTemplate.query(
			"SELECT a.timestamp, a.username, a.event_type, rg.name, 'Rollebuket' AS system_name, a.secondary_entity_id as role_id" +
			"  FROM audit_log a" +
			"  LEFT OUTER JOIN rolegroup rg ON a.secondary_entity_id = rg.id" +
			" WHERE a.entity_type = 'USER'" +
			"   AND a.entity_id = ?" +
			"   AND a.event_type in ('ASSIGN_ROLE_GROUP', 'REMOVE_ROLE_GROUP')",
			new Object[] { user.getUuid() },
			new UserHistoryRowMapper(EntityType.ROLEGROUP)
		);
	}
	
	@SuppressWarnings("deprecation")
	public List<UserHistory> getPositionRoleGroupHistory(User user) {
		return jdbcTemplate.query(
			"SELECT a.timestamp, a.username, a.event_type, rg.name, 'Rollebuket' AS system_name, a.secondary_entity_id as role_id" +
			"  FROM audit_log a" +
			"  LEFT OUTER JOIN rolegroup rg ON a.secondary_entity_id = rg.id" +
			"  LEFT OUTER JOIN positions p ON a.entity_id = p.id" +
			" WHERE a.entity_type = 'POSITION'" +
			"  AND p.user_uuid = ?" +
			"  AND a.event_type in ('ASSIGN_ROLE_GROUP', 'REMOVE_ROLE_GROUP')",
			new Object[] { user.getUuid() },
			new UserHistoryRowMapper(EntityType.ROLEGROUP)
		);
	}
}
