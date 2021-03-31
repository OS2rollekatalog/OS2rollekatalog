package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

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
