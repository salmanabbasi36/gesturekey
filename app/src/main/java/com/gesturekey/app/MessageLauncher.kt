package com.gesturekey.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the chosen app with the message ready. Uses only official, user-confirmed paths
 * (deep links and the share sheet): the user taps Send. No accessibility tricks, ever.
 */
object MessageLauncher {

    /** Returns a short note for the user when we had to fall back, otherwise null. */
    fun launch(context: Context, target: Target, message: String): String? {
        val phone = MessageLinks.normalizePhone(target.phone)
        val candidates: List<Intent> = when (target.app) {
            TargetApp.WHATSAPP ->
                if (phone.length >= 8) {
                    val url = Uri.parse(MessageLinks.whatsAppUrl(phone, message))
                    target.app.packages.map { Intent(Intent.ACTION_VIEW, url).setPackage(it) } +
                        Intent(Intent.ACTION_VIEW, url)
                } else {
                    target.app.packages.map { shareText(message).setPackage(it) }
                }
            TargetApp.INSTAGRAM, TargetApp.TELEGRAM -> {
                // Instagram may ignore shared text on some versions; keep it on the clipboard too.
                if (target.app == TargetApp.INSTAGRAM) copyToClipboard(context, message)
                target.app.packages.map { shareText(message).setPackage(it) }
            }
            TargetApp.SMS -> listOf(
                Intent(Intent.ACTION_SENDTO, Uri.parse(MessageLinks.smsUri(target.phone)))
                    .putExtra("sms_body", message),
            )
            TargetApp.ANY -> emptyList()
        }

        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return null
            } catch (e: ActivityNotFoundException) {
                // Try the next candidate.
            }
        }

        val chooser = Intent.createChooser(shareText(message), "Send with…")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return if (target.app == TargetApp.ANY) null else "${target.app.label} isn't installed, so pick an app"
    }

    private fun shareText(message: String) = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, message)

    private fun copyToClipboard(context: Context, message: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("GestureKey message", message))
    }
}
