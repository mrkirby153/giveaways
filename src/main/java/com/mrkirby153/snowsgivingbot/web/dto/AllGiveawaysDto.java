package com.mrkirby153.snowsgivingbot.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AllGiveawaysDto {
    @JsonProperty("active")
    private final List<GiveawayDto> activeGiveaways;
    @JsonProperty("inactive")
    private final List<GiveawayDto> inactiveGiveaways;
}
