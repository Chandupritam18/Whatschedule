package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.R
import com.example.data.model.ScheduleChannel
import com.example.util.SelectedWhatsAppContact
import com.example.util.WhatsAppContactSelectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SkeditAccessibilityService : AccessibilityService() {

  private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var windowManager: WindowManager? = null
  private var overlayView: View? = null
  private var countTextView: TextView? = null
  private var latestContactTextView: TextView? = null
  private var lastCapturedKey: String = ""

  override fun onServiceConnected() {
    super.onServiceConnected()
    windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

    serviceScope.launch {
      combine(
        WhatsAppContactSelectionManager.isSelectionActive,
        WhatsAppContactSelectionManager.selectedContacts
      ) { active, contacts -> active to contacts }
        .collect { (active, contacts) ->
          if (active) {
            showOrUpdateOverlay(contacts)
          } else {
            lastCapturedKey = ""
            removeOverlay()
          }
        }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!WhatsAppContactSelectionManager.isSelectionActive.value) return
    val pkg = event?.packageName?.toString() ?: return
    if (pkg != PACKAGE_WHATSAPP && pkg != PACKAGE_WHATSAPP_BUSINESS) return

    val root = rootInActiveWindow ?: return
    val detected = extractOpenedChatContact(root, pkg) ?: return
    val (name, target) = detected

    val channel = if (pkg == PACKAGE_WHATSAPP_BUSINESS) {
      ScheduleChannel.WHATSAPP_BUSINESS
    } else {
      ScheduleChannel.WHATSAPP
    }

    val captureKey = "${pkg}|$name|$target"
    if (captureKey != lastCapturedKey) {
      lastCapturedKey = captureKey
      WhatsAppContactSelectionManager.addSelectedContact(
        name = name,
        target = target,
        channel = channel
      )
    }
  }

  override fun onInterrupt() {
    removeOverlay()
  }

  override fun onDestroy() {
    removeOverlay()
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun extractOpenedChatContact(
    root: AccessibilityNodeInfo,
    pkg: String
  ): Pair<String, String>? {
    // 1. Direct WhatsApp conversation toolbar contact title ID
    val titleNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/conversation_contact_name")
    val contactName = titleNodes
      ?.firstOrNull { !it.text.isNullOrBlank() }
      ?.text
      ?.toString()
      ?.trim()

    if (!contactName.isNullOrBlank() && !isIgnoredHeader(contactName)) {
      val subtitleNodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/conversation_contact_status")
      val subtitle = subtitleNodes
        ?.firstOrNull { !it.text.isNullOrBlank() }
        ?.text
        ?.toString()
        ?.trim()
        .orEmpty()
      val phoneTarget = extractPhoneIfPresent(subtitle).ifBlank {
        extractPhoneIfPresent(contactName).ifBlank { contactName }
      }
      return contactName to phoneTarget
    }

    // 2. Fallback: Verify a chat composer input box ("entry") is on screen, then read top-left header
    val hasChatComposer = root.findAccessibilityNodeInfosByViewId("$pkg:id/entry").isNotEmpty() ||
      hasMessageInputBox(root)
    if (!hasChatComposer) return null

    val headerTitle = findTopToolbarTitle(root) ?: return null
    if (isIgnoredHeader(headerTitle)) return null

    val phoneTarget = extractPhoneIfPresent(headerTitle).ifBlank { headerTitle }
    return headerTitle to phoneTarget
  }

  private fun hasMessageInputBox(node: AccessibilityNodeInfo): Boolean {
    val className = node.className?.toString().orEmpty()
    if (className.endsWith("EditText")) {
      val hint = node.hintText?.toString()?.lowercase().orEmpty()
      if (hint.contains("message") || hint.contains("type")) {
        return true
      }
    }
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      if (hasMessageInputBox(child)) return true
    }
    return false
  }

  private fun findTopToolbarTitle(root: AccessibilityNodeInfo): String? {
    val candidates = mutableListOf<Pair<Int, String>>()
    collectTopTextViews(root, candidates)
    return candidates
      .sortedBy { it.first }
      .map { it.second }
      .firstOrNull { text ->
        text.length in 2..60 && !isIgnoredHeader(text)
      }
  }

  private fun collectTopTextViews(
    node: AccessibilityNodeInfo,
    out: MutableList<Pair<Int, String>>
  ) {
    val className = node.className?.toString().orEmpty()
    if (className.endsWith("TextView") && !node.text.isNullOrBlank()) {
      val rect = android.graphics.Rect()
      node.getBoundsInScreen(rect)
      if (rect.top in 0..320 && rect.height() > 0) {
        out.add(rect.top to node.text.toString().trim())
      }
    }
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      collectTopTextViews(child, out)
    }
  }

  private fun isIgnoredHeader(text: String): Boolean {
    val lower = text.trim().lowercase()
    return lower == "whatsapp" ||
      lower == "whatsapp business" ||
      lower == "chats" ||
      lower == "updates" ||
      lower == "calls" ||
      lower == "communities" ||
      lower == "search" ||
      lower == "search..." ||
      lower == "new chat" ||
      lower == "select contact"
  }

  private fun extractPhoneIfPresent(raw: String): String {
    val match = Regex("""\+?\d[\d\s\-()]{6,}\d""").find(raw)
    return match?.value?.replace(Regex("[^0-9+]"), "").orEmpty()
  }

  private fun showOrUpdateOverlay(contacts: List<SelectedWhatsAppContact>) {
    if (overlayView == null) {
      val container = buildFloatingPopupView()
      val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
      ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(12)
        y = dp(76)
      }
      try {
        windowManager?.addView(container, params)
        overlayView = container
      } catch (_: Exception) {
        overlayView = null
      }
    }

    val count = contacts.size
    countTextView?.text = when (count) {
      0 -> "Open a WhatsApp chat"
      1 -> "1 contact selected"
      else -> "$count contacts selected"
    }
    latestContactTextView?.text = contacts.lastOrNull()?.let {
      "Latest: ${it.name}"
    } ?: "Tap any chat to auto-select"
  }

  private fun buildFloatingPopupView(): View {
    val cardBg = GradientDrawable().apply {
      setColor(Color.parseColor("#FFFFFF"))
      cornerRadius = dp(18).toFloat()
      setStroke(dp(1), Color.parseColor("#16A34A"))
    }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = cardBg
      elevation = dp(8).toFloat()
      setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val logoView = ImageView(this).apply {
      setImageResource(R.drawable.whatschedule_logo)
      scaleType = ImageView.ScaleType.FIT_CENTER
      contentDescription = getString(R.string.app_name)
      layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
        marginEnd = dp(6)
      }
    }

    val badge = TextView(this).apply {
      text = "Whatschedule"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
      setTypeface(typeface, Typeface.BOLD)
      setPadding(dp(7), dp(2), dp(7), dp(2))
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#008F6F"))
        cornerRadius = dp(10).toFloat()
      }
    }

    val titleView = TextView(this).apply {
      setTextColor(Color.parseColor("#0F172A"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(typeface, Typeface.BOLD)
      setPadding(dp(8), 0, dp(8), 0)
    }
    countTextView = titleView

    val closeView = TextView(this).apply {
      text = "✕"
      setTextColor(Color.parseColor("#64748B"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setPadding(dp(6), dp(2), dp(2), dp(2))
      setOnClickListener {
        WhatsAppContactSelectionManager.stopSelection()
      }
    }

    topRow.addView(logoView)
    topRow.addView(badge)
    topRow.addView(titleView)
    topRow.addView(closeView)

    val subtitleView = TextView(this).apply {
      setTextColor(Color.parseColor("#475569"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      maxLines = 1
      ellipsize = TextUtils.TruncateAt.END
      setPadding(0, dp(4), 0, dp(8))
    }
    latestContactTextView = subtitleView

    val doneButton = TextView(this).apply {
      text = "Done • Return to Whatschedule"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setTypeface(typeface, Typeface.BOLD)
      gravity = Gravity.CENTER
      setPadding(dp(12), dp(7), dp(12), dp(7))
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#008F6F"))
        cornerRadius = dp(12).toFloat()
      }
      setOnClickListener {
        WhatsAppContactSelectionManager.finishSelectionAndReturn()
        val returnIntent = Intent(this@SkeditAccessibilityService, MainActivity::class.java).apply {
          addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_SINGLE_TOP or
              Intent.FLAG_ACTIVITY_CLEAR_TOP
          )
        }
        startActivity(returnIntent)
      }
    }

    root.addView(topRow)
    root.addView(subtitleView)
    root.addView(doneButton)
    return root
  }

  private fun removeOverlay() {
    val view = overlayView ?: return
    try {
      windowManager?.removeView(view)
    } catch (_: Exception) {
    } finally {
      overlayView = null
      countTextView = null
      latestContactTextView = null
    }
  }

  private fun dp(value: Int): Int {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      resources.displayMetrics
    ).toInt()
  }

  companion object {
    private const val PACKAGE_WHATSAPP = "com.whatsapp"
    private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
  }
}
