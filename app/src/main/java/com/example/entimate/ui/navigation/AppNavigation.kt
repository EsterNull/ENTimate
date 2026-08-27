package com.example.entimate.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.entimate.EntimateApplication
import kotlinx.coroutines.flow.first
import com.example.entimate.settings.SettingsDataStore
import com.example.entimate.ui.components.*
import com.example.entimate.ui.documents.DocumentsScreen
import com.example.entimate.ui.documents.DocumentStatsScreen
import com.example.entimate.ui.documents.DocumentEditScreen
import com.example.entimate.ui.patients.PatientsScreen
import com.example.entimate.ui.patients.PatientEditScreen
import com.example.entimate.ui.patients.PatientLinksScreen
import com.example.entimate.ui.patients.CustomFieldsScreen
import com.example.entimate.ui.reports.ReportsScreen
import com.example.entimate.ui.reports.ReportPreviewScreen
import com.example.entimate.ui.reports.ReportEditScreen
import com.example.entimate.ui.reports.DocumentPreviewScreen
import com.example.entimate.ui.settings.SettingsScreen
import com.example.entimate.ui.settings.ThemeSettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Documents : Screen("documents", "Документы", Icons.Filled.Description)
    object Patients : Screen("patients", "Пациенты", Icons.Filled.Person)
    object Reports : Screen("reports", "Отчёты", Icons.Filled.TableChart)
    object Settings : Screen("settings", "Настройки", Icons.Filled.Settings)
}

private val tutorialSteps = listOf(
    TutorialStep(
        null,
        "Добро пожаловать в ENTimate",
        "Кратко покажем, где что находится. Нажимайте «Далее», чтобы листать, или «Пропустить».",
        navTarget = "documents",
    ),
    TutorialStep(
        "doc_add",
        "Добавление документа",
        "Кнопка «+» создаёт новый документ — карточку с названием, цветом и счётчиком количества.",
        navTarget = "documents",
    ),
    TutorialStep(
        "doc_reorder",
        "Порядок документов",
        "Кнопка с тремя полосками включает режим перетаскивания: беритесь за полоску у карточки и меняйте порядок.",
        navTarget = "documents",
    ),
    TutorialStep(
        null,
        "Карточка документа",
        "Нажмите на карточку — статистика; свайп влево — удалить, вправо — редактировать; долгое нажатие — копия.",
        navTarget = "documents",
        showSwipeDemo = true,
    ),
    TutorialStep(
        null,
        "Пациенты",
        "Здесь учёт пациентов и связи с документами.",
        navTarget = "patients",
    ),
    TutorialStep(
        null,
        "Отчёты",
        "Сводные таблицы по пациентам за выбранный период.",
        navTarget = "reports",
    ),
    TutorialStep(
        null,
        "Настройки",
        "Тема оформления, экспорт и импорт резервной копии.",
        navTarget = "settings",
    ),
    TutorialStep(
        null,
        "Готово!",
        "Обучение можно открыть заново через кнопку «?» в шапке любого экрана.",
    ),
)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val items = listOf(Screen.Documents, Screen.Patients, Screen.Reports, Screen.Settings)
    val navBackStack by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: Screen.Documents.route
    val topRoutes = listOf("documents", "patients", "reports", "settings")

    val context = LocalContext.current
    val app = context.applicationContext as EntimateApplication
    val settings: SettingsDataStore = app.settingsDataStore
    val scope = rememberCoroutineScope()
    val tutorialState = remember {
        TutorialState(tutorialSteps) { scope.launch { settings.setTutorialSeen() } }
    }

    LaunchedEffect(Unit) {
        if (!settings.isTutorialSeen().first()) tutorialState.start()
    }

    LaunchedEffect(tutorialState.step) {
        val target = tutorialState.currentStep?.navTarget
        if (!tutorialState.active) return@LaunchedEffect
        if (target != null && target != currentRoute) {
            nav.navigate(target) { launchSingleTop = true }
        }
    }

    CompositionLocalProvider(LocalTutorial provides tutorialState) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (currentRoute in topRoutes) {
                        NavigationBar(
                            windowInsets = NavigationBarDefaults.windowInsets,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items.forEach { s ->
                                    NavigationBarItem(
                                        selected = currentRoute == s.route,
                                        modifier = Modifier
                                            .weight(1f)
                                            .tutorialAnchor("nav_${s.route}"),
                                        onClick = {
                                            nav.navigate(s.route) {
                                                popUpTo(nav.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = { Icon(s.icon, contentDescription = s.label) },
                                        label = { Text(s.label) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = Screen.Documents.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    composable(Screen.Documents.route) { DocumentsScreen(nav = nav) }
                    composable(Screen.Patients.route) { PatientsScreen(nav = nav) }
                    composable(Screen.Reports.route) { ReportsScreen(nav = nav) }
                    composable(Screen.Settings.route) { SettingsScreen(nav = nav) }
                    composable("theme") { ThemeSettingsScreen(nav = nav) }
                    composable("documents/edit/{docId}") { back ->
                        val id = back.arguments?.getString("docId")?.toLongOrNull() ?: 0L
                        DocumentEditScreen(docId = id, nav = nav)
                    }
                    composable("documents/stats/{docId}") { back ->
                        val id = back.arguments?.getString("docId")?.toLongOrNull() ?: 0L
                        DocumentStatsScreen(nav = nav, docId = id)
                    }
                    composable("patients/edit/{patientId}") { back ->
                        val id = back.arguments?.getString("patientId")?.toLongOrNull() ?: 0L
                        PatientEditScreen(patientId = id, nav = nav)
                    }
                    composable("patients/links") { PatientLinksScreen(nav = nav) }
                    composable("customfields") { CustomFieldsScreen(nav = nav) }
                    composable("reports/edit/{reportId}") { back ->
                        val id = back.arguments?.getString("reportId")?.toLongOrNull() ?: 0L
                        ReportEditScreen(reportId = id, nav = nav)
                    }
                    composable("reports/preview/{reportId}/{from}/{to}") { back ->
                        val id = back.arguments?.getString("reportId")?.toLongOrNull() ?: 0L
                        val from = back.arguments?.getString("from")?.toLongOrNull() ?: 0L
                        val to = back.arguments?.getString("to")?.toLongOrNull() ?: System.currentTimeMillis()
                        ReportPreviewScreen(reportId = id, from = from, to = to, nav = nav)
                    }
                    composable("reports/docpreview/{reportId}/{from}/{to}") { back ->
                        val id = back.arguments?.getString("reportId")?.toLongOrNull() ?: 0L
                        val from = back.arguments?.getString("from")?.toLongOrNull() ?: 0L
                        val to = back.arguments?.getString("to")?.toLongOrNull() ?: System.currentTimeMillis()
                        DocumentPreviewScreen(reportId = id, from = from, to = to, nav = nav)
                    }
                }
            }
            TutorialOverlay(tutorialState)
        }
    }
}
