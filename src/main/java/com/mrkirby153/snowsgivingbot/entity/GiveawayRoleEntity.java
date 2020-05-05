package com.mrkirby153.snowsgivingbot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "giveaway_roles")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class GiveawayRoleEntity {

    @Id
    @GeneratedValue
    private long id;

    @NonNull
    @Column(name = "guild")
    private String guildId;

    @NonNull
    @Column(name = "role_id")
    private String roleId;
}
