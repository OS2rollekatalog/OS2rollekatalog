package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.TitleAM;
import dk.digitalidentity.rc.controller.api.model.TitleShallowAM;
import dk.digitalidentity.rc.dao.model.Title;

public abstract class TitleMapper {

    public static TitleAM titleToApi(final Title title) {
        if (title == null) {
            return null;
        }
        return TitleAM.builder()
                .active(title.isActive())
                .lastUpdated(title.getLastUpdated())
                .name(title.getName())
                .uuid(title.getUuid())
                .build();
    }

    public static TitleShallowAM toShallowApi(final Title title) {
        if (title == null) {
            return null;
        }
        return TitleShallowAM.builder()
                .name(title.getName())
                .uuid(title.getUuid())
                .build();
    }

}
