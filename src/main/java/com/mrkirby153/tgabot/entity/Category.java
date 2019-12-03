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
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;


/**
 * A poll category
 */
@Entity
@Table(name = "categories")
@RequiredArgsConstructor
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Category {

    @Id
    @GeneratedValue
    private long id;

    /**
     * The name of the category
     */
    @NonNull
    private String name;

    /**
     * The guild which the category resides
     */
    @NonNull
    private String guild;

    /**
     * The channel in which the category resides
     */
    @NonNull
    private String channel;

    /**
     * The message in which the category resides
     */
    private String message;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "category")
    private List<Option> options = new ArrayList<>();

    @Override
    public String toString() {
        return "Category{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", guild='" + guild + '\'' +
            ", channel='" + channel + '\'' +
            ", message='" + message + '\'' +
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
        if (!(o instanceof Category)) {
            return false;
        }
        Category category = (Category) o;
        return getId() == category.getId();
    }
}
