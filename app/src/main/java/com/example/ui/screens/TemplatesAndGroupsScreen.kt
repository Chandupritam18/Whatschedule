package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MessageTemplate
import com.example.data.model.RecipientGroup
import com.example.data.model.ScheduleChannel
import com.example.data.model.TemplateCategory
import com.example.ui.UiState
import com.example.util.ChannelDispatcher

@Composable
fun TemplatesAndGroupsScreen(
  templatesState: UiState<List<MessageTemplate>>,
  groupsState: UiState<List<RecipientGroup>>,
  initialSubTab: Int = 0,
  onUseTemplateForSchedule: (MessageTemplate) -> Unit,
  onUseGroupForSchedule: (RecipientGroup) -> Unit,
  onCreateTemplate: (String, TemplateCategory, String, String) -> Unit,
  onDeleteTemplate: (String) -> Unit,
  onCreateGroup: (String, ScheduleChannel, String, Long) -> Unit,
  onDeleteGroup: (String) -> Unit,
  onLoadStarterKit: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedSubTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
  var showCreateTemplateDialog by remember { mutableStateOf(false) }
  var showCreateGroupDialog by remember { mutableStateOf(false) }

  val templates = (templatesState as? UiState.Success)?.data ?: emptyList()
  val groups = (groupsState as? UiState.Success)?.data ?: emptyList()

  Column(modifier = modifier.fillMaxSize()) {
    TabRow(
      selectedTabIndex = selectedSubTab,
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Tab(
        selected = selectedSubTab == 0,
        onClick = { selectedSubTab = 0 },
        modifier = Modifier.testTag("subtab_templates"),
        text = { Text("Message Templates (${templates.size})") },
        icon = { Icon(imageVector = Icons.Default.Description, contentDescription = null) }
      )
      Tab(
        selected = selectedSubTab == 1,
        onClick = { selectedSubTab = 1 },
        modifier = Modifier.testTag("subtab_groups"),
        text = { Text("Recipient Lists (${groups.size})") },
        icon = { Icon(imageVector = Icons.Default.Group, contentDescription = null) }
      )
    }

    if (selectedSubTab == 0) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Reusable Message Templates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Supports dynamic placeholders like {name}, {date}, {time}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Button(
              onClick = { showCreateTemplateDialog = true },
              modifier = Modifier.testTag("new_template_button")
            ) {
              Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("New Template")
            }
          }
        }

        if (templates.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "No Templates Saved Yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Save standup prompts, appointment reminders, or follow-up scripts for 1-tap scheduling.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(onClick = onLoadStarterKit) {
                  Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Load Starter Templates")
                }
              }
            }
          }
        } else {
          items(templates, key = { it.id }) { template ->
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = template.title,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "${template.categoryEnum.label} • Used ${template.usageCount}x",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }
                  IconButton(onClick = { onDeleteTemplate(template.id) }) {
                    Icon(
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = "Delete template",
                      tint = MaterialTheme.colorScheme.error
                    )
                  }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = template.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                  )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FilledTonalButton(
                  onClick = { onUseTemplateForSchedule(template) },
                  modifier = Modifier.align(Alignment.End)
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Schedule with Template")
                }
              }
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Broadcast & Recipient Lists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Group contacts for multi-recipient scheduling",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Button(
              onClick = { showCreateGroupDialog = true },
              modifier = Modifier.testTag("new_group_button")
            ) {
              Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("New Group")
            }
          }
        }

        if (groups.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "No Recipient Groups Yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Create contact lists for VIP clients, project squads, or family broadcast updates.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(onClick = onLoadStarterKit) {
                  Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Load Sample Groups")
                }
              }
            }
          }
        } else {
          items(groups, key = { it.id }) { group ->
            val channel = group.channelEnum
            val accent = ChannelDispatcher.channelColor(channel)
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.2f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = ChannelDispatcher.channelIcon(channel),
                        contentDescription = channel.displayName,
                        tint = accent
                      )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = "${channel.displayName} • ${group.memberCount} recipients",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                      )
                    }
                  }
                  IconButton(onClick = { onDeleteGroup(group.id) }) {
                    Icon(
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = "Delete group",
                      tint = MaterialTheme.colorScheme.error
                    )
                  }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                  text = group.membersSummary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                FilledTonalButton(
                  onClick = { onUseGroupForSchedule(group) },
                  modifier = Modifier.align(Alignment.End)
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Schedule to Group")
                }
              }
            }
          }
        }
      }
    }
  }

  // Create Template Dialog
  if (showCreateTemplateDialog) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TemplateCategory.WORK) }
    var content by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showCreateTemplateDialog = false },
      title = { Text("Create Message Template") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Template Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            TemplateCategory.entries.forEach { cat ->
              FilterChip(
                selected = category == cat,
                onClick = { category = cat },
                label = { Text(cat.label) }
              )
            }
          }
          OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Template Content") },
            placeholder = { Text("Hi {name}, quick reminder for {date} at {time}...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (title.isNotBlank() && content.isNotBlank()) {
              onCreateTemplate(title, category, "ALL", content)
              showCreateTemplateDialog = false
            }
          }
        ) {
          Text("Save Template")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateTemplateDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Create Recipient Group Dialog
  if (showCreateGroupDialog) {
    var name by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(ScheduleChannel.WHATSAPP) }
    var membersSummary by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showCreateGroupDialog = false },
      title = { Text("New Recipient Group") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Group / Broadcast Name") },
            placeholder = { Text("e.g., VIP Clients") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            ScheduleChannel.entries.forEach { ch ->
              FilterChip(
                selected = channel == ch,
                onClick = { channel = ch },
                label = { Text(ch.displayName) }
              )
            }
          }
          OutlinedTextField(
            value = membersSummary,
            onValueChange = { membersSummary = it },
            label = { Text("Recipients (comma-separated)") },
            placeholder = { Text("Sarah (+1 555-0192), Alex (+1 555-0142)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (name.isNotBlank() && membersSummary.isNotBlank()) {
              val count = membersSummary.split(",").count { it.isNotBlank() }.coerceAtLeast(1).toLong()
              onCreateGroup(name, channel, membersSummary, count)
              showCreateGroupDialog = false
            }
          }
        ) {
          Text("Create Group")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateGroupDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
