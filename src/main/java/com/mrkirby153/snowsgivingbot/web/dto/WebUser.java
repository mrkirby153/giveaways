package com.mrkirby153.snowsgivingbot.web.dto;

import lombok.Data;
import lombok.NonNull;

@Data
public class WebUser {

    @NonNull
    private final String id;
    @NonNull
    private final String username;
    @NonNull
    private final String discriminator;
    private final String avatar;
}
