package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds all data pre-loaded before a streaming report run.
 * <p>
 * Building this object upfront means the stream forEach-loop only needs one argument
 * instead of carrying 8–10 individual maps through every private method.
 * <p>
 * Every field is derived from data already in memory — no extra DB queries happen here.
 */
class StreamingReportContext {

    /** History users for the report date, keyed by userUuid. */
    final Map<String, HistoryUser> users;

    /** History OUs for the report date, keyed by ouUuid. */
    final Map<String, HistoryOU> orgUnits;

    /**
     * userUuid → the positions the user holds — one entry per position, so a user with two positions
     * in the same OU appears twice.
     * Pre-computed once so each assignment lookup is O(1) instead of O(n_OUs).
     */
    final Map<String, List<UserPosition>> userPositionsMap;

    /**
     * ouUuid → the OU itself plus all its ancestors, so "is this position inside that OU?" is O(1).
     */
    final Map<String, Set<String>> ouSelfAndAncestors;

    /**
     * OrgUnit UUID → display name, populated from the live (non-history) OrgUnit table.
     * Used to resolve organisation constraint values to human-readable names.
     */
    final Map<String, String> orgUnitNamesByUuid;

    /**
     * userRoleId → weight, derived from IT-system metadata.
     * Used to determine the sensitivity weight of each user-role.
     */
    final Map<Long, Long> userRoleIdWeight;

    /**
     * userId → (itSystemName → max weight across all assignments for that user/system pair).
     * Pre-computed in a lightweight first pass over the DB before streaming full entities.
     */
    final Map<String, Map<String, Long>> userItSystemMaxWeight;

    /**
     * assignmentId → list of constraint rows ([constraintTypeName, List&lt;String&gt; values]).
     * Loaded in one bulk query before streaming to avoid N+1 lazy loads per entity.
     */
    final Map<Long, List<Object[]>> constraintsByAssignmentId;

    StreamingReportContext(
            Map<String, HistoryUser> users,
            Map<String, HistoryOU> orgUnits,
            Map<String, List<UserPosition>> userPositionsMap,
            Map<String, Set<String>> ouSelfAndAncestors,
            Map<String, String> orgUnitNamesByUuid,
            Map<Long, Long> userRoleIdWeight,
            Map<String, Map<String, Long>> userItSystemMaxWeight,
            Map<Long, List<Object[]>> constraintsByAssignmentId) {
        this.users = users;
        this.orgUnits = orgUnits;
        this.userPositionsMap = userPositionsMap;
        this.ouSelfAndAncestors = ouSelfAndAncestors;
        this.orgUnitNamesByUuid = orgUnitNamesByUuid;
        this.userRoleIdWeight = userRoleIdWeight;
        this.userItSystemMaxWeight = userItSystemMaxWeight;
        this.constraintsByAssignmentId = constraintsByAssignmentId;
    }

    /**
     * Builds a context by pre-computing all derived maps from the supplied data.
     *
     * @param users                   Filtered user map for this report run
     * @param orgUnits                OU map — should be the <em>unfiltered</em> allOrgUnits so
     *                                positions are resolved across the whole organisation
     * @param liveOrgUnits            Current OrgUnit entities for UUID → name resolution
     * @param userRoleIdWeight        Weight map computed from IT-system metadata
     * @param userItSystemMaxWeight   Max-weight map from the lightweight first-pass query
     * @param constraintsByAssignmentId Bulk-loaded constraint projections
     */
    static StreamingReportContext build(
            Map<String, HistoryUser> users,
            Map<String, HistoryOU> orgUnits,
            List<OrgUnit> liveOrgUnits,
            Map<Long, Long> userRoleIdWeight,
            Map<String, Map<String, Long>> userItSystemMaxWeight,
            Map<Long, List<Object[]>> constraintsByAssignmentId) {

        // Pre-compute userUuid → positions (O(1) per assignment instead of O(n_OUs) scan)
        Map<String, List<UserPosition>> userPositionsMap = new HashMap<>();
        for (HistoryOU ou : orgUnits.values()) {
            for (HistoryOUUser ouUser : ou.getUsers()) {
                // has_position is false for users attached to an OU only through a trusted function
                if (!Boolean.TRUE.equals(ouUser.getHasPosition())) {
                    continue;
                }
                userPositionsMap
                        .computeIfAbsent(ouUser.getUserUuid(), _ -> new ArrayList<>())
                        .add(new UserPosition(ou, ouUser.getTitleUuid(), Boolean.TRUE.equals(ouUser.getDoNotInherit())));
            }
        }

        Map<String, Set<String>> ouSelfAndAncestors = buildOuSelfAndAncestors(orgUnits);

        // Pre-compute name lookup for organisation constraint display
        Map<String, String> orgUnitNamesByUuid = new HashMap<>(liveOrgUnits.size());
        for (OrgUnit ou : liveOrgUnits) {
            orgUnitNamesByUuid.put(ou.getUuid(), ou.getName());
        }

        return new StreamingReportContext(
                users, orgUnits, userPositionsMap, ouSelfAndAncestors, orgUnitNamesByUuid,
                userRoleIdWeight, userItSystemMaxWeight, constraintsByAssignmentId);
    }

    /**
     * One position a user holds, with the two fields that decide whether an assignment inherited from
     * an ancestor OU actually reached the user through this particular position.
     */
    record UserPosition(HistoryOU ou, String titleUuid, boolean doNotInherit) {}

    static Map<String, Set<String>> buildOuSelfAndAncestors(Map<String, HistoryOU> orgUnits) {
        Map<String, Set<String>> result = new HashMap<>(orgUnits.size());
        for (HistoryOU ou : orgUnits.values()) {
            Set<String> chain = new HashSet<>();
            HistoryOU current = ou;
            // add() returning false means we have looped back on ourselves - stops on cyclic parent data
            while (current != null && chain.add(current.getOuUuid())) {
                current = current.getOuParentUuid() != null ? orgUnits.get(current.getOuParentUuid()) : null;
            }
            result.put(ou.getOuUuid(), chain);
        }
        return result;
    }
}
