package dk.digitalidentity.rc.service;

import jakarta.mail.event.TransportEvent;
import jakarta.mail.event.TransportListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
