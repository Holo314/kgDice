package io.github.holo314

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.int
import dev.kord.rest.builder.interaction.number
import dev.kord.rest.builder.interaction.string
import java.math.RoundingMode
import java.text.DecimalFormat

class KgV2(private val history: History) {
    companion object {
        const val ROLL_COMMAND = "kg"
        const val SPECIFIC_ROLL_COMMAND = "sp"
        const val GENERAL_ROLL_COMMAND = "gn"
        const val SET_BASE_DIFFICULTY = "set_base_difficulty"

        private val format = DecimalFormat("#.##").also { it.roundingMode = RoundingMode.DOWN }

        suspend fun populate(kord: Kord) {
            kord.createGlobalChatInputCommand(ROLL_COMMAND, "roll dice") {
                addRollParameters()
            }

            kord.createGlobalChatInputCommand(SPECIFIC_ROLL_COMMAND, "roll dice for specific skill") {
                int("level", "the skill level") {
                    required = true
                    for (i in 1..3) {
                        choice("$i", i.toLong())
                    }
                }
                addRollParameters()
            }
            kord.createGlobalChatInputCommand(GENERAL_ROLL_COMMAND, "roll dice for general skill") {
                int("level", "the skill level") {
                    required = true
                    for (i in 1..4) {
                        choice("$i", i.toLong())
                    }
                }
                addRollParameters()
            }
            kord.createGlobalChatInputCommand(SET_BASE_DIFFICULTY, "set the base difficulty for general skills") {
                int("base_diff", "the new value of the base difficulty") {
                    required = true
                }
            }
        }

        private fun GlobalChatInputCreateBuilder.addRollParameters() {
            int("dice", "the stat value of the action") {
                required = true
            }
            number("av", "the Action Value to use for the Action Points") {
                required = true
                minValue = 0.0
            }
            int("difficulty", "the base difficulty of the dice") {
                required = false
                minValue = 1
            }
            string("character", "the character the roll is for") {
                required = false
            }
            int("threshold", "the amount of successes needed") {
                required = false
                minValue = 1
            }
            string("description", "the action description") {
                required = false
            }
            string("bonus", "the action bonus,should be from the form \"+n\", \"-n\", \"*n\" for a number n") {
                required = false
            }
        }

        fun resultString(
            result: KgResult,
            user: String,
            character: String,
            dice: Int,
            difficulty: Int,
            zoneLevel: Int,
            actionValue: Double,
            skillBonus: Double,
            actionBonus: Double,
            actionBonusEffect: String,
            actionPoint: Int
        ): String {
            val (diceResult, masteries, _, score, newZoneLevel, diff, threshold) = result
            val diceResultString = diceResult.joinToString(separator = ", ") {
                if (diff > 1 && it == 1)
                    "*1*"
                else if (it < diff)
                    it.toString()
                else if (it == 10 &&
                    (zoneLevel == 3 || (diff < 10 && zoneLevel == 0 && newZoneLevel == 1))
                )
                    "**__10__**"
                else
                    "__${it}__"
            }

            val masteriesResultString = masteries.joinToString(separator = ", ") {
                if (it == 10)
                    "**__10__**"
                else
                    "__${it}__"
            }


            val messageStart =
                "$user${
                    if (user == character) "" else " (character **$character**):"
                } $dice dice, $difficulty difficulty${
                    if (threshold > 1) ", $threshold threshold" else ""
                }"
            val diceString =
                if (masteriesResultString.isNotEmpty()) "roll: $diceResultString masteries: $masteriesResultString" else "roll: $diceResultString"
            val resultMessage =
                if (score >= threshold || score <= 0) "total score: **__${score}__**"
                else "overall successes (__${score}__) is less then the threshold (__${threshold}__), total score: **__0__**"
            val messageZoneAddon =
                if (zoneLevel == newZoneLevel) "Zone level: $newZoneLevel" else "Previous Zone level: $zoneLevel\n**__New Zone level: ${newZoneLevel}__**"

            val pointsEconomy = "Action Value: ${format.format(actionValue)}${
                if (skillBonus != 1.0) ", Skill Bonus: ${format.format(skillBonus)}" else ""
            }${
                if ((actionBonusEffect == "*" && actionBonus != 1.0) || (actionBonusEffect != "*" && actionBonus != 0.0))
                    ", Action Bonus: ${
                        when (actionBonusEffect) {
                            "*" -> "×"
                            "+" -> "+"
                            else -> ""
                        }
                    }${format.format(actionBonus)}" 
                else ""
            }, **__Action Points: ${actionPoint}__**"

            return "$messageStart\n$diceString, $resultMessage\n$messageZoneAddon\n$pointsEconomy"
        }


        fun calculateActionPoints(
            actionValue: Double,
            successes: Int,
            skillBonus: Double,
            actionBonus: Double,
            actionBonusEffect: String
        ): Int {
            val base = actionValue * successes * skillBonus
            return (if (actionBonusEffect == "*") base * actionBonus else base + actionBonus).toInt()
        }
    }

    private var baseDiff = 6

    suspend fun roll(interaction: GuildChatInputCommandInteraction) {
        val params = interaction.command

        val dice = params.integers["dice"]!!.toInt()
        val actionValue = params.numbers["av"]!!
        val difficulty = params.integers["difficulty"]?.toInt() ?: 6
        val character = params.strings["character"] ?: interaction.user.mention
        val threshold = params.integers["threshold"]?.toInt() ?: 1
        val description = params.strings["description"] ?: ""
        val actionBonusDescription = params.strings["bonus"] ?: "+0"

        val zoneLevel = history.getZone(character)
        val (actionBonusEffect, actionBonus) = try {
            parseActionBonus(actionBonusDescription)
        } catch (e: Exception) {
            interaction.respondPublic {
                content = "\"bonus\" value must of the the form `xn` where `x` is one of +,-,* and n is a number"
            }
            return
        }

        val request = RollRequest(dice, difficulty, zoneLevel, 0, threshold)

        val result = challenge(request)
        history.setZone(character, result.zoneLevel)

        val actionPoint =
            calculateActionPoints(
                actionValue,
                if (result.score >= result.threshold || result.score < 0) result.score else 0,
                1.0,
                actionBonus,
                actionBonusEffect
            )


        interaction.respondPublic {
            content = "${
                resultString(
                    result,
                    interaction.user.mention,
                    character,
                    dice,
                    difficulty,
                    zoneLevel,
                    actionValue,
                    1.0,
                    actionBonus,
                    actionBonusEffect,
                    actionPoint
                )
            }${
                if (description.isNotBlank()) "\nDescription: **${description}**" else ""
            }"
        }
    }

    suspend fun specific(interaction: GuildChatInputCommandInteraction) {
        val params = interaction.command

        val level = params.integers["level"]!!.toInt()
        val dice = params.integers["dice"]!!.toInt()
        val actionValue = params.numbers["av"]!!
        val difficulty = params.integers["difficulty"]?.toInt() ?: 6
        val character = params.strings["character"] ?: interaction.user.mention
        val threshold = params.integers["threshold"]?.toInt() ?: 1
        val description = params.strings["description"] ?: ""
        val actionBonusDescription = params.strings["bonus"] ?: "+0"

        val zoneLevel = history.getZone(character)
        val (actionBonusEffect, actionBonus) = try {
            parseActionBonus(actionBonusDescription)
        } catch (e: Exception) {
            interaction.respondPublic {
                content = "\"bonus\" value must of the the form `xn` where `x` is one of +,-,* and n is a number"
            }
            return
        }

        val request = RollRequest(dice, difficulty, zoneLevel, 0, threshold)

        var result = challenge(request)
        val expectedScore = result.score

        val scoreSkillEffect = maxOf(expectedScore, level - 1)
        val zoneSkillEffect = nextZoneLevel(zoneLevel, threshold, scoreSkillEffect, result.tens)

        result = result.copy(score = scoreSkillEffect, zoneLevel = zoneSkillEffect)
        history.setZone(character, result.zoneLevel)

        val actionPoint =
            calculateActionPoints(
                actionValue,
                if (result.score >= result.threshold || result.score < 0) result.score else 0,
                1.0,
                actionBonus,
                actionBonusEffect
            )

        val skillEffectDescription =
            getSpecificSkillEffect(expectedScore, scoreSkillEffect, level)
        interaction.respondPublic {
            content = "${
                resultString(
                    result,
                    interaction.user.mention,
                    character,
                    dice,
                    difficulty,
                    zoneLevel,
                    actionValue,
                    1.0,
                    actionBonus,
                    actionBonusEffect,
                    actionPoint
                )
            }${
                if (description.isNotBlank()) "\nDescription: **${description}**" else ""
            }${
                if (skillEffectDescription.isNotBlank()) "\n---\n$skillEffectDescription" else ""
            }"
        }
    }

    private fun getSpecificSkillEffect(
        expectedScore: Int,
        scoreSkillEffect: Int,
        level: Int
    ) =
        if (expectedScore != scoreSkillEffect) "Level $level specific skill increase roll successes from $expectedScore to $scoreSkillEffect"
        else ""

    suspend fun general(interaction: GuildChatInputCommandInteraction) {
        val params = interaction.command

        val level = params.integers["level"]!!.toInt()
        val dice = params.integers["dice"]!!.toInt()
        val actionValue = params.numbers["av"]!!
        val difficulty = params.integers["difficulty"]?.toInt() ?: 6
        val character = params.strings["character"] ?: interaction.user.mention
        val threshold = params.integers["threshold"]?.toInt() ?: 1
        val description = params.strings["description"] ?: ""
        val actionBonusDescription = params.strings["bonus"] ?: "+0"

        val zoneLevel = history.getZone(character)
        val (actionBonusEffect, actionBonus) = try {
            parseActionBonus(actionBonusDescription)
        } catch (e: Exception) {
            interaction.respondPublic {
                content = "\"bonus\" value must of the the form `xn` where `x` is one of +,-,* and n is a number"
            }
            return
        }

        val skillBonus = 1 + minOf(threshold - 1, level - 1, 2) * 0.5

        val extraDifficulty = difficulty - baseDiff
        val bonusDice = maxOf(minOf(extraDifficulty - 1, level - 1, 2), 0)
        val masteries = 0 +
                (if (level >= 4 && extraDifficulty >= 4) 1 else 0) +
                if (level >= 4 && threshold >= 4) 1 else 0

        val request = RollRequest(dice + bonusDice, difficulty, zoneLevel, masteries, threshold)

        val result = challenge(request)
        history.setZone(character, result.zoneLevel)

        val actionPoint =
            calculateActionPoints(
                actionValue,
                if (result.score >= result.threshold || result.score < 0) result.score else 0,
                skillBonus,
                actionBonus,
                actionBonusEffect
            )

        val skillEffectDescription =
            getGeneralSkillEffect(level, threshold, bonusDice, extraDifficulty, skillBonus)

        interaction.respondPublic {
            content = "${
                resultString(
                    result,
                    interaction.user.mention,
                    character,
                    dice,
                    difficulty,
                    zoneLevel,
                    actionValue,
                    skillBonus,
                    actionBonus,
                    actionBonusEffect,
                    actionPoint
                )
            }${
                if (description.isNotBlank()) "\nDescription: **${description}**" else ""
            }${
                if (skillEffectDescription.isNotBlank()) "\n---\n$skillEffectDescription" else ""
            }"
        }
    }

    private fun getGeneralSkillEffect(
        level: Int,
        threshold: Int,
        bonusDice: Int,
        extraDifficulty: Int,
        skillBonus: Double
    ): String {
        val thresholdBranchSkillEffectMax =
            if (level >= 4 && threshold >= 4) "Level $level general skill added 1 mastery die because of high threshold"
            else ""
        val thresholdBranchSkillEffect =
            if (skillBonus > 1) "Level $level general skill added ×$skillBonus skill bonus"
            else ""
        val difficultyBranchSkillEffect =
            if (bonusDice > 0) "Level $level general skill added $bonusDice normal dice"
            else ""
        val difficultyBranchSkillEffectMax =
            if (level >= 4 && extraDifficulty >= 4) "Level $level general skill added 1 mastery die because of high difficulty"
            else ""


        val skillEffects = buildList {
            if (thresholdBranchSkillEffectMax.isNotBlank()) add(thresholdBranchSkillEffectMax)
            if (thresholdBranchSkillEffect.isNotBlank()) add(thresholdBranchSkillEffect)
            if (difficultyBranchSkillEffectMax.isNotBlank()) add(difficultyBranchSkillEffectMax)
            if (difficultyBranchSkillEffect.isNotBlank()) add(difficultyBranchSkillEffect)
        }
        return skillEffects.joinToString("\n")
    }

    private fun parseActionBonus(actionBonusDescription: String): Pair<String, Double> {
        val actionBonusEffect = actionBonusDescription[0].toString()
        val actionBonus =
            if (actionBonusEffect != "-") actionBonusDescription.drop(1).toDouble()
            else actionBonusDescription.toDouble()
        return Pair(actionBonusEffect, actionBonus)
    }

    suspend fun setBaseDiff(interaction: GuildChatInputCommandInteraction) {
        if (!interaction.user.isOwner()) {
            interaction.respondPublic {
                content = "This action is only available for the owner"
            }
            return
        }
        val oldBaseDiff = baseDiff
        baseDiff = interaction.command.integers["base_diff"]!!.toInt()
        interaction.respondPublic {
            content = "Set base difficulty from $oldBaseDiff to $baseDiff"
        }
    }
}