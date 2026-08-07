package com.yeex.dlof.navigation

import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.ui.auth.LoginScreen
import com.yeex.dlof.ui.auth.RegisterScreen
import com.yeex.dlof.ui.comments.CommentsScreen
import com.yeex.dlof.ui.common.SplashScreen
import com.yeex.dlof.ui.components.BottomTab
import com.yeex.dlof.ui.components.YeexBottomBar
import com.yeex.dlof.ui.create.CreateParagraphScreen
import com.yeex.dlof.ui.feed.FeedScreen
import com.yeex.dlof.ui.profile.ProfileScreen
import com.yeex.dlof.ui.repost.RepostScreen
import com.yeex.dlof.ui.room.CreateRoomScreen
import com.yeex.dlof.ui.room.RoomScreen
import com.yeex.dlof.ui.search.SearchScreen
import com.yeex.dlof.ui.verify.VerificationRequestScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FEED = "feed"
    const val CREATE_PARAGRAPH = "create_paragraph"
    const val ROOM = "room/{roomId}"
    const val CREATE_ROOM = "create_room"
    const val PROFILE = "profile/{uid}"
    const val SEARCH = "search"
    const val VERIFY = "verify"
    const val COMMENTS = "comments/{paragraphId}"
    const val REPOST = "repost/{paragraphId}"

    fun room(id: String) = "room/$id"
    fun profile(uid: String) = "profile/$uid"
    fun comments(paragraphId: String) = "comments/$paragraphId"
    fun repost(paragraphId: String) = "repost/$paragraphId"
}

/**
 * [YeexBottomBar] is shown only while on one of its four routes — see the
 * activeTab computation in [YeexNavGraph] — so focused flows like login,
 * room detail, comments, repost, or verification aren't cluttered with tabs
 * that don't apply to them.
 */

@Composable
fun YeexNavGraph(authRepo: AuthRepository = AuthRepository()) {
    val navController: NavHostController = rememberNavController()
    val myUid = authRepo.currentUid()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isOwnProfile = currentRoute == Routes.PROFILE &&
        backStackEntry?.arguments?.getString("uid") == myUid
    val activeTab: BottomTab? = when {
        currentRoute == Routes.FEED -> BottomTab.HOME
        currentRoute == Routes.SEARCH -> BottomTab.SEARCH
        currentRoute == Routes.CREATE_PARAGRAPH -> BottomTab.CREATE
        isOwnProfile -> BottomTab.PROFILE
        else -> null
    }

    Scaffold(
        bottomBar = {
            if (activeTab != null && myUid != null) {
                YeexBottomBar(currentTab = activeTab) { tab ->
                    val destination = when (tab) {
                        BottomTab.HOME -> Routes.FEED
                        BottomTab.SEARCH -> Routes.SEARCH
                        BottomTab.CREATE -> Routes.CREATE_PARAGRAPH
                        BottomTab.PROFILE -> Routes.profile(myUid)
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.FEED) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding())
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    isLoggedIn = authRepo.currentUid() != null,
                    onFinished = { loggedIn ->
                        val destination = if (loggedIn) Routes.FEED else Routes.LOGIN
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = { navController.navigate(Routes.FEED) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                    onGoToRegister = { navController.navigate(Routes.REGISTER) }
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(
                    onRegistered = { navController.navigate(Routes.FEED) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                    onGoToLogin = { navController.popBackStack() }
                )
            }
            composable(Routes.FEED) {
                FeedScreen(
                    onOpenComments = { paragraphId -> navController.navigate(Routes.comments(paragraphId)) },
                    onRepost = { paragraphId -> navController.navigate(Routes.repost(paragraphId)) }
                )
            }
            // Kept as a direct-link fallback (e.g. from a future push notification or
            // deep link) even though FeedScreen/RoomScreen now publish via their own
            // in-place ModalBottomSheet pop-up instead of navigating here.
            composable(Routes.CREATE_PARAGRAPH) {
                CreateParagraphScreen(onPublished = { navController.popBackStack() })
            }
            composable(Routes.CREATE_ROOM) {
                CreateRoomScreen(onCreated = { id -> navController.navigate(Routes.room(id)) })
            }
            composable(
                Routes.ROOM,
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
                RoomScreen(
                    roomId = roomId,
                    onOpenComments = { paragraphId -> navController.navigate(Routes.comments(paragraphId)) },
                    onRepost = { paragraphId -> navController.navigate(Routes.repost(paragraphId)) }
                )
            }
            composable(
                Routes.PROFILE,
                arguments = listOf(navArgument("uid") { type = NavType.StringType })
            ) { backStackEntry ->
                val uid = backStackEntry.arguments?.getString("uid") ?: return@composable
                ProfileScreen(
                    targetUid = uid,
                    onRequestVerification = { navController.navigate(Routes.VERIFY) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenContainer = { })
            }
            composable(Routes.VERIFY) {
                VerificationRequestScreen(onSubmitted = { navController.popBackStack() })
            }
            composable(
                Routes.COMMENTS,
                arguments = listOf(navArgument("paragraphId") { type = NavType.StringType })
            ) { backStackEntry ->
                val paragraphId = backStackEntry.arguments?.getString("paragraphId") ?: return@composable
                CommentsScreen(paragraphId = paragraphId, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.REPOST,
                arguments = listOf(navArgument("paragraphId") { type = NavType.StringType })
            ) { backStackEntry ->
                val paragraphId = backStackEntry.arguments?.getString("paragraphId") ?: return@composable
                RepostScreen(paragraphId = paragraphId, onDone = { navController.popBackStack() })
            }
        }
    }
}
