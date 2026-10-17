package com.stark.jarvis.skills

import android.content.Context

data class AiActionResponse(
    val skill: String = "NONE",
    val target: String = "",
    val reply: String = "Right away, sir."
)

interface JarvisSkill {
    val skillId: String
    fun matchSpokenText(spokenText: String): AiActionResponse?
    fun execute(context: Context, target: String): Boolean

    fun resolveDynamicReply(context: Context, target: String, defaultReply: String): String {
        return defaultReply
    }
}
