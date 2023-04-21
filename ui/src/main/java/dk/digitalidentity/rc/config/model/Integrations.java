package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Integrations {
	private KspCics kspcics = new KspCics();
	private Kle kle = new Kle();
	private Kombit kombit = new Kombit();
	private Master master = new Master();
	private Email email = new Email();
	private AppManager appManager = new AppManager();
	private NemLogin nemLogin = new NemLogin();
}
