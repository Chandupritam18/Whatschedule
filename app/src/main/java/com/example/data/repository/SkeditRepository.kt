package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.MessageTemplate
import com.example.data.model.RecipientGroup
import com.example.data.model.RepeatInterval
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleStatus
import com.example.data.model.TemplateCategory
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SkeditRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth
    get() = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- SCHEDULES ---

  fun observeSchedules(userId: String = requireUserId()): Flow<List<ScheduleItem>> = flow {
    val path = "users/$userId/schedules"
    emitAll(
      db.collection("users").document(userId).collection("schedules")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            ScheduleItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedBy { it.scheduledEpochMillis }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun getScheduleById(scheduleId: String, userId: String = requireUserId()): Result<ScheduleItem?> = runCatching {
    val docRef = db.collection("users").document(userId).collection("schedules").document(scheduleId)
    try {
      val snapshot = docRef.get().await()
      snapshot.toObject(ScheduleItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun createSchedule(
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
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("schedules").document()
    val payload = mapOf(
      "userId" to uid,
      "channel" to channel.id,
      "recipientName" to recipientName.trim().take(120),
      "recipientTarget" to recipientTarget.trim().take(250),
      "subject" to subject.trim().take(180),
      "messageBody" to messageBody.trim().take(2000),
      "attachmentLabel" to attachmentLabel.trim().take(180),
      "scheduledEpochMillis" to scheduledEpochMillis,
      "repeatInterval" to repeatInterval.id,
      "askBeforeSending" to askBeforeSending,
      "notifyOnComplete" to notifyOnComplete,
      "status" to ScheduleStatus.PENDING.id,
      "executionCount" to 0L,
      "lastExecutedEpochMillis" to 0L,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun updateSchedule(schedule: ScheduleItem): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(schedule.id.isNotBlank()) { "Schedule ID must not be blank for update" }
    requireNotNull(schedule.createdAt) { "Schedule createdAt must not be null for update" }
    val docRef = db.collection("users").document(uid).collection("schedules").document(schedule.id)
    val sanitized = schedule.copy(
      userId = uid,
      recipientName = schedule.recipientName.trim().take(120),
      recipientTarget = schedule.recipientTarget.trim().take(250),
      subject = schedule.subject.trim().take(180),
      messageBody = schedule.messageBody.trim().take(2000),
      attachmentLabel = schedule.attachmentLabel.trim().take(180),
      updatedAt = null
    )
    try {
      docRef.set(sanitized).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun updateScheduleStatus(schedule: ScheduleItem, newStatus: ScheduleStatus): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(schedule.id.isNotBlank()) { "Schedule ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("schedules").document(schedule.id)
    try {
      docRef.update(
        mapOf(
          "status" to newStatus.id,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun markScheduleExecuted(schedule: ScheduleItem, executedAtMillis: Long = System.currentTimeMillis()): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(schedule.id.isNotBlank()) { "Schedule ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("schedules").document(schedule.id)
    val repeat = schedule.repeatEnum
    val updates = if (repeat != RepeatInterval.NONE) {
      val nextTime = repeat.nextOccurrenceAfter(
        maxOf(schedule.scheduledEpochMillis, executedAtMillis)
      )
      mapOf(
        "status" to ScheduleStatus.PENDING.id,
        "scheduledEpochMillis" to nextTime,
        "executionCount" to (schedule.executionCount + 1L),
        "lastExecutedEpochMillis" to executedAtMillis,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    } else {
      mapOf(
        "status" to ScheduleStatus.SENT.id,
        "executionCount" to (schedule.executionCount + 1L),
        "lastExecutedEpochMillis" to executedAtMillis,
        "updatedAt" to FieldValue.serverTimestamp()
      )
    }
    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteSchedule(scheduleId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(scheduleId.isNotBlank()) { "Schedule ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("schedules").document(scheduleId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- TEMPLATES ---

  fun observeTemplates(userId: String = requireUserId()): Flow<List<MessageTemplate>> = flow {
    val path = "users/$userId/templates"
    emitAll(
      db.collection("users").document(userId).collection("templates")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            MessageTemplate::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun createTemplate(
    title: String,
    category: TemplateCategory,
    channel: String,
    content: String
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("templates").document()
    val payload = mapOf(
      "userId" to uid,
      "title" to title.trim().take(100),
      "category" to category.id,
      "channel" to channel,
      "content" to content.trim().take(2000),
      "usageCount" to 0L,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun incrementTemplateUsage(template: MessageTemplate): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(template.id.isNotBlank()) { "Template ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("templates").document(template.id)
    try {
      docRef.update(
        mapOf(
          "usageCount" to (template.usageCount + 1L),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteTemplate(templateId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(templateId.isNotBlank()) { "Template ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("templates").document(templateId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- RECIPIENT GROUPS ---

  fun observeGroups(userId: String = requireUserId()): Flow<List<RecipientGroup>> = flow {
    val path = "users/$userId/groups"
    emitAll(
      db.collection("users").document(userId).collection("groups")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            RecipientGroup::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun createGroup(
    name: String,
    channel: ScheduleChannel,
    membersSummary: String,
    memberCount: Long
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("groups").document()
    val payload = mapOf(
      "userId" to uid,
      "name" to name.trim().take(100),
      "channel" to channel.id,
      "membersSummary" to membersSummary.trim().take(500),
      "memberCount" to memberCount.coerceIn(1L, 200L),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteGroup(groupId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(groupId.isNotBlank()) { "Group ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("groups").document(groupId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun seedStarterKit(): Result<Unit> = runCatching {
    val now = System.currentTimeMillis()
    createTemplate(
      title = "Daily Standup Check-In",
      category = TemplateCategory.WORK,
      channel = ScheduleChannel.WHATSAPP.id,
      content = "Good morning team! Please share your 1) Yesterday's progress, 2) Today's focus, and 3) Any blockers before 9:30 AM."
    ).getOrThrow()

    createTemplate(
      title = "Client Proposal Follow-Up",
      category = TemplateCategory.FOLLOW_UP,
      channel = ScheduleChannel.EMAIL.id,
      content = "Hi {name}, hope your week is going well! Just following up on the proposal we sent over. Let me know if you'd like to hop on a quick 10-min call."
    ).getOrThrow()

    createTemplate(
      title = "Appointment Reminder",
      category = TemplateCategory.REMINDER,
      channel = ScheduleChannel.SMS.id,
      content = "Hi {name}, friendly reminder of your upcoming session tomorrow at {time}. Reply YES to confirm or let us know if you need to reschedule."
    ).getOrThrow()

    createGroup(
      name = "Core Product Squad",
      channel = ScheduleChannel.WHATSAPP,
      membersSummary = "Alex Rivera (+1 555-0142), Priya Patel (+1 555-0188), Liam Chen (+1 555-0199)",
      memberCount = 3L
    ).getOrThrow()

    createGroup(
      name = "Executive Stakeholders",
      channel = ScheduleChannel.EMAIL,
      membersSummary = "sarah.jenkins@acme.io, marcus.vance@acme.io, elena.rostova@acme.io",
      memberCount = 3L
    ).getOrThrow()

    createSchedule(
      channel = ScheduleChannel.WHATSAPP,
      recipientName = "Core Product Squad",
      recipientTarget = "+1 555-0142, +1 555-0188",
      subject = "Morning Standup",
      messageBody = "Good morning team! Please post your daily standup updates in the thread before 9:30 AM.",
      attachmentLabel = "",
      scheduledEpochMillis = now + 3_600_000L,
      repeatInterval = RepeatInterval.DAILY,
      askBeforeSending = false,
      notifyOnComplete = true
    ).getOrThrow()

    createSchedule(
      channel = ScheduleChannel.EMAIL,
      recipientName = "Sarah Jenkins (VP Marketing)",
      recipientTarget = "sarah.jenkins@acme.io",
      subject = "Q4 Campaign Launch Deck & Timeline",
      messageBody = "Hi Sarah,\n\nAttached is the finalized Q4 campaign launch timeline and budget breakdown ahead of tomorrow's review.\n\nBest regards,",
      attachmentLabel = "Q4_Launch_Deck_v3.pdf",
      scheduledEpochMillis = now + 7_200_000L,
      repeatInterval = RepeatInterval.NONE,
      askBeforeSending = true,
      notifyOnComplete = true
    ).getOrThrow()

    createSchedule(
      channel = ScheduleChannel.TELEGRAM,
      recipientName = "DevOps Release Channel",
      recipientTarget = "@devops_releases",
      subject = "Weekly Deployment Window",
      messageBody = "Heads up: Production release train v2.14 starts in 30 minutes. Please freeze merges to main.",
      attachmentLabel = "",
      scheduledEpochMillis = now + 18_000_000L,
      repeatInterval = RepeatInterval.WEEKLY,
      askBeforeSending = false,
      notifyOnComplete = true
    ).getOrThrow()
  }
}
