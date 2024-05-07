package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "email_attachment_file")
@Getter
@Setter
public class AttachmentFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private byte[] content;
	
	@Column
	private String filename;
	
	@ManyToOne
	@JoinColumn(name="email_queue_id")
	private EmailQueue emailQueue;

}
