package com.example.jaldiqproject.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.jaldiqproject.data.AuthRepository
import com.example.jaldiqproject.model.User
import com.example.jaldiqproject.viewmodel.AuthUiState
import com.example.jaldiqproject.viewmodel.AuthViewModel
import com.example.jaldiqproject.viewmodel.CustomerViewModel
import com.example.jaldiqproject.viewmodel.ShopListUiState
import com.example.jaldiqproject.viewmodel.TokenUiState
import javax.inject.Inject

/**
 * Navigation routes for JaldiQ.
 */
object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val REGISTER_SHOP = "register_shop"
    const val SHOP_OWNER_DASHBOARD = "shop_owner_dashboard/{shopId}"
    const val SHOP_DISCOVERY = "shop_discovery"
    const val TOKEN_DETAILS = "token_details/{shopId}/{tokenId}"
    const val CUSTOMER_PROFILE = "customer_profile"
    const val OWNER_PROFILE = "owner_profile/{shopId}"
    const val OWNER_ANALYTICS = "owner_analytics/{shopId}"

    fun shopOwnerDashboard(shopId: String) = "shop_owner_dashboard/$shopId"
    fun tokenDetails(shopId: String, tokenId: String) = "token_details/$shopId/$tokenId"
    fun ownerProfile(shopId: String) = "owner_profile/$shopId"
    fun ownerAnalytics(shopId: String) = "owner_analytics/$shopId"
}

@Composable
fun JaldiQNavHost(
    authRepository: AuthRepository
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        // ─── Splash Screen ────────────────────────────────────
        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    val currentState = authViewModel.authState.value
                    val destination = when (currentState) {
                        is AuthUiState.Authenticated -> {
                            when {
                                currentState.role == User.ROLE_SHOP_OWNER && currentState.shopId.isNullOrEmpty() ->
                                    Routes.REGISTER_SHOP
                                currentState.role == User.ROLE_SHOP_OWNER && !currentState.shopId.isNullOrEmpty() ->
                                    Routes.shopOwnerDashboard(currentState.shopId)
                                else -> Routes.SHOP_DISCOVERY
                            }
                        }
                        else -> Routes.AUTH
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ─── Auth Screen ─────────────────────────────────────
        composable(Routes.AUTH) {
            when (val state = authState) {
                is AuthUiState.Authenticated -> {
                    val route = when {
                        state.role == User.ROLE_SHOP_OWNER && state.shopId.isNullOrEmpty() ->
                            Routes.REGISTER_SHOP
                        state.role == User.ROLE_SHOP_OWNER && !state.shopId.isNullOrEmpty() ->
                            Routes.shopOwnerDashboard(state.shopId)
                        else -> Routes.SHOP_DISCOVERY
                    }
                    LaunchedEffect(state) {
                        navController.navigate(route) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                else -> {
                    AuthScreen(viewModel = authViewModel)
                }
            }
        }

        // ─── Register Shop ───────────────────────────────────
        composable(Routes.REGISTER_SHOP) {
            RegisterShopScreen(
                viewModel = authViewModel,
                onShopRegistered = { shopId ->
                    navController.navigate(Routes.shopOwnerDashboard(shopId)) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ─── Shop Owner Dashboard ────────────────────────────
        composable(
            route = Routes.SHOP_OWNER_DASHBOARD,
            arguments = listOf(
                navArgument("shopId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            ShopOwnerDashboardScreen(
                onLogoutClicked = {
                    authViewModel.signOut()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onProfileClicked = {
                    navController.navigate(Routes.ownerProfile(shopId)) {
                        launchSingleTop = true
                    }
                },
                onAnalyticsClicked = {
                    navController.navigate(Routes.ownerAnalytics(shopId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ─── Customer: Shop Discovery ────────────────────────
        composable(Routes.SHOP_DISCOVERY) {
            val parentEntry = navController.getBackStackEntry(Routes.SHOP_DISCOVERY)
            val customerViewModel: CustomerViewModel = hiltViewModel(parentEntry)

            ShopDiscoveryScreen(
                viewModel = customerViewModel,
                onLogoutClicked = {
                    customerViewModel.resetToken()
                    authViewModel.signOut()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
                onTokenObtained = { shopId, tokenId ->
                    navController.navigate(Routes.tokenDetails(shopId, tokenId)) {
                        launchSingleTop = true
                    }
                },
                onViewActiveToken = { shopId, tokenId ->
                    navController.navigate(Routes.tokenDetails(shopId, tokenId)) {
                        launchSingleTop = true
                    }
                },
                onProfileClicked = {
                    navController.navigate(Routes.CUSTOMER_PROFILE) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ─── Customer: Token Details ─────────────────────────
        composable(
            route = Routes.TOKEN_DETAILS,
            arguments = listOf(
                navArgument("shopId") { type = NavType.StringType },
                navArgument("tokenId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            val tokenId = backStackEntry.arguments?.getString("tokenId") ?: ""

            val parentEntry = navController.getBackStackEntry(Routes.SHOP_DISCOVERY)
            val customerViewModel: CustomerViewModel = hiltViewModel(parentEntry)

            TokenDetailsScreen(
                viewModel = customerViewModel,
                shopId = shopId,
                tokenId = tokenId,
                onBackClicked = { navController.popBackStack() },
                onLeaveQueue = {
                    customerViewModel.leaveQueue(shopId, tokenId)
                    navController.popBackStack()
                }
            )
        }

        // ─── Customer Profile ────────────────────────────────
        composable(Routes.CUSTOMER_PROFILE) {
            CustomerProfileScreen(
                authRepository = authRepository,
                onBackClicked = { navController.popBackStack() }
            )
        }

        // ─── Owner Profile ───────────────────────────────────
        composable(
            route = Routes.OWNER_PROFILE,
            arguments = listOf(
                navArgument("shopId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val shopId = backStackEntry.arguments?.getString("shopId") ?: ""
            OwnerProfileScreen(
                authRepository = authRepository,
                shopId = shopId,
                onBackClicked = { navController.popBackStack() }
            )
        }

        // ─── Owner Analytics ─────────────────────────────────
        composable(
            route = Routes.OWNER_ANALYTICS,
            arguments = listOf(
                navArgument("shopId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            OwnerAnalyticsScreen(
                onBackClicked = { navController.popBackStack() }
            )
        }
    }
}
