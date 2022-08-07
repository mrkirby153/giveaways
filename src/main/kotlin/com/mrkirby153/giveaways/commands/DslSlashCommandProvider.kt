package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor

interface DslSlashCommandProvider {

    /**
     * Registers all slash commands
     */
    fun register(executor: DslCommandExecutor)
}