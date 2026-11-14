package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalSkeditStore
import com.example.data.model.ScheduleChannel
import com.example.data.model.ScheduleItem
import com.example.data.repository.SkeditRepository
import com.example.ui.SkeditViewModel
import com.example.ui.UiState
import com.example.ui.auth.signOut
import com.example.ui.components.PermissionsSetupDialog
import com.example.ui.screens.CalendarTimelineScreen
import com.example.ui.screens.ScheduleComposerSheet
import com.example.ui.screens.SchedulesDashboardScreen
import com.example.ui.screens.SettingsAndAutomationScreen
import com.example.ui.screens.TemplatesAndGroupsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ChannelDispatcher
import com.example.util.PermissionHelper
import com.example.util.WhatsAppContactSelectionManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT
      ),
      navigationBarStyle = SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT
      )
    )
    setContent {
      var isDarkMode by remember { mutableStateOf(false) }

      DisposableEffect(isDarkMode) {
        enableEdgeToEdge(
          statusBarStyle = if (isDarkMode) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
          } else {
            SystemBarStyle.light(
              android.graphics.Color.TRANSPARENT,
              android.graphics.Color.TRANSPARENT
            )
          },
          navigationBarStyle = if (isDarkMode) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
          } else {
            SystemBarStyle.light(
              android.graphics.Color.TRANSPARENT,
              android.graphics.Color.TRANSPARENT
            )
          }
        )
        onDispose {}
      }

      MyApplicationTheme(darkTheme = isDarkMode) {
        SkeditAppRoot(
          isDarkMode = isDarkMode,
          onToggleDarkMode = { isDarkMode = it }
        )
      }
    }
  }
}

@Composable
fun SkeditAppRoot(
  isDarkMode: Boolean,
  onToggleDarkMode: (Boolean) -> Unit
) {
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
  var showSplash by remember { mutableStateOf(true) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
    }
    Firebase.auth.addAuthStateListener(listener)
    onDispose {
      Firebase.auth.removeAuthStateListener(listener)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // Opens directly without requiring login! Keys by user UID or "local_no_login_session"
    val sessionKey = currentUser?.uid ?: "local_no_login_session"
    key(sessionKey) {
      val viewModel: SkeditViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) {
              "APPLICATION_KEY missing from CreationExtras"
            }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            SkeditViewModel(
              repository = SkeditRepository(db),
              localStore = LocalSkeditStore(app),
              currentUserId = currentUser?.uid
            )
          }
        }
      )
      SkeditMainContent(
        currentUser = currentUser,
        isDarkMode = isDarkMode,
        isSplashFinished = !showSplash,
        onToggleDarkMode = onToggleDarkMode,
        onRefreshAuth = { currentUser = Firebase.auth.currentUser },
        viewModel = viewModel
      )
    }

    AnimatedVisibility(
      visible = showSplash,
      exit = fadeOut(tween(durationMillis = 420, easing = FastOutSlowInEasing))
    ) {
      WhatscheduleOpeningSplash(
        onFinished = { showSplash = false }
      )
    }
  }
}

@Composable
fun WhatscheduleOpeningSplash(
  onFinished: () -> Unit
) {
  val logoScale = remember { Animatable(0.72f) }
  val logoAlpha = remember { Animatable(0f) }
  val glowAlpha = remember { Animatable(0f) }
  val textAlpha = remember { Animatable(0f) }
  val textOffsetY = remember { Animatable(18f) }

  LaunchedEffect(Unit) {
    launch {
      logoAlpha.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
      )
    }
    launch {
      logoScale.animateTo(
        targetValue = 1f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioLowBouncy,
          stiffness = Spring.StiffnessVeryLow
        )
      )
    }
    launch {
      glowAlpha.animateTo(
        targetValue = 0.28f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
      )
    }
    launch {
      delay(220L)
      launch {
        textAlpha.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
        )
      }
      launch {
        textOffsetY.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
        )
      }
    }
    delay(1500L)
    onFinished()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .testTag("whatschedule_opening_splash"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 32.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(196.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              brush = Brush.radialGradient(
                colors = listOf(
                  Color(0xFF1DB964).copy(alpha = glowAlpha.value),
                  Color.Transparent
                )
              ),
              shape = CircleShape
            )
        )
        Image(
          painter = painterResource(id = R.drawable.whatschedule_logo),
          contentDescription = stringResource(R.string.app_name),
          contentScale = ContentScale.Fit,
          modifier = Modifier
            .size(148.dp)
            .scale(logoScale.value)
            .alpha(logoAlpha.value)
            .testTag("splash_logo")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
          .alpha(textAlpha.value)
          .offset(y = textOffsetY.value.dp)
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = stringResource(R.string.top_creator_banner),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
          .alpha(textAlpha.value)
          .offset(y = textOffsetY.value.dp)
      )
    }
  }
}

enum class MainNavDestination(val labelRes: Int) {
  SCHEDULES(R.string.nav_schedules),
  CALENDAR(R.string.nav_calendar),
  TEMPLATES(R.string.nav_templates),
  SETTINGS(R.string.nav_settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkeditMainContent(
  currentUser: FirebaseUser?,
  isDarkMode: Boolean,
  isSplashFinished: Boolean,
  onToggleDarkMode: (Boolean) -> Unit,
  onRefreshAuth: () -> Unit,
  viewModel: SkeditViewModel
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
  val templatesState by viewModel.templatesState.collectAsStateWithLifecycle()
  val groupsState by viewModel.groupsState.collectAsStateWithLifecycle()
  val selectedStatusFilter by viewModel.selectedStatusFilter.collectAsStateWithLifecycle()
  val selectedChannelFilter by viewModel.selectedChannelFilter.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val currentTimeMillis by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
  val feedbackBanner by viewModel.feedbackBanner.collectAsStateWithLifecycle()
  val duePromptSchedule by viewModel.duePromptSchedule.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val defaultAskBeforeSending by viewModel.defaultAskBeforeSending.collectAsStateWithLifecycle()
  val defaultNotifyOnComplete by viewModel.defaultNotifyOnComplete.collectAsStateWithLifecycle()
  val daysRemainingInMonth by viewModel.daysRemainingInMonth.collectAsStateWithLifecycle()
  val isMonthlyLimitExpired by viewModel.isMonthlyLimitExpired.collectAsStateWithLifecycle()

  val selectedWhatsAppContacts by WhatsAppContactSelectionManager.selectedContacts.collectAsStateWithLifecycle()
  val isWhatsAppSelectionActive by WhatsAppContactSelectionManager.isSelectionActive.collectAsStateWithLifecycle()
  val shouldReturnToComposer by WhatsAppContactSelectionManager.shouldReturnToComposer.collectAsStateWithLifecycle()

  var currentDestination by remember { mutableStateOf(MainNavDestination.SCHEDULES) }
  var templatesSubTab by remember { mutableIntStateOf(0) }

  // Composer sheet state
  var isComposerOpen by remember { mutableStateOf(false) }
  var editingSchedule by remember { mutableStateOf<ScheduleItem?>(null) }
  var composerChannel by remember { mutableStateOf(ScheduleChannel.WHATSAPP) }
  var composerInitialRecipientName by remember { mutableStateOf("") }
  var composerInitialRecipientTarget by remember { mutableStateOf("") }
  var composerInitialMessageBody by remember { mutableStateOf("") }

  if (currentDestination != MainNavDestination.SCHEDULES) {
    BackHandler {
      currentDestination = MainNavDestination.SCHEDULES
    }
  }

  val templatesList = (templatesState as? UiState.Success)?.data ?: emptyList()
  val groupsList = (groupsState as? UiState.Success)?.data ?: emptyList()
  val currentScheduleCount = (schedulesState as? UiState.Success)?.data?.size ?: 0

  val lifecycleOwner = LocalLifecycleOwner.current
  var permissionsStatus by remember { mutableStateOf(PermissionHelper.checkAll(context)) }
  var showPermissionsDialog by remember { mutableStateOf(false) }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    permissionsStatus = PermissionHelper.checkAll(context)
    if (granted) {
      viewModel.showFeedback("Notification alerts enabled!")
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val updated = PermissionHelper.checkAll(context)
        permissionsStatus = updated
        if (updated.requiredGranted) {
          showPermissionsDialog = false
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  LaunchedEffect(isSplashFinished) {
    if (!isSplashFinished) return@LaunchedEffect
    val current = PermissionHelper.checkAll(context)
    permissionsStatus = current
    if (!current.requiredGranted) {
      showPermissionsDialog = true
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !current.notificationsGranted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  LaunchedEffect(shouldReturnToComposer) {
    if (shouldReturnToComposer) {
      WhatsAppContactSelectionManager.consumeReturnToComposer()
      val contacts = WhatsAppContactSelectionManager.selectedContacts.value
      editingSchedule = null
      composerChannel = WhatsAppContactSelectionManager.activeChannel.value
      composerInitialRecipientName = WhatsAppContactSelectionManager.formatRecipientNameSummary(contacts)
      composerInitialRecipientTarget = WhatsAppContactSelectionManager.formatRecipientTargetSummary(contacts)
      isComposerOpen = true
    }
  }

  fun ensurePermissionsGrantedForScheduling(): Boolean {
    val latest = PermissionHelper.checkAll(context)
    permissionsStatus = latest
    if (!latest.requiredGranted) {
      showPermissionsDialog = true
      viewModel.showFeedback(
        "You cannot schedule without permission. Please grant Accessibility, Notifications, and Alarms first."
      )
      return false
    }
    return true
  }

  fun openNewScheduleComposer(
    channel: ScheduleChannel,
    recipientName: String = "",
    recipientTarget: String = "",
    messageBody: String = ""
  ) {
    if (!ensurePermissionsGrantedForScheduling()) return
    if (isMonthlyLimitExpired) {
      viewModel.showFeedback("1-month usage limit reached. No new schedules can be added.")
      return
    }
    if (currentScheduleCount >= SkeditViewModel.MAX_SCHEDULED_MESSAGES) {
      viewModel.showFeedback(
        "Maximum ${SkeditViewModel.MAX_SCHEDULED_MESSAGES} scheduled messages reached ($currentScheduleCount/${SkeditViewModel.MAX_SCHEDULED_MESSAGES}). Delete an existing schedule to add a new one."
      )
      return
    }
    editingSchedule = null
    composerChannel = channel
    composerInitialRecipientName = recipientName
    composerInitialRecipientTarget = recipientTarget
    composerInitialMessageBody = messageBody
    isComposerOpen = true
  }

  Box(modifier = Modifier.fillMaxSize()) {
  Scaffold(
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.background)
          .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        // Prominent Top Banner: "Made by Chandu Pritam"
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.horizontalGradient(
                colors = listOf(
                  Color(0xFF007A5E),
                  Color(0xFF0A3A6E),
                  Color(0xFF007A5E)
                )
              )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("sena_creator_top_banner"),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = Color(0xFFFFD54F),
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.top_creator_banner),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = Color(0xFFFFD54F),
              modifier = Modifier.size(16.dp)
            )
          }
        }

        TopAppBar(
          windowInsets = WindowInsets(0, 0, 0, 0),
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Image(
                painter = painterResource(id = R.drawable.whatschedule_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .size(40.dp)
                  .testTag("top_bar_logo")
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = stringResource(R.string.top_creator_banner),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          },
          actions = {
            FilledTonalButton(
              onClick = {
                openNewScheduleComposer(ScheduleChannel.WHATSAPP)
              },
              modifier = Modifier
                .padding(end = 8.dp)
                .testTag("top_bar_new_schedule_button")
            ) {
              Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Schedule")
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          )
        )
      }
    },
    floatingActionButton = {
      if (currentDestination == MainNavDestination.SCHEDULES || currentDestination == MainNavDestination.CALENDAR) {
        ExtendedFloatingActionButton(
          onClick = {
            openNewScheduleComposer(selectedChannelFilter ?: ScheduleChannel.WHATSAPP)
          },
          icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
          text = { Text(stringResource(R.string.action_new_schedule)) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("fab_new_schedule")
        )
      }
    },
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = currentDestination == MainNavDestination.SCHEDULES,
          onClick = { currentDestination = MainNavDestination.SCHEDULES },
          icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ScheduleSend, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_schedules)) },
          modifier = Modifier.testTag("nav_tab_schedules")
        )
        NavigationBarItem(
          selected = currentDestination == MainNavDestination.CALENDAR,
          onClick = { currentDestination = MainNavDestination.CALENDAR },
          icon = { Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_calendar)) },
          modifier = Modifier.testTag("nav_tab_calendar")
        )
        NavigationBarItem(
          selected = currentDestination == MainNavDestination.TEMPLATES,
          onClick = { currentDestination = MainNavDestination.TEMPLATES },
          icon = { Icon(imageVector = Icons.Default.Description, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_templates)) },
          modifier = Modifier.testTag("nav_tab_templates")
        )
        NavigationBarItem(
          selected = currentDestination == MainNavDestination.SETTINGS,
          onClick = { currentDestination = MainNavDestination.SETTINGS },
          icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_settings)) },
          modifier = Modifier.testTag("nav_tab_settings")
        )
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Transient Status Banner
      AnimatedVisibility(
        visible = feedbackBanner != null,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = feedbackBanner ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
            IconButton(
              onClick = { viewModel.clearFeedbackBanner() },
              modifier = Modifier.size(26.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss message",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      when (currentDestination) {
        MainNavDestination.SCHEDULES -> {
          SchedulesDashboardScreen(
            schedulesState = schedulesState,
            selectedStatusFilter = selectedStatusFilter,
            selectedChannelFilter = selectedChannelFilter,
            searchQuery = searchQuery,
            currentTimeMillis = currentTimeMillis,
            isBusy = isBusy,
            onSelectStatusFilter = viewModel::setStatusFilter,
            onSelectChannelFilter = viewModel::setChannelFilter,
            onSearchQueryChange = viewModel::setSearchQuery,
            onQuickCreateForChannel = { channel ->
              openNewScheduleComposer(channel)
            },
            onEditSchedule = { item ->
              editingSchedule = item
              composerChannel = item.channelEnum
              isComposerOpen = true
            },
            onDuplicateSchedule = { item ->
              openNewScheduleComposer(
                channel = item.channelEnum,
                recipientName = item.recipientName,
                recipientTarget = item.recipientTarget,
                messageBody = item.messageBody
              )
            },
            onTogglePauseResume = viewModel::togglePauseResume,
            onDispatchScheduleNow = { item ->
              val statusNote = ChannelDispatcher.dispatchScheduleIntent(context, item)
              if (item.notifyOnComplete) {
                ChannelDispatcher.postScheduleNotification(
                  context = context,
                  title = "Whatschedule: ${item.channelEnum.displayName} Dispatched",
                  body = "To ${item.recipientName}: ${item.messageBody}",
                  notificationId = item.id.hashCode()
                )
              }
              viewModel.markExecuted(item)
              viewModel.showFeedback(statusNote)
            },
            onDeleteSchedule = viewModel::deleteSchedule,
            onLoadStarterKit = {
              if (ensurePermissionsGrantedForScheduling()) {
                viewModel.loadStarterKit()
              }
            },
            maxSchedules = SkeditViewModel.MAX_SCHEDULED_MESSAGES,
            daysRemainingInMonth = daysRemainingInMonth,
            allPermissionsGranted = permissionsStatus.requiredGranted,
            onOpenPermissionsDialog = { showPermissionsDialog = true }
          )
        }

        MainNavDestination.CALENDAR -> {
          CalendarTimelineScreen(
            schedulesState = schedulesState,
            onEditSchedule = { item ->
              editingSchedule = item
              composerChannel = item.channelEnum
              isComposerOpen = true
            },
            onDispatchScheduleNow = { item ->
              val statusNote = ChannelDispatcher.dispatchScheduleIntent(context, item)
              viewModel.markExecuted(item)
              viewModel.showFeedback(statusNote)
            },
            onQuickCreate = { channel ->
              openNewScheduleComposer(channel)
            }
          )
        }

        MainNavDestination.TEMPLATES -> {
          TemplatesAndGroupsScreen(
            templatesState = templatesState,
            groupsState = groupsState,
            initialSubTab = templatesSubTab,
            onUseTemplateForSchedule = { template ->
              viewModel.recordTemplateUsed(template)
              val tplChannel = if (template.channel == "ALL") {
                ScheduleChannel.WHATSAPP
              } else {
                ScheduleChannel.fromId(template.channel)
              }
              openNewScheduleComposer(
                channel = tplChannel,
                messageBody = template.content
              )
            },
            onUseGroupForSchedule = { group ->
              openNewScheduleComposer(
                channel = group.channelEnum,
                recipientName = "${group.name} (${group.memberCount})",
                recipientTarget = group.membersSummary
              )
            },
            onCreateTemplate = { title, category, channel, content ->
              viewModel.createTemplate(title, category, channel, content)
            },
            onDeleteTemplate = viewModel::deleteTemplate,
            onCreateGroup = { name, channel, members, count ->
              viewModel.createGroup(name, channel, members, count)
            },
            onDeleteGroup = viewModel::deleteGroup,
            onLoadStarterKit = {
              if (ensurePermissionsGrantedForScheduling()) {
                viewModel.loadStarterKit()
              }
            }
          )
        }

        MainNavDestination.SETTINGS -> {
          SettingsAndAutomationScreen(
            currentUser = currentUser,
            schedulesState = schedulesState,
            permissionsStatus = permissionsStatus,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            defaultAskBeforeSending = defaultAskBeforeSending,
            defaultNotifyOnComplete = defaultNotifyOnComplete,
            onToggleDefaultAskBeforeSending = viewModel::setDefaultAskBeforeSending,
            onToggleDefaultNotifyOnComplete = viewModel::setDefaultNotifyOnComplete,
            onRequestNotifications = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
            onOpenBatteryOptimization = { PermissionHelper.openBatteryOptimizationSettings(context) },
            onOpenExactAlarm = { PermissionHelper.openExactAlarmSettings(context) },
            onOpenPermissionsDialog = { showPermissionsDialog = true },
            onLoadStarterKit = {
              if (ensurePermissionsGrantedForScheduling()) {
                viewModel.loadStarterKit()
              }
            },
            onSignOut = {
              signOut(credentialManager, scope, onSignOutComplete = onRefreshAuth)
            },
            onAuthSuccess = onRefreshAuth,
            onShowFeedback = viewModel::showFeedback
          )
        }
      }
    }
  }

  // Small floating popup showing selected WhatsApp contact count on display
  AnimatedVisibility(
    visible = (selectedWhatsAppContacts.isNotEmpty() || isWhatsAppSelectionActive) && !isComposerOpen,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = Modifier
      .align(Alignment.BottomStart)
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(start = 16.dp, bottom = 92.dp, end = 16.dp)
  ) {
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier.testTag("whatsapp_selected_count_popup")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Image(
          painter = painterResource(id = R.drawable.whatschedule_logo),
          contentDescription = stringResource(R.string.app_name),
          contentScale = ContentScale.Fit,
          modifier = Modifier.size(22.dp)
        )
        Text(
          text = when (selectedWhatsAppContacts.size) {
            0 -> "Open a WhatsApp chat..."
            1 -> "1 contact selected"
            else -> "${selectedWhatsAppContacts.size} contacts selected"
          },
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold
        )
        if (selectedWhatsAppContacts.isNotEmpty()) {
          FilledTonalButton(
            onClick = {
              openNewScheduleComposer(
                channel = WhatsAppContactSelectionManager.activeChannel.value,
                recipientName = WhatsAppContactSelectionManager.formatRecipientNameSummary(selectedWhatsAppContacts),
                recipientTarget = WhatsAppContactSelectionManager.formatRecipientTargetSummary(selectedWhatsAppContacts)
              )
            }
          ) {
            Text("Use")
          }
        }
        IconButton(
          onClick = {
            WhatsAppContactSelectionManager.stopSelection()
            WhatsAppContactSelectionManager.clearSelection()
          },
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Clear selected contacts",
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
  }

  if (showPermissionsDialog && isSplashFinished) {
    PermissionsSetupDialog(
      status = permissionsStatus,
      onRequestNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      },
      onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
      onOpenBatteryOptimization = { PermissionHelper.openBatteryOptimizationSettings(context) },
      onOpenExactAlarm = { PermissionHelper.openExactAlarmSettings(context) },
      onDismiss = { showPermissionsDialog = false }
    )
  }

  // Whatschedule Composer Sheet
  if (isComposerOpen) {
    ScheduleComposerSheet(
      existingSchedule = editingSchedule,
      initialChannel = composerChannel,
      initialRecipientName = composerInitialRecipientName,
      initialRecipientTarget = composerInitialRecipientTarget,
      initialMessageBody = composerInitialMessageBody,
      defaultAskBeforeSending = defaultAskBeforeSending,
      defaultNotifyOnComplete = defaultNotifyOnComplete,
      templates = templatesList,
      groups = groupsList,
      onDismiss = { isComposerOpen = false },
      onSave = { channel, recipientName, recipientTarget, subject, messageBody, attachmentLabel, scheduledMillis, repeat, askFirst, notify ->
        if (!ensurePermissionsGrantedForScheduling()) {
          isComposerOpen = false
          return@ScheduleComposerSheet
        }
        viewModel.saveSchedule(
          existingItem = editingSchedule,
          channel = channel,
          recipientName = recipientName,
          recipientTarget = recipientTarget,
          subject = subject,
          messageBody = messageBody,
          attachmentLabel = attachmentLabel,
          scheduledEpochMillis = scheduledMillis,
          repeatInterval = repeat,
          askBeforeSending = askFirst,
          notifyOnComplete = notify,
          onComplete = {
            WhatsAppContactSelectionManager.clearSelection()
            isComposerOpen = false
          }
        )
      },
      onTemplateSelected = viewModel::recordTemplateUsed
    )
  }

  // Whatschedule Signature "Ask Me Before Sending" Due Prompt Dialog
  val promptItem = duePromptSchedule
  if (promptItem != null) {
    val channel = promptItem.channelEnum
    val accent = ChannelDispatcher.channelColor(channel)
    AlertDialog(
      onDismissRequest = { viewModel.dismissDuePrompt() },
      icon = {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = ChannelDispatcher.channelIcon(channel),
            contentDescription = channel.displayName,
            tint = accent
          )
        }
      },
      title = {
        Text(
          text = "Whatschedule: Ready to Send ${channel.displayName}",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "To: ${promptItem.recipientName} (${promptItem.recipientTarget})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = promptItem.messageBody,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(12.dp)
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            OutlinedButton(
              onClick = { viewModel.snoozeSchedule(promptItem, 15) }
            ) {
              Icon(imageVector = Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Snooze +15m")
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val statusNote = ChannelDispatcher.dispatchScheduleIntent(context, promptItem)
            viewModel.markExecuted(promptItem)
            viewModel.showFeedback(statusNote)
          }
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Dispatch Now")
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissDuePrompt() }) {
          Text("Later")
        }
      }
    )
  }
}
