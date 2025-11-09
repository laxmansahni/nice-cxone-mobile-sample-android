package com.isos.cxone.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.isos.cxone.compose.LauncherScreen
import com.isos.cxone.compose.SimplePreChatSurveyInput
import com.isos.cxone.compose.SingleThreadScreen
import com.isos.cxone.compose.ThreadListScreen
import com.nice.cxonechat.ChatState

@Composable
fun Navigation(
    navController: NavHostController,
    currentChatState: ChatState,
    onConnectionActionClick: () -> Unit
) {
    NavHost(navController = navController, startDestination = Launch) {

        dashboardCreationGraph(
            navController, currentChatState,
            onConnectionActionClick
        )
    }
}

private fun NavGraphBuilder.dashboardCreationGraph(
    navController: NavController, currentChatState: ChatState,
    onConnectionActionClick: () -> Unit
) {
    composable<Launch> {
        LauncherScreen(
            chatState = currentChatState, // Passed to LauncherScreen as a Composable parameter.
            onConnectionActionClick = onConnectionActionClick,
            onThreadListClicked = {
                navController.navigate(ThreadList)
            }
        )
    }

    composable<ThreadList> {
        ThreadListScreen(
            onThreadSelected = { threadId ->
                navController.navigate(SingleThread(threadId))
            },
            navigateUp = {
                navController.popBackStack()
            }
        )
    }

    composable<SingleThread>(
        enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }) + fadeOut() }
    ) { backStackEntry ->
        val singleThread: SingleThread = backStackEntry.toRoute()
        SingleThreadScreen(
            threadId = singleThread.threadId,
            navigateUp = {
                navController.popBackStack()
            }
        )
    }
}
