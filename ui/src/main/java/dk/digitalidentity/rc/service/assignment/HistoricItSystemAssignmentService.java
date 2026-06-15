package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.dao.assignment.HistoricItSystemAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignmentConstraint;
import dk.digitalidentity.rc.dao.serializer.SystemRoleAssignmentDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoricItSystemAssignmentService {

	private final HistoricItSystemAssignmentDao dao;
	private final SystemRoleAssignmentDao systemRoleAssignmentDao;
	private final AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Transactional
	public void recordSystemRoleAssignmentAdded(UserRole userRole, SystemRoleAssignment assignment) {
		dao.save(toHistoric(userRole, assignment));
	}

	@Transactional
	public void recordSystemRoleAssignmentRemoved(UserRole userRole, SystemRoleAssignment assignment) {
		LocalDateTime now = LocalDateTime.now();
		computeRecordHashVariants(userRole, assignment).forEach(hash -> dao.closeOpenByRecordHash(hash, now));
	}

	/**
	 * Lukker den åbne historic-række med en af {@code preEditHashes} og åbner en ny række der
	 * afspejler den nuværende state af assignment. Bruges af constraint-edit-stierne hvor SRA
	 * muteres in-place og hash dermed skifter — preEditHashes skal beregnes FØR mutationen via
	 * {@link #computeRecordHashVariants(UserRole, SystemRoleAssignment)}.
	 */
	@Transactional
	public void recordSystemRoleAssignmentEdited(UserRole userRole, SystemRoleAssignment assignment, List<String> preEditHashes) {
		HistoricItSystemAssignment newRecord = toHistoric(userRole, assignment);
		if (preEditHashes.contains(newRecord.getRecordHash())) {
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		preEditHashes.forEach(hash -> dao.closeOpenByRecordHash(hash, now));
		dao.save(newRecord);
	}

	/**
	 * Idempotent variant der kun indsætter en ny åben række hvis der ikke allerede findes
	 * én med samme hash (i en af varianterne, jf. {@link #computeRecordHashVariants}).
	 * Bruges af engangs-seed-tasken.
	 */
	@Transactional
	public void recordSystemRoleAssignmentSeedIfMissing(UserRole userRole, SystemRoleAssignment assignment) {
		if (computeRecordHashVariants(userRole, assignment).stream().anyMatch(dao::existsByRecordHashAndValidToIsNull)) {
			return;
		}
		dao.save(toHistoric(userRole, assignment));
	}

	/**
	 * Loader SRA + UserRole + ItSystem og delegerer til {@link #recordSystemRoleAssignmentSeedIfMissing}.
	 * Egen {@code @Transactional} sørger for at lazy-properties på UserRole (fx itSystem) kan
	 * tilgås — kaldere som seed-tasken har ingen tx-grænse omkring deres chunk-loop og ville
	 * ellers ramme LazyInitializationException når UserRole-proxy'en hydreres.
	 *
	 * @return true hvis en ny historic-række blev indsat eller allerede findes; false hvis SRA
	 *         er væk, mangler UserRole eller UserRole mangler ItSystem (rolle-katalog-roller har
	 *         ikke et IT-system).
	 */
	@Transactional
	public boolean seedHistoricRowFromSystemRoleAssignmentId(long sraId) {
		SystemRoleAssignment sra = systemRoleAssignmentDao.findById(sraId).orElse(null);
		if (sra == null || sra.getUserRole() == null || sra.getUserRole().getItSystem() == null) {
			return false;
		}
		recordSystemRoleAssignmentSeedIfMissing(sra.getUserRole(), sra);
		return true;
	}

	public String computeRecordHash(UserRole userRole, SystemRoleAssignment assignment) {
		return toHistoric(userRole, assignment).getRecordHash();
	}

	/**
	 * Hashes der kan identificere den åbne historic-række for dette assignment: den aktuelle
	 * (med collection-id) samt legacy-varianten uden collection-id. Rækker skrevet før systemets
	 * responsible-collection fandtes, mens collection-id fejlagtigt var betinget af
	 * tildelings-krydset, eller SQL-backfillet uden rehash, står med legacy-hashen indtil
	 * reparations-tasken har været forbi — luk/match derfor altid på begge varianter.
	 */
	public List<String> computeRecordHashVariants(UserRole userRole, SystemRoleAssignment assignment) {
		HistoricItSystemAssignment current = toHistoric(userRole, assignment);
		if (current.getResponsibleCollectionId() == null) {
			return List.of(current.getRecordHash());
		}
		HistoricItSystemAssignment legacy = buildRecord(userRole, assignment, userRole.getItSystem(), null);
		return List.of(current.getRecordHash(), legacy.getRecordHash());
	}

	private HistoricItSystemAssignment toHistoric(UserRole userRole, SystemRoleAssignment assignment) {
		ItSystem itSystem = userRole.getItSystem();

		// Rolleopbygnings-attestering (IT_SYSTEM_ROLES_ATTESTATION) kræver kun at it-systemet har en
		// systemansvarlig — IKKE at rollen har tildelings-krydset (roleAssignmentAttestationByAttestationResponsible),
		// som alene styrer hvem der attesterer *tildelinger*. Jf. doc/Attestation-Modul-Udvikler-Guide.md §2.3.
		Long responsibleCollectionId = attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId())
			.map(c -> c.getId())
			.orElse(null);

		return buildRecord(userRole, assignment, itSystem, responsibleCollectionId);
	}

	/**
	 * Reparerer responsibleCollectionId og recordHash på én åben historic-række ud fra rækkens egne
	 * felter. Bruges af engangs-reparations-tasken til rækker skrevet mens collection-id fejlagtigt
	 * var betinget af tildelings-krydset, samt rækker hvor en tidligere SQL-backfill satte
	 * collection-id uden at genberegne hashen.
	 *
	 * @return true hvis rækken blev ændret (opdateret eller lukket som duplikat)
	 */
	@Transactional
	public boolean repairResponsibleCollectionRow(long rowId) {
		HistoricItSystemAssignment row = dao.findById(rowId).orElse(null);
		if (row == null || row.getValidTo() != null) {
			return false;
		}
		Long desiredCollectionId = attestationResponsibleCollectionDao.findFirstByItSystemId(row.getItSystemId())
			.map(c -> c.getId())
			.orElse(null);
		return repairRow(row, desiredCollectionId);
	}

	/**
	 * Reparerer alle åbne historic-rækker for ét it-system, jf. {@link #repairResponsibleCollectionRow}.
	 * Kaldes når systemets responsible-collection oprettes/ændres, så rækker skrevet før collectionen
	 * fandtes også får collection-id (og konsistent hash).
	 *
	 * @return antal ændrede rækker
	 */
	@Transactional
	public int repairResponsibleCollectionForItSystem(long itSystemId) {
		Long desiredCollectionId = attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId)
			.map(c -> c.getId())
			.orElse(null);
		int changed = 0;
		for (HistoricItSystemAssignment row : dao.findByItSystemIdAndValidToIsNull(itSystemId)) {
			if (repairRow(row, desiredCollectionId)) {
				changed++;
			}
		}
		return changed;
	}

	private boolean repairRow(HistoricItSystemAssignment row, Long desiredCollectionId) {
		String oldHash = row.getRecordHash();
		Long oldCollectionId = row.getResponsibleCollectionId();
		row.setResponsibleCollectionId(desiredCollectionId);
		String newHash = computeHash(row);
		if (newHash.equals(oldHash)) {
			row.setResponsibleCollectionId(oldCollectionId);
			return false;
		}
		if (dao.existsByRecordHashAndValidToIsNull(newHash)) {
			// En anden åben række repræsenterer allerede mål-tilstanden — luk denne som duplikat
			// med de oprindelige feltværdier intakte, så fremtidige fjern-events rammer den anden række.
			row.setResponsibleCollectionId(oldCollectionId);
			row.setValidTo(LocalDateTime.now());
		} else {
			row.setRecordHash(newHash);
		}
		dao.save(row);
		return true;
	}

	private static HistoricItSystemAssignment buildRecord(UserRole userRole, SystemRoleAssignment assignment, ItSystem itSystem, Long responsibleCollectionId) {
		List<HistoricItSystemAssignmentConstraint> constraints = buildConstraints(assignment);

		HistoricItSystemAssignment record = HistoricItSystemAssignment.builder()
			.validFrom(LocalDateTime.now())
			.validTo(null)
			.itSystemId(itSystem.getId())
			.itSystemName(itSystem.getName())
			.itSystemAttestationExempt(itSystem.isAttestationExempt())
			.responsibleCollectionId(responsibleCollectionId)
			.userRoleId(userRole.getId())
			.userRoleName(userRole.getName())
			.userRoleDescription(userRole.getDescription())
			.systemRoleId(assignment.getSystemRole().getId())
			.systemRoleName(assignment.getSystemRole().getName())
			.systemRoleDescription(assignment.getSystemRole().getDescription())
			.constraints(constraints)
			.build();

		constraints.forEach(c -> c.setHistoricItSystemAssignment(record));
		record.setRecordHash(computeHash(record));
		return record;
	}

	private static List<HistoricItSystemAssignmentConstraint> buildConstraints(SystemRoleAssignment assignment) {
		if (assignment.getConstraintValues() == null) {
			return new ArrayList<>();
		}
		return assignment.getConstraintValues().stream()
			.map(cv -> HistoricItSystemAssignmentConstraint.builder()
				.constraintName(cv.getConstraintType().getName())
				.constraintValueType(cv.getConstraintValueType())
				.constraintValue(cv.getConstraintValue())
				.build())
			.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	// Hash includes responsibleCollectionId so each per-responsible-collection row has its own open/close lifecycle.
	private static String computeHash(HistoricItSystemAssignment r) {
		return dk.digitalidentity.rc.util.HashUtil.builder()
			.add(r.getItSystemId())
			.add(r.getUserRoleId())
			.add(r.getSystemRoleId())
			.add(r.getResponsibleCollectionId())
			.add(r.getConstraints().stream()
				.map(c -> c.getConstraintName() + ":" + c.getConstraintValueType() + ":" + c.getConstraintValue())
				.sorted()
				.collect(java.util.stream.Collectors.joining("|")))
			.build();
	}
}
