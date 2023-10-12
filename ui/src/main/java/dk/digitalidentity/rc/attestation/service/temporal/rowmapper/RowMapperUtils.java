package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RowMapperUtils {

    public static LocalDate nullSafeLocalDate(final java.sql.Date rowDate) {
        if (rowDate == null) {
            return null;
        }
        return new java.sql.Date(rowDate.getTime()).toLocalDate();
    }

    public static List<String> explodeToList(final String joined) {
        if ("".equals(joined)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(joined.split(",")));
    }

    public static String joinFromList(final List<String> list) {
        if (list == null) {
            return null;
        }
        return String.join(",", list);
    }

    public static Long zeroIsNull(long l) {
        return l == 0 ? null : l;
    }

}
