package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.DirtyADGroup;

public interface DirtyADGroupDao extends CrudRepository<DirtyADGroup, Long> {

	public List<DirtyADGroup> findAll();

	public void deleteByIdLessThan(long head);

	public List<DirtyADGroup> findFirst100ByOrderByIdAsc();

	public void deleteByItSystemId(long id);
}
