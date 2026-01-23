package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import java.util.List;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleForRoleGroupId;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleForRoleGroupView;

public interface UserRoleForRoleGroupViewDao extends DataTablesRepository<UserRoleForRoleGroupView, UserRoleForRoleGroupId> {
    @Query(value = """
        SELECT ur.id,
               :rolegroupId as role_group_id,
               ur.name,
               ur.description,
			   ur.it_system_id,
			   its.name as it_system_name,
               CASE WHEN rgr.role_id IS NOT NULL THEN 1 ELSE 0 END as selected,
               CASE WHEN its.readonly = 1 OR ur.read_only = 1 THEN 1 ELSE 0 END as read_only,
               ur.delegated_from_cvr,
               its.system_type as it_system_type
        FROM user_roles ur
            JOIN it_systems its ON ur.it_system_id = its.id
            LEFT JOIN rolegroup_roles rgr ON rgr.role_id = ur.id AND rgr.rolegroup_id = :rolegroupId
        WHERE its.deleted = 0 AND ur.allow_postponing = 0 AND ur.it_system_id in :itSystemIds
      """, nativeQuery = true)
    List<UserRoleForRoleGroupView> findUserRolesForRoleGroup(@Param("rolegroupId") long rolegroupId, @Param("itSystemIds") List<Long> itSystemIds);

    @Query(value = """
        SELECT ur.id,
               :rolegroupId as role_group_id,
               ur.name,
               ur.description,
			   ur.it_system_id,
			   its.name as it_system_name,
               CASE WHEN rgr.role_id IS NOT NULL THEN 1 ELSE 0 END as selected,
               CASE WHEN its.readonly = 1 OR ur.read_only = 1 THEN 1 ELSE 0 END as read_only,
               ur.delegated_from_cvr,
               its.system_type as it_system_type
        FROM user_roles ur
            JOIN it_systems its ON ur.it_system_id = its.id
            LEFT JOIN rolegroup_roles rgr ON rgr.role_id = ur.id AND rgr.rolegroup_id = :rolegroupId
        WHERE its.deleted = 0 AND ur.allow_postponing = 0
      """, nativeQuery = true)
    List<UserRoleForRoleGroupView> findUserRolesForRoleGroup(@Param("rolegroupId") long rolegroupId);

    @Query(value = """
        SELECT COUNT(*)
        FROM user_roles ur JOIN it_systems its ON ur.it_system_id = its.id
        WHERE its.deleted = 0 AND ur.allow_postponing = 0
        """, nativeQuery = true)
    long countUserRolesForRoleGroup();
}
