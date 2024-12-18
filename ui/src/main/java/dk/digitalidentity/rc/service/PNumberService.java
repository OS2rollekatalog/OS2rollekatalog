package dk.digitalidentity.rc.service;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.PNumberDao;
import dk.digitalidentity.rc.dao.model.PNumber;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PNumberService {

	@Autowired
	private PNumberDao pNumberDao;

	@Autowired
	private NemLoginService nemLoginService;

	public List<PNumber> getAll() {
		List<PNumber> pNumbers = pNumberDao.findAll();
		pNumbers.sort(Comparator.comparing(PNumber::getName));

		return pNumbers;
	}

	public PNumber save(PNumber pNumber) {
		return pNumberDao.save(pNumber);
	}

	public void delete(PNumber pNumber) {
		pNumberDao.delete(pNumber);
	}

	@Transactional
	public void updatePNR() {
		List<PNumber> newPNrs = nemLoginService.getAllPNR();
		if (newPNrs == null || newPNrs.isEmpty()) {
			log.error("Got 0 pNr from NemLog-in, aborting update");
			return;
		}

		List<PNumber> oldPNrs = this.getAll();
		List<String> oldCodes = oldPNrs.stream().map(PNumber::getCode).toList();
		List<String> newCodes = newPNrs.stream().map(PNumber::getCode).toList();

		// add
		for (PNumber newPNr : newPNrs) {
			if (!oldCodes.contains(newPNr.getCode())) {
				save(newPNr);
			}
		}

		// remove
		for (Iterator<PNumber> iterator = oldPNrs.iterator(); iterator.hasNext();) {
			PNumber oldPNr = iterator.next();
			
			if (!newCodes.contains(oldPNr.getCode())) {
				delete(oldPNr);
			}
		}

		// update
		oldPNrs.forEach(old ->
				newPNrs.stream().filter(o -> o.getCode().equals(old.getCode()))
						.findFirst()
						.ifPresent(o -> old.setName(o.getName())));
	}
}
