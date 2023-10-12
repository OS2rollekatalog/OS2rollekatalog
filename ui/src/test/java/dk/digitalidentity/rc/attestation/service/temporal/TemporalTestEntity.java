package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Id;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TemporalTestEntity {
    @Id
    private Integer id;
    @PartOfNaturalKey
    private String valueString;
    private Long valueLong;
    private List<Long> valueList;
}
