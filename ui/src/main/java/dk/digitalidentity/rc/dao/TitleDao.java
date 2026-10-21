package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.Title;

public interface TitleDao extends CrudRepository<Title, String>, JpaSpecificationExecutor<Title> {

	// not really deprecated, but findAll() should only be used when we want
	// to access entities that have been deleted, so this is used as a warning
	// to the developer
	//@Deprecated
	List<Title> findAll();

	List<Title> getByActiveTrue();

	Title getByUuidAndActiveTrue(String uuid);

	List<Title> findByUuidInAndActiveTrue(Set<String> titles);

	/**
	 * Union of three title sources, across ouUuid and its active descendants:
	 * <ul>
	 *   <li>{@code positions}: titles actually held by an employee in the org unit today</li>
	 *   <li>{@code ou_title_mapping}: titles the org-structure import feed declares for the
	 *       org unit regardless of current occupancy</li>
	 *   <li>{@code ou_roles_titles}/{@code ou_rolegroups_titles}: titles already referenced by
	 *       an existing role/role-group assignment on the org unit, kept so a stale-but-still-assigned title doesn't silently disappear</li>
	 * </ul>
	 */
	@Query(value = """
		WITH RECURSIVE descendant_tree AS (
		    SELECT uuid, parent_uuid, active
		    FROM ous
		    WHERE uuid = :ouUuid AND active = true

		    UNION ALL

		    SELECT o.uuid, o.parent_uuid, o.active
		    FROM ous o
		    INNER JOIN descendant_tree dt ON o.parent_uuid = dt.uuid
		    WHERE o.active = true
		)
		SELECT DISTINCT p.title_uuid
		FROM positions p
		WHERE p.title_uuid IS NOT NULL
		  AND p.ou_uuid IN (SELECT uuid FROM descendant_tree)

		UNION

		SELECT DISTINCT otm.title_uuid
		FROM ou_title_mapping otm
		WHERE otm.orgunit_uuid IN (SELECT uuid FROM descendant_tree)

		UNION

		SELECT DISTINCT ort.title_uuid
		FROM ou_roles_titles ort
		INNER JOIN ou_roles ur ON ur.id = ort.ou_roles_id
		WHERE ur.ou_uuid IN (SELECT uuid FROM descendant_tree)

		UNION

		SELECT DISTINCT orgt.title_uuid
		FROM ou_rolegroups_titles orgt
		INNER JOIN ou_rolegroups urg ON urg.id = orgt.ou_rolegroups_id
		WHERE urg.ou_uuid IN (SELECT uuid FROM descendant_tree)
		""", nativeQuery = true)
	Set<String> findRelevantTitleUuids(@Param("ouUuid") String ouUuid);

	/**
	 * Union of three title sources, for a single ou:
	 * <ul>
	 *   <li>{@code positions}: titles actually held by an employee in the org unit today</li>
	 *   <li>{@code ou_title_mapping}: titles the org-structure import feed declares for the org unit</li>
	 *   <li>{@code ou_roles_titles}/{@code ou_rolegroups_titles}: titles already referenced by
	 *       an existing role/role-group assignment on the org unit, kept so a stale-but-still-assigned title doesn't silently disappear</li>
	 * </ul>
	 */
	@Query(value = """
		SELECT DISTINCT p.title_uuid
		FROM positions p
		WHERE p.title_uuid IS NOT NULL
		  AND p.ou_uuid = :ouUuid

		UNION

		SELECT DISTINCT otm.title_uuid
		FROM ou_title_mapping otm
		WHERE otm.orgunit_uuid = :ouUuid

		UNION

		SELECT DISTINCT ort.title_uuid
		FROM ou_roles_titles ort
		INNER JOIN ou_roles ur ON ur.id = ort.ou_roles_id
		WHERE ur.ou_uuid = :ouUuid

		UNION

		SELECT DISTINCT orgt.title_uuid
		FROM ou_rolegroups_titles orgt
		INNER JOIN ou_rolegroups urg ON urg.id = orgt.ou_rolegroups_id
		WHERE urg.ou_uuid = :ouUuid
		""", nativeQuery = true)
	Set<String> findRelevantTitleUuidsForSingleOu(@Param("ouUuid") String ouUuid);

}
