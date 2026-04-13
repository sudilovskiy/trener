package com.example.trener

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.trener.data.local.TrenerDatabase
import com.example.trener.data.local.TrenerDatabaseProvider

@Composable
fun rememberTrenerDatabase(databaseRefreshToken: Int): TrenerDatabase {
    val context = LocalContext.current.applicationContext
    return remember(context, databaseRefreshToken) {
        TrenerDatabaseProvider.getInstance(context)
    }
}
