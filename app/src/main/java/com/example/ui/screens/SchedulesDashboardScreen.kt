package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RepeatInterval
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleStatus
import com.example.ui.StatusFilter
import com.example.ui.UiState
import com.example.util.ChannelDispatcher

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulesDashboardScreen(
  schedulesState: UiState<List<ScheduleItem>>,
  selectedStatusFilter: StatusFilter,
  selectedChannelFilter: ScheduleChannel?,
  searchQuery: String,
  currentTimeMillis: Long,
  isBusy: Boolean,
  onSelectStatusFilter: (StatusFilter) -> Unit,
  onSelectChannelFilter: (ScheduleChannel?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onQuickCreateForChannel: (ScheduleChannel) -> Unit,
  onEditSchedule: (ScheduleItem) -> Unit,
  onDuplicateSchedule: (ScheduleItem) -> Unit,
  onTogglePauseResume: (ScheduleItem) -> Unit,
  onDispatchScheduleNow: (ScheduleItem) -> Unit,
  onDeleteSchedule: (String) -> Unit,
  onLoadStarterKit: () -> Unit,
  maxSchedules: Int = 18,
  daysRemainingInMonth: Int = 30,
  allPermissionsGranted: Boolean = true,
  onOpenPermissionsDialog: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val allSchedules = (schedulesState as? UiState.Success)?.data ?: emptyList()

  val filteredSchedules = allSchedules.filter { item ->
    val statusMatch = when (selectedStatusFilter) {
      StatusFilter.ALL -> true
      StatusFilter.PENDING -> item.statusEnum == ScheduleStatus.PENDING
      StatusFilter.PAUSED -> item.statusEnum == ScheduleStatus.PAUSED
      StatusFilter.SENT -> item.statusEnum == ScheduleStatus.SENT
      StatusFilter.FAILED -> item.statusEnum == ScheduleStatus.FAILED || item.statusEnum == ScheduleStatus.CANCELLED
    }
    val channelMatch = selectedChannelFilter == null || item.channelEnum == selectedChannelFilter
    val query = searchQuery.trim()
    val searchMatch = query.isEmpty() ||
      item.recipientName.contains(query, ignoreCase = true) ||
      item.recipientTarget.contains(query, ignoreCase = true) ||
      item.messageBody.contains(query, ignoreCase = true) ||
      item.subject.contains(query, ignoreCase = true)
    statusMatch && channelMatch && searchMatch
  }

  val pendingItems = allSchedules.filter { it.statusEnum == ScheduleStatus.PENDING }
  val nextPending = pendingItems.minByOrNull { it.scheduledEpochMillis }
  val sentCount = allSchedules.count { it.statusEnum == ScheduleStatus.SENT }
  val recurringCount = allSchedules.count { it.repeatEnum != RepeatInterval.NONE }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("schedules_dashboard_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    if (!allPermissionsGranted) {
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_permissions_locked_card")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Permissions Required to Schedule",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                  text = "You cannot schedule messages until Accessibility, Notification & Alarm permissions are enabled (Battery Optimization is optional).",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = onOpenPermissionsDialog,
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
              ),
              modifier = Modifier.testTag("dashboard_grant_permissions_button")
            ) {
              Text("Grant")
            }
          }
        }
      }
    }

    // 1. Whatschedule 6-Channel Quick Scheduler Hub
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Tap a Service to Schedule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Choose WhatsApp, Telegram, SMS, Email, or Call",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
              shape = CircleShape
            ) {
              Text(
                text = "6 CHANNELS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 2x3 Channel Grid
          val channels = ScheduleChannel.entries
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              channels.take(3).forEach { channel ->
                ChannelQuickActionTile(
                  channel = channel,
                  onClick = { onQuickCreateForChannel(channel) },
                  modifier = Modifier.weight(1f)
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              channels.drop(3).forEach { channel ->
                ChannelQuickActionTile(
                  channel = channel,
                  onClick = { onQuickCreateForChannel(channel) },
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }
      }
    }

    // 2. Live Countdown & Automation Telemetry Strip
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
          .fillMaxWidth()
          .background(
            brush = Brush.horizontalGradient(
              colors = listOf(
                Color(0xFF0A2A4A),
                Color(0xFF063B3A)
              )
            ),
            shape = RoundedCornerShape(20.dp)
          )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            StatBadge(label = "PENDING", value = pendingItems.size.toString(), accent = Color(0xFF00E5B0))
            StatBadge(label = "SENT", value = sentCount.toString(), accent = Color(0xFF38BDF8))
            StatBadge(label = "RECURRING", value = recurringCount.toString(), accent = Color(0xFFFBBF24))
            StatBadge(
              label = "LIMIT",
              value = "${allSchedules.size}/$maxSchedules",
              accent = if (allSchedules.size >= maxSchedules) Color(0xFFF43F5E) else Color(0xFFA78BFA)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          val isLimitReached = allSchedules.size >= maxSchedules
          val quotaFraction = (allSchedules.size.toFloat() / maxSchedules.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
          Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.testTag("schedule_quota_indicator")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (isLimitReached) {
                  "Limit Reached ($maxSchedules/$maxSchedules) • Delete a schedule to add more"
                } else {
                  "Capacity: ${allSchedules.size}/$maxSchedules • 1-Month Limit (${daysRemainingInMonth}d left)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isLimitReached) Color(0xFFFDA4AF) else Color(0xFFB8CBE4),
                fontWeight = FontWeight.Medium
              )
              Text(
                text = if (daysRemainingInMonth <= 0) "Month ended" else "${maxOf(0, maxSchedules - allSchedules.size)} left",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLimitReached || daysRemainingInMonth <= 0) Color(0xFFF43F5E) else Color(0xFF00E5B0),
                fontWeight = FontWeight.Bold
              )
            }
            LinearProgressIndicator(
              progress = { quotaFraction },
              color = if (isLimitReached) Color(0xFFF43F5E) else Color(0xFF00E5B0),
              trackColor = Color.White.copy(alpha = 0.16f),
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
            )
          }

          if (nextPending != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
              color = Color.Black.copy(alpha = 0.28f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF00E5B0),
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Next: ${nextPending.channelEnum.displayName} → ${nextPending.recipientName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = ChannelDispatcher.formatCountdown(nextPending.scheduledEpochMillis, currentTimeMillis),
                  style = MaterialTheme.typography.labelMedium,
                  color = Color(0xFF00E5B0),
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    }

    // 3. Search & Filter Controls
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Search recipient, number, email, or message...") },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = "Search schedules")
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_search_input")
        )

        // Status Tabs (Pending, Paused, Sent, Failed, All)
        ScrollableTabRow(
          selectedTabIndex = StatusFilter.entries.indexOf(selectedStatusFilter),
          edgePadding = 0.dp,
          containerColor = Color.Transparent,
          divider = {}
        ) {
          StatusFilter.entries.forEach { filter ->
            val count = when (filter) {
              StatusFilter.ALL -> allSchedules.size
              StatusFilter.PENDING -> allSchedules.count { it.statusEnum == ScheduleStatus.PENDING }
              StatusFilter.PAUSED -> allSchedules.count { it.statusEnum == ScheduleStatus.PAUSED }
              StatusFilter.SENT -> allSchedules.count { it.statusEnum == ScheduleStatus.SENT }
              StatusFilter.FAILED -> allSchedules.count {
                it.statusEnum == ScheduleStatus.FAILED || it.statusEnum == ScheduleStatus.CANCELLED
              }
            }
            Tab(
              selected = selectedStatusFilter == filter,
              onClick = { onSelectStatusFilter(filter) },
              modifier = Modifier.testTag("status_tab_${filter.name.lowercase()}"),
              text = {
                Text(
                  text = "${filter.label} ($count)",
                  style = MaterialTheme.typography.labelLarge
                )
              }
            )
          }
        }

        // Channel Filter Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilterChip(
            selected = selectedChannelFilter == null,
            onClick = { onSelectChannelFilter(null) },
            label = { Text("All Channels") }
          )
          ScheduleChannel.entries.forEach { channel ->
            val accent = ChannelDispatcher.channelColor(channel)
            FilterChip(
              selected = selectedChannelFilter == channel,
              onClick = {
                onSelectChannelFilter(if (selectedChannelFilter == channel) null else channel)
              },
              leadingIcon = {
                Icon(
                  imageVector = ChannelDispatcher.channelIcon(channel),
                  contentDescription = null,
                  tint = accent,
                  modifier = Modifier.size(16.dp)
                )
              },
              label = { Text(channel.displayName) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.copy(alpha = 0.2f)
              )
            )
          }
        }
      }
    }

    // 4. Schedule List or Empty State
    when (schedulesState) {
      is UiState.Loading -> {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator()
          }
        }
      }

      is UiState.Error -> {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = schedulesState.message,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(16.dp)
            )
          }
        }
      }

      is UiState.Success -> {
        if (filteredSchedules.isEmpty()) {
          item {
            EmptySchedulesCard(
              hasAnySchedules = allSchedules.isNotEmpty(),
              isBusy = isBusy,
              onLoadStarterKit = onLoadStarterKit,
              onCreateFirst = { onQuickCreateForChannel(ScheduleChannel.WHATSAPP) }
            )
          }
        } else {
          items(filteredSchedules, key = { it.id.ifBlank { it.hashCode().toString() } }) { schedule ->
            ScheduleItemCard(
              schedule = schedule,
              currentTimeMillis = currentTimeMillis,
              onEdit = { onEditSchedule(schedule) },
              onDuplicate = { onDuplicateSchedule(schedule) },
              onTogglePauseResume = { onTogglePauseResume(schedule) },
              onDispatchNow = { onDispatchScheduleNow(schedule) },
              onDelete = { onDeleteSchedule(schedule.id) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChannelQuickActionTile(
  channel: ScheduleChannel,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val accent = ChannelDispatcher.channelColor(channel)
  Surface(
    color = accent.copy(alpha = 0.14f),
    shape = RoundedCornerShape(16.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag("quick_channel_${channel.id.lowercase()}")
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = ChannelDispatcher.channelIcon(channel),
          contentDescription = channel.displayName,
          tint = accent,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = channel.displayName,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun StatBadge(label: String, value: String, accent: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineMedium,
      color = accent,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Color(0xFFB8CBE4)
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleItemCard(
  schedule: ScheduleItem,
  currentTimeMillis: Long,
  onEdit: () -> Unit,
  onDuplicate: () -> Unit,
  onTogglePauseResume: () -> Unit,
  onDispatchNow: () -> Unit,
  onDelete: () -> Unit
) {
  val channel = schedule.channelEnum
  val accent = ChannelDispatcher.channelColor(channel)
  val status = schedule.statusEnum

  val statusColor = when (status) {
    ScheduleStatus.PENDING -> Color(0xFF00C49A)
    ScheduleStatus.PAUSED -> Color(0xFFF59E0B)
    ScheduleStatus.SENT -> Color(0xFF38BDF8)
    ScheduleStatus.FAILED, ScheduleStatus.CANCELLED -> Color(0xFFF43F5E)
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = accent.copy(alpha = 0.35f),
        shape = RoundedCornerShape(20.dp)
      )
      .testTag("schedule_card_${schedule.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Top Row: Channel Badge + Recipient + Status Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = ChannelDispatcher.channelIcon(channel),
              contentDescription = channel.displayName,
              tint = accent,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = schedule.recipientName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${channel.displayName} • ${schedule.recipientTarget}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          color = statusColor.copy(alpha = 0.18f),
          shape = RoundedCornerShape(50)
        ) {
          Text(
            text = status.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Subject (if present) + Message Body Preview
      Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          if (schedule.subject.isNotBlank()) {
            Text(
              text = schedule.subject,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
          }
          Text(
            text = schedule.messageBody,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Metadata Chips (Scheduled Date/Time, Countdown, Repeat, Ask Before Sending, Attachment)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        MetadataBadge(
          icon = Icons.Default.Schedule,
          text = ChannelDispatcher.formatDateTime(schedule.scheduledEpochMillis),
          tint = MaterialTheme.colorScheme.secondary
        )

        if (status == ScheduleStatus.PENDING) {
          MetadataBadge(
            icon = Icons.Default.NotificationsActive,
            text = ChannelDispatcher.formatCountdown(schedule.scheduledEpochMillis, currentTimeMillis),
            tint = MaterialTheme.colorScheme.primary
          )
        }

        if (schedule.repeatEnum != RepeatInterval.NONE) {
          MetadataBadge(
            icon = Icons.Default.Repeat,
            text = schedule.repeatEnum.label,
            tint = MaterialTheme.colorScheme.tertiary
          )
        }

        if (schedule.askBeforeSending) {
          MetadataBadge(
            icon = Icons.Default.VerifiedUser,
            text = "Ask Before Sending",
            tint = Color(0xFFF59E0B)
          )
        }

        if (schedule.attachmentLabel.isNotBlank()) {
          MetadataBadge(
            icon = Icons.Default.AttachFile,
            text = schedule.attachmentLabel,
            tint = Color(0xFF38BDF8)
          )
        }

        if (schedule.executionCount > 0L) {
          MetadataBadge(
            icon = Icons.Default.CheckCircle,
            text = "Sent ${schedule.executionCount}x",
            tint = Color(0xFF00C49A)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Buttons Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        FilledTonalButton(
          onClick = onDispatchNow,
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          modifier = Modifier.testTag("dispatch_now_${schedule.id}")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.action_send_now),
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (channel == ScheduleChannel.CALL) "Dial Now" else "Send Now",
            style = MaterialTheme.typography.labelLarge
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (status == ScheduleStatus.PENDING || status == ScheduleStatus.PAUSED) {
            IconButton(
              onClick = onTogglePauseResume,
              modifier = Modifier.testTag("pause_resume_${schedule.id}")
            ) {
              Icon(
                imageVector = if (status == ScheduleStatus.PAUSED) {
                  Icons.Default.PlayCircleOutline
                } else {
                  Icons.Default.PauseCircleOutline
                },
                contentDescription = if (status == ScheduleStatus.PAUSED) "Resume schedule" else "Pause schedule",
                tint = MaterialTheme.colorScheme.tertiary
              )
            }
          }

          IconButton(
            onClick = onEdit,
            modifier = Modifier.testTag("edit_schedule_${schedule.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit schedule",
              tint = MaterialTheme.colorScheme.primary
            )
          }

          IconButton(
            onClick = onDuplicate,
            modifier = Modifier.testTag("duplicate_schedule_${schedule.id}")
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Duplicate schedule",
              tint = MaterialTheme.colorScheme.secondary
            )
          }

          IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete_schedule_${schedule.id}")
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete schedule",
              tint = MaterialTheme.colorScheme.error
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MetadataBadge(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  tint: Color
) {
  Surface(
    color = tint.copy(alpha = 0.14f),
    shape = RoundedCornerShape(8.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(13.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint
      )
    }
  }
}

@Composable
private fun EmptySchedulesCard(
  hasAnySchedules: Boolean,
  isBusy: Boolean,
  onLoadStarterKit: () -> Unit,
  onCreateFirst: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(150.dp)
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_empty_schedule_1794648239973),
          contentDescription = "Empty Schedule Queue Illustration",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = if (hasAnySchedules) "No Matching Schedules" else stringResource(R.string.empty_schedules_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = if (hasAnySchedules) {
          "Try clearing your search or switching status/channel filters above."
        } else {
          stringResource(R.string.empty_schedules_subtitle)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(18.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Button(
          onClick = onCreateFirst,
          modifier = Modifier
            .weight(1f)
            .testTag("empty_create_schedule_button")
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Create Schedule")
        }

        if (!hasAnySchedules) {
          OutlinedButton(
            onClick = onLoadStarterKit,
            enabled = !isBusy,
            modifier = Modifier
              .weight(1f)
              .testTag("load_sample_schedules_button")
          ) {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Load Samples")
          }
        }
      }
    }
  }
}
