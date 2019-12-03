package com.mrkirby153.tgabot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.sql.Timestamp;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * A vote for a category
 */
@Table(name = "votes")
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
@Data
@EntityListeners(AuditingEntityListener.class)
public class Vote {

    /**
     * The id of the vote
     */
    @Id
    @GeneratedValue
    private long id;

    /**
     * The user who voted
     */
    @NonNull
    private String user;

    /**
     * The option that the user voted for
     */
    @ManyToOne
    @JoinColumn(name = "option")
    @NonNull
    private Option option;

    /**
     * The timestamp that the vote was cast
     */
    @CreatedDate
    private Timestamp timestamp;

    @Override
    public String toString() {
        return "Vote{" +
            "id=" + id +
            ", user='" + user + '\'' +
            ", option=" + option.getId() +
            ", timestamp=" + timestamp +
            '}';
    }
}
