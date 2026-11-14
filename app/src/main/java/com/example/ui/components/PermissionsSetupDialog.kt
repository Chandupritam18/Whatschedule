package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.util.SkeditPermissionsStatus

@Composable
fun PermissionsSetupDialog(
  status: SkeditPermissionsStatus,
  onRequestNotifications: () -> Unit,
  onOpenAccessibility: () -> Unit,
  onOpenBatteryOptimization: () -> Unit,
  onOpenExactAlarm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Image(
        painter = painterResource(id = R.drawable.whatschedule_logo),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(56.dp)
      )
    },
    title = {
      Text(
        text = "Permissions Required for Whatschedule",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "You cannot schedule messages without granting the required permissions (Accessibility, Notifications, Alarms). Battery Optimization is optional for extra background reliability:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionRowCard(
          icon = Icons.Default.AccessibilityNew,
          title = "Accessibility Service",
          description = "Assists with automated message sending in WhatsApp, Telegram & SMS.",
          isGranted = status.accessibilityEnabled,
          buttonText = "Enable",
          testTag = "perm_accessibility_button",
          onClick = onOpenAccessibility
        )

        PermissionRowCard(
          icon = Icons.Default.BatteryChargingFull,
          title = "Battery Optimization (Optional)",
          description = "Optional: Prevents Android from delaying background dispatches.",
          isGranted = status.batteryOptimizationIgnored,
          buttonText = "Allow",
          testTag = "perm_battery_button",
          onClick = onOpenBatteryOptimization
        )

        PermissionRowCard(
          icon = Icons.Default.NotificationsActive,
          title = "Notifications",
          description = "Shows 'Ask before sending' prompts and dispatch status alerts.",
          isGranted = status.notificationsGranted,
          buttonText = "Allow",
          testTag = "perm_notifications_button",
          onClick = onRequestNotifications
        )

        PermissionRowCard(
          icon = Icons.Default.Alarm,
          title = "Alarms & Reminders",
          description = "Triggers scheduled messages at the exact minute you choose.",
          isGranted = status.exactAlarmsAllowed,
          buttonText = "Allow",
          testTag = "perm_exact_alarm_button",
          onClick = onOpenExactAlarm
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            !status.notificationsGranted -> onRequestNotifications()
            !status.accessibilityEnabled -> onOpenAccessibility()
            !status.exactAlarmsAllowed -> onOpenExactAlarm()
            else -> onDismiss()
          }
        },
        modifier = Modifier.testTag("perm_grant_next_button")
      ) {
        Text(if (status.requiredGranted) "Continue" else "Grant Next Permission")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("perm_skip_button")
      ) {
        Text("Close")
      }
    }
  )
}

@Composable
fun PermissionRowCard(
  icon: ImageVector,
  title: String,
  description: String,
  isGranted: Boolean,
  buttonText: String,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
              if (isGranted) Color(0xFF008F6F).copy(alpha = 0.16f)
              else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF008F6F) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (isGranted) {
        Surface(
          color = Color(0xFF008F6F).copy(alpha = 0.16f),
          shape = RoundedCornerShape(50)
        ) {
          Text(
            text = "Enabled",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF008F6F),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
          )
        }
      } else {
        FilledTonalButton(
          onClick = onClick,
          modifier = Modifier.testTag(testTag)
        ) {
          Text(buttonText, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}
