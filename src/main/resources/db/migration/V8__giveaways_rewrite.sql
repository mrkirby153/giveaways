-- Add interaction uuid
ALTER TABLE `giveaways`
    ADD COLUMN `interaction_uuid` VARCHAR(255) DEFAULT NULL AFTER `final_winners`;

-- Create Jobs table

CREATE TABLE IF NOT EXISTS `jobs`
(
    `id`     INT          NOT NULL PRIMARY KEY,
    `class`  VARCHAR(255) NOT NULL,
    `data`   TEXT DEFAULT NULL,
    `queue`  VARCHAR(255) NOT NULL,
    `run_at` TIMESTAMP
)