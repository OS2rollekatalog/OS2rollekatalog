package dk.digitalidentity.rc.task.repair;

import java.util.List;

/**
 * A one-off data repair that runs in the background instead of as a Flyway migration.
 * <p>
 * Repairs that can only be computed in Java - record hashes above all - do not belong in a migration:
 * the hash would have to be reimplemented and then frozen, and a single large writeset is hard on
 * Galera. {@link RepairJobRunner} walks the rows in chunks instead, and reruns the whole job whenever
 * {@link #version()} is raised, so changing a hash formula costs one line rather than a migration.
 */
public interface RepairJob {

	/** Identifies the job in settings. Never rename it - a new name reruns the job everywhere. */
	String id();

	/** At least 1. Raise it when the repair should run again, e.g. after changing the hash formula it restores. */
	int version();

	/** Human readable, used in the log lines the runner writes. */
	String description();

	/**
	 * The next ids to repair, ordered ascending, at most {@code limit} of them. An empty list ends the
	 * pass. Keyset pagination on the id keeps every chunk cheap, so the query must be {@code id >
	 * afterId ORDER BY id}, not an offset.
	 */
	List<Long> nextChunk(long afterId, int limit);

	/**
	 * Repairs one row. Must be idempotent: an interrupted pass resumes at a chunk boundary and walks
	 * that chunk again, and the runner cannot tell an already repaired row from an untouched one. Most
	 * rows in a pass are usually fine already - say so by returning false rather than by writing them
	 * again.
	 * <p>
	 * Throw to report that the row could not be repaired: the runner skips it and lets the pass finish,
	 * but does not store the version. The job then idles for the rest of the process - a row that
	 * cannot be repaired now will not start working on the next tick - and the pass is retried when the
	 * application next starts.
	 *
	 * @return true when the row was actually changed, false when there was nothing to do
	 */
	boolean repair(long id);
}
