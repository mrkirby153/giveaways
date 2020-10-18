package com.mrkirby153.snowsgivingbot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "settings")
@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
public class SettingEntity {

    @Id
    @GeneratedValue
    private long id;

    @NonNull
    @Column(name = "guild")
    private String guild;

    @NonNull
    @Column(name = "`key`")
    private String key;

    @NonNull
    @Column(name = "`value`")
    private String value;
}
