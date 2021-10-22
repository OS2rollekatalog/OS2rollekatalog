package dk.digitalidentity.rc.dao.model;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.BatchSize;

import dk.digitalidentity.rc.dao.serializer.LocalDateAttributeConverter;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "title_rolegroups")
@Getter
@Setter
public class TitleRoleGroupAssignment {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "title_uuid")
	private Title title;
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rolegroup_id")
	private RoleGroup roleGroup;
	
	@Column
	private String assignedByUserId;
	
	@Column
	private String assignedByName;
	
	@Column
	private Date assignedTimestamp;
	
	@ElementCollection(targetClass = String.class)
	@CollectionTable(name = "title_rolegroups_ous", joinColumns = @JoinColumn(name = "title_rolegroups_id"))
	@Column(name = "ou_uuid")
	private List<String> ouUuids;

    @Convert(converter = LocalDateAttributeConverter.class)
	@Column
	private LocalDate startDate;
	
    @Convert(converter = LocalDateAttributeConverter.class)
	@Column
	private LocalDate stopDate;
	
	@Column
	private boolean inactive;
}