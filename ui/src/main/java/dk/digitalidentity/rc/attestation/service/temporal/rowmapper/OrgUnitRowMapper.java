package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Does not map related fields
 */
public class OrgUnitRowMapper implements RowMapper<OrgUnit> {
    @Override
    public OrgUnit mapRow(ResultSet rs, int rowNum) throws SQLException {
        final OrgUnit ou = new OrgUnit();
        ou.setUuid(rs.getString("uuid"));
        ou.setName(rs.getString("name"));
        ou.setActive(rs.getBoolean("active"));
        ou.setInheritKle(rs.getBoolean("inherit_kle"));
        ou.setLastAttestedBy(rs.getString("last_attested_by"));
        return ou;
    }

}
