package com.example.util

import com.example.data.model.ScheduleChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SelectedWhatsAppContact(
  val name: String,
  val target: String,
  val channel: ScheduleChannel,
  val selectedAtMillis: Long = System.currentTimeMillis()
)

object WhatsAppContactSelectionManager {

  private val _isSelectionActive = MutableStateFlow(false)
  val isSelectionActive: StateFlow<Boolean> = _isSelectionActive.asStateFlow()

  private val _activeChannel = MutableStateFlow(ScheduleChannel.WHATSAPP)
  val activeChannel: StateFlow<ScheduleChannel> = _activeChannel.asStateFlow()

  private val _selectedContacts = MutableStateFlow<List<SelectedWhatsAppContact>>(emptyList())
  val selectedContacts: StateFlow<List<SelectedWhatsAppContact>> = _selectedContacts.asStateFlow()

  private val _shouldReturnToComposer = MutableStateFlow(false)
  val shouldReturnToComposer: StateFlow<Boolean> = _shouldReturnToComposer.asStateFlow()

  fun startSelection(channel: ScheduleChannel) {
    _activeChannel.value = if (channel == ScheduleChannel.WHATSAPP_BUSINESS) {
      ScheduleChannel.WHATSAPP_BUSINESS
    } else {
      ScheduleChannel.WHATSAPP
    }
    _shouldReturnToComposer.value = false
    _isSelectionActive.value = true
  }

  fun addSelectedContact(name: String, target: String, channel: ScheduleChannel = _activeChannel.value) {
    val cleanName = name.trim()
    val cleanTarget = target.trim().ifBlank { cleanName }
    if (cleanName.isBlank()) return

    val current = _selectedContacts.value
    val alreadyExists = current.any { existing ->
      existing.name.equals(cleanName, ignoreCase = true) ||
        (cleanTarget.isNotBlank() && existing.target.equals(cleanTarget, ignoreCase = true))
    }
    if (!alreadyExists) {
      _selectedContacts.value = current + SelectedWhatsAppContact(
        name = cleanName,
        target = cleanTarget,
        channel = channel
      )
    }
  }

  fun removeContact(contact: SelectedWhatsAppContact) {
    _selectedContacts.value = _selectedContacts.value.filterNot {
      it.name.equals(contact.name, ignoreCase = true) &&
        it.target.equals(contact.target, ignoreCase = true)
    }
  }

  fun clearSelection() {
    _selectedContacts.value = emptyList()
  }

  fun stopSelection() {
    _isSelectionActive.value = false
  }

  fun finishSelectionAndReturn() {
    _isSelectionActive.value = false
    _shouldReturnToComposer.value = true
  }

  fun consumeReturnToComposer() {
    _shouldReturnToComposer.value = false
  }

  fun formatRecipientNameSummary(contacts: List<SelectedWhatsAppContact> = _selectedContacts.value): String {
    return when (contacts.size) {
      0 -> ""
      1 -> contacts.first().name
      else -> "${contacts.size} Contacts (${contacts.joinToString(", ") { it.name }.take(80)})"
    }
  }

  fun formatRecipientTargetSummary(contacts: List<SelectedWhatsAppContact> = _selectedContacts.value): String {
    return contacts.joinToString(", ") { it.target }
  }
}
