package io.github.holo314

import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.core.behavior.interaction.*
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.rest.builder.interaction.int
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.coroutineScope
import java.io.File
import kotlin.random.Random

suspend fun main() = coroutineScope {
    val historyLocation = System.getProperty("history", "history.json")
        .let {
            if (it.isNullOrBlank()) "history.json" else it
        }
    val history = History(File(historyLocation))
    val kgV1 = KgV1(history)
    val kord = Kord(System.getProperty("token"))

    generalPopulate(kord)
    KgV1.populateKg(kord)
    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        when (interaction.command.rootName) {
            KgV1.ROLL_COMMAND -> kgV1.roll(interaction)
            KgV1.SPECIFIC_ROLL_COMMAND -> kgV1.specific(interaction)
            KgV1.GENERAL_ROLL_COMMAND -> kgV1.general(interaction)
            "set" -> history.setLevel(interaction)
            "roll" -> rollCommand(interaction)
            "d" -> rollFormat(interaction)
        }
    }

    kord.login {
        @OptIn(PrivilegedIntent::class)
        intents += Intent.MessageContent
    }
}

suspend fun generalPopulate(kord: Kord) {
    kord.createGlobalChatInputCommand("d", "roll dice") {
        string("dice", "the dice to roll") {
            required = true
        }
    }

    kord.createGlobalChatInputCommand("roll", "roll dice") {
        int("dice", "the amount of dice to roll") {
            required = true
        }
        int("difficulty", "the difficulty of the dice") {
            required = false
            minValue = 1
        }
    }

    kord.createGlobalChatInputCommand("set", "set a character to a zone level") {
        string("character", "the character to set the zone level") {
            required = true
        }
        int("level", "the zone level") {
            required = true
            minValue = 0
        }
    }
}

suspend fun rollCommand(interaction: GuildChatInputCommandInteraction) {
    val params = interaction.command
    val dice = params.integers["dice"]!!.toInt()
    val difficulty = params.integers["difficulty"]?.toInt() ?: 6
    val user = interaction.user.mention

    val request = RollRequest(dice, difficulty, 0, 0)
    val (diceResult, _, _, score, _, diff) = challenge(request)

    val diceString = "roll: " + diceResult.joinToString(separator = ", ") {
        if (diff > 1 && it == 1)
            "*1*"
        else if (it < diff)
            it.toString()
        else
            "__${it}__"
    }

    val messageStart = "$user $dice dice, $difficulty difficulty"
    val messageBase = "$diceString, total score: **__${score}__**"
    interaction.respondPublic {
        content = messageStart + "\n" + messageBase
    }
}

suspend fun rollFormat(interaction: GuildChatInputCommandInteraction) {
    val params = interaction.command
    val dice = params.strings["dice"]!!.split("d")
    val amount = dice[0].toInt()
    val dSize = dice[1].toInt()
    val user = interaction.user.mention

    val result = List(amount) { Random.nextInt(1, dSize + 1)}

    val diceString = "rolled: " + result.joinToString(separator = ", ")

    val messageStart = "$user roll ${params.strings["dice"]!!} ($amount dice of size $dSize)"

    interaction.respondPublic {
        content = messageStart + "\n" + diceString
    }
}