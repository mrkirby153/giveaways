# Command Reference

## Category Comamnds

Comamnds that have to do with manipulation categories.

| Command Name | Description | Example Usage |
| ------------ | ----------- | ------------- |
| `!categories` | Displays a list of all the categories | `!categories` |
| `!category create <channel> <name>` | Creates a category | `!category create #polls Test Category` |
| `!category remove <id>` | Removes a category and all its votes. This action is irreversible. | `!category remove 5` |
| `!category rename <id> <new name>` | Renames a category | `!category rename 3 new category name` |
| `!category import` | Imports a list of categories from an attached json file | `!category import` |
| `!category tally <id>` | Tallies a category and displays the results | `!category tally 3` |
| `!category tally-all` | Tallies all the categories and uploads a text file of the results | `!category tally-all` |
| `!category refresh <id>` | Refreshes a category and ensures its message is up to date | `!category refresh 3` |

## Option Commands

Commands that have to do with manipulating options.

| Command Name | Description | Example Usage |
| ------------ | ----------- | ------------- |
| `!option add <category> <emoji> <name>` | Adds an option to a poll | `!option add 3 👀 Eyes`
| `!option rename <id> <new name>` | Renames an option | `!option rename 4 A new name` |
| `!option remove <id>` | Removes an option from a poll | `!option remove 34` |
| `!option emote <id> <emoji>` | Updates the emoji of an option | `!option emote 3  👀`
| `!options <id>` | Displays all options for the poll | `!options 3` |

## Role Commands

Commands that have to do with automatically assigning roles based on the user's votes

| Command Name | Description | Example Usage |
| ------------ | ----------- | ------------- |
| `!option-role add <option> <role id>` | Adds an option role to an option | `!option-role add 3 644416890659143682` |
| `!option-role list` | Lists all the option roles that are configured | `!option-role list` |
| `!option-role delete <id>` | Deletes an option role | `!option-role delete 6` |
| `!option-role sync` | Syncs all the option roles | `!option-role sync` |

## Action Commands

Commands for viewing the logs of a user's actions

| Command Name | Description | Example Usage |
| ------------ | ----------- | ------------- |
| `!logs <id> [page]` | Retrieves logs for the user | `!logs 117791909786812423 2` |
| `!logs all <id>` | Retrieves all a user's logs as a text file | `!logs all 117791909786812423` |

## Administrative Commands

Commands for administering the bot or changing its internal settings.

| Command Name | Description | Example Usage |
| ------------ | ----------- | ------------- |
| `!reaction-threshold [num]` | Gets or sets the threshold at which all reactions will be cleared and re-added (Defaults to 10) | `!reaction-threshold 5` |
| `!ping` | Checks the bots ping | `!ping` |
| `!update-message` | Updates the voted message | `!update-message` |
| `!update-role` | Updates the voted users. This may take a bit | `!update-role` |
| `!log-level <logger> [level]` | Gets or sets the log level of a logger | `!log-level com.mrkirby153.snowsgivingbot DEBUG` |
| `!reaction-threshold [new]` | Gets or sets the reaction threshold | `!reaction-threshold 10` |
| `!reaction-executors <new>` | Resizes the reaction removal thread pool | `!reaction-executors 10` |