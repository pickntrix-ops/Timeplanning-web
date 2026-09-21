package com.timeplanning.app

/** Simple state-based navigation — no nav library, the screen set is small enough not to need one yet. */
sealed class Screen {
    data object SignIn : Screen()
    data object Today : Screen()
    data object AddTask : Screen()
}
