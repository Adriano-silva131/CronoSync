package com.adriano.cronosync.ui.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.adriano.cronosync.R

enum class MissingAlarmPermission {
    Notifications,

    FullScreen,

    XiaomiLockScreen,
}

@Composable
fun rememberMissingAlarmPermission(): MissingAlarmPermission? {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(findMissingAlarmPermission(context)) }
    LifecycleResumeEffect(Unit) {
        missing = findMissingAlarmPermission(context)
        onPauseOrDispose {}
    }
    return missing
}

private fun findMissingAlarmPermission(context: Context): MissingAlarmPermission? {
    val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (!notificationsGranted) return MissingAlarmPermission.Notifications

    val fullScreenGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    if (!fullScreenGranted) return MissingAlarmPermission.FullScreen

    if (isXiaomiLockScreenDenied(context)) return MissingAlarmPermission.XiaomiLockScreen

    return null
}

@SuppressLint("DiscouragedPrivateApi")
// Sem API pública: consulta por reflexão a operação interna da Xiaomi; se falhar, não mostra aviso.
private fun isXiaomiLockScreenDenied(context: Context): Boolean {
    if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return false
    return runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val checkOp = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        val mode = checkOp.invoke(appOps, XIAOMI_OP_SHOW_WHEN_LOCKED, Process.myUid(), context.packageName) as Int
        mode != AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

private const val XIAOMI_OP_SHOW_WHEN_LOCKED = 10020

@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return {}

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun AlarmPermissionBanner(
    missing: MissingAlarmPermission,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(
                    when (missing) {
                        MissingAlarmPermission.Notifications -> R.string.alarm_banner_notifications
                        MissingAlarmPermission.FullScreen -> R.string.alarm_banner_full_screen
                        MissingAlarmPermission.XiaomiLockScreen -> R.string.alarm_banner_xiaomi_lock_screen
                    },
                ),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { openSettings(context, missing) }) {
                Text(stringResource(R.string.action_allow))
            }
        }
    }
}

private fun settingsIntent(context: Context, missing: MissingAlarmPermission): Intent = when {
    missing == MissingAlarmPermission.XiaomiLockScreen -> xiaomiPermissionsIntent(context)

    missing == MissingAlarmPermission.FullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
}

private fun openSettings(context: Context, missing: MissingAlarmPermission) {
    try {
        context.startActivity(settingsIntent(context, missing))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}

private fun xiaomiPermissionsIntent(context: Context): Intent =
    Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", context.packageName)
