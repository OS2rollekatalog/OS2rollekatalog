package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ManualAssignmentNotificationMapDao extends CrudRepository<ManualAssignmentNotificationMap, Long> {
	List<ManualAssignmentNotificationMap> findByUserRoleIdIn(Set<Long> userRoleIds);

	List<ManualAssignmentNotificationMap> findByDomainIdAndUserUserId(long domainId, String userUserId);

	@Query("SELECT m FROM ManualAssignmentNotificationMap m WHERE m.domainId IN :domainIds AND m.userUserId IN :userIds")
	List<ManualAssignmentNotificationMap> findByDomainIdInAndUserUserIdIn(Collection<Long> domainIds, Collection<String> userIds);

	@Modifying
	@Query("UPDATE ManualAssignmentNotificationMap m SET m.removedBy = :removedBy WHERE m.domainId = :domainId AND m.userUserId = :userId AND m.userRoleId IN :userRoleIds")
	void updateRemovedBy(@Param("domainId") long domainId, @Param("userId") String userId, @Param("userRoleIds") Collection<Long> userRoleIds, @Param("removedBy") String removedBy);
}
