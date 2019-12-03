package com.mrkirby153.tgabot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;


/**
 * An option for a poll
 */
@Table(name = "options")
@RequiredArgsConstructor
@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
public class Option {


    /**
     * The option's id
     */
    @Id
    @GeneratedValue
    private long id;

    /**
     * The category this option belongs to
     */
    @ManyToOne
    @JoinColumn(name = "category")
    @NonNull
    private Category category;

    /**
     * If the option uses a custom emote
     */
    @NonNull
    private boolean custom;

    /**
     * The reaction emote that this option uses
     */
    @NonNull
    private String reaction;

    /**
     * The name of the category
     */
    @NonNull
    private String name;

    @OneToMany(cascade = CascadeType.REMOVE, mappedBy = "option")
    private List<Vote> votes = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, mappedBy = "option")
    private OptionRole optionRole;

    @Override
    public String toString() {
        return "Option{" +
            "id=" + id +
            ", category=" + category +
            ", custom=" + custom +
            ", reaction='" + reaction + '\'' +
            ", name='" + name + '\'' +
            '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Option)) {
            return false;
        }
        Option option = (Option) o;
        return getId() == option.getId();
    }
}
