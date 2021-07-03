package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.controller.mvc.viewmodel.KleViewModel;
import dk.digitalidentity.rc.dao.KleDao;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.service.model.KleAssignment;
import dk.digitalidentity.rc.task.ReadKleTask;

@Service
public class KleService {

	@Autowired
	private KleDao kleDao;
	
	@Qualifier("defaultRestTemplate")
	@Autowired
	private RestTemplate restTemplate;

	public List<KleViewModel> getKleMainGroupList(){
		return getKleListFromParent("0"); //parentCode 0 = KLE main groups "parent"
	}
	
	public Kle getByCode(String code) {
		return kleDao.getByCode(code);
	}

	public List<Kle> findAll() { return kleDao.findAll(); }
	
	public Kle save(Kle kle) {
		return kleDao.save(kle);
	}

	public List<KleViewModel> getKleListFromParent(String parentCode){
		if (parentCode.length() > 2) {
			parentCode = parentCode.substring(0, 2) + "." + parentCode.substring(2, 4);
		}

		List<KleViewModel> kleMainGroupList = new ArrayList<>();

		for (Kle kle : kleDao.findAllByParent(parentCode)){
			KleViewModel kvm = new KleViewModel();
			kvm.setId(kle.getId());
			kvm.setCode(kle.getCode());
			kvm.setName(kle.getName());
			kvm.setActive(kle.isActive());
			kvm.setParent(kle.getParent());
			kleMainGroupList.add(kvm);
		}
		
		return kleMainGroupList.stream().sorted((k1, k2) -> k1.getCode().compareTo(k2.getCode())).collect(Collectors.toList());
	}

	public List<KleAssignment> getKleAssignments(OrgUnit orgUnit, KleType kleType, boolean recursive) {
		List<KleAssignment> result = getKleAssignments(orgUnit, kleType, recursive, true);
		
		// sort and return
		return result.stream().sorted((k1, k2) -> k1.getCode().compareTo(k2.getCode())).collect(Collectors.toList());
	}

	private List<KleAssignment> getKleAssignments(OrgUnit orgUnit, KleType kleType, boolean recursive, boolean firstLevel) {
		List<KleAssignment> result = new ArrayList<>();

		if (firstLevel || orgUnit.isInheritKle()) {
			List<KleAssignment> tmp = orgUnit.getKles().stream()
					.filter(k -> k.getAssignmentType().equals(kleType))
					.map(kle -> KleAssignment.builder().code(kle.getCode()).inheritedFrom(orgUnit.getName()).build())
					.collect(Collectors.toList());
			
			// update description
			for (KleAssignment assignment : tmp) {
				assignment.setDescription(ReadKleTask.getName(assignment.getCode()));
			}

			result.addAll(tmp);
		}

		// do a recursive lookup?
		if (recursive && orgUnit.getParent() != null) {
			List<KleAssignment> tmp = getKleAssignments(orgUnit.getParent(), kleType, true, false);

			// update description
			for (KleAssignment assignment : tmp) {
				assignment.setDescription(ReadKleTask.getName(assignment.getCode()));
			}
			
			result.addAll(tmp);
		}

		return result;
	}

	public List<KleAssignment> getKleAssignments(User user, KleType kleType, boolean inherit) {
		List<KleAssignment> result = user.getKles().stream()
				.filter(k -> k.getAssignmentType().equals(kleType))
				.map(kle -> KleAssignment.builder().code(kle.getCode()).build())
				.collect(Collectors.toList());

		// grab all KLEs assigned to OrgUnits where the user is positioned (if required)
		if (inherit && !user.isDoNotInherit()) {
			for (Position position : user.getPositions()) {
				result.addAll(getKleAssignments(position.getOrgUnit(), kleType, true));
			}
		}

		// sort
		result = result.stream().sorted((k1, k2) -> k1.getCode().compareTo(k2.getCode())).collect(Collectors.toList());
		
		// update description
		for (KleAssignment assignment : result) {
			assignment.setDescription(ReadKleTask.getName(assignment.getCode()));
		}
		
		return result;
	}
}
