package com.example.util

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.service.SkeditAccessibilityService

data class SkeditPermissionsStatus(
  val notificationsGranted: Boolean,
  val accessibilityEnabled: Boolean,
  val batteryOptimizationIgnored: Boolean,
  val exactAlarmsAllowed: Boolean
) {
  val requiredGranted: Boolean
    get() = notificationsGranted && accessibilityEnabled && exactAlarmsAllowed

  val allGranted: Boolean
    get() = requiredGranted && batteryOptimizationIgnored
}

object PermissionHelper {

  fun checkAll(context: Context): SkeditPermissionsStatus {
    return SkeditPermissionsStatus(
      notificationsGranted = hasNotificationPermission(context),
      accessibilityEnabled = isAccessibilityServiceEnabled(context),
      batteryOptimizationIgnored = isBatteryOptimizationIgnored(context),
      exactAlarmsAllowed = canScheduleExactAlarms(context)
    )
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    if (am == null || !am.isEnabled) return false

    val expectedComponent = ComponentName(context, SkeditAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
      val componentName = ComponentName.unflattenFromString(splitter.next())
      if (componentName == expectedComponent) {
        return true
      }
    }
    return false
  }

  fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun canScheduleExactAlarms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
      alarmManager?.canScheduleExactAlarms() ?: true
    } else {
      true
    }
  }

  fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent.resolveActivity(context.packageManager) != null) {
      context.startActivity(intent)
    } else {
      val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(appDetails)
    }
  }

  fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        return
      }
    }
    val fallback = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}")
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(fallback)
  }
}
