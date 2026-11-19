package com.example

import android.os.Bundle
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.*
import com.example.data.local.ZarnegarLocalDatabase
import com.example.data.local.ZarnegarLocalRepository
import com.example.security.SecurityPreferences
import com.example.ui.components.GatewayCheckoutDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LedgersScreen
import com.example.ui.screens.ReportsAndSheetsScreen
import com.example.ui.screens.TransactionsAndAccountsScreen
import com.example.ui.screens.VoiceAssistantScreen
import com.example.ui.theme.LocalZarnegarColors
import com.example.ui.theme.ZarnegarTheme
import com.example.viewmodel.ZarnegarViewModel
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_FINANCE = "finance"
private const val ROUTE_LEDGERS = "ledgers"
private const val ROUTE_VOICE = "voice"
private const val ROUTE_REPORTS = "reports"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val zarnegarDestinations = listOf(
    NavDestination(ROUTE_DASHBOARD, "خانه", Icons.Filled.Home, Icons.Outlined.Home, "nav_dashboard"),
    NavDestination(ROUTE_FINANCE, "حساب و خرج", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_finance"),
    NavDestination(ROUTE_LEDGERS, "انبار و افراد", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "nav_ledgers"),
    NavDestination(ROUTE_VOICE, "پیامگیر", Icons.Filled.Mic, Icons.Outlined.MicNone, "nav_voice"),
    NavDestination(ROUTE_REPORTS, "گزارش و شیت", Icons.Filled.Assessment, Icons.Outlined.Assessment, "nav_reports")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ZarnegarViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val localDao = ZarnegarLocalDatabase.getInstance(app).dao()
                        val dbId = app.getString(R.string.firestore_database_id)
                        val firestore = FirebaseFirestore.getInstance(dbId)
                        ZarnegarViewModel(
                            localRepo = ZarnegarLocalRepository(localDao),
                            cloudRepo = ZarnegarRepository(firestore),
                            inventoryService = GoldInventoryService(firestore),
                            marketRepo = MarketRateRepository(),
                            sheetsService = GoogleSheetsSyncService(),
                            gatewayService = PaymentGatewayService(),
                            prefs = SecurityPreferences(app)
                        )
                    }
                }
            )
            val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
            }

            ZarnegarTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZarnegarAppShell(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun ZarnegarAppShell(viewModel: ZarnegarViewModel) {
    val context = LocalContext.current
    val ext = LocalZarnegarColors.current
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: ROUTE_DASHBOARD

    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val pendingGateway by viewModel.pendingGatewayCheckout.collectAsStateWithLifecycle()

    var financeSubTab by remember { mutableIntStateOf(0) }
    var openAddTxSignal by remember { mutableIntStateOf(0) }
    var reportsSubTab by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        zarnegarDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = ext.mutedText,
                                    unselectedTextColor = ext.mutedText
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        zarnegarDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = ext.mutedText,
                                    unselectedTextColor = ext.mutedText
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_DASHBOARD) {
                        DashboardScreen(
                            viewModel = viewModel,
                            onQuickAddSale = {
                                financeSubTab = 0
                                openAddTxSignal += 1
                                navigateTopLevel(ROUTE_FINANCE)
                            },
                            onOpenExpenses = {
                                financeSubTab = 2
                                navigateTopLevel(ROUTE_FINANCE)
                            },
                            onOpenVoiceRecorder = {
                                navigateTopLevel(ROUTE_VOICE)
                            },
                            onOpenReportsAndSheets = {
                                reportsSubTab = 1
                                navigateTopLevel(ROUTE_REPORTS)
                            }
                        )
                    }

                    composable(ROUTE_FINANCE) {
                        TransactionsAndAccountsScreen(
                            viewModel = viewModel,
                            initialSubTab = financeSubTab,
                            openAddTxTrigger = openAddTxSignal,
                            onConsumeAddTxTrigger = { openAddTxSignal = 0 }
                        )
                    }

                    composable(ROUTE_LEDGERS) {
                        LedgersScreen(viewModel = viewModel)
                    }

                    composable(ROUTE_VOICE) {
                        VoiceAssistantScreen(
                            viewModel = viewModel,
                            onOpenSheetsSetup = {
                                reportsSubTab = 1
                                navigateTopLevel(ROUTE_REPORTS)
                            }
                        )
                    }

                    composable(ROUTE_REPORTS) {
                        ReportsAndSheetsScreen(
                            viewModel = viewModel,
                            initialSubTab = reportsSubTab
                        )
                    }
                }
            }
        }
    }

    pendingGateway?.let { (tx, checkout) ->
        GatewayCheckoutDialog(
            tx = tx,
            checkout = checkout,
            onOpenGatewayBrowser = { url ->
                try {
                    CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                } catch (e: Exception) {
                    viewModel.showMessage("امکان باز کردن مرورگر وجود ندارد: ${e.localizedMessage}")
                }
            },
            onConfirmPaid = { refId -> viewModel.confirmGatewayPayment(tx, refId) },
            onDismiss = viewModel::dismissPendingGateway
        )
    }
}
