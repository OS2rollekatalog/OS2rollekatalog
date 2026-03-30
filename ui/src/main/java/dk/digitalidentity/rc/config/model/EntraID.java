package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EntraID {
	@FeatureDocumentation(name = "EntraID Integration - gruppesynkronisering", description = "Integration til EntraID (tidligere Azure AD) til at oprette grupper fra EntraID som systemroller og jobfunktionsroller i Rollekataloget")
	private boolean backSyncEnabled = false;
	@FeatureDocumentation(name = "EntraID Integration - medlemskabssynkronisering", description = "Integration til EntraID (tidligere Azure AD) til at vedligeholde gruppemedlemskaber baseret på rolletildelinger i Rollekataloget")
	private boolean membershipSyncEnabled = false;

	// Legacy fields - kept for backward compatibility - can be deleted once every customer has been migrated
	private String clientId;
	private String clientSecret;
	private String tenantId;
	private AzureUsernameField usernameField = AzureUsernameField.MAIL_NICKNAME;
	private boolean reImportUsersEnabled = false;
	private String roleCatalogKey = "rollekatalog";

	// New multi-tenant support
	private List<EntraIDTenant> tenants = new ArrayList<>();

	/**
	 * Returns effective tenants - either from the tenants list or a single legacy tenant
	 * built from the old configuration fields.
	 */
	public List<EntraIDTenant> getEffectiveTenants() {
		if (!tenants.isEmpty()) {
			return tenants;
		}

		// Backward compatibility: create a single tenant from legacy fields
		if (clientId != null && clientSecret != null && tenantId != null) {
			EntraIDTenant legacyTenant = new EntraIDTenant();
			legacyTenant.setDomainName(null); // no domain filtering, works like the old flow
			legacyTenant.setClientId(clientId);
			legacyTenant.setClientSecret(clientSecret);
			legacyTenant.setTenantId(tenantId);
			legacyTenant.setUsernameField(usernameField);
			legacyTenant.setReImportUsersEnabled(reImportUsersEnabled);
			legacyTenant.setRoleCatalogKey(roleCatalogKey);
			return List.of(legacyTenant);
		}

		return List.of();
	}
}
