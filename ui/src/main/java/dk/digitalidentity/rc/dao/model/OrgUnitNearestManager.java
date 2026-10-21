package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.Objects;
import java.util.Optional;

/**
 * The managers responsible for an OU, derived from the OU hierarchy by
 * {@link dk.digitalidentity.rc.service.orgunit.NearestManagerCalculator}; never written elsewhere.
 * An OU without a manager anywhere above it has no row.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ou_nearest_manager")
public class OrgUnitNearestManager implements Persistable<String> {

	public record ApprovingManager(String managerUuid, String orgUnitUuid) {}

	@Id
	@Column(name = "ou_uuid")
	private String ouUuid;

	/** The OU's own manager, or the first manager found through its parents. */
	@Column(name = "nearest_manager_uuid")
	private String nearestManagerUuid;

	/** The OU where the nearest manager was found. Substitutes only count in this OU. */
	@Column(name = "nearest_manager_ou_uuid")
	private String nearestManagerOuUuid;

	/** The first manager above the nearest manager who is a different person, or {@code null}. */
	@Column(name = "next_manager_uuid")
	private String nextManagerUuid;

	/** The OU where the next manager was found. Substitutes only count in this OU. */
	@Column(name = "next_manager_ou_uuid")
	private String nextManagerOuUuid;

	@Transient
	private boolean isNew = true;

	public OrgUnitNearestManager(String ouUuid, String nearestManagerUuid, String nearestManagerOuUuid, String nextManagerUuid, String nextManagerOuUuid) {
		this.ouUuid = ouUuid;
		this.nearestManagerUuid = nearestManagerUuid;
		this.nearestManagerOuUuid = nearestManagerOuUuid;
		this.nextManagerUuid = nextManagerUuid;
		this.nextManagerOuUuid = nextManagerOuUuid;
	}

	/**
	 * The manager who approves a request for the receiver in this OU: the nearest manager, unless the receiver
	 * is that manager, in which case the next manager. {@code ApproverRule.nearestManager} makes the same choice in SQL.
	 */
	public Optional<ApprovingManager> approvingManagerFor(String receiverUuid) {
		if (!Objects.equals(nearestManagerUuid, receiverUuid)) {
			return Optional.of(new ApprovingManager(nearestManagerUuid, nearestManagerOuUuid));
		}
		return Optional.ofNullable(nextManagerUuid).map(next -> new ApprovingManager(next, nextManagerOuUuid));
	}

	public boolean hasSameManagersAs(OrgUnitNearestManager other) {
		return Objects.equals(nearestManagerUuid, other.nearestManagerUuid)
			&& Objects.equals(nearestManagerOuUuid, other.nearestManagerOuUuid)
			&& Objects.equals(nextManagerUuid, other.nextManagerUuid)
			&& Objects.equals(nextManagerOuUuid, other.nextManagerOuUuid);
	}

	public void copyManagersFrom(OrgUnitNearestManager other) {
		nearestManagerUuid = other.nearestManagerUuid;
		nearestManagerOuUuid = other.nearestManagerOuUuid;
		nextManagerUuid = other.nextManagerUuid;
		nextManagerOuUuid = other.nextManagerOuUuid;
	}

	@Override
	public String getId() {
		return ouUuid;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		isNew = false;
	}
}
