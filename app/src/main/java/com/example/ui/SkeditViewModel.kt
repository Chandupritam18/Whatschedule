package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalSkeditStore
import com.example.data.model.MessageTemplate
import com.example.data.model.RecipientGroup
import com.example.data.model.RepeatInterval
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleStatus
import com.example.data.model.TemplateCategory
import com.example.data.repository.SkeditRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class StatusFilter(val label: String) {
  ALL("All"),
  PENDING("Pending"),
  PAUSED("Paused"),
  SENT("Sent"),
  FAILED("Failed")
}

class SkeditViewModel(
  private val repository: SkeditRepository,
  private val localStore: LocalSkeditStore,
  private val currentUserId: String?
) : ViewModel() {

  private val isCloudAuthenticated: Boolean
    get() = !currentUserId.isNullOrBlank()

  private val schedulesSourceFlow: Flow<List<ScheduleItem>> =
    if (!currentUserId.isNullOrBlank()) {
      repository.observeSchedules(currentUserId)
    } else {
      localStore.observeSchedules()
    }

  private val templatesSourceFlow: Flow<List<MessageTemplate>> =
    if (!currentUserId.isNullOrBlank()) {
      repository.observeTemplates(currentUserId)
    } else {
      localStore.observeTemplates()
    }

  private val groupsSourceFlow: Flow<List<RecipientGroup>> =
    if (!currentUserId.isNullOrBlank()) {
      repository.observeGroups(currentUserId)
    } else {
      localStore.observeGroups()
    }

  val schedulesState: StateFlow<UiState<List<ScheduleItem>>> = schedulesSourceFlow
    .map<List<ScheduleItem>, UiState<List<ScheduleItem>>> { list ->
      checkDueSchedules(list)
      UiState.Success(list)
    }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing schedules", rootError)
      emit(UiState.Error(rootError.message ?: "Failed to load schedules"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading
    )

  val templatesState: StateFlow<UiState<List<MessageTemplate>>> = templatesSourceFlow
    .map<List<MessageTemplate>, UiState<List<MessageTemplate>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing templates", rootError)
      emit(UiState.Error(rootError.message ?: "Failed to load templates"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading
    )

  val groupsState: StateFlow<UiState<List<RecipientGroup>>> = groupsSourceFlow
    .map<List<RecipientGroup>, UiState<List<RecipientGroup>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing groups", rootError)
      emit(UiState.Error(rootError.message ?: "Failed to load recipient groups"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading
    )

  private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
  val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

  private val _selectedStatusFilter = MutableStateFlow(StatusFilter.ALL)
  val selectedStatusFilter: StateFlow<StatusFilter> = _selectedStatusFilter.asStateFlow()

  private val _selectedChannelFilter = MutableStateFlow<ScheduleChannel?>(null)
  val selectedChannelFilter: StateFlow<ScheduleChannel?> = _selectedChannelFilter.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _feedbackBanner = MutableStateFlow<String?>(null)
  val feedbackBanner: StateFlow<String?> = _feedbackBanner.asStateFlow()

  private val _duePromptSchedule = MutableStateFlow<ScheduleItem?>(null)
  val duePromptSchedule: StateFlow<ScheduleItem?> = _duePromptSchedule.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _defaultAskBeforeSending = MutableStateFlow(false)
  val defaultAskBeforeSending: StateFlow<Boolean> = _defaultAskBeforeSending.asStateFlow()

  private val _defaultNotifyOnComplete = MutableStateFlow(true)
  val defaultNotifyOnComplete: StateFlow<Boolean> = _defaultNotifyOnComplete.asStateFlow()

  private val _daysRemainingInMonth = MutableStateFlow(localStore.getDaysRemainingInMonth())
  val daysRemainingInMonth: StateFlow<Int> = _daysRemainingInMonth.asStateFlow()

  private val _isMonthlyLimitExpired = MutableStateFlow(localStore.isMonthlyLimitExpired())
  val isMonthlyLimitExpired: StateFlow<Boolean> = _isMonthlyLimitExpired.asStateFlow()

  private val promptedScheduleIds = mutableSetOf<String>()

  init {
    viewModelScope.launch {
      while (true) {
        delay(1000L)
        val now = System.currentTimeMillis()
        _currentTimeMillis.value = now
        _daysRemainingInMonth.value = localStore.getDaysRemainingInMonth(now)
        _isMonthlyLimitExpired.value = localStore.isMonthlyLimitExpired(now)
        val currentList = (schedulesState.value as? UiState.Success)?.data
        if (currentList != null) {
          checkDueSchedules(currentList, now)
        }
      }
    }
  }

  private fun checkDueSchedules(
    schedules: List<ScheduleItem>,
    now: Long = System.currentTimeMillis()
  ) {
    val dueItems = schedules.filter {
      it.statusEnum == ScheduleStatus.PENDING &&
        it.scheduledEpochMillis in 1..now &&
        it.id.isNotBlank() &&
        !promptedScheduleIds.contains(it.id)
    }
    val firstDue = dueItems.firstOrNull() ?: return
    promptedScheduleIds.add(firstDue.id)
    if (firstDue.askBeforeSending) {
      _duePromptSchedule.value = firstDue
    } else {
      viewModelScope.launch {
        if (isCloudAuthenticated) {
          repository.markScheduleExecuted(firstDue, now)
        } else {
          localStore.markScheduleExecuted(firstDue, now)
        }
        _feedbackBanner.value =
          "Auto-Scheduled ${firstDue.channelEnum.displayName} to ${firstDue.recipientName} triggered!"
      }
    }
  }

  fun setStatusFilter(filter: StatusFilter) {
    _selectedStatusFilter.value = filter
  }

  fun setChannelFilter(channel: ScheduleChannel?) {
    _selectedChannelFilter.value = channel
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun clearFeedbackBanner() {
    _feedbackBanner.value = null
  }

  fun showFeedback(message: String) {
    _feedbackBanner.value = message
  }

  fun dismissDuePrompt() {
    _duePromptSchedule.value = null
  }

  fun setDefaultAskBeforeSending(enabled: Boolean) {
    _defaultAskBeforeSending.value = enabled
  }

  fun setDefaultNotifyOnComplete(enabled: Boolean) {
    _defaultNotifyOnComplete.value = enabled
  }

  fun saveSchedule(
    existingItem: ScheduleItem?,
    channel: ScheduleChannel,
    recipientName: String,
    recipientTarget: String,
    subject: String,
    messageBody: String,
    attachmentLabel: String,
    scheduledEpochMillis: Long,
    repeatInterval: RepeatInterval,
    askBeforeSending: Boolean,
    notifyOnComplete: Boolean,
    onComplete: () -> Unit = {}
  ) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      if (localStore.isMonthlyLimitExpired(now)) {
        _feedbackBanner.value = "1-month limit reached. No new or updated schedules permitted."
        return@launch
      }
      if (scheduledEpochMillis > now + LocalSkeditStore.ONE_MONTH_MILLIS + 60_000L) {
        _feedbackBanner.value = "Schedules can only be set up to 1 month (30 days) ahead."
        return@launch
      }
      _isBusy.value = true
      val result = if (existingItem != null && existingItem.id.isNotBlank() && existingItem.createdAt != null) {
        promptedScheduleIds.remove(existingItem.id)
        val updatedObj = existingItem.copy(
          channel = channel.id,
          recipientName = recipientName,
          recipientTarget = recipientTarget,
          subject = subject,
          messageBody = messageBody,
          attachmentLabel = attachmentLabel,
          scheduledEpochMillis = scheduledEpochMillis,
          repeatInterval = repeatInterval.id,
          askBeforeSending = askBeforeSending,
          notifyOnComplete = notifyOnComplete,
          status = ScheduleStatus.PENDING.id
        )
        if (isCloudAuthenticated) {
          repository.updateSchedule(updatedObj).map { existingItem.id }
        } else {
          localStore.updateSchedule(updatedObj).map { existingItem.id }
        }
      } else {
        val currentCount = (schedulesState.value as? UiState.Success)?.data?.size ?: 0
        if (currentCount >= MAX_SCHEDULED_MESSAGES) {
          _isBusy.value = false
          _feedbackBanner.value =
            "Schedule limit reached ($currentCount/$MAX_SCHEDULED_MESSAGES). Delete an existing schedule to add a new one."
          return@launch
        }
        if (isCloudAuthenticated) {
          repository.createSchedule(
            channel = channel,
            recipientName = recipientName,
            recipientTarget = recipientTarget,
            subject = subject,
            messageBody = messageBody,
            attachmentLabel = attachmentLabel,
            scheduledEpochMillis = scheduledEpochMillis,
            repeatInterval = repeatInterval,
            askBeforeSending = askBeforeSending,
            notifyOnComplete = notifyOnComplete
          )
        } else {
          localStore.createSchedule(
            channel = channel,
            recipientName = recipientName,
            recipientTarget = recipientTarget,
            subject = subject,
            messageBody = messageBody,
            attachmentLabel = attachmentLabel,
            scheduledEpochMillis = scheduledEpochMillis,
            repeatInterval = repeatInterval,
            askBeforeSending = askBeforeSending,
            notifyOnComplete = notifyOnComplete
          )
        }
      }
      _isBusy.value = false
      result.fold(
        onSuccess = {
          _feedbackBanner.value = if (existingItem != null) {
            "Updated ${channel.displayName} schedule for $recipientName"
          } else {
            "Scheduled ${channel.displayName} for $recipientName"
          }
          onComplete()
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Failed to save schedule"
        }
      )
    }
  }

  fun togglePauseResume(schedule: ScheduleItem) {
    viewModelScope.launch {
      val nextStatus = if (schedule.statusEnum == ScheduleStatus.PAUSED) {
        ScheduleStatus.PENDING
      } else {
        ScheduleStatus.PAUSED
      }
      val res = if (isCloudAuthenticated) {
        repository.updateScheduleStatus(schedule, nextStatus)
      } else {
        localStore.updateScheduleStatus(schedule, nextStatus)
      }
      res.fold(
        onSuccess = {
          _feedbackBanner.value = "${schedule.recipientName}: ${nextStatus.label}"
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Failed to update status"
        }
      )
    }
  }

  fun markExecuted(schedule: ScheduleItem) {
    viewModelScope.launch {
      _duePromptSchedule.value = null
      val res = if (isCloudAuthenticated) {
        repository.markScheduleExecuted(schedule)
      } else {
        localStore.markScheduleExecuted(schedule)
      }
      res.fold(
        onSuccess = {
          val msg = if (schedule.repeatEnum != RepeatInterval.NONE) {
            "Sent & scheduled next ${schedule.repeatEnum.label.lowercase()} run for ${schedule.recipientName}"
          } else {
            "Marked ${schedule.channelEnum.displayName} to ${schedule.recipientName} as Sent"
          }
          _feedbackBanner.value = msg
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Failed to mark schedule sent"
        }
      )
    }
  }

  fun snoozeSchedule(schedule: ScheduleItem, minutes: Int = 15) {
    val existingCreatedAt = schedule.createdAt ?: return
    viewModelScope.launch {
      _duePromptSchedule.value = null
      promptedScheduleIds.remove(schedule.id)
      val newTime = maxOf(System.currentTimeMillis(), schedule.scheduledEpochMillis) + minutes * 60_000L
      val updatedObj = schedule.copy(
        scheduledEpochMillis = newTime,
        status = ScheduleStatus.PENDING.id,
        createdAt = existingCreatedAt
      )
      val res = if (isCloudAuthenticated) {
        repository.updateSchedule(updatedObj)
      } else {
        localStore.updateSchedule(updatedObj)
      }
      res.fold(
        onSuccess = {
          _feedbackBanner.value = "Snoozed ${schedule.recipientName} by +$minutes mins"
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Failed to snooze schedule"
        }
      )
    }
  }

  fun deleteSchedule(scheduleId: String) {
    viewModelScope.launch {
      val res = if (isCloudAuthenticated) {
        repository.deleteSchedule(scheduleId)
      } else {
        localStore.deleteSchedule(scheduleId)
      }
      res.fold(
        onSuccess = {
          _feedbackBanner.value = "Schedule deleted"
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Failed to delete schedule"
        }
      )
    }
  }

  fun createTemplate(
    title: String,
    category: TemplateCategory,
    channel: String,
    content: String,
    onComplete: () -> Unit = {}
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = if (isCloudAuthenticated) {
        repository.createTemplate(title, category, channel, content)
      } else {
        localStore.createTemplate(title, category, channel, content)
      }
      _isBusy.value = false
      res.fold(
        onSuccess = {
          _feedbackBanner.value = "Saved template \"$title\""
          onComplete()
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Failed to save template"
        }
      )
    }
  }

  fun recordTemplateUsed(template: MessageTemplate) {
    viewModelScope.launch {
      if (isCloudAuthenticated) {
        repository.incrementTemplateUsage(template)
      } else {
        localStore.incrementTemplateUsage(template)
      }
    }
  }

  fun deleteTemplate(templateId: String) {
    viewModelScope.launch {
      val res = if (isCloudAuthenticated) {
        repository.deleteTemplate(templateId)
      } else {
        localStore.deleteTemplate(templateId)
      }
      res.fold(
        onSuccess = { _feedbackBanner.value = "Template removed" },
        onFailure = { err -> _feedbackBanner.value = err.localizedMessage ?: "Failed to delete template" }
      )
    }
  }

  fun createGroup(
    name: String,
    channel: ScheduleChannel,
    membersSummary: String,
    memberCount: Long,
    onComplete: () -> Unit = {}
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = if (isCloudAuthenticated) {
        repository.createGroup(name, channel, membersSummary, memberCount)
      } else {
        localStore.createGroup(name, channel, membersSummary, memberCount)
      }
      _isBusy.value = false
      res.fold(
        onSuccess = {
          _feedbackBanner.value = "Created recipient group \"$name\""
          onComplete()
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Failed to create group"
        }
      )
    }
  }

  fun deleteGroup(groupId: String) {
    viewModelScope.launch {
      val res = if (isCloudAuthenticated) {
        repository.deleteGroup(groupId)
      } else {
        localStore.deleteGroup(groupId)
      }
      res.fold(
        onSuccess = { _feedbackBanner.value = "Recipient group deleted" },
        onFailure = { err -> _feedbackBanner.value = err.localizedMessage ?: "Failed to delete group" }
      )
    }
  }

  fun loadStarterKit() {
    viewModelScope.launch {
      val currentCount = (schedulesState.value as? UiState.Success)?.data?.size ?: 0
      if (currentCount >= MAX_SCHEDULED_MESSAGES) {
        _feedbackBanner.value =
          "Schedule limit reached ($currentCount/$MAX_SCHEDULED_MESSAGES). Delete an existing schedule before loading samples."
        return@launch
      }
      _isBusy.value = true
      val res = if (isCloudAuthenticated) {
        repository.seedStarterKit()
      } else {
        localStore.seedStarterKit()
      }
      res.fold(
        onSuccess = {
          _feedbackBanner.value = "Loaded sample schedules, templates & recipient groups!"
        },
        onFailure = { err ->
          _feedbackBanner.value = err.localizedMessage ?: "Could not load starter kit"
        }
      )
      _isBusy.value = false
    }
  }

  companion object {
    private const val TAG = "SkeditViewModel"
    private const val STOP_TIMEOUT_MILLIS = 5000L
    const val MAX_SCHEDULED_MESSAGES = 18
    const val MAX_HORIZON_DAYS = LocalSkeditStore.MAX_SCHEDULE_HORIZON_DAYS
  }
}
