package com.mrkirby153.snowsgivingbot.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.List;

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
    @Column(name = "channel_id")
    private String channelId;

    @NonNull
    @Column(name = "message_id")
    private String messageId;

    private int winners;

    @CreatedDate
    @Column(name = "created_at")
    private Timestamp createdAt;

    @NonNull
    @Column(name = "ends_at")
    private Timestamp endsAt;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "giveaway")
    private List<GiveawayEntrantEntity> entrants;

    private GiveawayState state = GiveawayState.RUNNING;


    public enum GiveawayState {
        RUNNING,
        ENDED
    }

    @Override
    public String toString() {
        return "GiveawayEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", channelId='" + channelId + '\'' +
                '}';
    }
}
