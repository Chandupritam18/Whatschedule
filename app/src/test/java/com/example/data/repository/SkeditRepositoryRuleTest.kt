package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.RepeatInterval
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleStatus
import com.example.data.model.TemplateCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkeditRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAndObserveSchedule_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = SkeditRepository(firestore)

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createSchedule(
        channel = ScheduleChannel.WHATSAPP,
        recipientName = "Alex Rivera",
        recipientTarget = "+1 555-0142",
        subject = "Standup Sync",
        messageBody = "Morning update reminder!",
        attachmentLabel = "",
        scheduledEpochMillis = 1800000000000L,
        repeatInterval = RepeatInterval.DAILY,
        askBeforeSending = true,
        notifyOnComplete = true
      )
    }
    assertTrue("Expected schedule creation to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
    val scheduleId = createResult.getOrThrow()
    assertTrue(scheduleId.isNotEmpty())

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeSchedules(uid).first { list -> list.any { it.id == scheduleId } }
    }
    val item = emitted.first { it.id == scheduleId }
    assertEquals("Alex Rivera", item.recipientName)
    assertEquals(ScheduleStatus.PENDING, item.statusEnum)

    val execResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.markScheduleExecuted(item, 1800000000000L)
    }
    assertTrue(execResult.isSuccess)
  }

  @Test
  fun createTemplateAndGroup_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = SkeditRepository(firestore)

    val tplResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createTemplate(
        title = "Weekly Report",
        category = TemplateCategory.WORK,
        channel = ScheduleChannel.EMAIL.id,
        content = "Hi team, please submit your weekly KPIs."
      )
    }
    assertTrue(tplResult.isSuccess)
    val tplId = tplResult.getOrThrow()

    val templates = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeTemplates(uid).first { list -> list.any { it.id == tplId } }
    }
    assertTrue(templates.any { it.id == tplId })

    val grpResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createGroup(
        name = "Product Team",
        channel = ScheduleChannel.TELEGRAM,
        membersSummary = "@alex, @priya",
        memberCount = 2L
      )
    }
    assertTrue(grpResult.isSuccess)
  }

  @Test
  fun getScheduleById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = SkeditRepository(firestore)

    val scheduleId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createSchedule(
        channel = ScheduleChannel.SMS,
        recipientName = "Private Contact",
        recipientTarget = "+1 555-0199",
        subject = "",
        messageBody = "Confidential reminder",
        attachmentLabel = "",
        scheduledEpochMillis = 1800000000000L,
        repeatInterval = RepeatInterval.NONE,
        askBeforeSending = false,
        notifyOnComplete = true
      ).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.getScheduleById(scheduleId = scheduleId, userId = aliceUid)
    }
    assertTrue(crossResult.isFailure)
    val firestoreException = crossResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun observeSchedules_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repository = SkeditRepository(firestore)

    var caughtException: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeSchedules(aliceUid).first()
      }
    } catch (e: Exception) {
      caughtException = e
    }
    assertNotNull("Expected exception for unauthenticated listener", caughtException)
    val firestoreException = caughtException?.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $caughtException", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@skedit.test"
    const val BOB_EMAIL = "bob@skedit.test"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
