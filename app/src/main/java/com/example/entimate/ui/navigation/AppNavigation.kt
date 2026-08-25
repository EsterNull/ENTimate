package com.example.entimate.ui.navigation

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
import androidx.navigation.NavController
import androidx.navigation.compose.*
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
import com.example.entimate.ui.settings.SettingsScreen
import com.example.entimate.ui.settings.ThemeSettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Documents : Screen("documents", "Документы", Icons.Filled.Description)
    object Patients : Screen("patients", "Пациенты", Icons.Filled.Person)
    object Reports : Screen("reports", "Отчёты", Icons.Filled.TableChart)
    object Settings : Screen("settings", "Настройки", Icons.Filled.Settings)
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val items = listOf(Screen.Documents, Screen.Patients, Screen.Reports, Screen.Settings)
    val navBackStack by nav.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: Screen.Documents.route
    val topRoutes = listOf("documents", "patients", "reports", "settings")

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
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    nav.navigate(s.route) {
                                        popUpTo(nav.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
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
        }
    }
}
