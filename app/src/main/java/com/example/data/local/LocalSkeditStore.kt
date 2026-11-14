package com.example.data.local

import android.content.Context
import com.example.data.model.MessageTemplate
import com.example.data.model.RecipientGroup
import com.example.data.model.RepeatInterval
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleStatus
import com.example.data.model.TemplateCategory
import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class LocalSkeditStore(context: Context) {

  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _schedulesFlow = MutableStateFlow<List<ScheduleItem>>(emptyList())
  private val _templatesFlow = MutableStateFlow<List<MessageTemplate>>(emptyList())
  private val _groupsFlow = MutableStateFlow<List<RecipientGroup>>(emptyList())

  init {
    if (!prefs.contains(KEY_PLAN_START_MS)) {
      prefs.edit().putLong(KEY_PLAN_START_MS, System.currentTimeMillis()).apply()
    }
    if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
      seedDefaultLocalData()
      prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
    } else {
      _schedulesFlow.value = loadSchedulesFromPrefs()
      _templatesFlow.value = loadTemplatesFromPrefs()
      _groupsFlow.value = loadGroupsFromPrefs()
    }
  }

  fun getPlanStartEpochMillis(): Long {
    val stored = prefs.getLong(KEY_PLAN_START_MS, 0L)
    if (stored > 0L) return stored
    val now = System.currentTimeMillis()
    prefs.edit().putLong(KEY_PLAN_START_MS, now).apply()
    return now
  }

  fun getPlanExpiryEpochMillis(): Long = getPlanStartEpochMillis() + ONE_MONTH_MILLIS

  fun getDaysRemainingInMonth(nowMs: Long = System.currentTimeMillis()): Int {
    val remainingMs = (getPlanExpiryEpochMillis() - nowMs).coerceAtLeast(0L)
    return ((remainingMs + 86_399_999L) / 86_400_000L).toInt().coerceIn(0, MAX_SCHEDULE_HORIZON_DAYS)
  }

  fun isMonthlyLimitExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
    return nowMs >= getPlanExpiryEpochMillis()
  }

  fun observeSchedules(): Flow<List<ScheduleItem>> = _schedulesFlow.asStateFlow()
  fun observeTemplates(): Flow<List<MessageTemplate>> = _templatesFlow.asStateFlow()
  fun observeGroups(): Flow<List<RecipientGroup>> = _groupsFlow.asStateFlow()

  fun createSchedule(
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
  ): Result<String> = runCatching {
    if (_schedulesFlow.value.size >= MAX_SCHEDULED_MESSAGES) {
      throw IllegalStateException(
        "Schedule limit reached: Maximum $MAX_SCHEDULED_MESSAGES scheduled messages allowed."
      )
    }
    val nowMs = System.currentTimeMillis()
    if (isMonthlyLimitExpired(nowMs)) {
      throw IllegalStateException(
        "1-month usage limit expired. No new schedules can be created."
      )
    }
    if (scheduledEpochMillis > nowMs + ONE_MONTH_MILLIS + 60_000L) {
      throw IllegalStateException(
        "Schedules can only be set up to 1 month (30 days) in advance."
      )
    }
    val id = "loc_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val now = Timestamp(Date())
    val newItem = ScheduleItem(
      id = id,
      userId = LOCAL_USER_ID,
      channel = channel.id,
      recipientName = recipientName.trim().take(120),
      recipientTarget = recipientTarget.trim().take(250),
      subject = subject.trim().take(180),
      messageBody = messageBody.trim().take(2000),
      attachmentLabel = attachmentLabel.trim().take(180),
      scheduledEpochMillis = scheduledEpochMillis,
      repeatInterval = repeatInterval.id,
      askBeforeSending = askBeforeSending,
      notifyOnComplete = notifyOnComplete,
      status = ScheduleStatus.PENDING.id,
      executionCount = 0L,
      lastExecutedEpochMillis = 0L,
      createdAt = now,
      updatedAt = now
    )
    val updated = (_schedulesFlow.value + newItem).sortedBy { it.scheduledEpochMillis }
    saveSchedulesToPrefs(updated)
    _schedulesFlow.value = updated
    id
  }

  fun updateSchedule(schedule: ScheduleItem): Result<Unit> = runCatching {
    val now = Timestamp(Date())
    val updated = _schedulesFlow.value.map { existing ->
      if (existing.id == schedule.id) {
        schedule.copy(
          recipientName = schedule.recipientName.trim().take(120),
          recipientTarget = schedule.recipientTarget.trim().take(250),
          subject = schedule.subject.trim().take(180),
          messageBody = schedule.messageBody.trim().take(2000),
          attachmentLabel = schedule.attachmentLabel.trim().take(180),
          updatedAt = now
        )
      } else {
        existing
      }
    }.sortedBy { it.scheduledEpochMillis }
    saveSchedulesToPrefs(updated)
    _schedulesFlow.value = updated
  }

  fun updateScheduleStatus(schedule: ScheduleItem, newStatus: ScheduleStatus): Result<Unit> = runCatching {
    val now = Timestamp(Date())
    val updated = _schedulesFlow.value.map { existing ->
      if (existing.id == schedule.id) {
        existing.copy(status = newStatus.id, updatedAt = now)
      } else {
        existing
      }
    }
    saveSchedulesToPrefs(updated)
    _schedulesFlow.value = updated
  }

  fun markScheduleExecuted(
    schedule: ScheduleItem,
    executedAtMillis: Long = System.currentTimeMillis()
  ): Result<Unit> = runCatching {
    val now = Timestamp(Date(executedAtMillis))
    val repeat = schedule.repeatEnum
    val updated = _schedulesFlow.value.map { existing ->
      if (existing.id == schedule.id) {
        if (repeat != RepeatInterval.NONE) {
          val nextTime = repeat.nextOccurrenceAfter(maxOf(existing.scheduledEpochMillis, executedAtMillis))
          existing.copy(
            status = ScheduleStatus.PENDING.id,
            scheduledEpochMillis = nextTime,
            executionCount = existing.executionCount + 1L,
            lastExecutedEpochMillis = executedAtMillis,
            updatedAt = now
          )
        } else {
          existing.copy(
            status = ScheduleStatus.SENT.id,
            executionCount = existing.executionCount + 1L,
            lastExecutedEpochMillis = executedAtMillis,
            updatedAt = now
          )
        }
      } else {
        existing
      }
    }.sortedBy { it.scheduledEpochMillis }
    saveSchedulesToPrefs(updated)
    _schedulesFlow.value = updated
  }

  fun deleteSchedule(scheduleId: String): Result<Unit> = runCatching {
    val updated = _schedulesFlow.value.filterNot { it.id == scheduleId }
    saveSchedulesToPrefs(updated)
    _schedulesFlow.value = updated
  }

  fun createTemplate(
    title: String,
    category: TemplateCategory,
    channel: String,
    content: String
  ): Result<String> = runCatching {
    val id = "tpl_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val now = Timestamp(Date())
    val tpl = MessageTemplate(
      id = id,
      userId = LOCAL_USER_ID,
      title = title.trim().take(100),
      category = category.id,
      channel = channel,
      content = content.trim().take(2000),
      usageCount = 0L,
      createdAt = now,
      updatedAt = now
    )
    val updated = listOf(tpl) + _templatesFlow.value
    saveTemplatesToPrefs(updated)
    _templatesFlow.value = updated
    id
  }

  fun incrementTemplateUsage(template: MessageTemplate): Result<Unit> = runCatching {
    val now = Timestamp(Date())
    val updated = _templatesFlow.value.map { existing ->
      if (existing.id == template.id) {
        existing.copy(usageCount = existing.usageCount + 1L, updatedAt = now)
      } else {
        existing
      }
    }
    saveTemplatesToPrefs(updated)
    _templatesFlow.value = updated
  }

  fun deleteTemplate(templateId: String): Result<Unit> = runCatching {
    val updated = _templatesFlow.value.filterNot { it.id == templateId }
    saveTemplatesToPrefs(updated)
    _templatesFlow.value = updated
  }

  fun createGroup(
    name: String,
    channel: ScheduleChannel,
    membersSummary: String,
    memberCount: Long
  ): Result<String> = runCatching {
    val id = "grp_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val now = Timestamp(Date())
    val group = RecipientGroup(
      id = id,
      userId = LOCAL_USER_ID,
      name = name.trim().take(100),
      channel = channel.id,
      membersSummary = membersSummary.trim().take(500),
      memberCount = memberCount.coerceIn(1L, 200L),
      createdAt = now,
      updatedAt = now
    )
    val updated = listOf(group) + _groupsFlow.value
    saveGroupsToPrefs(updated)
    _groupsFlow.value = updated
    id
  }

  fun deleteGroup(groupId: String): Result<Unit> = runCatching {
    val updated = _groupsFlow.value.filterNot { it.id == groupId }
    saveGroupsToPrefs(updated)
    _groupsFlow.value = updated
  }

  fun seedStarterKit(): Result<Unit> = runCatching {
    seedDefaultLocalData()
  }

  private fun seedDefaultLocalData() {
    val nowMs = System.currentTimeMillis()
    createTemplate(
      title = "Daily Standup Check-In",
      category = TemplateCategory.WORK,
      channel = ScheduleChannel.WHATSAPP.id,
      content = "Good morning team! Please share your 1) Yesterday's progress, 2) Today's focus, and 3) Any blockers before 9:30 AM."
    )
    createTemplate(
      title = "Client Proposal Follow-Up",
      category = TemplateCategory.FOLLOW_UP,
      channel = ScheduleChannel.EMAIL.id,
      content = "Hi {name}, hope your week is going well! Just following up on the proposal we sent over. Let me know if you'd like to hop on a quick 10-min call."
    )
    createTemplate(
      title = "Appointment Reminder",
      category = TemplateCategory.REMINDER,
      channel = ScheduleChannel.SMS.id,
      content = "Hi {name}, friendly reminder of your upcoming session tomorrow at {time}. Reply YES to confirm."
    )

    createGroup(
      name = "Core Product Squad",
      channel = ScheduleChannel.WHATSAPP,
      membersSummary = "Alex Rivera (+1 555-0142), Priya Patel (+1 555-0188), Liam Chen (+1 555-0199)",
      memberCount = 3L
    )
    createGroup(
      name = "Executive Stakeholders",
      channel = ScheduleChannel.EMAIL,
      membersSummary = "sarah.jenkins@acme.io, marcus.vance@acme.io, elena.rostova@acme.io",
      memberCount = 3L
    )

    if (_schedulesFlow.value.size < MAX_SCHEDULED_MESSAGES) {
      createSchedule(
        channel = ScheduleChannel.WHATSAPP,
        recipientName = "Core Product Squad",
        recipientTarget = "+1 555-0142, +1 555-0188",
        subject = "Morning Standup",
        messageBody = "Good morning team! Please post your daily standup updates in the thread before 9:30 AM.",
        attachmentLabel = "",
        scheduledEpochMillis = nowMs + 3_600_000L,
        repeatInterval = RepeatInterval.DAILY,
        askBeforeSending = false,
        notifyOnComplete = true
      )
    }
    if (_schedulesFlow.value.size < MAX_SCHEDULED_MESSAGES) {
      createSchedule(
        channel = ScheduleChannel.EMAIL,
        recipientName = "Sarah Jenkins (VP Marketing)",
        recipientTarget = "sarah.jenkins@acme.io",
        subject = "Q4 Campaign Launch Deck & Timeline",
        messageBody = "Hi Sarah,\n\nAttached is the finalized Q4 campaign launch timeline and budget breakdown ahead of tomorrow's review.\n\nBest regards,",
        attachmentLabel = "Q4_Launch_Deck_v3.pdf",
        scheduledEpochMillis = nowMs + 7_200_000L,
        repeatInterval = RepeatInterval.NONE,
        askBeforeSending = true,
        notifyOnComplete = true
      )
    }
    if (_schedulesFlow.value.size < MAX_SCHEDULED_MESSAGES) {
      createSchedule(
        channel = ScheduleChannel.TELEGRAM,
        recipientName = "DevOps Release Channel",
        recipientTarget = "@devops_releases",
        subject = "Weekly Deployment Window",
        messageBody = "Heads up: Production release train v2.14 starts in 30 minutes. Please freeze merges to main.",
        attachmentLabel = "",
        scheduledEpochMillis = nowMs + 18_000_000L,
        repeatInterval = RepeatInterval.WEEKLY,
        askBeforeSending = false,
        notifyOnComplete = true
      )
    }
  }

  private fun saveSchedulesToPrefs(list: List<ScheduleItem>) {
    val arr = JSONArray()
    list.forEach { item ->
      arr.put(
        JSONObject().apply {
          put("id", item.id)
          put("userId", item.userId)
          put("channel", item.channel)
          put("recipientName", item.recipientName)
          put("recipientTarget", item.recipientTarget)
          put("subject", item.subject)
          put("messageBody", item.messageBody)
          put("attachmentLabel", item.attachmentLabel)
          put("scheduledEpochMillis", item.scheduledEpochMillis)
          put("repeatInterval", item.repeatInterval)
          put("askBeforeSending", item.askBeforeSending)
          put("notifyOnComplete", item.notifyOnComplete)
          put("status", item.status)
          put("executionCount", item.executionCount)
          put("lastExecutedEpochMillis", item.lastExecutedEpochMillis)
          put("createdAtMs", item.createdAt?.toDate()?.time ?: System.currentTimeMillis())
          put("updatedAtMs", item.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
        }
      )
    }
    prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply()
  }

  private fun loadSchedulesFromPrefs(): List<ScheduleItem> {
    val raw = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            ScheduleItem(
              id = obj.optString("id"),
              userId = obj.optString("userId", LOCAL_USER_ID),
              channel = obj.optString("channel", ScheduleChannel.WHATSAPP.id),
              recipientName = obj.optString("recipientName"),
              recipientTarget = obj.optString("recipientTarget"),
              subject = obj.optString("subject"),
              messageBody = obj.optString("messageBody"),
              attachmentLabel = obj.optString("attachmentLabel"),
              scheduledEpochMillis = obj.optLong("scheduledEpochMillis", 0L),
              repeatInterval = obj.optString("repeatInterval", RepeatInterval.NONE.id),
              askBeforeSending = obj.optBoolean("askBeforeSending", false),
              notifyOnComplete = obj.optBoolean("notifyOnComplete", true),
              status = obj.optString("status", ScheduleStatus.PENDING.id),
              executionCount = obj.optLong("executionCount", 0L),
              lastExecutedEpochMillis = obj.optLong("lastExecutedEpochMillis", 0L),
              createdAt = Timestamp(Date(obj.optLong("createdAtMs", System.currentTimeMillis()))),
              updatedAt = Timestamp(Date(obj.optLong("updatedAtMs", System.currentTimeMillis())))
            )
          )
        }
      }.sortedBy { it.scheduledEpochMillis }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun saveTemplatesToPrefs(list: List<MessageTemplate>) {
    val arr = JSONArray()
    list.forEach { tpl ->
      arr.put(
        JSONObject().apply {
          put("id", tpl.id)
          put("userId", tpl.userId)
          put("title", tpl.title)
          put("category", tpl.category)
          put("channel", tpl.channel)
          put("content", tpl.content)
          put("usageCount", tpl.usageCount)
          put("createdAtMs", tpl.createdAt?.toDate()?.time ?: System.currentTimeMillis())
          put("updatedAtMs", tpl.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
        }
      )
    }
    prefs.edit().putString(KEY_TEMPLATES, arr.toString()).apply()
  }

  private fun loadTemplatesFromPrefs(): List<MessageTemplate> {
    val raw = prefs.getString(KEY_TEMPLATES, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            MessageTemplate(
              id = obj.optString("id"),
              userId = obj.optString("userId", LOCAL_USER_ID),
              title = obj.optString("title"),
              category = obj.optString("category", TemplateCategory.WORK.id),
              channel = obj.optString("channel", "ALL"),
              content = obj.optString("content"),
              usageCount = obj.optLong("usageCount", 0L),
              createdAt = Timestamp(Date(obj.optLong("createdAtMs", System.currentTimeMillis()))),
              updatedAt = Timestamp(Date(obj.optLong("updatedAtMs", System.currentTimeMillis())))
            )
          )
        }
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun saveGroupsToPrefs(list: List<RecipientGroup>) {
    val arr = JSONArray()
    list.forEach { grp ->
      arr.put(
        JSONObject().apply {
          put("id", grp.id)
          put("userId", grp.userId)
          put("name", grp.name)
          put("channel", grp.channel)
          put("membersSummary", grp.membersSummary)
          put("memberCount", grp.memberCount)
          put("createdAtMs", grp.createdAt?.toDate()?.time ?: System.currentTimeMillis())
          put("updatedAtMs", grp.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
        }
      )
    }
    prefs.edit().putString(KEY_GROUPS, arr.toString()).apply()
  }

  private fun loadGroupsFromPrefs(): List<RecipientGroup> {
    val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            RecipientGroup(
              id = obj.optString("id"),
              userId = obj.optString("userId", LOCAL_USER_ID),
              name = obj.optString("name"),
              channel = obj.optString("channel", ScheduleChannel.WHATSAPP.id),
              membersSummary = obj.optString("membersSummary"),
              memberCount = obj.optLong("memberCount", 1L),
              createdAt = Timestamp(Date(obj.optLong("createdAtMs", System.currentTimeMillis()))),
              updatedAt = Timestamp(Date(obj.optLong("updatedAtMs", System.currentTimeMillis())))
            )
          )
        }
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  companion object {
    private const val PREFS_NAME = "skedit_local_store"
    private const val KEY_INITIALIZED = "initialized_v1"
    private const val KEY_SCHEDULES = "schedules_json"
    private const val KEY_TEMPLATES = "templates_json"
    private const val KEY_GROUPS = "groups_json"
    private const val KEY_PLAN_START_MS = "plan_start_epoch_ms"
    const val LOCAL_USER_ID = "local_device_user"
    const val MAX_SCHEDULED_MESSAGES = 18
    const val MAX_SCHEDULE_HORIZON_DAYS = 30
    const val ONE_MONTH_MILLIS = 30L * 24L * 60L * 60L * 1000L
  }
}
