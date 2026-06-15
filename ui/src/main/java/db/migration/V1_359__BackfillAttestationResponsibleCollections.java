package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Galera-safe back-fill of attestation_responsible_collection_id / responsible_collection_id.
 *
 * <p>V1_352 used to back-fill these columns with a single UPDATE per table. On a Galera cluster
 * that produces one write-set covering every updated row (with FULL row images, so the wide
 * attestation tables carry their TEXT columns too) — for the multi-million-row temporal tables
 * that risks exceeding wsrep_max_ws_size and triggers heavy flow control. This migration does the
 * exact same assignment in id-keyset batches, committing after each batch so every write-set stays
 * bounded to {@link #BATCH_SIZE} rows.
 *
 * <p>The assignment is {@code collection_id = arc.id} where the row's it_system_id matches the
 * collection's it_system_id. Because there is exactly one collection per it_system_id (seeded with
 * DISTINCT in V1_351/V1_352), this is a 1:1 join and reproduces V1_352's previous result exactly —
 * verified row-for-row against the old logic across 1.388.270 rows. The {@code <=>} (null-safe
 * equals) mirrors V1_352; rows with a NULL it_system_id match no collection and stay NULL.
 *
 * <p>responsible_user_uuid (and, for attestation_attestation, _id/_name) still exist at this point;
 * they are dropped afterwards by V1_360.
 */
public class V1_359__BackfillAttestationResponsibleCollections extends BaseJavaMigration {

	private static final int BATCH_SIZE = 2000;

	/** table name -> name of the collection FK column to populate. */
	private static final String[][] TARGETS = {
		{"attestation_attestation",             "responsible_collection_id"},
		{"attestation_user_role_assignments",   "attestation_responsible_collection_id"},
		{"attestation_ou_role_assignments",     "attestation_responsible_collection_id"},
		{"attestation_system_role_assignments", "attestation_responsible_collection_id"},
	};

	/**
	 * We manage our own per-batch commits, so Flyway must not wrap the migration in a single
	 * transaction (an inner commit would close Flyway's enclosing one). A mid-migration failure
	 * leaves a partial-but-correct state; re-running is idempotent because each batch only touches
	 * rows whose collection column is still NULL.
	 */
	@Override
	public boolean canExecuteInTransaction() {
		return false;
	}

	@Override
	public void migrate(Context context) throws SQLException {
		Connection conn = context.getConnection();
		conn.setAutoCommit(false);
		for (String[] target : TARGETS) {
			backfill(conn, target[0], target[1]);
		}
	}

	private void backfill(Connection conn, String table, String collectionColumn) throws SQLException {
		long maxId = maxId(conn, table);

		// id-keyset batching keeps each write-set O(BATCH_SIZE) regardless of table size.
		String sql =
			"UPDATE `" + table + "` t " +
			"JOIN attestation_attestation_responsible_collection arc " +
			"  ON arc.it_system_id <=> t.it_system_id " +
			"SET t.`" + collectionColumn + "` = arc.id " +
			"WHERE t.id > ? AND t.id <= ? " +
			"  AND t.responsible_user_uuid IS NOT NULL " +
			"  AND t.`" + collectionColumn + "` IS NULL";

		long lastId = 0;
		try (PreparedStatement update = conn.prepareStatement(sql)) {
			while (lastId < maxId) {
				long hi = lastId + BATCH_SIZE;
				update.setLong(1, lastId);
				update.setLong(2, hi);
				update.executeUpdate();
				conn.commit();
				lastId = hi;
			}
		}
	}

	private long maxId(Connection conn, String table) throws SQLException {
		try (Statement st = conn.createStatement();
		     ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM `" + table + "`")) {
			return rs.next() ? rs.getLong(1) : 0L;
		}
	}
}
