package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporalHasherTest {

    @Data
    @Builder
    @AllArgsConstructor
    public static class TemporalHasherTestObject {
        private int id;
        @PartOfNaturalKey
        private String valueString;
        @PartOfNaturalKey
        private Long valueLong;
        @PartOfNaturalKey
        private List<Long> valueList;
    }

    @Test
    public void canHashEntityEquals() {
        // Check that two entity with similar content results in same hash
        final var t1 = TemporalHasherTestObject.builder()
                .id(1)
                .valueLong(1L)
                .valueString("værdi")
                .valueList(Arrays.asList(1L, 2L, 3L))
                .build();
        final var t2 = TemporalHasherTestObject.builder()
                .id(2)
                .valueLong(1L)
                .valueString("værdi")
                .valueList(Arrays.asList(1L, 2L, 3L))
                .build();
        final var t1hash = TemporalHasher.hashEntity(t1);
        final var t2hash = TemporalHasher.hashEntity(t2);
        assertThat(t1hash).isEqualTo(t2hash);
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    public void changeToFieldShouldResultInDifferentHash() {
        // Check that changing a field annotated with PartOfNaturalKey will result in a new hash
        final var t1 = TemporalHasherTestObject.builder()
                .id(1)
                .valueLong(1L)
                .valueString("værdi")
                .valueList(Arrays.asList(1L, 2L, 3L))
                .build();
        final var t1hash = TemporalHasher.hashEntity(t1);
        t1.setValueLong(777L);
        assertThat(t1hash).isNotEqualTo(TemporalHasher.hashEntity(t1));
    }

}
