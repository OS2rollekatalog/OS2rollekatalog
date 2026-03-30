package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.json.ADConfigurationJSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ADGroupMappingService {

	@Autowired
	private ADConfigurationService adConfigurationService;
	@Autowired
	private ClientService clientService;


	private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");

	/**
	 * Returns a map of role ID -> list of AD group names
	 */
	public Map<Long, List<String>> getRoleToADGroupsMap() {
		return parseIdToGroupsMap(config -> config.getItSystemGroupFeatureRoleMap());
	}

	/**
	 * Returns a map of IT-system ID -> list of AD group names
	 */
	public Map<Long, List<String>> getItSystemToADGroupsMap() {
		return parseIdToGroupsMap(config -> config.getItSystemGroupFeatureSystemMap());
	}

	/**
	 * Generic method to parse any mapping list from AD configuration
	 */
	private Map<Long, List<String>> parseIdToGroupsMap(MappingExtractor extractor) {
		Map<Long, List<String>> idToGroups = new HashMap<>();

		// Get latest AD configurations for all clients
		List<Client> clients = clientService.findADSyncServices();
		for (Client client : clients) {
			ADConfiguration adConfiguration = adConfigurationService.getByClient(client);
			ADConfigurationJSON json = adConfiguration != null ? adConfiguration.getJson() : null;
			if (json != null) {
				List<String> mappings = extractor.extractMappings(json);

				if (mappings != null) {
					for (String mapping : mappings) {
						if (mapping != null && mapping.contains(";")) {
							String[] parts = mapping.split(";", 2);

							try {
								Long id = Long.parseLong(parts[0]);
								String dn = parts[1];

								// Extract CN value from DN
								String groupName = extractCNFromDN(dn);
								if (groupName != null) {
									idToGroups.computeIfAbsent(id, _ -> new ArrayList<>()).add(groupName);
								}
							} catch (NumberFormatException e) {
								// Skip invalid IDs
							}
						}
					}
				}
			}
		}

		return idToGroups;
	}

	/**
	 * Extract CN value from DN string
	 * Example: "CN=gruppe24,OU=Groups,DC=domain,DC=dk" -> "gruppe24"
	 */
	private String extractCNFromDN(String dn) {
		if (dn == null) return null;

		Matcher matcher = CN_PATTERN.matcher(dn);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	/**
	 * Functional interface for extracting different mapping types
	 */
	@FunctionalInterface
	private interface MappingExtractor {
		List<String> extractMappings(ADConfigurationJSON config);
	}
}