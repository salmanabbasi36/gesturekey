package com.gesturekey.app

import android.content.Context

/** Settings in SharedPreferences. Small, local, never synced. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("gesturekey", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val defaults = AppSettings.defaults()
        val defaultTarget = Target(
            app = TargetApp.fromName(prefs.getString(KEY_DEFAULT_APP, null)) ?: defaults.defaultTarget.app,
            phone = prefs.getString(KEY_DEFAULT_PHONE, "") ?: "",
        )
        val gestures = defaults.gestures.map { d ->
            val key = d.gesture.name
            val overrideApp = TargetApp.fromName(prefs.getString("${key}_override_app", null))
            GestureSetting(
                gesture = d.gesture,
                enabled = prefs.getBoolean("${key}_enabled", d.enabled),
                message = prefs.getString("${key}_message", d.message) ?: d.message,
                override = overrideApp?.let { Target(it, prefs.getString("${key}_override_phone", "") ?: "") },
            )
        }
        return AppSettings(defaultTarget, gestures)
    }

    fun saveDefaultTarget(target: Target) {
        prefs.edit()
            .putString(KEY_DEFAULT_APP, target.app.name)
            .putString(KEY_DEFAULT_PHONE, target.phone)
            .apply()
    }

    fun saveGesture(setting: GestureSetting) {
        val key = setting.gesture.name
        val editor = prefs.edit()
            .putBoolean("${key}_enabled", setting.enabled)
            .putString("${key}_message", setting.message)
        val override = setting.override
        if (override == null) {
            editor.remove("${key}_override_app").remove("${key}_override_phone")
        } else {
            editor.putString("${key}_override_app", override.app.name)
                .putString("${key}_override_phone", override.phone)
        }
        editor.apply()
    }

    private companion object {
        const val KEY_DEFAULT_APP = "default_app"
        const val KEY_DEFAULT_PHONE = "default_phone"
    }
}
