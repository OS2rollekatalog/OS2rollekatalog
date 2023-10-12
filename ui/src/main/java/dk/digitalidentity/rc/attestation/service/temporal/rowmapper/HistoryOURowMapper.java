package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class HistoryOURowMapper implements RowMapper<HistoryOU> {
    @Override
    public HistoryOU mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryOU historyOU = new HistoryOU();
        final LocalDate dato = new java.sql.Date(rs.getDate("dato").getTime()).toLocalDate();
        historyOU.setId(rs.getLong("id"));
        historyOU.setDato(dato);
        historyOU.setOuUuid(rs.getString("ou_uuid"));
        historyOU.setOuName(rs.getString("ou_name"));
        historyOU.setOuParentUuid(rs.getString("ou_parent_uuid"));
        historyOU.setOuManagerUuid(rs.getString("ou_manager_uuid"));
        return historyOU;
    }

}
