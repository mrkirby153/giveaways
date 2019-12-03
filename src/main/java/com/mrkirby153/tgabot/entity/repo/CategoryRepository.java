package com.mrkirby153.tgabot.entity.repo;

import com.mrkirby153.tgabot.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import javax.persistence.Tuple;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Finds a category by name
     *
     * @param name  The name
     * @param guild The guild
     *
     * @return The category
     */
    Category findByNameIgnoreCaseAndGuild(String name, String guild);

    /**
     * Finds a category by its message
     *
     * @param message The message of the category
     *
     * @return The category
     */
    Optional<Category> findByMessage(String message);


    @Query("select o, count(v) as c from Vote v left join Option o on v.option = o.id where o.category = (:category) group by o order by c desc")
    List<Tuple> tally(@Param("category") Category category);
}
