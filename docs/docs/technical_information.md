# Technical Notes

## Technologies Used

TGABot uses the following technologies and libraries:

* [MySQL](https://www.mysql.com)/[MariaDB](https://www.mariadb.com) for vote storing and tallying
* [Spring Boot](https://spring.io/projects/spring-boot) for dependency injection and as a general framework
    * [Spring Data](https://spring.io/projects/spring-data) for database management via Hibernate

## Installation and Running

_The below instructions are provided for informational purposes only, and we're not responsible for any harm or damage to your system that may result in following these instructions_

### Requirements

* Java 8 or above
* Gradle
* MySQL/MariadDB
* A Discord bot token.

### Installation

1. Clone the [source](https://github.com/mrkirby153/tgabot)
2. Build the bot: `gradle bootJar`

### Running

#### Example Configuration

```properties
bot.token=<BOT TOKEN>
bot.admins=<ADMINS> -- u:<id> for individual users, r:<id> for roles. Comma separated
bot.voted.role=<Role ID> -- The role id of the voted role
bot.guild=<Guild ID> -- The id of the guild the bot operates in
bot.log-channel=<Channel ID> -- The text channel that the bot will log to

spring.datasource.url=<url> -- The url of the datasource (i.e. jdbc:mysql://localhost:3306/tgabot)
spring.datasource.username=<username>
spring.datasource.password=<password>
```

1. Copy the example config into `config/application.properties`
2. Create a file called `config/voted.txt` and populate it with the message that users see when they have voted. (The path can be overwritten with the `bot.voted.message` property)
4. Run the bot: `java -jar TGABot.jar`