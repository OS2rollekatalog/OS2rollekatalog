package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

public interface RoleRequestDao extends CrudRepository<RoleRequest, Long> {

	Set<RoleRequest> findByStatus(RequestApproveStatus status);

    Set<RoleRequest> findByOrgUnitInAndRequesterNotAndReceiverNotAndStatus(Collection<OrgUnit> orgUnits, User requester, User reciever, RequestApproveStatus status);

	Set<RoleRequest> findByRequestActionAndReceiver_UuidAndUserRole_Id(RequestAction requestAction, String uuid, long id);

	Set<RoleRequest> findByRequestActionAndReceiver_UuidAndRoleGroup_Id(RequestAction requestAction, String uuid, long id);

	List<RoleRequest> findByReceiver_UuidAndStatus(String uuid, RequestApproveStatus status);


	List<RoleRequest> findByRequester_Uuid(String uuid);

	long deleteByRequestTimestampBeforeAndStatus(Date requestTimestamp, RequestApproveStatus status);

	List<RoleRequest> findByStatusInAndEmailSent(Collection<RequestApproveStatus> statuses, boolean emailSent);

	void deleteByStatusInAndStatusTimestampBefore(List<RequestApproveStatus> stati, Date before);
}
