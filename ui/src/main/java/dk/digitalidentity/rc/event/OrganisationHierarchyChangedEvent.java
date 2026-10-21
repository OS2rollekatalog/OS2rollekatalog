package dk.digitalidentity.rc.event;

/**
 * Published by code that changes OU parents or managers; keeps {@code ou_nearest_manager} current.
 */
public record OrganisationHierarchyChangedEvent() {
}
