package com.jihun.textviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jihun.textviewer.data.repository.DefaultReadingHistoryRepository
import com.jihun.textviewer.data.repository.DefaultTextFileRepository
import com.jihun.textviewer.data.source.HistoryPreferencesDataSource
import com.jihun.textviewer.data.source.SafTextFileLoader
import com.jihun.textviewer.domain.repository.ReadingHistoryRepository
import com.jihun.textviewer.domain.repository.TextFileRepository
import com.jihun.textviewer.domain.viewmodel.TextViewerAction
import com.jihun.textviewer.domain.viewmodel.TextViewerEffect
import com.jihun.textviewer.domain.viewmodel.TextViewerViewModel
import com.jihun.textviewer.ui.screens.TextViewerHistoryScreen
import com.jihun.textviewer.ui.screens.TextViewerHomeScreen
import com.jihun.textviewer.ui.screens.TextViewerSettingsScreen
import com.jihun.textviewer.ui.theme.TextViewerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var volumeKeyActionDispatcher: ((TextViewerAction) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TextViewerRoot(
                onReaderActionDispatcherChanged = { dispatcher ->
                    volumeKeyActionDispatcher = dispatcher
                },
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TextViewerAction.PreviousPage
            KeyEvent.KEYCODE_VOLUME_DOWN -> TextViewerAction.NextPage
            else -> null
        } ?: return super.onKeyDown(keyCode, event)

        val dispatcher = volumeKeyActionDispatcher ?: return super.onKeyDown(keyCode, event)
        dispatcher(action)
        return true
    }
}

private data class TopLevelDestination(val route: String, val label: String)

private val destinations = listOf(
    TopLevelDestination("home", "Home"),
    TopLevelDestination("history", "History"),
    TopLevelDestination("settings", "Settings"),
)

@Composable
private fun TextViewerRoot(
    onReaderActionDispatcherChanged: (((TextViewerAction) -> Unit)?) -> Unit,
) {
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    TextViewerTheme(darkTheme = darkTheme) {
        TextViewerApp(
            darkTheme = darkTheme,
            onToggleTheme = { darkTheme = !darkTheme },
            onReaderActionDispatcherChanged = onReaderActionDispatcherChanged,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TextViewerApp(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onReaderActionDispatcherChanged: (((TextViewerAction) -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = rememberTextViewerViewModel(context = context)
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.onAction(TextViewerAction.OpenFile(uri))
        }
    }

    DisposableEffect(viewModel) {
        onReaderActionDispatcherChanged(viewModel::onAction)
        onDispose {
            onReaderActionDispatcherChanged(null)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onAction(TextViewerAction.LoadHistory)
        viewModel.effect.collect { effect ->
            val message = when (effect) {
                TextViewerEffect.FileOpened -> "File opened"
                TextViewerEffect.Resumed -> "Resumed from history"
                is TextViewerEffect.ShowError -> effect.message
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = { Text("TextViewer") },
                actions = {
                    TextButton(onClick = onToggleTheme) {
                        Text(if (darkTheme) "Day mode" else "Night mode")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomAppBar {
                destinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues),
        ) {
            composable("home") {
                TextViewerHomeScreen(
                    state = state,
                    onOpenFileClick = { filePickerLauncher.launch(arrayOf("text/plain", "text/*")) },
                    onResumeClick = { viewModel.onAction(TextViewerAction.ResumeLastSession) },
                    onPreviousPage = { viewModel.onAction(TextViewerAction.PreviousPage) },
                    onNextPage = { viewModel.onAction(TextViewerAction.NextPage) },
                    onToggleTheme = onToggleTheme,
                )
            }
            composable("history") {
                TextViewerHistoryScreen(
                    history = state.history,
                    onContinueReading = { entry ->
                        viewModel.onAction(
                            TextViewerAction.OpenHistoryEntry(
                                fileUri = entry.fileUri,
                                page = entry.currentPage,
                            ),
                        )
                    },
                    onOpenNewFile = { filePickerLauncher.launch(arrayOf("text/plain", "text/*")) },
                )
            }
            composable("settings") {
                TextViewerSettingsScreen(
                    darkTheme = darkTheme,
                    onToggleTheme = onToggleTheme,
                )
            }
        }
    }
}

@Composable
private fun rememberTextViewerViewModel(context: Context): TextViewerViewModel {
    val factory = remember(context) {
        val textFileRepository: TextFileRepository = DefaultTextFileRepository(
            loader = SafTextFileLoader(context.contentResolver),
        )
        val historyRepository: ReadingHistoryRepository = DefaultReadingHistoryRepository(
            dataSource = HistoryPreferencesDataSource(
                preferences = context.getSharedPreferences("text_viewer_history", Context.MODE_PRIVATE),
            ),
        )
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TextViewerViewModel(
                    textFileRepository = textFileRepository,
                    historyRepository = historyRepository,
                ) as T
            }
        }
    }
    return viewModel(factory = factory)
}

@Preview(showBackground = true)
@Composable
private fun PreviewTextViewerApp() {
    TextViewerTheme {
        TextViewerSettingsScreen(
            darkTheme = false,
            onToggleTheme = {},
        )
    }
}
