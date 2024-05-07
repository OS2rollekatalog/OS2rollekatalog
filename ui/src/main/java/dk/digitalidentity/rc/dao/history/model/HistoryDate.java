package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "history_dates")
@Getter
@Setter
public class HistoryDate {
    @Id
    @Column
    private LocalDate dato;
}
