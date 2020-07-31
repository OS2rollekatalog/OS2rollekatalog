package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
