package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.UserAM2;
import dk.digitalidentity.rc.controller.api.model.UserShallowAM;
import dk.digitalidentity.rc.dao.model.User;

public abstract class UserMapper {

    public static UserAM2 toApi(final User user) {
        return UserAM2.builder()
                .userId(user.getUserId())
                .uuid(user.getUuid())
                .name(user.getName())
                .extUuid(user.getExtUuid())
                .build();
    }

    public static UserShallowAM toShallowApi(final User user) {
        return UserShallowAM.builder()
                .name(user.getName())
                .userId(user.getUserId())
                .uuid(user.getUuid())
                .build();
    }

}
