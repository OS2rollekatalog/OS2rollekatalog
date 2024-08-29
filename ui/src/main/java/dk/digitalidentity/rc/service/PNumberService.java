package dk.digitalidentity.rc.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

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

		Collections.sort(pNumbers, new Comparator<PNumber>() {
			@Override
			public int compare(final PNumber p1, final PNumber p2) {
				return p1.getName().compareTo(p2.getName());
			}
		});

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
		if (newPNrs == null || newPNrs.size() == 0) {
			log.error("Got 0 pNr from NemLog-in, aborting update");
			return;
		}

		List<PNumber> oldPNrs = this.getAll();

		List<String> oldCodes = oldPNrs.stream().map(o -> o.getCode()).toList();
		List<String> newCodes = newPNrs.stream().map(o -> o.getCode()).toList();
		
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
	}
}
