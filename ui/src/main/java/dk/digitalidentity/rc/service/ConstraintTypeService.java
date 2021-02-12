package dk.digitalidentity.rc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.ConstraintTypeDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;

@Service
public class ConstraintTypeService {

    @Autowired
    private ConstraintTypeDao constraintTypeDao;
    
    public ConstraintType getByUuid(String uuid) {
    	return constraintTypeDao.findByUuid(uuid);
    }
    
    public ConstraintType getByEntityId(String entityId) {
    	return constraintTypeDao.findByEntityId(entityId);
    }

	public ConstraintType save(ConstraintType entity) {
		return constraintTypeDao.save(entity);
	}
}
