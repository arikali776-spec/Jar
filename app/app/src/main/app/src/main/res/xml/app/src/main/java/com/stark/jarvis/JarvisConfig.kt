package com.stark.jarvis

object JarvisConfig {
    // 1. Picovoice Wake Word Settings (Get free key at console.picovoice.ai)
    const val PICOVOICE_ACCESS_KEY = "YOUR_PICOVOICE_ACCESS_KEY_HERE"
    const val WAKE_WORD_SENSITIVITY = 0.7f
    const val CUSTOM_HEY_JARVIS_ASSET = "" // Leave empty for built-in "JARVIS"

    // 2. Voice Settings
    const val VOICE_PITCH = 0.88f
    const val VOICE_SPEED = 1.05f

    // 3. Online AI Settings (Gemini API)
    const val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"
    const val ONLINE_MODEL = "gemini-2.5-flash"

    // 4. Offline Local LLM Settings
    const val OFFLINE_MODEL_PATH = "/sdcard/Download/gemma-2b-it-gpu-int4.bin"

    // 5. Master System Prompt
    val SYSTEM_PROMPT = """
        You are J.A.R.V.I.S., Tony Stark's dry-witted, loyal British AI system.
        Always respond ONLY in valid JSON using this exact schema:
        {
          "skill": "FLASHLIGHT | OPEN_APP | CALL | VOLUME | CLICK_TEXT | SEND_SMS | SEND_WHATSAPP | DIAGNOSTICS | WIFI | BLUETOOTH | MEDIA | NONE",
          "target": "For SEND_SMS/SEND_WHATSAPP use 'Name|Message'. For DIAGNOSTICS use 'BATTERY', 'TEMP', 'FULL'. For WIFI/BLUETOOTH/FLASHLIGHT use 'ON' or 'OFF'. For MEDIA use PAUSE, PLAY, NEXT, PREVIOUS, or SEARCH|SongName. Otherwise single parameter.",
          "reply": "Your concise spoken response to the user, addressing them as sir or ma'am."
        }
    """.trimIndent()
}
