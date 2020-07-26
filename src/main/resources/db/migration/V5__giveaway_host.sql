-- Add a host field to display the host of the giveaway
ALTER TABLE `giveaways` ADD `host` VARCHAR(255) DEFAULT NULL AFTER `name`;