package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum ContainsTitles {
    NO(0, "html.enum.containtitles.no"),
    POSITIVE(1, "html.enum.containtitles.positive"),
    NEGATIVE(2, "html.enum.containtitles.negative");

    private int value;
    private String status;

    private ContainsTitles(int value, String status) {
        this.value = value;
        this.status = status;
    }
}
