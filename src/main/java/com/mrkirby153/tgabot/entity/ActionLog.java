package com.mrkirby153.tgabot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
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
import javax.persistence.Table;

@Entity
@Table(name = "action_log")
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
@Data
@EntityListeners(AuditingEntityListener.class)
public class ActionLog {


    /**
     * The id of the action
     */
    @Id
    @GeneratedValue
    private long id;

    /**
     * The user who performed the action
     */
    @NonNull
    private String user;

    /**
     * The type of action
     */
    @NonNull
    private ActionType type;

    /**
     * The action's data
     */
    private String data;

    /**
     * The creation time of the action
     */
    @CreatedDate
    private Timestamp timestamp;

    /**
     * The available action types
     */
    @RequiredArgsConstructor
    public enum ActionType {
        VOTE_CAST("Vote Cast"),
        VOTE_RETRACT("Vote Retract"),
        SEND_DM("Send Vote DM"),
        DM_FAILED("DM Failed");

        @Getter
        private final String friendlyName;
    }
}
