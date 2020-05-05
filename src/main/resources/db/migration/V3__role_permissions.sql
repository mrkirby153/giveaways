-- Create a role permissions table for giveaway managers
CREATE TABLE `giveaway_roles` (
    `id` INT NOT NULL,
    `guild` VARCHAR(255) NOT NULL,
    `role_id` VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
)