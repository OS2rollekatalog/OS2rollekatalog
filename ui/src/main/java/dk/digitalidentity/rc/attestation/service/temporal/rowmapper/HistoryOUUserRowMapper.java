package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HistoryOUUserRowMapper implements RowMapper<HistoryOUUser> {
    @Override
    public HistoryOUUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryOUUser historyOUUser = new HistoryOUUser();
        historyOUUser.setId(rs.getLong("id"));
        historyOUUser.setUserUuid(rs.getString("user_uuid"));
        historyOUUser.setTitleUuid(rs.getString("title_uuid"));
        historyOUUser.setDoNotInherit(rs.getBoolean("do_not_inherit"));
        return historyOUUser;
    }
}
