package dk.digitalidentity.rc.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationStatus;

public interface ManualAssignmentEffectuationDao extends CrudRepository<ManualAssignmentEffectuation, Long> {

	Optional<ManualAssignmentEffectuation> findByUser_UuidAndUserRole_IdAndOperationAndStatus(
		String userUuid, long userRoleId, ManualAssignmentEffectuationOperation operation, ManualAssignmentEffectuationStatus status);

	List<ManualAssignmentEffectuation> findByStatus(ManualAssignmentEffectuationStatus status);

	List<ManualAssignmentEffectuation> findByStatusAndItSystem_IdIn(ManualAssignmentEffectuationStatus status, List<Long> itSystemIds);

	long countByStatusAndItSystem_IdIn(ManualAssignmentEffectuationStatus status, List<Long> itSystemIds);

	long countByStatus(ManualAssignmentEffectuationStatus status);

	List<ManualAssignmentEffectuation> findByStatusAndEmailSent(ManualAssignmentEffectuationStatus status, boolean emailSent);

	long deleteByStatusAndCompletedAtBefore(ManualAssignmentEffectuationStatus status, LocalDateTime completedBefore);
}
