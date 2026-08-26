package dk.digitalidentity.rc.task;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class CertificateExpiryCheckerTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// check for certificate expiry every Tuesday
	@Scheduled(cron = "#{new java.util.Random().nextInt(60)} 0 14 * * TUE")
	public void checkExpiry() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		if (configuration.getIntegrations().getKombit().isEnabled()) {
			checkKeystore("KOMBIT", configuration.getIntegrations().getKombit().getKeystoreLocation(), configuration.getIntegrations().getKombit().getKeystorePassword());
		}
		
		if (configuration.getIntegrations().getNemLogin().isEnabled()) {
			checkKeystore("NEMLOGIN", configuration.getIntegrations().getNemLogin().getKeystoreLocation(), configuration.getIntegrations().getNemLogin().getKeystorePassword());
		}
		
		if (configuration.getIntegrations().getKspcics().isEnabled()) {
			checkKeystore("CICS", configuration.getIntegrations().getKspcics().getKeystoreLocation(), configuration.getIntegrations().getKspcics().getKeystorePassword());
		}
	}

	private void checkKeystore(String integration, String keystoreLocation, String keystorePassword) {
		try {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DAY_OF_MONTH, 30);
			Date thirtyDaysFromNow = cal.getTime();
			
			KeyStore ks = KeyStore.getInstance("PKCS12");
			ks.load(new FileInputStream(keystoreLocation), keystorePassword.toCharArray());
			
			Enumeration<String> aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				
				Certificate certificate = ks.getCertificate(alias);
				if (certificate instanceof X509Certificate x509) {
					x509.checkValidity(thirtyDaysFromNow);
				}
			}
		}
		catch (Exception ex) {
			log.error("Keystore for '" + integration + "' has an error: " + ex.getMessage());
		}
	}
}
