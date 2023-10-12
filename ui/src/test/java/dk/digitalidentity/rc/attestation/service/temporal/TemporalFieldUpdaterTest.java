package dk.digitalidentity.rc.attestation.service.temporal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporalFieldUpdaterTest {

    @Test
    public void willUpdateFields() {
        final var source = TemporalTestEntity.builder()
                .id(1)
                .valueLong(1L)
                .valueString("værdi")
                .build();
        final var target = TemporalTestEntity.builder()
                .id(2)
                .valueString("different")
                .build();

        TemporalFieldUpdater.updateFields(target, source);
        assertThat(target.getId()).isEqualTo(2);
        assertThat(target.getValueString()).isEqualTo("different");
        assertThat(target.getValueLong()).isEqualTo(source.getValueLong());
        assertThat(target.getValueList()).isEqualTo(source.getValueList());

    }

}
