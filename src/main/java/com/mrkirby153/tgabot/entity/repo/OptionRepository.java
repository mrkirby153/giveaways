package com.mrkirby153.tgabot.entity.repo;

import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OptionRepository extends JpaRepository<Option, Long> {

    /**
     * Finds an option by the category and its reaction
     *
     * @param category The category
     * @param reaction THe reaction
     *
     * @return The option
     */
    Optional<Option> findByCategoryAndReaction(Category category, String reaction);

    List<Option> findAllByCategory(Category category);
}
