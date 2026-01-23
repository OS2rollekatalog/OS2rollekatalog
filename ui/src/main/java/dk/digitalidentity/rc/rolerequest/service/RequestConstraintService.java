package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.rolerequest.dao.RequestConstraintDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestConstraint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RequestConstraintService {
    @Autowired
    private RequestConstraintDao requestConstraintDao;

    public List<RequestConstraint> getAllConstraints() {
        return requestConstraintDao.findAll();
    }

    public RequestConstraint getRequestConstraintById(Long id) {
        return requestConstraintDao.getById(id);
    }

    public void deleteConstraint(Long requestConstraintId) {
        requestConstraintDao.deleteById(requestConstraintId);
    }

    public RequestConstraint save(RequestConstraint requestConstraint) {
        return requestConstraintDao.save(requestConstraint);
    }
}
