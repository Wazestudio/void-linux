package com.voidlinux.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions

/**
 * Helper pour la navigation entre fragments.
 */
object NavGraph {

    fun navigateTo(navController: NavController, destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return

        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(
                navController.graph.startDestinationId,
                inclusive = false,
                saveState = true
            )
            .build()

        navController.navigate(destinationId, null, options)
    }
}