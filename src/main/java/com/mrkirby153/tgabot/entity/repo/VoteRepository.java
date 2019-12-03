package com.mrkirby153.tgabot.entity.repo;

import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import com.mrkirby153.tgabot.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    /**
     * Counts all the votes for the given option
     *
     * @param option The option
     *
     * @return The amount of votes for this option
     */
    long countAllByOption(Option option);

    /**
     * Finds the vote by the given user
     */
    @Query("select v from Vote v inner join Option o on v.option = o.id where v.user = (:user) and o.category = (:category)")
    Optional<Vote> findUserVote(@Param("user") String user, @Param("category") Category category);

    List<Vote> getAllByUser(String user);

    @Query("SELECT DISTINCT v.user FROM Vote v")
    List<String> getAllUserIds();
}
