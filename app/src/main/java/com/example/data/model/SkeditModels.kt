package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Calendar

enum class ScheduleChannel(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val targetHint: String,
  val targetLabel: String,
  val supportsSubject: Boolean = false
) {
  WHATSAPP(
    id = "WHATSAPP",
    displayName = "WhatsApp",
    shortLabel = "WA",
    targetHint = "+1 555-0192 or Contact Name",
    targetLabel = "WhatsApp Recipient / Number"
  ),
  WHATSAPP_BUSINESS(
    id = "WHATSAPP_BUSINESS",
    displayName = "WA Business",
    shortLabel = "WAB",
    targetHint = "+1 555-0199 or Broadcast List",
    targetLabel = "WA Business Contact / Broadcast"
  ),
  TELEGRAM(
    id = "TELEGRAM",
    displayName = "Telegram",
    shortLabel = "TG",
    targetHint = "@username or +1 555-0145",
    targetLabel = "Telegram Handle / Phone"
  ),
  SMS(
    id = "SMS",
    displayName = "SMS / Text",
    shortLabel = "SMS",
    targetHint = "+1 555-0123",
    targetLabel = "Phone Number(s)"
  ),
  EMAIL(
    id = "EMAIL",
    displayName = "Email",
    shortLabel = "MAIL",
    targetHint = "client@company.com",
    targetLabel = "Recipient Email Address(es)",
    supportsSubject = true
  ),
  CALL(
    id = "CALL",
    displayName = "Phone Call",
    shortLabel = "CALL",
    targetHint = "+1 555-0188",
    targetLabel = "Phone Number to Dial"
  );

  companion object {
    fun fromId(id: String): ScheduleChannel =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WHATSAPP
  }
}

enum class RepeatInterval(val id: String, val label: String) {
  NONE("NONE", "One-time (No repeat)"),
  HOURLY("HOURLY", "Every Hour"),
  DAILY("DAILY", "Every Day"),
  WEEKLY("WEEKLY", "Every Week"),
  MONTHLY("MONTHLY", "Every Month"),
  YEARLY("YEARLY", "Every Year");

  fun nextOccurrenceAfter(epochMillis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    when (this) {
      NONE -> return epochMillis
      HOURLY -> cal.add(Calendar.HOUR_OF_DAY, 1)
      DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
      WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
      MONTHLY -> cal.add(Calendar.MONTH, 1)
      YEARLY -> cal.add(Calendar.YEAR, 1)
    }
    return cal.timeInMillis
  }

  companion object {
    fun fromId(id: String): RepeatInterval =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
  }
}

enum class ScheduleStatus(val id: String, val label: String) {
  PENDING("PENDING", "Pending"),
  PAUSED("PAUSED", "Paused"),
  SENT("SENT", "Sent"),
  FAILED("FAILED", "Failed"),
  CANCELLED("CANCELLED", "Cancelled");

  companion object {
    fun fromId(id: String): ScheduleStatus =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PENDING
  }
}

enum class TemplateCategory(val id: String, val label: String) {
  WORK("WORK", "Work & Standups"),
  PERSONAL("PERSONAL", "Personal & Birthdays"),
  REMINDER("REMINDER", "Reminders & Alerts"),
  MARKETING("MARKETING", "Outreach & VIP"),
  FOLLOW_UP("FOLLOW_UP", "Follow-ups");

  companion object {
    fun fromId(id: String): TemplateCategory =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WORK
  }
}

@IgnoreExtraProperties
data class ScheduleItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val channel: String = ScheduleChannel.WHATSAPP.id,
  val recipientName: String = "",
  val recipientTarget: String = "",
  val subject: String = "",
  val messageBody: String = "",
  val attachmentLabel: String = "",
  val scheduledEpochMillis: Long = 0L,
  val repeatInterval: String = RepeatInterval.NONE.id,
  val askBeforeSending: Boolean = false,
  val notifyOnComplete: Boolean = true,
  val status: String = ScheduleStatus.PENDING.id,
  val executionCount: Long = 0L,
  val lastExecutedEpochMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val channelEnum: ScheduleChannel
    get() = ScheduleChannel.fromId(channel)

  val repeatEnum: RepeatInterval
    get() = RepeatInterval.fromId(repeatInterval)

  val statusEnum: ScheduleStatus
    get() = ScheduleStatus.fromId(status)
}

@IgnoreExtraProperties
data class MessageTemplate(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val category: String = TemplateCategory.WORK.id,
  val channel: String = "ALL",
  val content: String = "",
  val usageCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val categoryEnum: TemplateCategory
    get() = TemplateCategory.fromId(category)
}

@IgnoreExtraProperties
data class RecipientGroup(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val channel: String = ScheduleChannel.WHATSAPP.id,
  val membersSummary: String = "",
  val memberCount: Long = 1L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  val channelEnum: ScheduleChannel
    get() = ScheduleChannel.fromId(channel)
}
