package dk.digitalidentity.rc.dao.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "email_queue")
public class EmailQueue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@NotNull
	private String title;

	@Column
	@NotNull
	private String message;
	
	@Column
	private String email;
	
	@Column
	@Temporal(TemporalType.TIMESTAMP)
	private Date deliveryTts;
	
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "email_template_id")
	private EmailTemplate emailTemplate;
	
	@OneToMany(mappedBy = "emailQueue", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true )
	private List<AttachmentFile> attachments;
	
	public void addAllAttachments(List<AttachmentFile> attachments) {
		if (this.attachments == null) {
			this.attachments = new ArrayList<>();
		}

		for (AttachmentFile att : attachments) {
			att.setEmailQueue(this);
			this.attachments.add(att);
		}		
	}
	
	// when sending emails, it is done Async, so on another thread, with no session open
	public void forceLoadAttachments() {
		if (attachments != null) {
			for (AttachmentFile attachment : attachments) {
				
				@SuppressWarnings("unused")
				int length = attachment.getContent().length;
			}
		}		
	}
}
