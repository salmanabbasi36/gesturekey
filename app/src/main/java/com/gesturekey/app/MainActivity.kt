@file:OptIn(ExperimentalMaterial3Api::class)

package com.gesturekey.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings: who to send to, and what each gesture says. Saved on every change. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        store = store,
                        onAddWidget = { pinWidget() },
                        onTry = { startActivity(Intent(this, CaptureActivity::class.java)) },
                    )
                }
            }
        }
    }

    private fun pinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, GestureWidget::class.java), null, null)
        } else {
            Toast.makeText(
                this,
                "Long-press your home screen, tap Widgets, and drag GestureKey onto it",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}

@Composable
private fun SettingsScreen(store: SettingsStore, onAddWidget: () -> Unit, onTry: () -> Unit) {
    var settings by remember { mutableStateOf(store.load()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(onAddWidget, onTry) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Send to", style = MaterialTheme.typography.titleMedium)
                    TargetEditor(settings.defaultTarget) { target ->
                        store.saveDefaultTarget(target)
                        settings = settings.copy(defaultTarget = target)
                    }
                }
            }
        }
        item {
            Text(
                "Gestures",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(settings.gestures, key = { it.gesture.name }) { setting ->
            GestureCard(setting) { updated ->
                store.saveGesture(updated)
                settings = settings.copy(
                    gestures = settings.gestures.map { if (it.gesture == updated.gesture) updated else it },
                )
            }
        }
        item {
            Text(
                "The camera only runs while the gesture screen is open. Frames are processed on your " +
                    "phone and never saved or uploaded. Your chat app opens with the message ready; you tap Send.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Header(onAddWidget: () -> Unit, onTry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GestureKey", style = MaterialTheme.typography.headlineMedium)
        Text(
            "1. Add the widget to your home screen.\n" +
                "2. Tap it: the front camera opens.\n" +
                "3. Hold a gesture for half a second, e.g. 🤟.\n" +
                "4. Your chat opens with the message ready. Tap Send.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddWidget) { Text("Add widget") }
            OutlinedButton(onClick = onTry) { Text("Try it now") }
        }
    }
}

@Composable
private fun TargetEditor(target: Target, onChange: (Target) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TargetApp.entries.forEach { app ->
                FilterChip(
                    selected = target.app == app,
                    onClick = { onChange(target.copy(app = app)) },
                    label = { Text(app.label) },
                )
            }
        }
        if (target.app.usesPhone) {
            OutlinedTextField(
                value = target.phone,
                onValueChange = { onChange(target.copy(phone = it)) },
                label = { Text("Phone number with country code") },
                placeholder = { Text("+92 300 1234567") },
                supportingText = {
                    Text(
                        if (target.app == TargetApp.WHATSAPP) "Leave empty to pick a chat each time"
                        else "Leave empty to pick a contact each time",
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (target.app == TargetApp.INSTAGRAM) {
            Text(
                "Instagram opens its share screen: pick the person. The message is also copied, in case you need to paste it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GestureCard(setting: GestureSetting, onChange: (GestureSetting) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(setting.gesture.emoji, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    setting.gesture.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = setting.enabled, onCheckedChange = { onChange(setting.copy(enabled = it)) })
            }
            if (setting.enabled) {
                OutlinedTextField(
                    value = setting.message,
                    onValueChange = { onChange(setting.copy(message = it)) },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Send to someone else", modifier = Modifier.weight(1f))
                    Switch(
                        checked = setting.override != null,
                        onCheckedChange = { on ->
                            onChange(setting.copy(override = if (on) Target(TargetApp.WHATSAPP) else null))
                        },
                    )
                }
                setting.override?.let { override ->
                    TargetEditor(override) { onChange(setting.copy(override = it)) }
                }
            }
        }
    }
}
