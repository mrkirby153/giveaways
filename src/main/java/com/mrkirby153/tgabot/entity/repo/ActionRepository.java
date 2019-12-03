package com.mrkirby153.tgabot.entity.repo;

import com.mrkirby153.tgabot.entity.ActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRepository extends JpaRepository<ActionLog, Long> {

    /**
     * Gets all the actions that a user has performed
     *
     * @param user     The user
     * @param pageable Pageable data
     *
     * @return The actions a user has performed
     */
    Page<ActionLog> getAllByUser(String user, Pageable pageable);
}
