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

class KgV1(val history: History) {
    companion object {
        const val ROLL_COMMAND = "_kg"
        const val SPECIFIC_ROLL_COMMAND = "_sp"
        const val GENERAL_ROLL_COMMAND = "_gn"
        suspend fun populateKg(kord: Kord) {
            kord.createGlobalChatInputCommand(ROLL_COMMAND, "roll dice") {
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

            kord.createGlobalChatInputCommand(SPECIFIC_ROLL_COMMAND, "roll dice for specific skill") {
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
            kord.createGlobalChatInputCommand(GENERAL_ROLL_COMMAND, "roll dice for specific skill") {
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
        }
    }

    suspend fun roll(interaction: GuildChatInputCommandInteraction, skillLevel: Int = 0) {
        val params = interaction.command
        val dice = params.integers["dice"]!!.toInt()
        val difficulty = params.integers["difficulty"]?.toInt() ?: 6
        val user = interaction.user.mention
        val character = params.strings["character"] ?: interaction.user.mention
        if (!history.zoneLevelMapping.contains(character)) {
            history.zoneLevelMapping[character] = 0
        }
        val zoneLevel = history.zoneLevelMapping[character]!!
        val (diceResult, masteries, tens, score, newZoneLevel, diff) = kgRoll(dice, difficulty, zoneLevel, skillLevel)
        history.zoneLevelMapping[character] = newZoneLevel

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
        history.save()
    }

    suspend fun specific(interaction: GuildChatInputCommandInteraction) {
        val level = interaction.command.integers["level"]!!.toInt() + 10
        roll(interaction, level)
    }

    suspend fun general(interaction: GuildChatInputCommandInteraction) {
        val level = interaction.command.integers["level"]!!.toInt()
        roll(interaction, level)
    }
}