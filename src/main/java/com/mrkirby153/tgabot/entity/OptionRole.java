package com.mrkirby153.tgabot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Table(name = "option_roles")
@RequiredArgsConstructor
@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
public class OptionRole {

    @Id
    @GeneratedValue
    private long id;

    /**
     * The option this role belongs to
     */
    @OneToOne
    @JoinColumn(name = "option")
    @NonNull
    private Option option;

    /**
     * The role id that this role belongs to
     */
    @Column(name = "role_id")
    @NonNull
    private String roleId;
}
