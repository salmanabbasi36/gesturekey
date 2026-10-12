package com.gesturekey.app

import java.net.URLEncoder

/** Google's canned MediaPipe gesture classes, each mapped to a default message. */
enum class Gesture(
    val label: String,
    val emoji: String,
    val title: String,
    val defaultMessage: String,
    val defaultEnabled: Boolean,
) {
    LOVE("ILoveYou", "🤟", "I love you", "❤️ I love you", true),
    THUMB_UP("Thumb_Up", "👍", "Thumbs up", "👍 Sounds good!", true),
    VICTORY("Victory", "✌️", "Peace / victory", "✌️ On my way!", true),
    POINTING_UP("Pointing_Up", "☝️", "Pointing up", "☝️ Give me a minute", true),
    THUMB_DOWN("Thumb_Down", "👎", "Thumbs down", "Sorry, can't make it 🙏", true),
    FIST("Closed_Fist", "✊", "Closed fist", "💪 You've got this!", false),
    OPEN_PALM("Open_Palm", "🖐️", "Open palm", "👋 Hi!", false);

    companion object {
        /** MediaPipe's label for "a hand, but no known gesture". */
        const val NONE = "None"

        fun fromLabel(label: String): Gesture? = entries.firstOrNull { it.label == label }
    }
}

/** Where a message goes. The user always taps Send in the target app; Android allows nothing else. */
enum class TargetApp(val label: String, val usesPhone: Boolean, val packages: List<String>) {
    WHATSAPP("WhatsApp", true, listOf("com.whatsapp", "com.whatsapp.w4b")),
    INSTAGRAM("Instagram", false, listOf("com.instagram.android")),
    TELEGRAM("Telegram", false, listOf("org.telegram.messenger", "org.telegram.messenger.web")),
    SMS("SMS", true, emptyList()),
    ANY("Any app", false, emptyList());

    companion object {
        fun fromName(name: String?): TargetApp? = entries.firstOrNull { it.name == name }
    }
}

data class Target(val app: TargetApp, val phone: String = "")

data class GestureSetting(
    val gesture: Gesture,
    val enabled: Boolean,
    val message: String,
    /** Null means "send to the default person". */
    val override: Target?,
)

data class AppSettings(val defaultTarget: Target, val gestures: List<GestureSetting>) {
    val enabledLabels: Set<String>
        get() = gestures.filter { it.enabled && it.message.isNotBlank() }.map { it.gesture.label }.toSet()

    fun forLabel(label: String): GestureSetting? = gestures.firstOrNull { it.gesture.label == label }

    fun targetFor(setting: GestureSetting): Target = setting.override ?: defaultTarget

    companion object {
        fun defaults() = AppSettings(
            defaultTarget = Target(TargetApp.WHATSAPP, ""),
            gestures = Gesture.entries.map { GestureSetting(it, it.defaultEnabled, it.defaultMessage, null) },
        )
    }
}

/**
 * Turns a noisy per-frame classifier into one deliberate trigger: a gesture must be held
 * for [framesNeeded] consecutive frames (score >= [minScore]) and fires exactly once.
 */
class GestureStabilizer(
    private val enabled: Set<String>,
    private val framesNeeded: Int = 8,
    private val minScore: Float = 0.6f,
) {
    var candidate: String? = null
        private set
    var count: Int = 0
        private set

    val progress: Float
        get() = (count.toFloat() / framesNeeded).coerceAtMost(1f)

    /** Returns the gesture label on the frame it becomes stable, otherwise null. */
    fun offer(label: String, score: Float): String? {
        val accepted = if (label in enabled && score >= minScore) label else null
        if (accepted == null) {
            reset()
            return null
        }
        if (accepted == candidate) count++ else {
            candidate = accepted
            count = 1
        }
        return if (count == framesNeeded) accepted else null
    }

    fun reset() {
        candidate = null
        count = 0
    }
}

/** Link building, kept free of Android classes so it is unit-tested on the JVM. */
object MessageLinks {
    /** International number as digits only: "+92 300-123 4567" or "0092 300..." -> "923001234567". */
    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (raw.trim().startsWith("00")) digits.removePrefix("00") else digits
    }

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    fun whatsAppUrl(phone: String, message: String): String =
        "https://api.whatsapp.com/send?phone=${normalizePhone(phone)}&text=${encode(message)}"

    fun smsUri(phone: String): String = "smsto:" + phone.filter { it.isDigit() || it == '+' }
}
