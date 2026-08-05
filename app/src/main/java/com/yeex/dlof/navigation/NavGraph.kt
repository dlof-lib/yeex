package com.yeex.dlof.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.yeex.dlof.data.repository.AuthRepository
import com.yeex.dlof.ui.auth.LoginScreen
import com.yeex.dlof.ui.auth.RegisterScreen
import com.yeex.dlof.ui.create.CreateParagraphScreen
import com.yeex.dlof.ui.feed.FeedScreen
import com.yeex.dlof.ui.profile.ProfileScreen
import com.yeex.dlof.ui.room.CreateRoomScreen
import com.yeex.dlof.ui.room.RoomScreen
import com.yeex.dlof.ui.search.SearchScreen
import com.yeex.dlof.ui.verify.VerificationRequestScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FEED = "feed"
    const val CREATE_PARAGRAPH = "create_paragraph"
    const val ROOM = "room/{roomId}"
    const val CREATE_ROOM = "create_room"
    const val PROFILE = "profile/{uid}"
    const val SEARCH = "search"
    const val VERIFY = "verify"

    fun room(id: String) = "room/$id"
    fun profile(uid: String) = "profile/$uid"
}

@Composable
fun YeexNavGraph(authRepo: AuthRepository = AuthRepository()) {
    val navController: NavHostController = rememberNavController()
    val startDestination = if (authRepo.currentUid() != null) Routes.FEED else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
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
                onCreateParagraph = { navController.navigate(Routes.CREATE_PARAGRAPH) },
                onOpenComments = { /* TODO: comments bottom sheet */ },
                onRepost = { /* TODO: repost-into-room picker dialog */ }
            )
        }
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
                onCreateParagraph = { navController.navigate(Routes.CREATE_PARAGRAPH) },
                onOpenComments = { },
                onRepost = { }
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
    }
}
