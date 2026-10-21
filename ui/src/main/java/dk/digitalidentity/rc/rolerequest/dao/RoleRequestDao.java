package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.dao.SpecificationIdFinder;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

public interface RoleRequestDao extends CrudRepository<RoleRequest, Long>, SpecificationIdFinder<RoleRequest> {

	Set<RoleRequest> findByStatus(RequestApproveStatus status);

	/**
	 * No DISTINCT on purpose: it is emitted as SQL SELECT DISTINCT, which applies to the whole
	 * joined row and therefore removes nothing, but it does force the database into a temporary
	 * table and blocks it from satisfying the ORDER BY from an index. The duplicate root rows
	 * from the collection fetch join are removed by the caller instead.
	 */
	@Query("""
		SELECT r FROM RoleRequest r
		LEFT JOIN FETCH r.receiver
		LEFT JOIN FETCH r.requester
		LEFT JOIN FETCH r.orgUnit
		LEFT JOIN FETCH r.userRole ur
		LEFT JOIN FETCH r.roleGroup rg
		LEFT JOIN FETCH r.requestPostponedConstraints pc
		LEFT JOIN FETCH pc.constraintType
		WHERE r.id IN :ids
		ORDER BY r.requestTimestamp DESC, r.id DESC
	""")
	List<RoleRequest> findByIdInEager(@Param("ids") Collection<Long> ids);

	@Query("""
		SELECT r FROM RoleRequest r
		LEFT JOIN FETCH r.receiver receiver
		LEFT JOIN FETCH receiver.positions position
		LEFT JOIN FETCH position.orgUnit
		LEFT JOIN FETCH r.orgUnit
		LEFT JOIN FETCH r.userRole ur
		LEFT JOIN FETCH ur.itSystem
		LEFT JOIN FETCH r.roleGroup
		WHERE r.status = dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus.REQUESTED
		AND (:includeAlreadyEmailed = true OR r.emailSent = false)
	""")
	List<RoleRequest> findPendingForApprovalEmails(@Param("includeAlreadyEmailed") boolean includeAlreadyEmailed);

    Set<RoleRequest> findByOrgUnitInAndRequesterNotAndReceiverNotAndStatus(Collection<OrgUnit> orgUnits, User requester, User reciever, RequestApproveStatus status);

	Set<RoleRequest> findByRequestActionAndReceiver_UuidAndUserRole_IdAndStatus(RequestAction requestAction, String uuid, long id, RequestApproveStatus status);

	Set<RoleRequest> findByRequestActionAndReceiver_UuidAndRoleGroup_IdAndStatus(RequestAction requestAction, String uuid, long id, RequestApproveStatus status);

	List<RoleRequest> findByReceiver_UuidAndStatus(String uuid, RequestApproveStatus status);


	List<RoleRequest> findByRequester_Uuid(String uuid);

	long deleteByRequestTimestampBeforeAndStatus(Date requestTimestamp, RequestApproveStatus status);

	List<RoleRequest> findByStatusInAndEmailSent(Collection<RequestApproveStatus> statuses, boolean emailSent);

	void deleteByStatusInAndStatusTimestampBefore(List<RequestApproveStatus> stati, Date before);

	List<RoleRequest> findAll();

	List<RoleRequest> findByStatusInAndEmailSentToServicedesk(List<RequestApproveStatus> statuses, boolean emailSentToServicedesk);
}
