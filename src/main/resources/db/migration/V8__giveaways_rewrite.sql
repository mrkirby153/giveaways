-- Add interaction uuid
ALTER TABLE `giveaways`
    ADD COLUMN `interaction_uuid` VARCHAR(255) DEFAULT NULL AFTER `final_winners`;

-- Remove secret column from giveaways
ALTER TABLE `giveaways`
    DROP COLUMN `secret`;

-- Add sequences
CREATE TABLE `giveaways_seq`
(
    `next_val` bigint(20) DEFAULT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO giveaways_seq (`next_val`)
VALUES ((SELECT COALESCE(MAX(`id`) + 1000, 1) FROM giveaways));

CREATE TABLE `entrants_seq`
(
    `next_val` bigint(20) DEFAULT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO `entrants_seq` (`next_val`)
VALUES ((SELECT COALESCE(MAX(`id`) + 1000, 1) FROM entrants));

CREATE TABLE `giveaway_roles_seq`
(
    `next_val` bigint(20) DEFAULT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO `giveaway_roles_seq` (`next_val`)
VALUES ((SELECT COALESCE(MAX(`id`) + 1000, 1) FROM giveaway_roles));

CREATE TABLE `settings_seq`
(
    `next_val` bigint(20) DEFAULT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO `settings_seq` (`next_val`)
VALUES ((SELECT COALESCE(MAX(`id`) + 1000, 1) FROM settings));

-- Drop old hibernate sequence
DROP TABLE `hibernate_sequence`;