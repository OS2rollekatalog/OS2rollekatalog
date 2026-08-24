package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import java.io.Serializable;
import java.util.Objects;

public class CombinedRoleViewId implements Serializable {
	private static final long serialVersionUID = 5841081431177790581L;
	private long id;
	private String type;

	public CombinedRoleViewId() {}

	public CombinedRoleViewId(long id, String type) {
		this.id = id;
		this.type = type;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CombinedRoleViewId that)) return false;
		return id == that.id && Objects.equals(type, that.type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, type);
	}
}