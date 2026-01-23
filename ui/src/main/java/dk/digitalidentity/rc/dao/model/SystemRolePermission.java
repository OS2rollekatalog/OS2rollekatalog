package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "system_role_permission")
@Getter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class SystemRolePermission {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column
	private Long id;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Section entity;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Permission permission;

	@Column(nullable = false)
	private String roleIdentifier;

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		SystemRolePermission that = (SystemRolePermission) o;
		if (Objects.equals(id, that.id)) return true;
		return entity == that.entity && permission == that.permission && Objects.equals(roleIdentifier, that.roleIdentifier);
	}

	@Override
	public int hashCode() {
		return Objects.hash(entity, permission, roleIdentifier);
	}
}
