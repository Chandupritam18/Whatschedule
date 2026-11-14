package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.LocalSkeditStore
import com.example.data.model.MessageTemplate
import com.example.data.model.RecipientGroup
import com.example.data.model.RepeatInterval
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.util.ChannelDispatcher
import com.example.util.PermissionHelper
import com.example.util.WhatsAppContactSelectionManager
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleComposerSheet(
  existingSchedule: ScheduleItem?,
  initialChannel: ScheduleChannel,
  initialRecipientName: String = "",
  initialRecipientTarget: String = "",
  initialMessageBody: String = "",
  defaultAskBeforeSending: Boolean,
  defaultNotifyOnComplete: Boolean,
  templates: List<MessageTemplate>,
  groups: List<RecipientGroup>,
  onDismiss: () -> Unit,
  onSave: (
    channel: ScheduleChannel,
    recipientName: String,
    recipientTarget: String,
    subject: String,
    messageBody: String,
    attachmentLabel: String,
    scheduledEpochMillis: Long,
    repeatInterval: RepeatInterval,
    askBeforeSending: Boolean,
    notifyOnComplete: Boolean
  ) -> Unit,
  onTemplateSelected: (MessageTemplate) -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val selectedWhatsAppContacts by WhatsAppContactSelectionManager.selectedContacts.collectAsStateWithLifecycle()

  var selectedChannel by remember(existingSchedule, initialChannel) {
    mutableStateOf(existingSchedule?.channelEnum ?: initialChannel)
  }
  var recipientName by remember(existingSchedule, initialRecipientName) {
    mutableStateOf(existingSchedule?.recipientName ?: initialRecipientName)
  }
  var recipientTarget by remember(existingSchedule, initialRecipientTarget) {
    mutableStateOf(existingSchedule?.recipientTarget ?: initialRecipientTarget)
  }
  var subject by remember(existingSchedule) {
    mutableStateOf(existingSchedule?.subject ?: "")
  }
  var messageBody by remember(existingSchedule, initialMessageBody) {
    mutableStateOf(existingSchedule?.messageBody ?: initialMessageBody)
  }
  var attachmentLabel by remember(existingSchedule) {
    mutableStateOf(existingSchedule?.attachmentLabel ?: "")
  }
  var scheduledEpochMillis by remember(existingSchedule) {
    mutableLongStateOf(
      existingSchedule?.scheduledEpochMillis?.takeIf { it > 0L }
        ?: (System.currentTimeMillis() + 3_600_000L)
    )
  }
  var repeatInterval by remember(existingSchedule) {
    mutableStateOf(existingSchedule?.repeatEnum ?: RepeatInterval.NONE)
  }
  var askBeforeSending by remember(existingSchedule, defaultAskBeforeSending) {
    mutableStateOf(existingSchedule?.askBeforeSending ?: defaultAskBeforeSending)
  }
  var notifyOnComplete by remember(existingSchedule, defaultNotifyOnComplete) {
    mutableStateOf(existingSchedule?.notifyOnComplete ?: defaultNotifyOnComplete)
  }

  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }
  var showTemplatePickerDialog by remember { mutableStateOf(false) }
  var showWhatsAppGuideDialog by remember { mutableStateOf(false) }
  var whatsAppGuideReason by remember { mutableStateOf("") }
  var validationError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(selectedWhatsAppContacts) {
    if (existingSchedule == null && selectedWhatsAppContacts.isNotEmpty()) {
      recipientName = WhatsAppContactSelectionManager.formatRecipientNameSummary(selectedWhatsAppContacts)
      recipientTarget = WhatsAppContactSelectionManager.formatRecipientTargetSummary(selectedWhatsAppContacts)
    }
  }

  // Zero-permission Phone Contact Picker
  val phoneContactPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val contactUri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
    if (contactUri != null) {
      val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
      )
      context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
          val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
          val pickedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
          val pickedNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
          val finalName = pickedName.ifBlank { pickedNum }
          val finalTarget = pickedNum.ifBlank { pickedName }
          if (finalName.isNotBlank()) {
            WhatsAppContactSelectionManager.addSelectedContact(
              name = finalName,
              target = finalTarget,
              channel = selectedChannel
            )
            val updated = WhatsAppContactSelectionManager.selectedContacts.value
            recipientName = WhatsAppContactSelectionManager.formatRecipientNameSummary(updated)
            recipientTarget = WhatsAppContactSelectionManager.formatRecipientTargetSummary(updated)
          }
        }
      }
    }
  }

  // Zero-permission Android Photo Picker per Google Play policy
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      val lastSegment = uri.lastPathSegment?.substringAfterLast('/') ?: "media_attachment.jpg"
      attachmentLabel = "Photo: $lastSegment"
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Sheet Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          val accent = ChannelDispatcher.channelColor(selectedChannel)
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = ChannelDispatcher.channelIcon(selectedChannel),
              contentDescription = null,
              tint = accent
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = if (existingSchedule != null && existingSchedule.id.isNotBlank()) {
                "Edit ${selectedChannel.displayName} Schedule"
              } else {
                "Schedule ${selectedChannel.displayName}"
              },
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Configure recipient, content, date/time & automation rules",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close composer")
        }
      }

      // 1. Channel Selector Strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ScheduleChannel.entries.forEach { channel ->
          val accent = ChannelDispatcher.channelColor(channel)
          FilterChip(
            selected = selectedChannel == channel,
            onClick = { selectedChannel = channel },
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
              selectedContainerColor = accent.copy(alpha = 0.22f)
            ),
            modifier = Modifier.testTag("composer_channel_${channel.id.lowercase()}")
          )
        }
      }

      // 2. Saved Recipient Groups Quick Fill (if available)
      if (groups.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Group,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Quick-Fill from Saved Recipient Groups:",
              style = MaterialTheme.typography.labelLarge
            )
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            groups.forEach { group ->
              AssistChip(
                onClick = {
                  recipientName = "${group.name} (${group.memberCount})"
                  recipientTarget = group.membersSummary
                  selectedChannel = group.channelEnum
                },
                label = { Text("${group.name} (${group.memberCount})") }
              )
            }
          }
        }
      }

      // 3. Direct WhatsApp & Device Contact Picker + Recipient Fields
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (selectedChannel == ScheduleChannel.WHATSAPP || selectedChannel == ScheduleChannel.WHATSAPP_BUSINESS) {
          FilledTonalButton(
            onClick = {
              val accessibilityOn = PermissionHelper.isAccessibilityServiceEnabled(context)
              val whatsappInstalled = ChannelDispatcher.isWhatsAppInstalled(context, selectedChannel)
              when {
                !accessibilityOn -> {
                  whatsAppGuideReason =
                    "Enable Whatschedule Accessibility Service so contacts are automatically selected as soon as you open their chat in ${selectedChannel.displayName}, with a live selection counter popup on screen."
                  showWhatsAppGuideDialog = true
                }
                !whatsappInstalled -> {
                  whatsAppGuideReason =
                    "${selectedChannel.displayName} is not installed on this device. You can select contacts from Phone Contacts instead."
                  showWhatsAppGuideDialog = true
                }
                else -> {
                  WhatsAppContactSelectionManager.startSelection(selectedChannel)
                  ChannelDispatcher.launchWhatsAppForSelection(context, selectedChannel)
                }
              }
            },
            modifier = Modifier
              .weight(1.25f)
              .testTag("select_from_whatsapp_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Chat,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Select from ${selectedChannel.displayName}",
              maxLines = 1
            )
          }
        }

        OutlinedButton(
          onClick = {
            val pickIntent = Intent(
              Intent.ACTION_PICK,
              ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            )
            phoneContactPickerLauncher.launch(pickIntent)
          },
          modifier = Modifier
            .weight(1f)
            .testTag("pick_phone_contact_button")
        ) {
          Icon(
            imageVector = Icons.Default.Contacts,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Contacts", maxLines = 1)
        }
      }

      if (selectedWhatsAppContacts.isNotEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("composer_selected_contacts_popup")
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (selectedWhatsAppContacts.size == 1) {
                  "1 contact selected"
                } else {
                  "${selectedWhatsAppContacts.size} contacts selected"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              TextButton(
                onClick = {
                  WhatsAppContactSelectionManager.clearSelection()
                  recipientName = ""
                  recipientTarget = ""
                }
              ) {
                Text("Clear")
              }
            }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              selectedWhatsAppContacts.forEach { contact ->
                InputChip(
                  selected = true,
                  onClick = {
                    WhatsAppContactSelectionManager.removeContact(contact)
                  },
                  label = { Text(contact.name) },
                  trailingIcon = {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Remove ${contact.name}",
                      modifier = Modifier.size(14.dp)
                    )
                  }
                )
              }
            }
          }
        }
      }

      OutlinedTextField(
        value = recipientName,
        onValueChange = { recipientName = it },
        label = { Text("Recipient / Contact / Group Name *") },
        placeholder = { Text("e.g., Sarah Jenkins or Marketing Team") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("composer_recipient_name_input")
      )

      OutlinedTextField(
        value = recipientTarget,
        onValueChange = { recipientTarget = it },
        label = { Text("${selectedChannel.targetLabel} *") },
        placeholder = { Text(selectedChannel.targetHint) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("composer_recipient_target_input")
      )

      // 4. Optional Subject / Schedule Note
      OutlinedTextField(
        value = subject,
        onValueChange = { subject = it },
        label = {
          Text(
            if (selectedChannel.supportsSubject) {
              "Email Subject Line"
            } else {
              "Schedule Label / Note (Optional)"
            }
          )
        },
        placeholder = { Text("e.g., Weekly Status Check-In") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("composer_subject_input")
      )

      // 5. Message Body + Template Picker + Variable Chips
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (selectedChannel == ScheduleChannel.CALL) {
              "Call Agenda / Talking Points *"
            } else {
              "Message Content *"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
          if (templates.isNotEmpty()) {
            TextButton(
              onClick = { showTemplatePickerDialog = true },
              modifier = Modifier.testTag("open_template_picker_button")
            ) {
              Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Insert Template (${templates.size})")
            }
          }
        }

        OutlinedTextField(
          value = messageBody,
          onValueChange = { messageBody = it },
          placeholder = {
            Text("Type your scheduled message or insert a template...")
          },
          minLines = 3,
          maxLines = 6,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("composer_message_body_input")
        )

        // Dynamic Variable Chips & Media Attachment
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(
            "Hi {name}," to "+ Greeting",
            "{date}" to "+ Date",
            "{time}" to "+ Time",
            "Please confirm receipt." to "+ Confirm"
          ).forEach { (snippet, label) ->
            AssistChip(
              onClick = {
                val resolved = snippet
                  .replace("{name}", recipientName.ifBlank { "there" })
                  .replace("{date}", ChannelDispatcher.formatDateTime(scheduledEpochMillis).substringBefore("•").trim())
                  .replace("{time}", ChannelDispatcher.formatDateTime(scheduledEpochMillis).substringAfter("•").trim())
                messageBody = if (messageBody.isBlank()) resolved else "$messageBody $resolved"
              },
              label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
          }

          AssistChip(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
              )
            },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
              )
            },
            label = { Text("Attach Photo/Video", style = MaterialTheme.typography.labelSmall) }
          )
        }

        if (attachmentLabel.isNotBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.AttachFile,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = attachmentLabel,
                  style = MaterialTheme.typography.bodySmall
                )
              }
              IconButton(
                onClick = { attachmentLabel = "" },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Remove attachment",
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }

      HorizontalDivider()

      // 6. Schedule Date & Time Selection + Quick Presets (Max 1 Month Ahead)
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Schedule Date & Time",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = "1-Month Limit: Schedule up to 30 days ahead",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }

      // Quick Presets Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuickTimePresetChip("+15 Mins") {
          scheduledEpochMillis = System.currentTimeMillis() + 15 * 60_000L
        }
        QuickTimePresetChip("+1 Hour") {
          scheduledEpochMillis = System.currentTimeMillis() + 60 * 60_000L
        }
        QuickTimePresetChip("Tonight 8 PM") {
          val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
              add(Calendar.DAY_OF_YEAR, 1)
            }
          }
          scheduledEpochMillis = cal.timeInMillis
        }
        QuickTimePresetChip("Tomorrow 9 AM") {
          val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
          }
          scheduledEpochMillis = cal.timeInMillis
        }
        QuickTimePresetChip("Next Week") {
          val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
          }
          scheduledEpochMillis = cal.timeInMillis
        }
        QuickTimePresetChip("1 Month") {
          val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 29)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
          }
          scheduledEpochMillis = cal.timeInMillis
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = { showDatePicker = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("pick_date_button")
        ) {
          Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = ChannelDispatcher.formatDateTime(scheduledEpochMillis).substringBefore("•").trim(),
            maxLines = 1
          )
        }

        OutlinedButton(
          onClick = { showTimePicker = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("pick_time_button")
        ) {
          Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = ChannelDispatcher.formatDateTime(scheduledEpochMillis).substringAfter("•").trim(),
            maxLines = 1
          )
        }
      }

      // 7. Repeat / Recurrence Selector
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Repeat Frequency",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          RepeatInterval.entries.forEach { interval ->
            FilterChip(
              selected = repeatInterval == interval,
              onClick = { repeatInterval = interval },
              label = { Text(interval.label) }
            )
          }
        }
      }

      HorizontalDivider()

      // 8. Signature Skedit Automation Switches
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                  text = stringResource(R.string.ask_before_sending_title),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = stringResource(R.string.ask_before_sending_desc),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = askBeforeSending,
              onCheckedChange = { askBeforeSending = it },
              modifier = Modifier.testTag("ask_before_sending_switch")
            )
          }

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
                  text = stringResource(R.string.notify_on_complete_title),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = stringResource(R.string.notify_on_complete_desc),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = notifyOnComplete,
              onCheckedChange = { notifyOnComplete = it },
              modifier = Modifier.testTag("notify_on_complete_switch")
            )
          }
        }
      }

      if (validationError != null) {
        Text(
          text = validationError ?: "",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall
        )
      }

      // 9. Save Schedule Button
      Button(
        onClick = {
          if (recipientName.isBlank()) {
            validationError = "Please enter a recipient or contact name."
            return@Button
          }
          if (recipientTarget.isBlank()) {
            validationError = "Please enter a phone number, handle, or email address."
            return@Button
          }
          if (messageBody.isBlank()) {
            validationError = "Please enter the message or call reminder content."
            return@Button
          }
          if (scheduledEpochMillis > System.currentTimeMillis() + LocalSkeditStore.ONE_MONTH_MILLIS + 60_000L) {
            validationError = "1-Month Limit: Schedules can only be set up to 30 days ahead."
            return@Button
          }
          validationError = null
          onSave(
            selectedChannel,
            recipientName,
            recipientTarget,
            subject,
            messageBody,
            attachmentLabel,
            scheduledEpochMillis,
            repeatInterval,
            askBeforeSending,
            notifyOnComplete
          )
        },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("save_schedule_button")
      ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ScheduleSend, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (existingSchedule != null && existingSchedule.id.isNotBlank()) {
            stringResource(R.string.action_update_schedule)
          } else {
            stringResource(R.string.action_save_schedule)
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }

  // Material 3 DatePickerDialog (Restricted to 1 Month / 30 Days Ahead)
  if (showDatePicker) {
    val todayUtcMillis = remember {
      val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      cal.timeInMillis
    }
    val maxHorizonUtcMillis = remember(todayUtcMillis) {
      todayUtcMillis + LocalSkeditStore.ONE_MONTH_MILLIS
    }
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = scheduledEpochMillis.coerceIn(todayUtcMillis, maxHorizonUtcMillis),
      selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
          return utcTimeMillis >= todayUtcMillis - 86_400_000L && utcTimeMillis <= maxHorizonUtcMillis
        }
      }
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val pickedDateMillis = datePickerState.selectedDateMillis
            if (pickedDateMillis != null) {
              val pickedCal = Calendar.getInstance().apply { timeInMillis = pickedDateMillis }
              val existingCal = Calendar.getInstance().apply { timeInMillis = scheduledEpochMillis }
              existingCal.set(
                pickedCal.get(Calendar.YEAR),
                pickedCal.get(Calendar.MONTH),
                pickedCal.get(Calendar.DAY_OF_MONTH)
              )
              scheduledEpochMillis = existingCal.timeInMillis.coerceAtMost(
                System.currentTimeMillis() + LocalSkeditStore.ONE_MONTH_MILLIS
              )
            }
            showDatePicker = false
          }
        ) {
          Text("Confirm")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text("Cancel")
        }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }

  // Material 3 TimePicker Dialog
  if (showTimePicker) {
    val cal = remember(scheduledEpochMillis) {
      Calendar.getInstance().apply { timeInMillis = scheduledEpochMillis }
    }
    val timePickerState = rememberTimePickerState(
      initialHour = cal.get(Calendar.HOUR_OF_DAY),
      initialMinute = cal.get(Calendar.MINUTE),
      is24Hour = false
    )
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      title = { Text("Select Schedule Time") },
      text = {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          TimePicker(state = timePickerState)
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            val updatedCal = Calendar.getInstance().apply {
              timeInMillis = scheduledEpochMillis
              set(Calendar.HOUR_OF_DAY, timePickerState.hour)
              set(Calendar.MINUTE, timePickerState.minute)
              set(Calendar.SECOND, 0)
            }
            scheduledEpochMillis = updatedCal.timeInMillis
            showTimePicker = false
          }
        ) {
          Text("Confirm")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTimePicker = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Template Picker Dialog
  if (showTemplatePickerDialog) {
    AlertDialog(
      onDismissRequest = { showTemplatePickerDialog = false },
      title = { Text("Insert Saved Template") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          templates.forEach { template ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  messageBody = template.content
                  if (subject.isBlank()) {
                    subject = template.title
                  }
                  onTemplateSelected(template)
                  showTemplatePickerDialog = false
                }
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = template.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = template.content,
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 2
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showTemplatePickerDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  // WhatsApp Selection Guide Dialog (Accessibility / Not Installed Fallback)
  if (showWhatsAppGuideDialog) {
    val accessibilityOn = PermissionHelper.isAccessibilityServiceEnabled(context)
    AlertDialog(
      onDismissRequest = { showWhatsAppGuideDialog = false },
      icon = {
        Icon(
          imageVector = if (!accessibilityOn) Icons.Default.AccessibilityNew else Icons.AutoMirrored.Filled.Chat,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
      },
      title = {
        Text("Direct WhatsApp Contact Selection")
      },
      text = {
        Text(
          text = whatsAppGuideReason,
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        if (!accessibilityOn) {
          Button(
            onClick = {
              showWhatsAppGuideDialog = false
              PermissionHelper.openAccessibilitySettings(context)
            }
          ) {
            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Enable Accessibility")
          }
        } else {
          Button(
            onClick = {
              showWhatsAppGuideDialog = false
              val pickIntent = Intent(
                Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
              )
              phoneContactPickerLauncher.launch(pickIntent)
            }
          ) {
            Text("Pick Phone Contact")
          }
        }
      },
      dismissButton = {
        Row {
          if (!accessibilityOn) {
            TextButton(
              onClick = {
                showWhatsAppGuideDialog = false
                val pickIntent = Intent(
                  Intent.ACTION_PICK,
                  ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                )
                phoneContactPickerLauncher.launch(pickIntent)
              }
            ) {
              Text("Phone Contacts")
            }
          }
          TextButton(onClick = { showWhatsAppGuideDialog = false }) {
            Text("Close")
          }
        }
      }
    )
  }
}

@Composable
private fun QuickTimePresetChip(
  label: String,
  onClick: () -> Unit
) {
  AssistChip(
    onClick = onClick,
    label = { Text(label) }
  )
}
