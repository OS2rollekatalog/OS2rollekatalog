-- Stored procedure for populating history_kle_assignments table
-- should only be called once per day like this
--
-- CALL SP_InsertHistoryItSystems();
--

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryItSystems $$
 
CREATE PROCEDURE SP_InsertHistoryItSystems()
BEGIN

  INSERT INTO history_it_systems (
    dato, it_system_id, it_system_name, it_system_hidden, attestation_responsible_uuid, attestation_exempt, system_owner_uuid)
  SELECT CURRENT_TIMESTAMP, it.id, it.name, it.hidden, it.attestation_responsible_uuid, it.attestation_exempt, it.system_owner_uuid
  FROM it_systems it;
  
  INSERT INTO history_system_roles (
    history_it_systems_id, system_role_id, system_role_name, system_role_description)
  SELECT hit.id, sr.id, sr.name, sr.description
  FROM history_it_systems hit
  JOIN system_roles sr ON sr.it_system_id = hit.it_system_id
  WHERE hit.dato = CAST(CURRENT_TIMESTAMP AS DATE);

  INSERT INTO history_user_roles (
    history_it_systems_id, user_role_id, user_role_name, user_role_description, user_role_delegated_from_cvr, sensitive_role, role_assignment_attestation_by_attestation_responsible)
  SELECT hit.id, ur.id, ur.name, ur.description, ur.delegated_from_cvr, ur.sensitive_role, ur.role_assignment_attestation_by_attestation_responsible
  FROM history_it_systems hit
  JOIN user_roles ur ON ur.it_system_id = hit.it_system_id
  WHERE hit.dato = CAST(CURRENT_TIMESTAMP AS DATE);

  INSERT INTO history_user_roles_system_roles (
    history_user_roles_id, system_role_assignments_id, system_role_name, system_role_id, system_role_description)
  SELECT hur.id, sra.id, sr.name, sr.id, sr.description
  FROM history_it_systems hit
  JOIN history_user_roles hur ON hur.history_it_systems_id = hit.id
  JOIN user_roles ur ON ur.id = hur.user_role_id
  JOIN system_role_assignments sra ON sra.user_role_id = ur.id
  JOIN system_roles sr ON sr.id = sra.system_role_id
  WHERE hit.dato = CAST(CURRENT_TIMESTAMP AS DATE);
  
  INSERT INTO history_user_roles_system_role_constraints (
    history_user_roles_system_roles_id, constraint_name, constraint_value_type, constraint_value)
  SELECT hursr.id, ct.name, sracv.constraint_value_type, sracv.constraint_value
  FROM history_it_systems hit
  JOIN history_user_roles hur ON hur.history_it_systems_id = hit.id
  JOIN history_user_roles_system_roles hursr ON hursr.history_user_roles_id = hur.id
  JOIN system_role_assignments sra ON hursr.system_role_assignments_id = sra.id
  JOIN system_role_assignment_constraint_values sracv ON sracv.system_role_assignment_id = sra.id
  JOIN constraint_types ct ON ct.id = sracv.constraint_type_id
  WHERE hit.dato = CAST(CURRENT_TIMESTAMP AS DATE);
  
END $$
DELIMITER ;
