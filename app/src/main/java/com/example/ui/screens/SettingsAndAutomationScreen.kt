package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.ui.UiState
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.components.PermissionRowCard
import com.example.util.ChannelDispatcher
import com.example.util.SkeditPermissionsStatus
import com.google.firebase.auth.FirebaseUser

@Composable
fun SettingsAndAutomationScreen(
  currentUser: FirebaseUser?,
  schedulesState: UiState<List<ScheduleItem>>,
  permissionsStatus: SkeditPermissionsStatus,
  isDarkMode: Boolean,
  onToggleDarkMode: (Boolean) -> Unit,
  defaultAskBeforeSending: Boolean,
  defaultNotifyOnComplete: Boolean,
  onToggleDefaultAskBeforeSending: (Boolean) -> Unit,
  onToggleDefaultNotifyOnComplete: (Boolean) -> Unit,
  onRequestNotifications: () -> Unit,
  onOpenAccessibility: () -> Unit,
  onOpenBatteryOptimization: () -> Unit,
  onOpenExactAlarm: () -> Unit,
  onOpenPermissionsDialog: () -> Unit,
  onLoadStarterKit: () -> Unit,
  onSignOut: () -> Unit,
  onAuthSuccess: () -> Unit,
  onShowFeedback: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isSigningIn by remember { mutableStateOf(false) }

  val schedules = (schedulesState as? UiState.Success)?.data ?: emptyList()
  val hasNotificationPermission = permissionsStatus.notificationsGranted

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("settings_automation_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Account & Storage Mode Card (Works without login + optional Google Cloud Sync)
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
              painter = painterResource(id = R.drawable.whatschedule_logo),
              contentDescription = stringResource(R.string.app_name),
              contentScale = ContentScale.Fit,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (currentUser != null) {
                  currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Cloud Synced Account"
                } else {
                  "Instant Local Mode (No Login Required)"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = if (currentUser != null) {
                  currentUser.email ?: "Synced with Cloud Firestore"
                } else {
                  "All schedules, templates & groups are saved on your device. Sign in optionally for Cloud Backup."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            OutlinedButton(
              onClick = onLoadStarterKit,
              modifier = Modifier.weight(1f)
            ) {
              Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Reset Samples")
            }

            if (currentUser != null) {
              Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("sign_out_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Logout,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_sign_out))
              }
            } else {
              Button(
                onClick = {
                  isSigningIn = true
                  onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                      isSigningIn = false
                      onAuthSuccess()
                      onShowFeedback("Signed in with Google for Cloud Sync!")
                    },
                    onAuthError = { err ->
                      isSigningIn = false
                      onShowFeedback(err)
                    },
                    scope = scope,
                    onAuthCancelled = {
                      isSigningIn = false
                    }
                  )
                },
                enabled = !isSigningIn,
                modifier = Modifier
                  .weight(1f)
                  .testTag("settings_google_sign_in_button")
              ) {
                if (isSigningIn) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.sign_in_with_google))
                }
              }
            }
          }
        }
      }
    }

    // 1B. Required System Permissions Card
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("settings_permissions_card")
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Required System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }
            OutlinedButton(onClick = onOpenPermissionsDialog) {
              Text("Setup Guide")
            }
          }

          PermissionRowCard(
            icon = Icons.Default.AccessibilityNew,
            title = "Accessibility Service",
            description = "Assists with automated message sending across WhatsApp, Telegram & SMS.",
            isGranted = permissionsStatus.accessibilityEnabled,
            buttonText = "Enable",
            testTag = "settings_perm_accessibility_button",
            onClick = onOpenAccessibility
          )

          PermissionRowCard(
            icon = Icons.Default.BatteryChargingFull,
            title = "Battery Optimization (Optional)",
            description = "Optional: Prevents Android from delaying background message schedules.",
            isGranted = permissionsStatus.batteryOptimizationIgnored,
            buttonText = "Allow",
            testTag = "settings_perm_battery_button",
            onClick = onOpenBatteryOptimization
          )

          PermissionRowCard(
            icon = Icons.Default.NotificationsActive,
            title = "Notifications",
            description = "Shows 'Ask before sending' prompts and dispatch status alerts.",
            isGranted = permissionsStatus.notificationsGranted,
            buttonText = "Allow",
            testTag = "settings_perm_notifications_button",
            onClick = onRequestNotifications
          )

          PermissionRowCard(
            icon = Icons.Default.Alarm,
            title = "Alarms & Reminders",
            description = "Triggers scheduled messages at the exact selected minute.",
            isGranted = permissionsStatus.exactAlarmsAllowed,
            buttonText = "Allow",
            testTag = "settings_perm_exact_alarm_button",
            onClick = onOpenExactAlarm
          )
        }
      }
    }

    // 2. Automation Defaults & Notification Engine
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Text(
            text = "Whatschedule Appearance & Automation Defaults",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Dark Mode",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "Light mode is enabled by default; toggle for dark theme",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = isDarkMode,
              onCheckedChange = onToggleDarkMode,
              modifier = Modifier.testTag("dark_mode_switch")
            )
          }

          HorizontalDivider()

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Default 'Ask me before sending'",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "Pre-enable confirmation dialog for all newly created schedules",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = defaultAskBeforeSending,
              onCheckedChange = onToggleDefaultAskBeforeSending
            )
          }

          HorizontalDivider()

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Default Notification Alert",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "Post status bar alert when scheduled time is reached",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = defaultNotifyOnComplete,
              onCheckedChange = onToggleDefaultNotifyOnComplete
            )
          }

          HorizontalDivider()

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (hasNotificationPermission) {
                  "System Notifications: Granted"
                } else {
                  "System Notifications: Permission Required"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "Test or enable Android status bar notifications for due schedules",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
              onClick = {
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  onRequestNotifications()
                } else {
                  ChannelDispatcher.postScheduleNotification(
                    context = context,
                    title = "Whatschedule Dispatch Ready",
                    body = "Test notification: Your scheduled automation channel is active!",
                    notificationId = 9002
                  )
                  onShowFeedback("Sent test notification alert!")
                }
              }
            ) {
              Text(if (hasNotificationPermission) "Test Alert" else "Grant")
            }
          }
        }
      }
    }

    // 3. Channel Breakdown Analytics
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "Channel Volume Breakdown (${schedules.size} total)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )

          ScheduleChannel.entries.forEach { channel ->
            val count = schedules.count { it.channelEnum == channel }
            val ratio = if (schedules.isNotEmpty()) count.toFloat() / schedules.size.toFloat() else 0f
            val accent = ChannelDispatcher.channelColor(channel)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = ChannelDispatcher.channelIcon(channel),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                  )
                }
                Text(
                  text = "$count schedules",
                  style = MaterialTheme.typography.labelMedium,
                  color = accent
                )
              }
              LinearProgressIndicator(
                progress = { ratio },
                color = accent,
                trackColor = accent.copy(alpha = 0.16f),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(7.dp)
                  .clip(RoundedCornerShape(4.dp))
              )
            }
          }
        }
      }
    }
  }
}
