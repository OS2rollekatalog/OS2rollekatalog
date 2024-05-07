package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "history_ous_users")
@Getter
@Setter
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

	@Column
	private Boolean doNotInherit;

}
