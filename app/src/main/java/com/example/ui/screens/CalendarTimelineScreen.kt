package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.RepeatInterval
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.ui.UiState
import com.example.util.ChannelDispatcher
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarTimelineScreen(
  schedulesState: UiState<List<ScheduleItem>>,
  onEditSchedule: (ScheduleItem) -> Unit,
  onDispatchScheduleNow: (ScheduleItem) -> Unit,
  onQuickCreate: (ScheduleChannel) -> Unit,
  modifier: Modifier = Modifier
) {
  val allSchedules = (schedulesState as? UiState.Success)?.data ?: emptyList()
  var selectedDayOffset by remember { mutableIntStateOf(0) }

  val dayOffsets = remember { (0..29).toList() }

  val selectedDayBounds = remember(selectedDayOffset) {
    val startCal = Calendar.getInstance().apply {
      add(Calendar.DAY_OF_YEAR, selectedDayOffset)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val endCal = (startCal.clone() as Calendar).apply {
      add(Calendar.DAY_OF_YEAR, 1)
    }
    startCal.timeInMillis to endCal.timeInMillis
  }

  val daySchedules = allSchedules.filter { item ->
    item.scheduledEpochMillis in selectedDayBounds.first until selectedDayBounds.second
  }.sortedBy { it.scheduledEpochMillis }

  val dayHeaderFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
  val timeOnlyFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("calendar_timeline_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
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
                text = "1-Month Dispatch Horizon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = dayHeaderFormatter.format(Date(selectedDayBounds.first)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Icon(
              imageVector = Icons.Default.CalendarToday,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(dayOffsets) { offset ->
              val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
              }
              val startMs = cal.timeInMillis
              val endMs = startMs + 86_400_000L
              val countForDay = allSchedules.count { it.scheduledEpochMillis in startMs until endMs }
              val dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(startMs))
              val dayNum = SimpleDateFormat("d", Locale.getDefault()).format(Date(startMs))
              val isSelected = selectedDayOffset == offset

              Surface(
                color = if (isSelected) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .width(62.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .clickable { selectedDayOffset = offset }
                  .testTag("calendar_day_offset_$offset")
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 10.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Text(
                    text = if (offset == 0) "TODAY" else dayOfWeek.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                      MaterialTheme.colorScheme.onPrimary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    }
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = dayNum,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                      MaterialTheme.colorScheme.onPrimary
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    }
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(
                        if (countForDay > 0) {
                          if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                          else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        } else {
                          androidx.compose.ui.graphics.Color.Transparent
                        }
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = if (countForDay > 0) countForDay.toString() else "·",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                      } else {
                        MaterialTheme.colorScheme.primary
                      }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    if (daySchedules.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "No Dispatches Scheduled for This Day",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Queue a WhatsApp message, Email, SMS, or Call reminder to populate your timeline.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = { onQuickCreate(ScheduleChannel.WHATSAPP) }) {
              Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Schedule for This Day")
            }
          }
        }
      }
    } else {
      items(daySchedules, key = { it.id }) { schedule ->
        val channel = schedule.channelEnum
        val accent = ChannelDispatcher.channelColor(channel)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Top
        ) {
          // Left Time Column
          Column(
            modifier = Modifier
              .width(76.dp)
              .padding(top = 8.dp),
            horizontalAlignment = Alignment.End
          ) {
            Text(
              text = timeOnlyFormatter.format(Date(schedule.scheduledEpochMillis)),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = schedule.statusEnum.label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          // Timeline Card
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            ),
            modifier = Modifier
              .weight(1f)
              .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = ChannelDispatcher.channelIcon(channel),
                    contentDescription = channel.displayName,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = schedule.recipientName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Row {
                  IconButton(
                    onClick = { onDispatchScheduleNow(schedule) },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Send,
                      contentDescription = "Send Now",
                      tint = accent,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  IconButton(
                    onClick = { onEditSchedule(schedule) },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Edit,
                      contentDescription = "Edit",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              Text(
                text = schedule.messageBody,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )

              if (schedule.repeatEnum != RepeatInterval.NONE) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = schedule.repeatEnum.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
