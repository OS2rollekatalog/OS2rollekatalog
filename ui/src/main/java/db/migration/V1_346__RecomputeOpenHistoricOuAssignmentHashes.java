package db.migration;

import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentHashCalculator;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V1_345 added start_date and stop_date to historic_ou_assignment. These fields are now part of
 * the record_hash, because a planned-stop-date change represents a new temporal state that should
 * close the previous interval and open a new one.
 *
 * HashUtil uses positional hashing: adding two new fields shifts the hash even when both are NULL,
 * so every open row's stored hash is now stale. This migration recomputes it using the same
 * HistoricOuAssignmentHashCalculator that production code uses, keeping a single source of truth.
 * Only open rows (valid_to IS NULL) are affected — closed rows represent immutable past intervals
 * and their hashes are never read again.
 */
public class V1_346__RecomputeOpenHistoricOuAssignmentHashes extends BaseJavaMigration {

	private static final int BATCH_SIZE = 500;

	/**
	 * Flyway wraps {@link #migrate(Context)} in its own transaction by default, which collides
	 * with the per-batch commits we issue: an inner commit on the same connection would close
	 * Flyway's enclosing transaction and prevent it from recording the migration as successful.
	 * Opt out of the wrapping transaction so we can manage commits ourselves — a mid-migration
	 * failure then leaves a partial-but-correct state, and re-running picks up where it
	 * stopped (the operation is idempotent).
	 */
	@Override
	public boolean canExecuteInTransaction() {
		return false;
	}

	@Override
	public void migrate(Context context) throws SQLException {
		Connection conn = context.getConnection();
		conn.setAutoCommit(false);

		// Keyset pagination on id keeps each batch O(BATCH_SIZE) regardless of table size.
		// OFFSET would force the database to re-scan all skipped rows on every batch.
		long lastId = 0;
		List<Long> processedIds;
		do {
			processedIds = processBatch(conn, lastId);
			conn.commit();
			if (!processedIds.isEmpty()) {
				lastId = processedIds.get(processedIds.size() - 1);
			}
		} while (processedIds.size() == BATCH_SIZE);
	}

	private List<Long> processBatch(Connection conn, long lastId) throws SQLException {
		List<Long> ids = new ArrayList<>();
		List<HistoricOuAssignment> rows = new ArrayList<>();

		// Only the fields that feed into the hash are selected — no need to load display fields.
		try (PreparedStatement selectRows = conn.prepareStatement(
				"SELECT id, ou_uuid, it_system_id, role_id, role_role_group_id, " +
				"assigned_through_type, assigned_through_uuid, " +
				"applies_only_to_manager, applies_also_to_substitutes, inherit_to_children, " +
				"start_date, stop_date " +
				"FROM historic_ou_assignment " +
				"WHERE valid_to IS NULL AND id > ? " +
				"ORDER BY id LIMIT ?")) {
			selectRows.setLong(1, lastId);
			selectRows.setInt(2, BATCH_SIZE);
			try (ResultSet rs = selectRows.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getLong("id"));
					rows.add(buildRowWithoutExclusions(rs));
				}
			}
		}

		if (ids.isEmpty()) {
			return ids;
		}

		// Load exclusions for the whole batch in a single IN-clause query — avoids the
		// N+1 round-trip pattern of one SELECT per row.
		Map<Long, List<HistoricOuAssignmentExclusion>> exclusionsById = loadExclusionsForBatch(conn, ids);

		try (PreparedStatement update = conn.prepareStatement(
				"UPDATE historic_ou_assignment SET record_hash = ? WHERE id = ?")) {
			for (int i = 0; i < ids.size(); i++) {
				HistoricOuAssignment row = rows.get(i);
				row.setExclusions(exclusionsById.getOrDefault(ids.get(i), Collections.emptyList()));
				update.setString(1, HistoricOuAssignmentHashCalculator.compute(row));
				update.setLong(2, ids.get(i));
				update.addBatch();
			}
			update.executeBatch();
		}

		return ids;
	}

	private Map<Long, List<HistoricOuAssignmentExclusion>> loadExclusionsForBatch(
			Connection conn, List<Long> ids) throws SQLException {
		String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
		Map<Long, List<HistoricOuAssignmentExclusion>> result = new HashMap<>();
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT historic_ou_assignment_id, exclusion_type, uuids " +
				"FROM historic_ou_assignment_exclusion " +
				"WHERE historic_ou_assignment_id IN (" + placeholders + ")")) {
			for (int i = 0; i < ids.size(); i++) {
				ps.setLong(i + 1, ids.get(i));
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					long assignmentId = rs.getLong("historic_ou_assignment_id");
					ExclusionType type = ExclusionType.valueOf(rs.getString("exclusion_type"));
					String raw = rs.getString("uuids");
					List<String> uuids = StringUtils.isEmpty(raw)
						? Collections.emptyList()
						: new ArrayList<>(Arrays.asList(raw.split(",")));
					result.computeIfAbsent(assignmentId, k -> new ArrayList<>())
						.add(HistoricOuAssignmentExclusion.builder()
							.exclusionType(type)
							.uuids(uuids)
							.build());
				}
			}
		}
		return result;
	}

	private HistoricOuAssignment buildRowWithoutExclusions(ResultSet rs) throws SQLException {
		String assignedThroughTypeStr = rs.getString("assigned_through_type");
		AssignedThrough assignedThroughType = assignedThroughTypeStr != null
			? AssignedThrough.valueOf(assignedThroughTypeStr)
			: null;

		Long roleRoleGroupId = rs.getObject("role_role_group_id") != null
			? rs.getLong("role_role_group_id")
			: null;

		Long itSystemId = rs.getObject("it_system_id") != null
			? rs.getLong("it_system_id")
			: null;

		Long roleId = rs.getObject("role_id") != null
			? rs.getLong("role_id")
			: null;

		java.sql.Date startDateSql = rs.getDate("start_date");
		LocalDate startDate = startDateSql != null ? startDateSql.toLocalDate() : null;

		java.sql.Date stopDateSql = rs.getDate("stop_date");
		LocalDate stopDate = stopDateSql != null ? stopDateSql.toLocalDate() : null;

		return HistoricOuAssignment.builder()
			.ouUuid(rs.getString("ou_uuid"))
			.itSystemId(itSystemId)
			.roleId(roleId)
			.roleRoleGroupId(roleRoleGroupId)
			.assignedThroughType(assignedThroughType)
			.assignedThroughUuid(rs.getString("assigned_through_uuid"))
			.appliesOnlyToManager(rs.getBoolean("applies_only_to_manager"))
			.appliesAlsoToSubstitutes(rs.getBoolean("applies_also_to_substitutes"))
			.inheritToChildren(rs.getBoolean("inherit_to_children"))
			.startDate(startDate)
			.stopDate(stopDate)
			.exclusions(new ArrayList<>())
			.build();
	}
}
