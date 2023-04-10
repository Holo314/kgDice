package io.github.holo314

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class KgResult(
    val diceResult: List<Int>,
    val masteries: List<Int>,
    val tens: Int,
    val score: Int,
    val zoneLevel: Int,
    val difficulty: Int
)

data class RollRequest(val diceNumber: Int, val difficulty: Int, val zoneLevel: Int, val masteryNumber: Int)


fun kgRoll(diceNo: Int, diff: Int, zoneLevel: Int, skillLevel: Int): KgResult =
    if (skillLevel <= 10) {
        val request = preKgRollGeneralSkill(diceNo, diff, skillLevel, zoneLevel)
        val base = challenge(request)
        postKgRollGeneralSkill(base, skillLevel)
    } else {
        val request = preKgRollSpecificSkill(diceNo, diff, skillLevel - 10, zoneLevel)
        val base = challenge(request)
        postKgRollSpecificSkill(base, skillLevel - 10)
    }

fun preKgRollSpecificSkill(diceNo: Int, diff: Int, skillLevel: Int, zoneLevel: Int): RollRequest {
    var difficulty = diff
    var masteryNumber = 0
    if (skillLevel >= 2) {
        difficulty -= 1
    }
    if (skillLevel >= 4) {
        masteryNumber = 1
    }
    difficulty = max(3, difficulty)
    return RollRequest(diceNo, difficulty, zoneLevel, masteryNumber)
}

fun postKgRollSpecificSkill(baseResult: KgResult, skillLevel: Int): KgResult {
    var score = baseResult.score
    if (skillLevel >= 3) {
        if (score < 0) {
            score = 1
        }
    }

    return baseResult.copy(score = score)
}

fun preKgRollGeneralSkill(diceNo: Int, diff: Int, skillLevel: Int, zoneLevel: Int): RollRequest {
    var newDiceNum = diceNo
    var masteryNum = 0
    var newDiff = diff

    if (skillLevel >= 9) {
        masteryNum += 1
    }
    if (skillLevel >= 8) {
        newDiceNum += 1
    }
    if (skillLevel >= 7) {
        newDiceNum += 1
    }
    if (skillLevel >= 4) {
        newDiff -= 1
    }
    if (skillLevel >= 3) {
        newDiff -= 1
    }
    newDiff = max(3, newDiff)
    return RollRequest(newDiceNum, newDiff, zoneLevel, masteryNum)
}

fun postKgRollGeneralSkill(baseResult: KgResult, skillLevel: Int): KgResult {
    var score = baseResult.score
    if (skillLevel >= 6) {
        score += 1
    }
    if (skillLevel >= 2) {
        if (score > 1) {
            score += 1
        }
    }
    if (skillLevel >= 5) {
        if (score < 0) {
            score = 1
        }
    }

    return baseResult.copy(score = score)
}


fun challenge(request: RollRequest): KgResult {
    var difficulty = request.difficulty
    if (request.zoneLevel > 0) {
        difficulty -= 1
    }
    val masteries = roll(request.masteryNumber, difficulty)
    val nums = roll(request.diceNumber)
    val tens = nums.count { it == 10 } + masteries.count { it == 10 }
    val oScore = nums.sumOf { worth(difficulty, it) } +
            (if (request.zoneLevel >= 3) tens else 0) +
            (if (difficulty <= 10) request.masteryNumber else 0)

    val score =
        if (oScore < 1 && request.zoneLevel > 0)
            1
        else if (request.zoneLevel > 0 || (oScore > 1 && tens > 1))
            oScore + 1
        else
            oScore

    val zLevel =
        if (request.zoneLevel == 0 && score > 1 && tens > 1) {
            1
        } else if (request.zoneLevel in 1..score) {
            request.zoneLevel + 1
        } else {
            0
        }

    return KgResult(nums, masteries, tens, score, zLevel, difficulty)
}

fun worth(diff: Int, i: Int): Int =
    if (i < diff) {
        if (i == 1) -1 else 0
    } else {
        1
    }

fun roll(diceNo: Int, start: Int = 1): List<Int> {
    return List(diceNo) { Random.nextInt(min(10, start), 11) }
}