# TGA Bot

Welcome to the TGA Bot documentation. Here's a reference guide for all the features and functionalities of the bot, as well as any quirks that it may have.

## About The Bot

TGA Bot is a bot designed and built for The Game Awards discord server to handle community voting for [The Game Awards](https://thegameawards.com). The bot utilizes reaction-based anonymous voting and tallying.

Once a user has voted, they will be given a special `Voted` role, which by default only gives them access to the `#vote-confirmation` channel, which notifies the user that they've voted on a poll and they can click a reaction to have a copy of their responses DMed to them.

## Adding The Bot

You can invite the bot to your server by clicking [this](https://discord.com/api/oauth2/authorize?client_id=488844479868960791&permissions=268766288&scope=bot) link.

**Note:** The bot is private, the link above is purely for reference.

### Permission Breakdown

Below is a breakdown of permissions that the bot requires.

| Permission | Description |
| ---------- | ----------- |
| `MANAGE_ROLES` | Create and assign the `Voted` role that is given to users after they vote |
| `READ_MESSAGES` | Read messages sent in the server |
| `EXTERNAL_EMOJI` | Use emoji in polls that may not be on the server. |
| `MANAGE_CHANNELS` | Create the `#vote-confirmation` channel that notifies the user that their vote has been recorded. |
| `SEND_MESSAGES` | Send messages in the server |
| `ADD_REACTIONS` | Add reactions to the polls |

## Logging

The bot logs most activities (vote changes, new votes) to a logging channel specified in the configuration. To ensure confidentiality in votes, the actual vote isn't logged.