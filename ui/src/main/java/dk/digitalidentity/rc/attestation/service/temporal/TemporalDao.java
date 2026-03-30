package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.AttestationOuRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.AttestationSystemRoleAssignmentConstraintsRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.AttestationSystemRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.AttestationUserRoleAssignmentRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.IdRowMapper;
import dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TemporalDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final AttestationUserRoleAssignmentRowMapper attestationUserRoleAssignmentRowMapper = new AttestationUserRoleAssignmentRowMapper();
    private final AttestationOuRoleAssignmentRowMapper attestationOuRoleAssignmentRowMapper = new AttestationOuRoleAssignmentRowMapper();
    private final AttestationSystemRoleAssignmentRowMapper attestationSystemRoleAssignmentRowMapper = new AttestationSystemRoleAssignmentRowMapper();
    private final AttestationSystemRoleAssignmentConstraintsRowMapper attestationSystemRoleAssignmentConstraintsRowMapper = new AttestationSystemRoleAssignmentConstraintsRowMapper();

    private final IdRowMapper idRowMapper = new IdRowMapper();

    /**
     * Reads from {@code historic_ou_assignment} + {@code historic_ou_assignment_exclusion} and
     * maps directly into {@link HistoricOuAssignment} objects.
     */
    public List<HistoricOuAssignment> listHistoricOuAssignmentsByDate(final LocalDate when) {
        String sql = """
            SELECT
                hoa.id AS assignment_id,
                hoa.ou_uuid,
                hoa.ou_name,
                hoa.it_system_id,
                hoa.it_system_name,
                hoa.role_id,
                hoa.role_name,
                hoa.role_description,
                hoa.role_role_group_id,
                hoa.role_role_group_name,
                hoa.role_group_description,
                hoa.sensitive_role,
                hoa.extra_sensitive_role,
                hoa.responsible_user_uuid,
                hoa.it_system_attestation_exempt,
                hoa.assigned_through_type,
                hoa.assigned_through_uuid,
                hoa.assigned_through_name,
                hoa.inherit_to_children,
                hoa.applies_only_to_manager,
                hoa.applies_also_to_substitutes,
                hoa.assigned_when,
                e.id AS exclusion_id,
                e.exclusion_type,
                e.uuids AS exclusion_uuids
            FROM historic_ou_assignment hoa
            LEFT JOIN historic_ou_assignment_exclusion e ON e.historic_ou_assignment_id = hoa.id
            WHERE hoa.valid_from <= ? AND (hoa.valid_to IS NULL OR hoa.valid_to > ?)
            ORDER BY hoa.id
        """;

        return jdbcTemplate.query(sql, rs -> {
            Map<Long, HistoricOuAssignment> map = new LinkedHashMap<>();
            while (rs.next()) {
                long assignmentId = rs.getLong("assignment_id");
                HistoricOuAssignment assignment = map.get(assignmentId);
                if (assignment == null) {
                    String throughType = rs.getString("assigned_through_type");
                    java.sql.Timestamp assignedWhen = rs.getTimestamp("assigned_when");
                    Long roleRoleGroupId = rs.getLong("role_role_group_id");
                    if (rs.wasNull()) roleRoleGroupId = null;

                    assignment = HistoricOuAssignment.builder()
                        .id(assignmentId)
                        .ouUuid(rs.getString("ou_uuid"))
                        .ouName(rs.getString("ou_name"))
                        .itSystemId(rs.getLong("it_system_id"))
                        .itSystemName(rs.getString("it_system_name"))
                        .roleId(rs.getLong("role_id"))
                        .roleName(rs.getString("role_name"))
                        .roleDescription(rs.getString("role_description"))
                        .roleRoleGroupId(roleRoleGroupId)
                        .roleRoleGroupName(rs.getString("role_role_group_name"))
                        .roleGroupDescription(rs.getString("role_group_description"))
                        .sensitiveRole(rs.getBoolean("sensitive_role"))
                        .extraSensitiveRole(rs.getBoolean("extra_sensitive_role"))
                        .responsibleUserUuid(rs.getString("responsible_user_uuid"))
                        .itSystemAttestationExempt(rs.getBoolean("it_system_attestation_exempt"))
                        .assignedThroughType(throughType != null ? AssignedThrough.valueOf(throughType) : null)
                        .assignedThroughUuid(rs.getString("assigned_through_uuid"))
                        .assignedThroughName(rs.getString("assigned_through_name"))
                        .inheritToChildren(rs.getBoolean("inherit_to_children"))
                        .appliesOnlyToManager(rs.getBoolean("applies_only_to_manager"))
                        .appliesAlsoToSubstitutes(rs.getBoolean("applies_also_to_substitutes"))
                        .assignedWhen(assignedWhen != null ? assignedWhen.toLocalDateTime() : null)
                        .exclusions(new ArrayList<>())
                        .build();
                    map.put(assignmentId, assignment);
                }
                long exclusionId = rs.getLong("exclusion_id");
                if (!rs.wasNull()) {
                    String exclusionType = rs.getString("exclusion_type");
                    String uuids = rs.getString("exclusion_uuids");
                    if (exclusionType != null) {
                        HistoricOuAssignmentExclusion ex = HistoricOuAssignmentExclusion.builder()
                            .id(exclusionId)
                            .exclusionType(HistoricOuAssignmentExclusion.ExclusionType.valueOf(exclusionType))
                            .uuids(uuids != null && !uuids.isEmpty() ? Arrays.asList(uuids.split(",")) : new ArrayList<>())
                            .build();
                        assignment.getExclusions().add(ex);
                    }
                }
            }
            return new ArrayList<>(map.values());
        }, when.plusDays(1).atStartOfDay(), when.atStartOfDay()); // Includes all active assignments, including those that are only active during part of this date
    }

    /**
     * Returns distinct IT-system IDs from {@code historic_assignment} that are active at {@code when}
     * and whose IT-system is NOT marked {@code attestation_exempt} in the {@code it_systems} table.
     */
    public List<Long> getDistinctAssignedItSystems(final LocalDate when) {
        java.time.LocalDateTime start = when.atStartOfDay();
        java.time.LocalDateTime end = when.plusDays(1).atStartOfDay();
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT ha.it_system_id " +
            "FROM historic_assignment ha " +
            "JOIN it_systems its ON its.id = ha.it_system_id " +
            "WHERE ha.valid_from <= ? AND (ha.valid_to IS NULL OR ha.valid_to > ?) " +
            "  AND ha.it_system_id IS NOT NULL " +
            "  AND its.attestation_exempt = 0",
            Long.class, end, start);
    }

    /**
     * Returns distinct IT-system IDs from {@code historic_it_system_assignment} that are active at {@code when}
     * and whose IT-system is NOT marked {@code attestation_exempt} in the {@code it_systems} table.
     */
    public List<Long> getDistinctItSystemIdsFromHistoricItSystemAssignment(final LocalDate when) {
        final java.time.LocalDateTime start = when.atStartOfDay();
        final java.time.LocalDateTime end = when.plusDays(1).atStartOfDay();
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT it_system_id FROM historic_it_system_assignment " +
            "JOIN it_systems its ON its.id = it_system_id " +
            "WHERE valid_from <= ? AND (valid_to IS NULL OR valid_to > ?) AND its.attestation_exempt = 0",
            Long.class, end, start);
    }

    /**
     * Returns all {@link HistoricItSystemAssignment} records (with their constraints) that are active at
     * {@code when} for the given IT-system. Populated entirely via JDBC to avoid Hibernate overhead.
     */
    public List<HistoricItSystemAssignment> listHistoricItSystemAssignmentsByItSystemAndDate(final LocalDate when, final long itSystemId) {
        final java.time.LocalDateTime start = when.atStartOfDay();
        final java.time.LocalDateTime end = when.plusDays(1).atStartOfDay();
        final String sql = """
			SELECT h.id, h.record_hash, h.valid_from, h.valid_to,
			       h.it_system_id, h.it_system_name, h.it_system_attestation_exempt,
			       h.responsible_user_uuid,
			       h.user_role_id, h.user_role_name, h.user_role_description,
			       h.system_role_id, h.system_role_name, h.system_role_description,
			       c.id AS constraint_id, c.constraint_name, c.constraint_value_type, c.constraint_value
			FROM historic_it_system_assignment h
			INNER JOIN (                                                                                                                                                                                                                                                                    \s
				SELECT user_role_id, system_role_id, MAX(id) AS max_id                                                                                                                                                                                                                      \s
				FROM historic_it_system_assignment                                                                                                                                                                                                                                          \s
				WHERE valid_from <= ? AND (valid_to IS NULL OR valid_to > ?) AND it_system_id = ?                                                                                                                                                                                           \s
				GROUP BY user_role_id, system_role_id                                                                                                                                                                                                                                       \s
			) latest ON latest.max_id = h.id
			LEFT JOIN historic_it_system_assignment_constraint c ON c.historic_it_system_assignment_id = h.id
			ORDER BY h.id
            """;
        return jdbcTemplate.query(sql, rs -> {
            final LinkedHashMap<Long, HistoricItSystemAssignment> map = new LinkedHashMap<>();
            while (rs.next()) {
                final long assignmentId = rs.getLong("id");
                HistoricItSystemAssignment assignment = map.get(assignmentId);
                if (assignment == null) {
                    assignment = HistoricItSystemAssignment.builder()
                        .id(assignmentId)
                        .recordHash(rs.getString("record_hash"))
                        .validFrom(rs.getTimestamp("valid_from") != null ? rs.getTimestamp("valid_from").toLocalDateTime() : null)
                        .validTo(rs.getTimestamp("valid_to") != null ? rs.getTimestamp("valid_to").toLocalDateTime() : null)
                        .itSystemId(rs.getLong("it_system_id"))
                        .itSystemName(rs.getString("it_system_name"))
                        .itSystemAttestationExempt(rs.getBoolean("it_system_attestation_exempt"))
                        .responsibleUserUuid(rs.getString("responsible_user_uuid"))
                        .userRoleId(rs.getLong("user_role_id"))
                        .userRoleName(rs.getString("user_role_name"))
                        .userRoleDescription(rs.getString("user_role_description"))
                        .systemRoleId(rs.getLong("system_role_id"))
                        .systemRoleName(rs.getString("system_role_name"))
                        .systemRoleDescription(rs.getString("system_role_description"))
                        .build();
                    map.put(assignmentId, assignment);
                }
                final long constraintId = rs.getLong("constraint_id");
                if (!rs.wasNull()) {
                    final String valueTypeStr = rs.getString("constraint_value_type");
                    HistoricItSystemAssignmentConstraint constraint = HistoricItSystemAssignmentConstraint.builder()
                        .id(constraintId)
                        .constraintName(rs.getString("constraint_name"))
                        .constraintValueType(valueTypeStr != null ? ConstraintValueType.valueOf(valueTypeStr) : null)
                        .constraintValue(rs.getString("constraint_value"))
                        .historicItSystemAssignment(assignment)
                        .build();
                    assignment.getConstraints().add(constraint);
                }
            }
            return new ArrayList<>(map.values());
        }, end, start, itSystemId);
    }

    /**
     * Returns all {@link HistoricAssignment} records (with their constraints) that are active at
     * {@code when} for the given IT-system. Populated entirely via JDBC to avoid Hibernate overhead.
     */
    public List<HistoricAssignment> findHistoricAssignmentsByItSystemAndDate(final LocalDate when, final long itSystemId) {
        final java.time.LocalDateTime startOfDay = when.atStartOfDay();
        final java.time.LocalDateTime endOfDay = when.plusDays(1).atStartOfDay();
        final String sql = """
            SELECT ha.id, ha.record_hash, ha.valid_from, ha.valid_to, ha.start_date, ha.stop_date,
                   ha.user_uuid, ha.user_id, ha.user_name,
                   ha.user_role_id, ha.user_role_name, ha.user_role_description,
                   ha.sensitive_role, ha.extra_sensitive_role,
                   ha.it_system_id, ha.it_system_name,
                   ha.role_group_id, ha.role_group_name, ha.role_group_description,
                   ha.assigned_by, ha.assigned_through_type,
                   ha.assigned_through_ou_uuid, ha.assigned_through_ou_name,
                   ha.assigned_through_title_uuid, ha.assigned_through_title_name,
                   ha.assigned_through_rg_id, ha.assigned_through_rg_name,
                   ha.responsible_ou_uuid, ha.responsible_ou_name, ha.responsible_user_uuid,
                   hac.id AS constraint_id, hac.constraint_type_uuid, hac.constraint_type_name,
                   hac.constraint_type_entity_id, hac.value AS constraint_value
            FROM historic_assignment ha
            LEFT JOIN historic_assignment_constraint hac ON hac.historic_assignment_id = ha.id
            WHERE ha.valid_from <= ? AND (ha.valid_to IS NULL OR ha.valid_to > ?) AND ha.it_system_id = ?
            ORDER BY ha.id
            """;
        return jdbcTemplate.query(sql, rs -> {
            final LinkedHashMap<Long, HistoricAssignment> map = new LinkedHashMap<>();
            while (rs.next()) {
                final long assignmentId = rs.getLong("id");
                HistoricAssignment assignment = map.get(assignmentId);
                if (assignment == null) {
                    assignment = mapHistoricAssignment(rs, assignmentId);
                    map.put(assignmentId, assignment);
                }
                final long constraintId = rs.getLong("constraint_id");
                if (!rs.wasNull()) {
                    assignment.getConstraints().add(mapHistoricAssignmentConstraint(rs, constraintId, assignment));
                }
            }
            return new ArrayList<>(map.values());
        }, endOfDay, startOfDay, itSystemId);
    }

    private static HistoricAssignment mapHistoricAssignment(final java.sql.ResultSet rs, final long id) throws java.sql.SQLException {
        final HistoricAssignment a = new HistoricAssignment();
        a.setId(id);
        a.setRecordHash(rs.getString("record_hash"));
        final java.sql.Timestamp validFrom = rs.getTimestamp("valid_from");
        a.setValidFrom(validFrom != null ? validFrom.toLocalDateTime() : null);
        final java.sql.Timestamp validTo = rs.getTimestamp("valid_to");
        a.setValidTo(validTo != null ? validTo.toLocalDateTime() : null);
        final java.sql.Date startDate = rs.getDate("start_date");
        a.setStartDate(startDate != null ? startDate.toLocalDate() : null);
        final java.sql.Date stopDate = rs.getDate("stop_date");
        a.setStopDate(stopDate != null ? stopDate.toLocalDate() : null);
        a.setUserUuid(rs.getString("user_uuid"));
        a.setUserId(rs.getString("user_id"));
        a.setUserName(rs.getString("user_name"));
        a.setUserRoleId(nullableLong(rs, "user_role_id"));
        a.setUserRoleName(rs.getString("user_role_name"));
        a.setUserRoleDescription(rs.getString("user_role_description"));
        a.setSensitiveRole(rs.getBoolean("sensitive_role"));
        a.setExtraSensitiveRole(rs.getBoolean("extra_sensitive_role"));
        a.setItSystemId(nullableLong(rs, "it_system_id"));
        a.setItSystemName(rs.getString("it_system_name"));
        a.setRoleGroupId(nullableLong(rs, "role_group_id"));
        a.setRoleGroupName(rs.getString("role_group_name"));
        a.setRoleGroupDescription(rs.getString("role_group_description"));
        a.setAssignedBy(rs.getString("assigned_by"));
        final String throughType = rs.getString("assigned_through_type");
        a.setAssignedThroughType(throughType != null ? AssignedThrough.valueOf(throughType) : null);
        a.setAssignedThroughOUUuid(rs.getString("assigned_through_ou_uuid"));
        a.setAssignedThroughOUName(rs.getString("assigned_through_ou_name"));
        a.setAssignedThroughTitleUuid(rs.getString("assigned_through_title_uuid"));
        a.setAssignedThroughTitleName(rs.getString("assigned_through_title_name"));
        a.setAssignedThroughRoleGroupId(nullableLong(rs, "assigned_through_rg_id"));
        a.setAssignedThroughRoleGroupName(rs.getString("assigned_through_rg_name"));
        a.setResponsibleOUUuid(rs.getString("responsible_ou_uuid"));
        a.setResponsibleOUName(rs.getString("responsible_ou_name"));
        a.setResponsibleUserUuid(rs.getString("responsible_user_uuid"));
        a.setConstraints(new HashSet<>());
        return a;
    }

    private static HistoricAssignmentConstraint mapHistoricAssignmentConstraint(
            final java.sql.ResultSet rs, final long id, final HistoricAssignment parent) throws java.sql.SQLException {
        final HistoricAssignmentConstraint c = new HistoricAssignmentConstraint();
        c.setId(id);
        c.setConstraintTypeUuid(rs.getString("constraint_type_uuid"));
        c.setConstraintTypeName(rs.getString("constraint_type_name"));
        c.setConstraintTypeEntityId(rs.getString("constraint_type_entity_id"));
        final String rawValue = rs.getString("constraint_value");
        c.setValue(rawValue != null ? Arrays.asList(rawValue.split(",")) : new ArrayList<>());
        c.setHistoricAssignment(parent);
        return c;
    }

    /** Reads a nullable BIGINT column, returning {@code null} instead of {@code 0} when the value is SQL NULL. */
    private static Long nullableLong(final java.sql.ResultSet rs, final String column) throws java.sql.SQLException {
        final long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    // Roles assigned to Users

    public Optional<AttestationUserRoleAssignment> findValidUserRoleAssignmentWithHash(final LocalDate when, final String hash) {
        List<AttestationUserRoleAssignment> result = jdbcTemplate.query("SELECT * FROM attestation_user_role_assignments a WHERE a.record_hash = ? and valid_from <= ? AND (valid_to > ? or valid_to is null)", attestationUserRoleAssignmentRowMapper, hash, when, when);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.getFirst());
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
        final List<AttestationSystemRoleAssignment> result = jdbcTemplate.query("SELECT * FROM attestation_system_role_assignments a WHERE a.record_hash = ? and valid_from <= ? AND (valid_to > ? or valid_to is null)", attestationSystemRoleAssignmentRowMapper, hash, when, when);
        result.forEach(r -> {
            final List<AttestationSystemRoleAssignmentConstraint> constraints = jdbcTemplate.query("SELECT * FROM attestation_system_role_assignment_constraints WHERE attestation_system_role_assignments_id=?", attestationSystemRoleAssignmentConstraintsRowMapper, r.getId());
            constraints.forEach(c -> c.setAssignment(r));
            r.setConstraints(new HashSet<>(constraints));
        });
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

    public long setUpdatedTimestampForUserRoleAssignmentsWithIdsIn(final List<Long> ids, final LocalDate updatedAt) {
        return namedParameterJdbcTemplate.update("UPDATE attestation_user_role_assignments SET updated_at=:updatedAt WHERE id in (:ids)",
                Map.of("ids", ids, "updatedAt", updatedAt));
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
        final KeyHolder keyHolder = new GeneratedKeyHolder();
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
                new MapSqlParameterSource(parameters),
                keyHolder,
                new String[] { "id" }
        );
        assignment.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        insertAttestationSystemRoleAssignmentConstraints(assignment);
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
        namedParameterJdbcTemplate.update("DELETE FROM attestation_system_role_assignment_constraints WHERE attestation_system_role_assignments_id=:id", in);
        insertAttestationSystemRoleAssignmentConstraints(assignment);
        return namedParameterJdbcTemplate.update("UPDATE attestation_system_role_assignments SET " +
                "record_hash=:record_hash, updated_at=:updated_at, valid_from=:valid_from, valid_to=:valid_to, it_system_id=:it_system_id, "+
                "it_system_name=:it_system_name, responsible_user_uuid=:responsible_user_uuid, system_role_description=:system_role_description, " +
                "system_role_id=:system_role_id, system_role_name=:system_role_name, user_role_description=:user_role_description, " +
                "user_role_id=:user_role_id, user_role_name=:user_role_name " +
                " WHERE id=:id", in);
    }

    private void insertAttestationSystemRoleAssignmentConstraints(AttestationSystemRoleAssignment assignment) {
        if (assignment.getConstraints() != null && !assignment.getConstraints().isEmpty() && assignment.getId() != null) {
            assignment.getConstraints().forEach(constraint -> {
                final Map<String, Object> constraintParameters = new HashMap<>();
                constraintParameters.put("attestation_system_role_assignments_id", assignment.getId());
                constraintParameters.put("name", constraint.getName());
                constraintParameters.put("value_type", constraint.getValueType().name());
                constraintParameters.put("value", constraint.getValue());
                namedParameterJdbcTemplate.update("INSERT INTO attestation_system_role_assignment_constraints (attestation_system_role_assignments_id, name, value_Type, value) " +
                        "VALUES (:attestation_system_role_assignments_id, :name, :value_type, :value)", constraintParameters);
            });
        }
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
        parameters.put("function_uuids", RowMapperUtils.joinFromList(assignment.getFunctionUuids()));
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
		parameters.put("manager", assignment.isManager());
		parameters.put("substitutes", assignment.isSubstitutes());
        namedParameterJdbcTemplate.update("INSERT INTO attestation_ou_role_assignments (record_hash, updated_at, valid_from, valid_to," +
                "                                                    assigned_through_name, assigned_through_type, assigned_through_uuid," +
                "                                                    inherited, it_system_id, it_system_name, role_description, role_id," +
                "                                                    role_name, role_group_name, role_group_id, role_group_description," +
                "                                                    sensitive_role, extra_sensitive_role, ou_name, ou_uuid, excepted_user_uuids, title_uuids," +
                "                                                    function_uuids, responsible_user_uuid, responsible_ou_name, responsible_ou_uuid," +
                "                                                    inherit, excepted_title_uuids, manager, substitutes) " +
                " VALUES (:record_hash, :updated_at, :valid_from, :valid_to, :assigned_through_name, :assigned_through_type, :assigned_through_uuid," +
                "         :inherited, :it_system_id, :it_system_name, :role_description, :role_id, :role_name, :role_group_name, " +
                "         :role_group_id, :role_group_description, :sensitive_role, :extra_sensitive_role, :ou_name, :ou_uuid, :excepted_user_uuids, " +
                "         :title_uuids, :function_uuids, :responsible_user_uuid, :responsible_ou_name, :responsible_ou_uuid, :inherit, :excepted_title_uuids, :manager, :substitutes)",
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
        in.addValue("function_uuids", RowMapperUtils.joinFromList(assignment.getFunctionUuids()));
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
		in.addValue("manager", assignment.isManager());
		in.addValue("substitutes", assignment.isSubstitutes());

        return namedParameterJdbcTemplate.update("UPDATE attestation_ou_role_assignments SET " +
                "record_hash=:record_hash, updated_at=:updated_at, valid_from=:valid_from, valid_to=:valid_to, assigned_through_name=:assigned_through_name, " +
                "assigned_through_type=:assigned_through_type, assigned_through_uuid=:assigned_through_uuid, inherited=:inherited, " +
                "it_system_id=:it_system_id, it_system_name=it_system_name, role_description=:role_description, role_id=:role_id, " +
                "role_name=:role_name, role_group_name=:role_group_name, role_group_id=:role_group_id, role_group_description=:role_group_description, " +
                "sensitive_role=:sensitive_role, extra_sensitive_role=:extra_sensitive_role, ou_name=:ou_name, ou_uuid=:ou_uuid, excepted_user_uuids=:excepted_user_uuids, " +
                "title_uuids=:title_uuids, function_uuids=:function_uuids, responsible_user_uuid=:responsible_user_uuid, responsible_ou_name=:responsible_ou_name, " +
                "responsible_ou_uuid=:responsible_ou_uuid, inherit=:inherit, excepted_title_uuids=:excepted_title_uuids, manager=:manager, substitutes=:substitutes " +
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
        parameters.put("postponed_constraints", assignment.getPostponedConstraints());
        parameters.put("assigned_from", assignment.getAssignedFrom());
        namedParameterJdbcTemplate.update("INSERT INTO attestation_user_role_assignments (record_hash, updated_at, valid_from, valid_to," +
                "                                                      assigned_through_name, assigned_through_type," +
                "                                                      assigned_through_uuid, inherited, responsible_ou_name," +
                "                                                      responsible_ou_uuid, responsible_user_uuid," +
                "                                                      sensitive_role, extra_sensitive_role, user_role_description, role_group_id," +
                "                                                      role_group_name, role_group_description, user_role_id," +
                "                                                      user_role_name, user_uuid, user_id, user_name, it_system_id," +
                "                                                      it_system_name, role_ou_uuid, role_ou_name, postponed_constraints, assigned_from) " +
                "VALUES (:record_hash, :updated_at, :valid_from, :valid_to, " +
                "        :assigned_through_name, :assigned_through_type, :assigned_through_uuid, :inherited, :responsible_ou_name, " +
                "        :responsible_ou_uuid, :responsible_user_uuid, :sensitive_role, :extra_sensitive_role, :user_role_description, :role_group_id, " +
                "        :role_group_name, :role_group_description, :user_role_id, " +
                "        :user_role_name, :user_uuid, :user_id, :user_name, :it_system_id, :it_system_name, :role_ou_uuid, :role_ou_name, :postponed_constraints, :assigned_from) ",
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
		in.addValue("postponed_constraints", assignment.getPostponedConstraints());
        in.addValue("assigned_from", assignment.getAssignedFrom());
        return namedParameterJdbcTemplate.update("UPDATE attestation_user_role_assignments SET " +
                "record_hash = :record_hash, updated_at = :updated_at, valid_from = :valid_from, valid_to = :valid_to, " +
                "assigned_through_name = :assigned_through_name, assigned_through_type = :assigned_through_type, assigned_through_uuid = :assigned_through_uuid, " +
                "inherited = :inherited, responsible_ou_name = :responsible_ou_name, responsible_ou_uuid = :responsible_ou_uuid, " +
                "responsible_user_uuid = :responsible_user_uuid, sensitive_role = :sensitive_role, extra_sensitive_role = :extra_sensitive_role, user_role_description = :user_role_description, " +
                "role_group_id = :role_group_id, role_group_name = :role_group_name, role_group_description = :role_group_description, " +
                "user_role_id = :user_role_id, user_role_name = :user_role_name, user_uuid = :user_uuid, user_id = :user_id, user_name = :user_name, " +
                "it_system_id = :it_system_id, it_system_name = :it_system_name, role_ou_uuid = :role_ou_uuid, role_ou_name = :role_ou_name, postponed_constraints=:postponed_constraints, " +
                "assigned_from = :assigned_from " +
                "WHERE id=:id", in);
    }
}
