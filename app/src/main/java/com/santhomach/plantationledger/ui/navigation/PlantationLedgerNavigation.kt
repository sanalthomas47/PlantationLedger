package com.santhomach.plantationledger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.santhomach.plantationledger.ui.screens.CsvImportScreen
import com.santhomach.plantationledger.ui.screens.DailyExpenseScreen
import com.santhomach.plantationledger.ui.screens.ExpenseTypeSummaryScreen
import com.santhomach.plantationledger.ui.screens.HomeScreen
import com.santhomach.plantationledger.ui.screens.ReportsScreen
import com.santhomach.plantationledger.ui.screens.SearchScreen
import com.santhomach.plantationledger.ui.screens.SettingsScreen
import com.santhomach.plantationledger.ui.screens.VendorLedgerScreen
import com.santhomach.plantationledger.ui.screens.WeeklyBalanceScreen
import com.santhomach.plantationledger.ui.screens.WeeklyFundsScreen
import com.santhomach.plantationledger.ui.screens.WorkerPaymentScreen
import java.time.LocalDate

sealed class Screen(val route: String) {
    object Home : Screen("home")

    object DailyExpense : Screen("daily_expense/{date}?expenseId={expenseId}") {
        fun createRoute(date: LocalDate, expenseId: Int? = null): String =
            "daily_expense/$date?expenseId=${expenseId ?: 0}"
    }

    object Reports : Screen("reports?startDate={startDate}&endDate={endDate}") {
        fun createRoute(startDate: LocalDate? = null, endDate: LocalDate? = null): String =
            if (startDate != null && endDate != null) "reports?startDate=$startDate&endDate=$endDate"
            else "reports"
    }

    object Settings : Screen("settings")
    object Payments : Screen("payments")
    object WeeklyFunds : Screen("weekly_funds")
    object Search : Screen("search")
    object ExpenseTypeSummary : Screen("expense_type_summary")
    object CsvImport : Screen("csv_import")
    object VendorLedger : Screen("vendor_ledger")
    object WeeklyBalance : Screen("weekly_balance")
}

@Composable
fun PlantationLedgerNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToExpenseEntry = { date, expenseId ->
                    navController.navigate(Screen.DailyExpense.createRoute(date, expenseId))
                },
                onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToPayments = { navController.navigate(Screen.Payments.route) },
                onNavigateToWeeklyFunds = { navController.navigate(Screen.WeeklyFunds.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToExpenseTypeSummary = { navController.navigate(Screen.ExpenseTypeSummary.route) },
                onNavigateToVendorLedger = { navController.navigate(Screen.VendorLedger.route) },
                onNavigateToWeeklyBalance = { navController.navigate(Screen.WeeklyBalance.route) }
            )
        }

        composable(Screen.WeeklyBalance.route) {
            WeeklyBalanceScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenWeek = { start, end -> navController.navigate(Screen.Reports.createRoute(start, end)) }
            )
        }

        composable(Screen.VendorLedger.route) {
            VendorLedgerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExpenseEntry = { date, expenseId ->
                    navController.navigate(Screen.DailyExpense.createRoute(date, expenseId))
                }
            )
        }

        composable(
            route = Screen.Reports.route,
            arguments = listOf(
                navArgument("startDate") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("endDate") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val startDateStr = backStackEntry.arguments?.getString("startDate")
            val endDateStr = backStackEntry.arguments?.getString("endDate")
            val startDate = startDateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val endDate = endDateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ReportsScreen(
                startDate = startDate,
                endDate = endDate,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExpenseEntry = { date, expenseId ->
                    navController.navigate(Screen.DailyExpense.createRoute(date, expenseId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCsvImport = { navController.navigate(Screen.CsvImport.route) }
            )
        }

        composable(Screen.CsvImport.route) {
            CsvImportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Payments.route) {
            WorkerPaymentScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ExpenseTypeSummary.route) {
            ExpenseTypeSummaryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.WeeklyFunds.route) {
            WeeklyFundsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReports = { startDate, endDate ->
                    navController.navigate(Screen.Reports.createRoute(startDate, endDate))
                }
            )
        }

        composable(
            route = Screen.DailyExpense.route,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("expenseId") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val dateStr = backStackEntry.arguments?.getString("date") ?: LocalDate.now().toString()
            val expenseIdArg = backStackEntry.arguments?.getInt("expenseId") ?: 0
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                LocalDate.now()
            }
            val expenseId = if (expenseIdArg > 0) expenseIdArg else null
            DailyExpenseScreen(
                date = date,
                expenseId = expenseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
