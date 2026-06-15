package dk.digitalidentity.rc.controller.rest.model;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AuditLogView;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public class AuditLogDatatableSpecificationBuilder {

	public static Specification<AuditLogView> build(List<String> eventTypeNames, String entityType, String entityId) {
		Specification<AuditLogView> spec = null;

		if (eventTypeNames != null && !eventTypeNames.isEmpty()) {
			List<EventType> eventTypes = eventTypeNames.stream()
					.map(name -> {
						try {
							return EventType.valueOf(name);
						} catch (IllegalArgumentException e) {
							return null;
						}
					})
					.filter(Objects::nonNull)
					.toList();

			if (!eventTypes.isEmpty()) {
				spec = (root, query, cb) -> root.get("eventType").in(eventTypes);
			}
		}

		if (StringUtils.hasText(entityType) && StringUtils.hasText(entityId)) {
			try {
				EntityType et = EntityType.valueOf(entityType);
				Specification<AuditLogView> entitySpec = (root, query, cb) -> cb.or(
						cb.and(cb.equal(root.get("entityType"), et), cb.equal(root.get("entityId"), entityId)),
						cb.and(cb.equal(root.get("secondaryEntityType"), et), cb.equal(root.get("secondaryEntityId"), entityId))
				);
				spec = spec != null ? spec.and(entitySpec) : entitySpec;
			} catch (IllegalArgumentException ignored) { }
		}

		return spec;
	}
}
