package io.github.holo314

import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.core.behavior.interaction.*
import kotlinx.coroutines.coroutineScope

suspend fun main() = coroutineScope {
    val kg = Kg(System.getProperty("history", "history.json"))
    val kord = Kord(System.getProperty("token"))

    populateKg(kord)
    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        when(interaction.command.rootName) {
            "kg" -> kg.kg(interaction)
            "sp" -> kg.sp(interaction)
            "gn" -> kg.gn(interaction)
            "set" -> kg.setLevel(interaction)
            "roll" -> rollCommand(interaction)
            "d" -> rollFormat(interaction)
        }
    }

    kord.login {
        @OptIn(PrivilegedIntent::class)
        intents += Intent.MessageContent
    }
}