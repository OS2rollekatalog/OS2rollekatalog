package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.AttestationOuRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.AttestationSystemRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.AttestationUserRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryItSystemRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryOURoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryOURoleAssignmentWithExceptionsRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryOURoleAssignmentWithNegativeTitlesRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryOURoleAssignmentWithTitlesRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryOURowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryOUUserRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistorySystemRoleAssignmentConstraintRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistorySystemRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.HistoryUserRoleRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.IdRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.OrgUnitRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithNegativeTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TemporalDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final HistorySystemRoleAssignmentConstraintRowMapper historySystemRoleAssignmentConstraintRowMapper = new HistorySystemRoleAssignmentConstraintRowMapper();
    private final HistorySystemRoleAssignmentRowMapper historySystemRoleAssignmentRowMapper = new HistorySystemRoleAssignmentRowMapper();
    private final HistoryUserRoleRowMapper historyUserRoleRowMapper = new HistoryUserRoleRowMapper();
    private final HistoryItSystemRowMapper historyItSystemRowMapper = new HistoryItSystemRowMapper();
    private final HistoryRoleAssignmentRowMapper historyRoleAssignmentRowMapper = new HistoryRoleAssignmentRowMapper();
    private final HistoryOURowMapper historyOURowMapper = new HistoryOURowMapper();
    private final HistoryOURoleAssignmentRowMapper historyOURoleAssignmentRowMapper = new HistoryOURoleAssignmentRowMapper();
    private final HistoryOURoleAssignmentWithExceptionsRowMapper historyOURoleAssignmentWithExceptionsRowMapper = new HistoryOURoleAssignmentWithExceptionsRowMapper();
    private final HistoryOURoleAssignmentWithTitlesRowMapper historyOURoleAssignmentWithTitlesRowMapper = new HistoryOURoleAssignmentWithTitlesRowMapper();
    private final HistoryOURoleAssignmentWithNegativeTitlesRowMapper historyOURoleAssignmentWithNegativeTitlesRowMapper = new HistoryOURoleAssignmentWithNegativeTitlesRowMapper();
    private final HistoryOUUserRowMapper historyOUUserRowMapper = new HistoryOUUserRowMapper();
    private final AttestationUserRoleAssignmentRowMapper attestationUserRoleAssignmentRowMapper = new AttestationUserRoleAssignmentRowMapper();
    private final AttestationOuRoleAssignmentRowMapper attestationOuRoleAssignmentRowMapper = new AttestationOuRoleAssignmentRowMapper();
    private final AttestationSystemRoleAssignmentRowMapper attestationSystemRoleAssignmentRowMapper = new AttestationSystemRoleAssignmentRowMapper();

    private final OrgUnitRowMapper orgUnitRowMapper = new OrgUnitRowMapper();
    private final IdRowMapper idRowMapper = new IdRowMapper();

    public Optional<OrgUnit> findActiveOUByUuid(final String uuid) {
        final List<OrgUnit> foundOus = jdbcTemplate.query("SELECT ou.* FROM ous ou WHERE uuid=? and active=1", orgUnitRowMapper, uuid);
        return !foundOus.isEmpty() ? Optional.of(foundOus.getFirst()) : Optional.empty();
    }

    public Optional<HistoryOU> findHistoricOUByUuid(final LocalDate when, final String uuid) {
        final List<HistoryOU> foundOus = jdbcTemplate.query("SELECT hou.* FROM history_ous hou WHERE hou.dato=? and hou.ou_uuid=?", historyOURowMapper, when, uuid);
        return !foundOus.isEmpty() ? Optional.of(foundOus.getFirst()) : Optional.empty();
    }


    public List<HistoryOURoleAssignmentWithExceptions> listHistoryOURoleAssignmentWithExceptionsByDate(final LocalDate when) {
        return jdbcTemplate.query("SELECT hra.* FROM history_role_assignment_excepted_users hra WHERE hra.dato=?", historyOURoleAssignmentWithExceptionsRowMapper, when);
    }

    public List<HistoryOURoleAssignmentWithTitles> listHistoryOURoleAssignmentWithTitlesByDate(final LocalDate when) {
        return jdbcTemplate.query("SELECT hra.* FROM history_role_assignment_titles hra WHERE hra.dato=?", historyOURoleAssignmentWithTitlesRowMapper, when);
    }

    public List<HistoryOURoleAssignmentWithNegativeTitles> listHistoryOURoleAssignmentWithNegativeTitlesByDate(final LocalDate when) {
        return jdbcTemplate.query("SELECT hra.* FROM history_role_assignment_negative_titles hra WHERE hra.dato=?", historyOURoleAssignmentWithNegativeTitlesRowMapper, when);
    }

    public List<HistoryOURoleAssignment> listHistoryOURoleAssignmentsByDate(final LocalDate when) {
        return jdbcTemplate.query("SELECT hra.* FROM history_ou_role_assignments hra WHERE hra.dato=?", historyOURoleAssignmentRowMapper, when);
    }

    public List<HistoryRoleAssignment> listHistoryRoleAssignmentsByItSystemAndDate(final LocalDate when, final long itSystemId) {
        return jdbcTemplate.query("SELECT hra.* FROM history_role_assignments hra WHERE hra.dato=? AND hra.role_it_system_id=?", historyRoleAssignmentRowMapper, when, itSystemId);
    }

    public List<HistoryOU> listHistoryOUs(final LocalDate when) {
        return jdbcTemplate.query("SELECT * FROM history_ous WHERE dato=?", historyOURowMapper, when);
    }

    public List<HistoryItSystem> listHistoryItSystems(final LocalDate when) {
        return jdbcTemplate.query("SELECT * FROM history_it_systems WHERE dato=?", historyItSystemRowMapper, when);
    }

    public List<HistorySystemRoleAssignmentConstraint> listConstraintsForHistorySystemRoleAssignment(long historySystemRoleAssignmentId) {
        return jdbcTemplate.query("SELECT * FROM history_user_roles_system_role_constraints WHERE history_user_roles_system_roles_id=?", historySystemRoleAssignmentConstraintRowMapper, historySystemRoleAssignmentId);
    }

    public List<HistorySystemRoleAssignment> listHistorySystemRoleAssignment(final long historyUserRoleId) {
        return jdbcTemplate.query("SELECT * FROM history_user_roles_system_roles WHERE history_user_roles_id=?", historySystemRoleAssignmentRowMapper, historyUserRoleId);
    }

    public List<HistoryUserRole> listHistoryUserRoles(final long historyItSystemId) {
        return jdbcTemplate.query("SELECT * FROM history_user_roles WHERE history_it_systems_id=?", historyUserRoleRowMapper,historyItSystemId);
    }

    public List<HistoryOUUser> listHistoryOUUsers(final Long historyOuId) {
        return jdbcTemplate.query("SELECT * FROM history_ous_users WHERE history_ous_id=?", historyOUUserRowMapper, historyOuId);
    }

    public AttestationUserRoleAssignment findValidUserRoleAssignmentWithHash(final LocalDate when, final String hash) {
        List<AttestationUserRoleAssignment> result = jdbcTemplate.query("SELECT * FROM attestation_user_role_assignments a WHERE a.record_hash = ? and valid_from <= ? AND (valid_to > ? or valid_to is null)", attestationUserRoleAssignmentRowMapper, hash, when, when);
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    public AttestationOuRoleAssignment findValidOuRoleAssignmentWithHash(final LocalDate when, final String hash) {
        List<AttestationOuRoleAssignment> result = jdbcTemplate.query("SELECT * FROM attestation_ou_role_assignments a WHERE a.record_hash = ? and valid_from <= ? AND (valid_to > ? or valid_to is null)", attestationOuRoleAssignmentRowMapper, hash, when, when);
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    public List<AttestationOuRoleAssignment> findAllValidOuRoleAssignment(final LocalDate when) {
        return jdbcTemplate.query("SELECT * FROM attestation_ou_role_assignments a WHERE valid_from <= ? AND (valid_to > ? or valid_to is null)", attestationOuRoleAssignmentRowMapper, when, when);
    }

    public AttestationSystemRoleAssignment findValidSystemRoleAssignmentWithHash(final LocalDate when, final String hash) {
        List<AttestationSystemRoleAssignment> result = jdbcTemplate.query("SELECT * FROM attestation_system_role_assignments a WHERE a.record_hash = ? and valid_from <= ? AND (valid_to > ? or valid_to is null)", attestationSystemRoleAssignmentRowMapper, hash, when, when);
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    public List<Long> findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(final LocalDate updatedAt) {
        return namedParameterJdbcTemplate.query("SELECT id FROM attestation_user_role_assignments WHERE updated_at < :updated_at AND (valid_to > :updated_at or valid_to is null)",
                Map.of("updated_at", updatedAt), idRowMapper);
    }

    public long invalidateUserRoleAssignmentsWithIdsIn(final List<Long> ids, final LocalDate when) {
        return namedParameterJdbcTemplate.update("UPDATE attestation_user_role_assignments SET valid_to=:when WHERE id in (:ids)",
                Map.of("ids", ids, "when", when));
    }

    public long invalidateSystemRoleAssignmentsWithIdsIn(final List<Long> ids, final LocalDate when) {
        return namedParameterJdbcTemplate.update("UPDATE attestation_system_role_assignments SET valid_to=:when WHERE id in (:ids)",
                Map.of("ids", ids, "when", when));
    }

    public int invalidateAttestationOuRoleAssignmentsByUpdatedAtLessThan(final LocalDate updatedAt) {
        return namedParameterJdbcTemplate.update("UPDATE attestation_ou_role_assignments SET valid_to=:updated_at WHERE updated_at < :updated_at AND (valid_to > :updated_at or valid_to is null)",
                Map.of("updated_at", updatedAt));
    }

    public List<Long> findAllValidSystemRoleAssignmentIdsByUpdatedAtLessThan(final LocalDate updatedAt) {
        return namedParameterJdbcTemplate.query("SELECT id from attestation_system_role_assignments WHERE updated_at < :updated_at AND (valid_to > :updated_at or valid_to is null)",
                Map.of("updated_at", updatedAt), idRowMapper);
    }

    public void saveAttestationSystemRoleAssignment(final AttestationSystemRoleAssignment assignment) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", assignment.getId());
        parameters.put("valid_from", assignment.getValidFrom());
        parameters.put("valid_to", assignment.getValidTo());
        parameters.put("updated_at", assignment.getUpdatedAt());
        parameters.put("record_hash", assignment.getRecordHash());
        parameters.put("it_system_id", assignment.getItSystemId());
        parameters.put("it_system_name", assignment.getItSystemName());
        parameters.put("responsible_user_uuid", assignment.getResponsibleUserUuid());
        parameters.put("system_role_description", assignment.getSystemRoleDescription());
        parameters.put("system_role_id", assignment.getSystemRoleId());
        parameters.put("system_role_name", assignment.getSystemRoleName());
        parameters.put("user_role_description", assignment.getUserRoleDescription());
        parameters.put("user_role_id", assignment.getUserRoleId());
        parameters.put("user_role_name", assignment.getUserRoleName());
        namedParameterJdbcTemplate.update("INSERT INTO attestation_system_role_assignments (record_hash, updated_at, valid_from, valid_to, it_system_id," +
                "                                 it_system_name, responsible_user_uuid, system_role_description," +
                "                                 system_role_id, system_role_name, user_role_description," +
                "                                 user_role_id, user_role_name) " +
                "VALUES (:record_hash, :updated_at, :valid_from, :valid_to, :it_system_id, :it_system_name, :responsible_user_uuid, " +
                "        :system_role_description, :system_role_id, :system_role_name, :user_role_description, :user_role_id, :user_role_name)",
                parameters
        );
    }

    public int updateAttestationSystemRoleAssignment(final AttestationSystemRoleAssignment assignment) {
        MapSqlParameterSource in = new MapSqlParameterSource();
        in.addValue("id", assignment.getId());
        in.addValue("valid_from", assignment.getValidFrom());
        in.addValue("valid_to", assignment.getValidTo());
        in.addValue("updated_at", assignment.getUpdatedAt());
        in.addValue("record_hash", assignment.getRecordHash());
        in.addValue("it_system_id", assignment.getItSystemId());
        in.addValue("it_system_name", assignment.getItSystemName());
        in.addValue("responsible_user_uuid", assignment.getResponsibleUserUuid());
        in.addValue("system_role_description", assignment.getSystemRoleDescription());
        in.addValue("system_role_id", assignment.getSystemRoleId());
        in.addValue("system_role_name", assignment.getSystemRoleName());
        in.addValue("user_role_description", assignment.getUserRoleDescription());
        in.addValue("user_role_id", assignment.getUserRoleId());
        in.addValue("user_role_name", assignment.getUserRoleName());

        return namedParameterJdbcTemplate.update("UPDATE attestation_system_role_assignments SET " +
                "record_hash=:record_hash, updated_at=:updated_at, valid_from=:valid_from, valid_to=:valid_to, it_system_id=:it_system_id, "+
                "it_system_name=:it_system_name, responsible_user_uuid=:responsible_user_uuid, system_role_description=:system_role_description, " +
                "system_role_id=:system_role_id, system_role_name=:system_role_name, user_role_description=:user_role_description, " +
                "user_role_id=:user_role_id, user_role_name=:user_role_name " +
                " WHERE id=:id", in);
    }

    public void saveAttestationOuRoleAssignment(final AttestationOuRoleAssignment assignment) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", assignment.getId());
        parameters.put("valid_from", assignment.getValidFrom());
        parameters.put("valid_to", assignment.getValidTo());
        parameters.put("updated_at", assignment.getUpdatedAt());
        parameters.put("record_hash", assignment.getRecordHash());
        parameters.put("role_id", assignment.getRoleId());
        parameters.put("role_name", assignment.getRoleName());
        parameters.put("role_description", assignment.getRoleDescription());
        parameters.put("ou_uuid", assignment.getOuUuid());
        parameters.put("ou_name", assignment.getOuName());
        parameters.put("role_group_id", assignment.getRoleGroupId());
        parameters.put("role_group_name", assignment.getRoleGroupName());
        parameters.put("role_group_description", assignment.getRoleGroupDescription());
        parameters.put("responsible_user_uuid", assignment.getResponsibleUserUuid());
        parameters.put("responsible_ou_uuid", assignment.getResponsibleOuUuid());
        parameters.put("responsible_ou_name", assignment.getResponsibleOuName());
        parameters.put("title_uuids", RowMapperUtils.joinFromList(assignment.getTitleUuids()));
        parameters.put("excepted_user_uuids", RowMapperUtils.joinFromList(assignment.getExceptedUserUuids()));
        parameters.put("it_system_id", assignment.getItSystemId());
        parameters.put("it_system_name", assignment.getItSystemName());
        parameters.put("assigned_through_type", assignment.getAssignedThroughType().name());
        parameters.put("assigned_through_name", assignment.getAssignedThroughName());
        parameters.put("assigned_through_uuid", assignment.getAssignedThroughUuid());
        parameters.put("inherited", assignment.isInherited());
        parameters.put("inherit", assignment.isInherit());
        parameters.put("sensitive_role", assignment.isSensitiveRole());
        parameters.put("extra_sensitive_role", assignment.isExtraSensitiveRole());
        parameters.put("excepted_title_uuids", RowMapperUtils.joinFromList(assignment.getExceptedTitleUuids()));
        namedParameterJdbcTemplate.update("INSERT INTO attestation_ou_role_assignments (record_hash, updated_at, valid_from, valid_to," +
                "                                                    assigned_through_name, assigned_through_type, assigned_through_uuid," +
                "                                                    inherited, it_system_id, it_system_name, role_description, role_id," +
                "                                                    role_name, role_group_name, role_group_id, role_group_description," +
                "                                                    sensitive_role, extra_sensitive_role, ou_name, ou_uuid, excepted_user_uuids, title_uuids," +
                "                                                    responsible_user_uuid, responsible_ou_name, responsible_ou_uuid," +
                "                                                    inherit, excepted_title_uuids) " +
                " VALUES (:record_hash, :updated_at, :valid_from, :valid_to, :assigned_through_name, :assigned_through_type, :assigned_through_uuid," +
                "         :inherited, :it_system_id, :it_system_name, :role_description, :role_id, :role_name, :role_group_name, " +
                "         :role_group_id, :role_group_description, :sensitive_role, :extra_sensitive_role, :ou_name, :ou_uuid, :excepted_user_uuids, " +
                "         :title_uuids, :responsible_user_uuid, :responsible_ou_name, :responsible_ou_uuid, :inherit, :excepted_title_uuids)",
                parameters);

    }

    public int updateAttestationOuRoleAssignment(final AttestationOuRoleAssignment assignment) {
        MapSqlParameterSource in = new MapSqlParameterSource();
        in.addValue("id", assignment.getId());
        in.addValue("record_hash", assignment.getRecordHash());
        in.addValue("updated_at", assignment.getUpdatedAt());
        in.addValue("valid_from", assignment.getValidFrom());
        in.addValue("valid_to", assignment.getValidTo());
        in.addValue("role_id", assignment.getRoleId());
        in.addValue("role_name", assignment.getRoleName());
        in.addValue("role_description", assignment.getRoleDescription());
        in.addValue("ou_uuid", assignment.getOuUuid());
        in.addValue("ou_name", assignment.getOuName());
        in.addValue("role_group_id", assignment.getRoleGroupId());
        in.addValue("role_group_name", assignment.getRoleGroupName());
        in.addValue("role_group_description", assignment.getRoleGroupDescription());
        in.addValue("responsible_user_uuid", assignment.getResponsibleUserUuid());
        in.addValue("responsible_ou_uuid", assignment.getResponsibleOuUuid());
        in.addValue("responsible_ou_name", assignment.getResponsibleOuName());
        in.addValue("title_uuids", RowMapperUtils.joinFromList(assignment.getTitleUuids()));
        in.addValue("excepted_user_uuids", RowMapperUtils.joinFromList(assignment.getExceptedUserUuids()));
        in.addValue("it_system_id", assignment.getItSystemId());
        in.addValue("it_system_name", assignment.getItSystemName());
        in.addValue("assigned_through_type", assignment.getAssignedThroughType().name());
        in.addValue("assigned_through_name", assignment.getAssignedThroughName());
        in.addValue("assigned_through_uuid", assignment.getAssignedThroughUuid());
        in.addValue("inherited", assignment.isInherited());
        in.addValue("inherit", assignment.isInherit());
        in.addValue("sensitive_role", assignment.isSensitiveRole());
        in.addValue("extra_sensitive_role", assignment.isExtraSensitiveRole());
        in.addValue("excepted_title_uuids", RowMapperUtils.joinFromList(assignment.getExceptedTitleUuids()));

        return namedParameterJdbcTemplate.update("UPDATE attestation_ou_role_assignments SET " +
                "record_hash=:record_hash, updated_at=:updated_at, valid_from=:valid_from, valid_to=:valid_to, assigned_through_name=:assigned_through_name, " +
                "assigned_through_type=:assigned_through_type, assigned_through_uuid=:assigned_through_uuid, inherited=:inherited, " +
                "it_system_id=:it_system_id, it_system_name=it_system_name, role_description=:role_description, role_id=:role_id, " +
                "role_name=:role_name, role_group_name=:role_group_name, role_group_id=:role_group_id, role_group_description=:role_group_description, " +
                "sensitive_role=:sensitive_role, extra_sensitive_role=:extra_sensitive_role, ou_name=:ou_name, ou_uuid=:ou_uuid, excepted_user_uuids=:excepted_user_uuids, " +
                "title_uuids=:title_uuids, responsible_user_uuid=:responsible_user_uuid, responsible_ou_name=:responsible_ou_name, " +
                "responsible_ou_uuid=:responsible_ou_uuid, inherit=:inherit, excepted_title_uuids=:excepted_title_uuids " +
                " WHERE id=:id", in);
    }

    public void saveAttestationUserRoleAssignment(final AttestationUserRoleAssignment assignment) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", assignment.getId());
        parameters.put("valid_from", assignment.getValidFrom());
        parameters.put("valid_to", assignment.getValidTo());
        parameters.put("updated_at", assignment.getUpdatedAt());
        parameters.put("record_hash", assignment.getRecordHash());
        parameters.put("user_uuid", assignment.getUserUuid());
        parameters.put("user_id", assignment.getUserId());
        parameters.put("user_name", assignment.getUserName());
        parameters.put("user_role_id", assignment.getUserRoleId());
        parameters.put("user_role_name", assignment.getUserRoleName());
        parameters.put("user_role_description", assignment.getUserRoleDescription());
        parameters.put("role_group_id", assignment.getRoleGroupId());
        parameters.put("role_group_name", assignment.getRoleGroupName());
        parameters.put("role_group_description", assignment.getRoleGroupDescription());
        parameters.put("it_system_id", assignment.getItSystemId());
        parameters.put("it_system_name", assignment.getItSystemName());
        parameters.put("responsible_user_uuid", assignment.getResponsibleUserUuid());
        parameters.put("responsible_ou_name", assignment.getResponsibleOuName());
        parameters.put("role_ou_uuid", assignment.getRoleOuUuid());
        parameters.put("role_ou_name", assignment.getRoleOuName());
        parameters.put("responsible_ou_uuid", assignment.getResponsibleOuUuid());
        parameters.put("assigned_through_type", assignment.getAssignedThroughType().name());
        parameters.put("assigned_through_name", assignment.getAssignedThroughName());
        parameters.put("assigned_through_uuid", assignment.getAssignedThroughUuid());
        parameters.put("inherited", assignment.isInherited());
        parameters.put("sensitive_role", assignment.isSensitiveRole());
        parameters.put("extra_sensitive_role", assignment.isExtraSensitiveRole());
        parameters.put("manager", assignment.isManager());
        namedParameterJdbcTemplate.update("INSERT INTO attestation_user_role_assignments (record_hash, updated_at, valid_from, valid_to," +
                "                                                      assigned_through_name, assigned_through_type," +
                "                                                      assigned_through_uuid, inherited, responsible_ou_name," +
                "                                                      responsible_ou_uuid, manager, responsible_user_uuid," +
                "                                                      sensitive_role, extra_sensitive_role, user_role_description, role_group_id," +
                "                                                      role_group_name, role_group_description, user_role_id," +
                "                                                      user_role_name, user_uuid, user_id, user_name, it_system_id," +
                "                                                      it_system_name, role_ou_uuid, role_ou_name) " +
                "VALUES (:record_hash, :updated_at, :valid_from, :valid_to, " +
                "        :assigned_through_name, :assigned_through_type, :assigned_through_uuid, :inherited, :responsible_ou_name, " +
                "        :responsible_ou_uuid, :manager, :responsible_user_uuid, :sensitive_role, :extra_sensitive_role, :user_role_description, :role_group_id, " +
                "        :role_group_name, :role_group_description, :user_role_id, " +
                "        :user_role_name, :user_uuid, :user_id, :user_name, :it_system_id, :it_system_name, :role_ou_uuid, :role_ou_name)",
                parameters);
    }

    public int updateAttestationUserRoleAssignment(final AttestationUserRoleAssignment assignment) {
        MapSqlParameterSource in = new MapSqlParameterSource();
        in.addValue("id", assignment.getId());
        in.addValue("record_hash", assignment.getRecordHash());
        in.addValue("updated_at", assignment.getUpdatedAt());
        in.addValue("valid_from", assignment.getValidFrom());
        in.addValue("valid_to", assignment.getValidTo());
        in.addValue("assigned_through_name", assignment.getAssignedThroughName());
        in.addValue("assigned_through_type", assignment.getAssignedThroughType().name());
        in.addValue("assigned_through_uuid", assignment.getAssignedThroughUuid());
        in.addValue("inherited", assignment.isInherited());
        in.addValue("responsible_ou_name", assignment.getResponsibleOuName());
        in.addValue("responsible_ou_uuid", assignment.getResponsibleOuUuid());
        in.addValue("manager", assignment.isManager());
        in.addValue("responsible_user_uuid", assignment.getResponsibleUserUuid());
        in.addValue("sensitive_role", assignment.isSensitiveRole());
        in.addValue("extra_sensitive_role", assignment.isExtraSensitiveRole());
        in.addValue("user_role_description", assignment.getUserRoleDescription());
        in.addValue("role_group_id", assignment.getRoleGroupId());
        in.addValue("role_group_name", assignment.getRoleGroupName());
        in.addValue("role_group_description", assignment.getRoleGroupDescription());
        in.addValue("user_role_id", assignment.getUserRoleId());
        in.addValue("user_role_name", assignment.getUserRoleName());
        in.addValue("user_uuid", assignment.getUserUuid());
        in.addValue("user_id", assignment.getUserId());
        in.addValue("user_name", assignment.getUserName());
        in.addValue("it_system_id", assignment.getItSystemId());
        in.addValue("it_system_name", assignment.getItSystemName());
        in.addValue("role_ou_uuid", assignment.getRoleOuUuid());
        in.addValue("role_ou_name", assignment.getRoleOuName());
        return namedParameterJdbcTemplate.update("UPDATE attestation_user_role_assignments SET " +
                "record_hash = :record_hash, updated_at = :updated_at, valid_from = :valid_from, valid_to = :valid_to, " +
                "assigned_through_name = :assigned_through_name, assigned_through_type = :assigned_through_type, assigned_through_uuid = :assigned_through_uuid, " +
                "inherited = :inherited, responsible_ou_name = :responsible_ou_name, responsible_ou_uuid = :responsible_ou_uuid, manager = :manager, " +
                "responsible_user_uuid = :responsible_user_uuid, sensitive_role = :sensitive_role, extra_sensitive_role = :extra_sensitive_role, user_role_description = :user_role_description, " +
                "role_group_id = :role_group_id, role_group_name = :role_group_name, role_group_description = :role_group_description, " +
                "user_role_id = :user_role_id, user_role_name = :user_role_name, user_uuid = :user_uuid, user_id = :user_id, user_name = :user_name, " +
                "it_system_id = :it_system_id, it_system_name = :it_system_name, role_ou_uuid = :role_ou_uuid, role_ou_name = :role_ou_name " +
                "WHERE id=:id", in);
    }
}
