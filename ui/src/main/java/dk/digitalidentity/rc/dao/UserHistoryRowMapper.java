package dk.digitalidentity.rc.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.springframework.jdbc.core.RowMapper;

import dk.digitalidentity.rc.dao.model.UserHistory;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;

public class UserHistoryRowMapper implements RowMapper<UserHistory> {
	private EntityType entityType;

	public UserHistoryRowMapper(EntityType entityType) {
		this.entityType = entityType;
	}

	@Override
	public UserHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
		UserHistory userHistory = new UserHistory();
		userHistory.setEntityType(entityType);
		userHistory.setEventType(EventType.valueOf(rs.getString("event_type")));
		userHistory.setRoleName(rs.getString("name"));
		userHistory.setUsername(rs.getString("username"));
		userHistory.setTimestamp(new Date(rs.getTimestamp("timestamp").getTime()));
		userHistory.setSystemName(rs.getString("system_name"));

		// special case, where the role referenced by the audit log has been deleted
		if (userHistory.getRoleName() == null || userHistory.getRoleName().length() == 0) {
			userHistory.setRoleName("[Slettet rolle: " + rs.getLong("role_id") + "]");
		}

		return userHistory;
	}
}
