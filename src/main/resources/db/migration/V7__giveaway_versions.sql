-- Add a version field for giveaways
ALTER TABLE `giveaways` ADD `version` INT NOT NULL DEFAULT 1 AFTER `final_winners`