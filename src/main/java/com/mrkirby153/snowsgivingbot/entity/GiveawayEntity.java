package com.mrkirby153.snowsgivingbot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.sql.Timestamp;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "giveaways")
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
@Data
@EntityListeners(AuditingEntityListener.class)
public class GiveawayEntity {

    @Id
    @GeneratedValue
    private long id;

    @NonNull
    private String name;

    @NonNull
    @Column(name = "guild_id")
    private String guildId;

    @NonNull
    @Column(name = "channel")
    private String channelId;

    @NonNull
    @Column(name = "message")
    private String messageId;

    private int winners;

    @NonNull
    @Column(name = "secret")
    private boolean secret = false;

    @CreatedDate
    @Column(name = "created_at")
    private Timestamp createdAt;

    @NonNull
    @Column(name = "ends_at")
    private Timestamp endsAt;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "giveaway")
    private List<GiveawayEntrantEntity> entrants;

    @Enumerated(value = EnumType.ORDINAL)
    private GiveawayState state = GiveawayState.RUNNING;

    @Column(name = "final_winners")
    private String finalWinners;


    @Override
    public String toString() {
        return "GiveawayEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", channelId='" + channelId + '\'' +
                '}';
    }
}
