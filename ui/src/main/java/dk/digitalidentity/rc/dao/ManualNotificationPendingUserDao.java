package dk.digitalidentity.rc.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.ManualNotificationPendingUser;
import dk.digitalidentity.rc.dao.model.ManualNotificationPendingUserProjection;

public interface ManualNotificationPendingUserDao extends CrudRepository<ManualNotificationPendingUser, Long> {
	Optional<ManualNotificationPendingUser> findByUserUuid(String userUuid);

	List<ManualNotificationPendingUser> findAll();

	@Query("""
			select new dk.digitalidentity.rc.dao.model.ManualNotificationPendingUserProjection(p.id, p.userUuid)
			from ManualNotificationPendingUser p
			where p.id > :afterId
			order by p.id asc
			""")
	List<ManualNotificationPendingUserProjection> findBatchAfter(@Param("afterId") long afterId, Limit limit);

	@Modifying(flushAutomatically = true)
	@Query("delete from ManualNotificationPendingUser p where p.id in :ids")
	void deleteAllByIdIn(@Param("ids") Collection<Long> ids);
}
