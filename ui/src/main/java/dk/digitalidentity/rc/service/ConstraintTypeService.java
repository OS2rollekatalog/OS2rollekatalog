package dk.digitalidentity.rc.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.ConstraintTypeDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;

@Service
public class ConstraintTypeService {

    @Autowired
    private ConstraintTypeDao constraintTypeDao;
    
    public ConstraintType getByUuid(String uuid) {
    	return constraintTypeDao.findByUuid(uuid).orElse(null);
    }

	public Optional<ConstraintType> getByUuidOptional(final String uuid) {
		return constraintTypeDao.findByUuid(uuid);
	}
    
    public ConstraintType getByEntityId(String entityId) {
    	List<ConstraintType> constraintTypes = constraintTypeDao.findByEntityId(entityId);
    	
    	// there might be several different "casings" of an EntityID, and the DB is not case-sensitive on searches :(
    	for (ConstraintType ct : constraintTypes) {
    		if (Objects.equals(ct.getEntityId(), entityId)) {
    			return ct;
    		}
    	}
    	
    	return null;
    }

	public ConstraintType save(ConstraintType entity) {
		return constraintTypeDao.save(entity);
	}
}
