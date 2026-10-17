package com.stark.jarvis.skills

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.stark.jarvis.automation.JarvisAccessibilityService

object SkillRegistry {

    val skills = listOf<JarvisSkill>(
        FlashlightSkill(),
        OpenAppSkill(),
        PhoneCallSkill(),
        VolumeControlSkill(),
        ScreenClickSkill(),
        SmsSkill(),
        WhatsAppSkill(),
        DiagnosticsSkill(),
        WifiSkill(),
        BluetoothSkill(),
        MediaControlSkill()
    )

    fun findInstantOfflineMatch(spokenText: String): AiActionResponse? {
        for (skill in skills) {
            val match = skill.matchSpokenText(spokenText)
            if (match != null) return match
        }
        return null
    }

    fun executeSkill(context: Context, action: AiActionResponse): String {
        val matchedSkill = skills.find { it.skillId.equals(action.skill, ignoreCase = true) }
        matchedSkill?.execute(context, action.target)
        return matchedSkill?.resolveDynamicReply(context, action.target, action.reply) ?: action.reply
    }
}

// --- 1. FLASHLIGHT ---
class FlashlightSkill : JarvisSkill {
    override val skillId = "FLASHLIGHT"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        return when {
            "flashlight on" in spokenText || "torch on" in spokenText ->
                AiActionResponse(skillId, "ON", "Auxiliary lighting activated, sir.")
            "flashlight off" in spokenText || "torch off" in spokenText ->
                AiActionResponse(skillId, "OFF", "Turning off auxiliary lighting.")
            else -> null
        }
    }
    override fun execute(context: Context, target: String): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cm.cameraIdList.firstOrNull() ?: return false
            cm.setTorchMode(camId, target.equals("ON", ignoreCase = true))
            true
        } catch (e: Exception) { false }
    }
}

// --- 2. LAUNCH APP ---
class OpenAppSkill : JarvisSkill {
    override val skillId = "OPEN_APP"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        if (spokenText.startsWith("open ") || spokenText.startsWith("launch ")) {
            val appName = spokenText.replaceFirst("open ", "").replaceFirst("launch ", "").trim()
            return AiActionResponse(skillId, appName, "Launching $appName, sir.")
        }
        return null
    }
    override fun execute(context: Context, target: String): Boolean {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(0)
        val matchedApp = installed.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(target.lowercase())
        } ?: return false
        val intent = pm.getLaunchIntentForPackage(matchedApp.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        context.startActivity(intent)
        return true
    }
}

// --- 3. PHONE CALL ---
class PhoneCallSkill : JarvisSkill {
    override val skillId = "CALL"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        if (spokenText.startsWith("call ") || spokenText.startsWith("dial ")) {
            val digits = spokenText.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                return AiActionResponse(skillId, digits, "Dialing $digits now, sir.")
            }
        }
        return null
    }
    override fun execute(context: Context, target: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$target")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }
}

// --- 4. VOLUME ---
class VolumeControlSkill : JarvisSkill {
    override val skillId = "VOLUME"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        return when {
            "mute phone" in spokenText || "silence phone" in spokenText ->
                AiActionResponse(skillId, "MUTE", "Engaging stealth mode, sir.")
            "max volume" in spokenText || "full volume" in spokenText ->
                AiActionResponse(skillId, "MAX", "Audio output set to maximum.")
            else -> null
        }
    }
    override fun execute(context: Context, target: String): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (target == "MUTE") am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        else if (target == "MAX") am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        return true
    }
}

// --- 5. SCREEN CLICK ---
class ScreenClickSkill : JarvisSkill {
    override val skillId = "CLICK_TEXT"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        if (spokenText.startsWith("click ") || spokenText.startsWith("tap ")) {
            val btn = spokenText.replaceFirst("click ", "").replaceFirst("tap ", "").trim()
            return AiActionResponse(skillId, btn, "Tapping $btn.")
        }
        return null
    }
    override fun execute(context: Context, target: String): Boolean {
        return JarvisAccessibilityService.instance?.clickTextOnScreen(target) ?: false
    }
}

// --- CONTACT HELPER ---
object ContactHelper {
    private const val DEFAULT_COUNTRY_CODE = "91"
    fun resolvePhoneNumber(context: Context, nameOrNumber: String, forWhatsApp: Boolean = false): String? {
        val cleanedDigits = nameOrNumber.filter { it.isDigit() }
        if (cleanedDigits.length >= 7) return formatNumber(cleanedDigits, forWhatsApp)
        val resolver = context.contentResolver
        val cursor = resolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%${nameOrNumber.trim()}%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val rawNumber = it.getString(1).filter { ch -> ch.isDigit() || ch == '+' }
                return formatNumber(rawNumber, forWhatsApp)
            }
        }
        return null
    }
    private fun formatNumber(raw: String, forWhatsApp: Boolean): String {
        if (!forWhatsApp) return raw
        val digitsOnly = raw.filter { it.isDigit() }
        return if (digitsOnly.length == 10) "$DEFAULT_COUNTRY_CODE$digitsOnly" else digitsOnly
    }
}

// --- 6. SMS ---
class SmsSkill : JarvisSkill {
    override val skillId = "SEND_SMS"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        val regex = Regex("""(?:send sms to|text)\s+(.+?)\s+(?:saying|that)\s+(.+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(spokenText) ?: return null
        return AiActionResponse(skillId, "${match.groupValues[1].trim()}|${match.groupValues[2].trim()}", "Sending text message, sir.")
    }
    override fun execute(context: Context, target: String): Boolean {
        val parts = target.split("|", limit = 2)
        if (parts.size < 2) return false
        val number = ContactHelper.resolvePhoneNumber(context, parts[0].trim(), false) ?: return false
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            sms.sendTextMessage(number, null, parts[1].trim(), null, null)
            true
        } catch (e: Exception) { false }
    }
}

// --- 7. WHATSAPP ---
class WhatsAppSkill : JarvisSkill {
    override val skillId = "SEND_WHATSAPP"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        val regex = Regex("""(?:send whatsapp to|whatsapp)\s+(.+?)\s+(?:saying|that)\s+(.+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(spokenText) ?: return null
        return AiActionResponse(skillId, "${match.groupValues[1].trim()}|${match.groupValues[2].trim()}", "Dispatching WhatsApp transmission, sir.")
    }
    override fun execute(context: Context, target: String): Boolean {
        val parts = target.split("|", limit = 2)
        if (parts.size < 2) return false
        val number = ContactHelper.resolvePhoneNumber(context, parts[0].trim(), true) ?: return false
        return try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(parts[1].trim())}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Handler(Looper.getMainLooper()).postDelayed({
                JarvisAccessibilityService.instance?.clickTextOnScreen("Send")
            }, 1500)
            true
        } catch (e: Exception) { false }
    }
}

// --- 8. DIAGNOSTICS ---
class DiagnosticsSkill : JarvisSkill {
    override val skillId = "DIAGNOSTICS"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        return when {
            "battery" in spokenText || "power level" in spokenText -> AiActionResponse(skillId, "BATTERY", "")
            "temperature" in spokenText || "how hot" in spokenText -> AiActionResponse(skillId, "TEMP", "")
            "system status" in spokenText || "diagnostics" in spokenText -> AiActionResponse(skillId, "FULL", "")
            else -> null
        }
    }
    override fun execute(context: Context, target: String): Boolean = true
    override fun resolveDynamicReply(context: Context, target: String, defaultReply: String): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Telemetry error, sir."
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        }
        val powerState = if (charging) "connected to external power" else "running on internal cells"
        return when (target.uppercase()) {
            "BATTERY" -> "Power reserves at $pct percent and $powerState, sir."
            "TEMP" -> "Core internal temperature is $temp degrees Celsius, sir."
            else -> "All systems nominal. Power reserves at $pct percent ($powerState), thermal reading at $temp degrees Celsius."
        }
    }
}

// --- 9. WIFI ---
class WifiSkill : JarvisSkill {
    override val skillId = "WIFI"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        return when {
            "wifi on" in spokenText || "wi-fi on" in spokenText -> AiActionResponse(skillId, "ON", "Engaging wireless network interface, sir.")
            "wifi off" in spokenText || "wi-fi off" in spokenText -> AiActionResponse(skillId, "OFF", "Disengaging wireless network interface, sir.")
            else -> null
        }
    }
    @Suppress("DEPRECATION")
    override fun execute(context: Context, target: String): Boolean {
        val turnOn = target.equals("ON", ignoreCase = true)
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wifi.isWifiEnabled = turnOn
            } else {
                context.startActivity(Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                Handler(Looper.getMainLooper()).postDelayed({ JarvisAccessibilityService.instance?.clickTextOnScreen("Wi-Fi") }, 900)
            }
            true
        } catch (e: Exception) { false }
    }
}

// --- 10. BLUETOOTH ---
class BluetoothSkill : JarvisSkill {
    override val skillId = "BLUETOOTH"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        return when {
            "bluetooth on" in spokenText -> AiActionResponse(skillId, "ON", "Initializing Bluetooth transceiver, sir.")
            "bluetooth off" in spokenText -> AiActionResponse(skillId, "OFF", "Deactivating Bluetooth transceiver, sir.")
            else -> null
        }
    }
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun execute(context: Context, target: String): Boolean {
        val turnOn = target.equals("ON", ignoreCase = true)
        return try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (turnOn) adapter.enable() else adapter.disable()
            } else {
                if (turnOn && !adapter.isEnabled) {
                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    Handler(Looper.getMainLooper()).postDelayed({ JarvisAccessibilityService.instance?.clickTextOnScreen("Allow") }, 900)
                } else if (!turnOn && adapter.isEnabled) {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
            }
            true
        } catch (e: Exception) { false }
    }
}

// --- 11. MEDIA ---
class MediaControlSkill : JarvisSkill {
    override val skillId = "MEDIA"
    override fun matchSpokenText(spokenText: String): AiActionResponse? {
        return when {
            spokenText == "pause" || "pause music" in spokenText -> AiActionResponse(skillId, "PAUSE", "Pausing media, sir.")
            spokenText == "resume" || "play music" in spokenText -> AiActionResponse(skillId, "PLAY", "Resuming playback, sir.")
            "next song" in spokenText || "skip track" in spokenText -> AiActionResponse(skillId, "NEXT", "Skipping track, sir.")
            "previous song" in spokenText -> AiActionResponse(skillId, "PREVIOUS", "Previous track.")
            spokenText.startsWith("play ") -> AiActionResponse(skillId, "SEARCH|${spokenText.removePrefix("play ").trim()}", "Playing music, sir.")
            else -> null
        }
    }
    override fun execute(context: Context, target: String): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (target.uppercase()) {
            "PAUSE" -> { am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)); am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)); return true }
            "PLAY" -> { am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)); am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)); return true }
            "NEXT" -> { am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)); am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)); return true }
            "PREVIOUS" -> { am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)); am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)); return true }
        }
        if (target.startsWith("SEARCH|")) {
            return try {
                context.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, target.removePrefix("SEARCH|"))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (e: Exception) { false }
        }
        return false
    }
}
