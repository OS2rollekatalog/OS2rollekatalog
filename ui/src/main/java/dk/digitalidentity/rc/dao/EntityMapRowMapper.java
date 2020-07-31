package dk.digitalidentity.rc.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import dk.digitalidentity.rc.dao.model.EntityMap;

public class EntityMapRowMapper implements RowMapper<EntityMap> {

	@Override
	public EntityMap mapRow(ResultSet rs, int rowNum) throws SQLException {
		EntityMap entityMap = new EntityMap();

		entityMap.setId(rs.getString("id"));
		entityMap.setName(rs.getString("name"));

		return entityMap;
	}
}
