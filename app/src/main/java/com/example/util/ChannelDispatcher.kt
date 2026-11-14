package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.ui.theme.CallAmber
import com.example.ui.theme.EmailCoral
import com.example.ui.theme.SmsIndigo
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.WhatsAppBusinessTeal
import com.example.ui.theme.WhatsAppGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChannelDispatcher {

  private const val NOTIFICATION_CHANNEL_ID = "skedit_schedule_alerts"

  fun channelColor(channel: ScheduleChannel): Color = when (channel) {
    ScheduleChannel.WHATSAPP -> WhatsAppGreen
    ScheduleChannel.WHATSAPP_BUSINESS -> WhatsAppBusinessTeal
    ScheduleChannel.TELEGRAM -> TelegramBlue
    ScheduleChannel.SMS -> SmsIndigo
    ScheduleChannel.EMAIL -> EmailCoral
    ScheduleChannel.CALL -> CallAmber
  }

  fun channelIcon(channel: ScheduleChannel): ImageVector = when (channel) {
    ScheduleChannel.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    ScheduleChannel.WHATSAPP_BUSINESS -> Icons.Default.BusinessCenter
    ScheduleChannel.TELEGRAM -> Icons.AutoMirrored.Filled.Send
    ScheduleChannel.SMS -> Icons.Default.Sms
    ScheduleChannel.EMAIL -> Icons.Default.Email
    ScheduleChannel.CALL -> Icons.Default.Call
  }

  fun formatDateTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Not scheduled"
    val formatter = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis))
  }

  fun formatCountdown(targetMillis: Long, currentMillis: Long): String {
    val diff = targetMillis - currentMillis
    if (diff <= 0L) return "Due now"
    val totalSeconds = diff / 1000L
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
      days > 0L -> String.format(Locale.US, "%dd %02dh %02dm", days, hours, minutes)
      hours > 0L -> String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
      else -> String.format(Locale.US, "%02dm %02ds", minutes, seconds)
    }
  }

  fun dispatchScheduleIntent(context: Context, schedule: ScheduleItem): String {
    val firstTarget = schedule.recipientTarget.split(",").firstOrNull()?.trim().orEmpty()
    val cleanPhone = firstTarget.replace(Regex("[^0-9+]"), "")
    val encodedText = Uri.encode(schedule.messageBody)
    return try {
      when (schedule.channelEnum) {
        ScheduleChannel.WHATSAPP -> {
          val uri = if (cleanPhone.isNotBlank()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
          } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
          }
          val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            "Opened WhatsApp for ${schedule.recipientName}"
          } else {
            val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            "Dispatched WhatsApp schedule for ${schedule.recipientName}"
          }
        }

        ScheduleChannel.WHATSAPP_BUSINESS -> {
          val uri = if (cleanPhone.isNotBlank()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
          } else {
            Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
          }
          val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp.w4b")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            "Opened WA Business for ${schedule.recipientName}"
          } else {
            copyToClipboard(context, schedule.messageBody)
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            "Dispatched WA Business schedule for ${schedule.recipientName}"
          }
        }

        ScheduleChannel.TELEGRAM -> {
          val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, schedule.messageBody)
            setPackage("org.telegram.messenger")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          if (shareIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(shareIntent)
            "Opened Telegram for ${schedule.recipientName}"
          } else {
            val handle = schedule.recipientTarget.trim().removePrefix("@")
            val tgUri = if (handle.isNotBlank()) {
              Uri.parse("https://t.me/$handle?text=$encodedText")
            } else {
              Uri.parse("https://t.me/share/url?url=&text=$encodedText")
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, tgUri).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Dispatched Telegram message for ${schedule.recipientName}"
          }
        }

        ScheduleChannel.SMS -> {
          val smsUri = Uri.parse("smsto:${schedule.recipientTarget}")
          val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", schedule.messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(smsIntent)
          "Opened SMS composer for ${schedule.recipientName}"
        }

        ScheduleChannel.EMAIL -> {
          val mailUri = Uri.parse(
            "mailto:${Uri.encode(schedule.recipientTarget)}" +
              "?subject=${Uri.encode(schedule.subject.ifBlank { "Scheduled via Whatschedule" })}" +
              "&body=$encodedText"
          )
          val emailIntent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          if (emailIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(emailIntent)
          } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "message/rfc822"
              putExtra(Intent.EXTRA_EMAIL, arrayOf(schedule.recipientTarget))
              putExtra(Intent.EXTRA_SUBJECT, schedule.subject.ifBlank { "Scheduled via Whatschedule" })
              putExtra(Intent.EXTRA_TEXT, schedule.messageBody)
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Send Email").apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
          }
          "Opened Email client for ${schedule.recipientName}"
        }

        ScheduleChannel.CALL -> {
          val dialUri = Uri.parse("tel:${cleanPhone.ifBlank { schedule.recipientTarget }}")
          val dialIntent = Intent(Intent.ACTION_DIAL, dialUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(dialIntent)
          "Opened Phone Dialer for ${schedule.recipientName}"
        }
      }
    } catch (e: Exception) {
      copyToClipboard(context, schedule.messageBody)
      "Message copied to clipboard & marked sent for ${schedule.recipientName}"
    }
  }

  fun postScheduleNotification(context: Context, title: String, body: String, notificationId: Int) {
    ensureNotificationChannel(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }
    try {
      val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
      NotificationManagerCompat.from(context).notify(notificationId, notification)
    } catch (ignored: SecurityException) {
    }
  }

  private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Whatschedule Scheduled Alerts",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Notifications when scheduled messages, emails, or calls are triggered"
      }
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      manager?.createNotificationChannel(channel)
    }
  }

  private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Whatschedule Message", text))
  }

  fun isWhatsAppInstalled(context: Context, channel: ScheduleChannel): Boolean {
    val pkg = if (channel == ScheduleChannel.WHATSAPP_BUSINESS) {
      "com.whatsapp.w4b"
    } else {
      "com.whatsapp"
    }
    return try {
      context.packageManager.getLaunchIntentForPackage(pkg) != null
    } catch (_: Exception) {
      false
    }
  }

  fun launchWhatsAppForSelection(context: Context, channel: ScheduleChannel): Boolean {
    val preferredPkg = if (channel == ScheduleChannel.WHATSAPP_BUSINESS) {
      "com.whatsapp.w4b"
    } else {
      "com.whatsapp"
    }
    val launchIntent = context.packageManager.getLaunchIntentForPackage(preferredPkg)
      ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp")
      ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
      ?: return false
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
      context.startActivity(launchIntent)
      true
    } catch (_: Exception) {
      false
    }
  }
}
