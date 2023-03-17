package io.github.holo314

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.rest.builder.interaction.int
import dev.kord.rest.builder.interaction.string
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

class Kg(historyLocation: String) {
    private val history = File(historyLocation)
    private var zoneLevelMapping = HashMap<String, Int>()

    init {
        if (!history.exists()) {
            history.createNewFile()
            history.writeText("{}")
        }
        val content = history.readText()
        zoneLevelMapping = Json.decodeFromString(content)
    }

    private fun save() {
        history.writeText(Json.encodeToString(zoneLevelMapping))
    }

    suspend fun kg(interaction: GuildChatInputCommandInteraction, skillLevel: Int = 0) {
        val params = interaction.command
        val dice = params.integers["dice"]!!.toInt()
        val difficulty = params.integers["difficulty"]?.toInt() ?: 6
        val user = interaction.user.mention
        val character = params.strings["character"] ?: interaction.user.mention
        if (!zoneLevelMapping.contains(character)) {
            zoneLevelMapping[character] = 0
        }
        val zoneLevel = zoneLevelMapping[character]!!
        val (diceResult, masteries, tens, score, newZoneLevel, diff) = kgRoll(dice, difficulty, zoneLevel, skillLevel)
        zoneLevelMapping[character] = newZoneLevel

        val diceResultString = diceResult.joinToString(separator = ", ") {
            if (diff > 1 && it == 1)
                "*1*"
            else if (it < diff)
                it.toString()
            else if (it == 10 && score > 1 && tens > 1)
                "**__10__**"
            else
                "__${it}__"
        }

        val masteriesResultString = masteries.joinToString(separator = ", ") {
            if (diff > 1 && it == 1)
                "*1*"
            else if (it < diff)
                it.toString()
            else if (it == 10)
                "**__10__**"
            else
                "__${it}__"
        }

        val diceString =
            if (masteriesResultString.isNotEmpty()) "roll: $diceResultString masteries: $masteriesResultString" else "roll: $diceResultString"


        val messageStart =
            "$user " + (if (user == character) "" else "(character **$character**): ") + "$dice dice, $difficulty difficulty"
        val messageBase = "$diceString, total score: **__${score}__**"
        val messageAddon = if (zoneLevel == newZoneLevel) "Zone level: $newZoneLevel" else "Previous Zone level: $zoneLevel\n**__New Zone level: ${newZoneLevel}__**"
        interaction.respondPublic {
            content = messageStart + "\n" + messageBase + "\n" + messageAddon
        }
        save()
    }

    suspend fun sp(interaction: GuildChatInputCommandInteraction) {
        val level = interaction.command.integers["level"]!!.toInt() + 10
        kg(interaction, level)
    }

    suspend fun gn(interaction: GuildChatInputCommandInteraction) {
        val level = interaction.command.integers["level"]!!.toInt()
        kg(interaction, level)
    }

    suspend fun setLevel(interaction: GuildChatInputCommandInteraction) {
        if (!interaction.user.isOwner()) {
            interaction.respondPublic {
                content = "This action is only available for the owner"
            }
            return
        }
        val character = interaction.command.strings["character"]!!
        val level = interaction.command.integers["level"]!!.toInt()
        val original = zoneLevelMapping[character] ?: 0

        zoneLevelMapping[character] = level
        interaction.respondPublic {
            content = "Set character (**$character**) level to **$level**, previously was $original"
        }

        save()
    }
}

suspend fun populateKg(kord: Kord) {
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

    kord.createGlobalChatInputCommand("kg", "roll dice") {
        int("dice", "the amount of dice to roll") {
            required = true
        }
        int("difficulty", "the difficulty of the dice") {
            required = false
            minValue = 1
        }
        string("character", "the character the roll is for") {
            required = false
        }
    }

    kord.createGlobalChatInputCommand("sp", "roll dice for specific skill") {
        int("dice", "the amount of dice to roll") {
            required = true
        }
        int("level", "the skill level (1-5)") {
            required = true
            for (i in 1..5) {
                choice("$i", i.toLong())
            }
        }
        int("difficulty", "the difficulty of the dice") {
            required = false
            minValue = 1
        }
        string("character", "the character the roll is for") {
            required = false
        }
    }
    kord.createGlobalChatInputCommand("gn", "roll dice for specific skill") {
        int("dice", "the amount of dice to roll") {
            required = true
        }
        int("level", "the skill level (1-10)") {
            required = true
            for (i in 1..10) {
                choice("$i", i.toLong())
            }
        }
        int("difficulty", "the difficulty of the dice") {
            required = false
            minValue = 1
        }
        string("character", "the character the roll is for") {
            required = false
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



