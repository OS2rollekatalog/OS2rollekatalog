package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "manager_delegate")
@Getter
@Setter
public class ManagerDelegate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "delegate_uuid")
	private User delegate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manager_uuid")
	private User manager;

	@Column(nullable = false)
	private LocalDate fromDate;

	@Column
	private LocalDate toDate;

	@Column
	private boolean indefinitely;



}
