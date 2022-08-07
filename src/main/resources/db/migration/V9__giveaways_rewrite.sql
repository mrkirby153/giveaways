-- Add interaction uuid
ALTER TABLE `giveaways` ADD COLUMN `interaction_uuid` VARCHAR(255) DEFAULT NULL AFTER `final_winners`;