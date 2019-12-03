-- Categories table
CREATE TABLE `categories` (
    id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    guild VARCHAR(255) NOT NULL,
    channel VARCHAR(255) NOT NULL,
    message VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Options table
CREATE TABLE `options` (
    id INT NOT NULL,
    category INT NOT NULL,
    custom BOOLEAN DEFAULT FALSE,
    reaction VARCHAR(255) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (category) REFERENCES categories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Votes table
CREATE TABLE `votes`(
    id INT NOT NULL,
    user VARCHAR(255) NOT NULL,
    option INT NOT NULL,
    `timestamp` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (option) REFERENCES options(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Action log table
CREATE TABLE `action_log`(
    id INT NOT NULL,
    user VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    data TEXT DEFAULT NULL,
    `timestamp` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Option Role table
CREATE TABLE `option_roles`(
    id INT NOT NULL,
    option INT NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (option) REFERENCES options(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Hibernate sequence table
CREATE TABLE `hibernate_sequence`(
    `next_val` bigint(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO hibernate_sequence (`next_val`) VALUES (1);