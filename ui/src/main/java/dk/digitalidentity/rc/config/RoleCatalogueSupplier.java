package dk.digitalidentity.rc.config;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dk.digitalidentity.reporter.suppliers.ReporterSupplier;

@Component
public class RoleCatalogueSupplier implements ReporterSupplier {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Value(value = "${git.commit.id.abbrev}")
	private String gitCommitId;

	@Override
	public Supplier<String> getCustomerNameSupplier() {
		return null;
	}

	@Override
	public Supplier<String> getProductNameSupplier() {
		return (() -> "OS2rollekatalog");
	}

	@Override
	public Supplier<String> getVersionSupplier() {
		return (() -> configuration.getVersion() + " (" + gitCommitId + ")");
	}

	@Override
	public Supplier<Map<String, String>> getAttributeMapSupplier() {
		return null;
	}
}
