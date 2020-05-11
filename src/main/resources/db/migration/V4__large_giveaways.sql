-- Alter the giveaway winners table to support a large number of giveaway winners

ALTER TABLE `giveaways` CHANGE `final_winners` `final_winners` TEXT DEFAULT NULL;