package dk.digitalidentity.rc.service;

import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;

import lombok.extern.log4j.Log4j;

@Log4j
public class TransportErrorHandler implements TransportListener {

	@Override
	public void messageDelivered(TransportEvent e) {
		; // do nothing
	}

	@Override
	public void messageNotDelivered(TransportEvent e) {
		log.warn("Message NOT delivered!");
	}

	@Override
	public void messagePartiallyDelivered(TransportEvent e) {
		log.warn("Message partialy delivered!");
	}
}
