package dk.digitalidentity.rc.dao.history.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.BatchSize;

import lombok.Getter;

@Entity
@Table(name = "history_ous_users")
@Getter
public class HistoryOUUser {

	@Id
	private long id;

	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_ous_id")
	private HistoryOU historyOU;

	@Column
	private String userUuid;
	
	@Column
	private String titleUuid;

}
