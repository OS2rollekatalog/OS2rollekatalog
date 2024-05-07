package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table
@Getter
@Setter
public class Kle {

	@Id
    @JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false, length=8)
	private String code;

    @JsonIgnore
	@Column(nullable = false, length=256)
	private String name;

    @JsonIgnore
	@Column(nullable = false)
	private boolean active;

    @JsonIgnore
	@Column(nullable = false, length=8)
	private String parent;
}
