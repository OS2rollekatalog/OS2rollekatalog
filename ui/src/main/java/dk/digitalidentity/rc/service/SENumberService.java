package dk.digitalidentity.rc.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.SENumberDao;
import dk.digitalidentity.rc.dao.model.SENumber;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;

@Service
public class SENumberService {

	@Autowired
	private SENumberDao seNumberDao;

	@Autowired
	private NemLoginService nemLoginService;
	
	public SENumber save(SENumber seNumber) {
		return seNumberDao.save(seNumber);
	}
	
	public void delete(SENumber seNumber) {
		seNumberDao.delete(seNumber);
	}

	public List<SENumber> getAll() {
		List<SENumber> sENR = seNumberDao.findAll();

		Collections.sort(sENR, new Comparator<SENumber>() {
			@Override
			public int compare(final SENumber p1, final SENumber p2) {
				return p1.getName().compareTo(p2.getName());
			}
		});

		return sENR;
	}
	
	@Transactional
	public void updateSENR() {
		List<SENumber> newSeNrs = nemLoginService.getAllSENR();
		List<SENumber> oldSeNrs = this.getAll();		

		List<String> oldCodes = oldSeNrs.stream().map(o -> o.getCode()).toList();
		List<String> newCodes = newSeNrs.stream().map(o -> o.getCode()).toList();
		
		// add
		for (SENumber newSeNr : newSeNrs) {
			if (!oldCodes.contains(newSeNr.getCode())) {
				save(newSeNr);
			}
		}
		
		// remove
		for (Iterator<SENumber> iterator = oldSeNrs.iterator(); iterator.hasNext();) {
			SENumber oldSeNr = iterator.next();
			
			if (!newCodes.contains(oldSeNr.getCode())) {
				delete(oldSeNr);
			}
		}
	}
}
