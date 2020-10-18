CREATE TABLE `settings` (
    `id` INT NOT NULL,
    `guild` VARCHAR(255) NOT NULL,
    `key` VARCHAR(255) NOT NULL,
    `value` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX(`guild`, `key`)
)