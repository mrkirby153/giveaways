-- Adds a secret flag to the giveaway. Secret giveaways do not announce winners
ALTER TABLE `giveaways` ADD secret tinyint(1) DEFAULT 0 AFTER winners;