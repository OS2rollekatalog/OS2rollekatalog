package dk.digitalidentity.rc.dao.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;

import org.hibernate.annotations.BatchSize;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "dmp_queue")
@Getter
@Setter
public class DmpQueue {
    
	@Id
	@Column(unique = true, nullable = false, name = "user_uuid")
	private String userUuid;

    @MapsId("user_uuid")
	@BatchSize(size = 50)
	@OneToOne(fetch = FetchType.LAZY)
	private User user;

	@Column
	private LocalDateTime tts;
}
