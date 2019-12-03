CREATE TABLE `giveaways`(
    `id` INT NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `channel` VARCHAR(255) NOT NULL,
    `message` VARCHAR(255) NOT NULL,
    `winners` INT NOT NULL,
    `created_at` TIMESTAMP NOT NULL,
    `ends_at` TIMESTAMP NOT NULL,
    `state` INT NOT NULL,
    PRIMARY KEY (id),
    INDEX(message)
);

CREATE TABLE `entrants` (
    `id` INT NOT NULL,
    `giveaway_id` INT NOT NULL,
    `user_id` VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (giveaway_id) REFERENCES giveaways(id)
);

-- Hibernate sequence table
CREATE TABLE `hibernate_sequence`(
    `next_val` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO hibernate_sequence (`next_val`) VALUES (1);