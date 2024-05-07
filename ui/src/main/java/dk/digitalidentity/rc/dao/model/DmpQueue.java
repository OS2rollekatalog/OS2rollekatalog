package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;

@Entity(name = "dmp_queue")
@Getter
@Setter
public class DmpQueue {
    
	@Id
	@Column(unique = true, nullable = false, name = "user_uuid")
	private String userUuid;

    @MapsId
	@BatchSize(size = 50)
	@OneToOne(fetch = FetchType.LAZY)
	private User user;

	@Column
	private LocalDateTime tts;
}
