package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Entrald {
	@FeatureDocumentation(name = "Entrald Integration - gruppesynkronisering", description = "Integration til Entrald (tidligere Azure AD) til at oprette grupper fra Entrald som systemroller og jobfunktionsroller i Rollekataloget")
	private boolean backSyncEnabled = false;
	@FeatureDocumentation(name = "Entrald Integration - medlemskabssynkronisering", description = "Integration til Entrald (tidligere Azure AD) til at vedligeholde gruppemedlemskaber baseret på rolletildelinger i Rollekataloget")
	private boolean membershipSyncEnabled = false;
	private String clientId;
	private String clientSecret;
	private String tenantId;

	// this should only be true if membershipSyncEnabled is false
	// if its true the memberships has to be managed in Azure and not in RC - opposite if false
	private boolean reImportUsersEnabled = false;

	// this is used to find groups in Azure that we manage.
	// if we manage the group the description must contain "{roleCatalogKey}_{itSystemId}"
	private String roleCatalogKey = "rollekatalog";
}
