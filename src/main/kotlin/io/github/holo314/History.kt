package io.github.holo314

import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.GuildChatInputCommandInteraction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class History(private val history: File, var zoneLevelMapping: HashMap<String, Int> = HashMap()) {
    init {
        if (!history.exists()) {
            history.createNewFile()
            history.writeText("{}")
        }
        val content = history.readText()
        zoneLevelMapping = Json.decodeFromString(content)
    }

    fun save() {
        history.writeText(Json.encodeToString(zoneLevelMapping))
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
