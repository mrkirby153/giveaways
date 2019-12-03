package com.mrkirby153.snowsgivingbot.entity;

import lombok.*;

import javax.persistence.*;

@Table(name = "entrants")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
@Data
public class GiveawayEntrantEntity {

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @JoinColumn(name = "giveaway_id")
    @NonNull
    private GiveawayEntity giveaway;

    @Column(name = "user_id")
    private String userId;
}
