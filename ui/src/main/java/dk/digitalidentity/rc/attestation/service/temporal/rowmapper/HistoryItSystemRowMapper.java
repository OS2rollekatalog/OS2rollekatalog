package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class HistoryItSystemRowMapper implements RowMapper<HistoryItSystem> {
    @Override
    public HistoryItSystem mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryItSystem historyItSystem = new HistoryItSystem();
        final LocalDate dato = new java.sql.Date(rs.getDate("dato").getTime()).toLocalDate();
        historyItSystem.setId(rs.getLong("id"));
        historyItSystem.setDato(dato);
        historyItSystem.setItSystemId(rs.getLong("it_system_id"));
        historyItSystem.setItSystemName(rs.getString("it_system_name"));
        historyItSystem.setItSystemHidden(rs.getBoolean("it_system_hidden"));
        historyItSystem.setAttestationResponsible(rs.getString("attestation_responsible_uuid"));
        historyItSystem.setAttestationExempt(rs.getBoolean("attestation_exempt"));

        return historyItSystem;
    }
}
