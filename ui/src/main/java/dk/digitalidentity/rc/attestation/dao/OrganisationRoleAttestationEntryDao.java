package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface OrganisationRoleAttestationEntryDao extends CrudRepository<OrganisationRoleAttestationEntry, Long> {

    @Query("SELECT e.attestation.id FROM OrganisationRoleAttestationEntry e WHERE e.attestation.id IN :ids")
    Set<Long> findAttestationIdsWithEntry(@Param("ids") Collection<Long> ids);
}
