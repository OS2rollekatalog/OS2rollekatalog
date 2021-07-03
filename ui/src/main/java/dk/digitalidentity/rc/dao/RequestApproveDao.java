package dk.digitalidentity.rc.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;

public interface RequestApproveDao extends CrudRepository<RequestApprove, Long> {

	RequestApprove getById(long id);

	List<RequestApprove> getByRequester(User requester);

	List<RequestApprove> getByRoleAssignerNotifiedFalseAndStatusIn(Collection<RequestApproveStatus> stati);

	List<RequestApprove> getByStatusIn(List<RequestApproveStatus> stati);

	void deleteByStatusInAndStatusTimestampBefore(List<RequestApproveStatus> stati, Date before);
	
	List<RequestApprove> findAll();
}
