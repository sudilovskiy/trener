package com.example.trener

import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.trener.data.local.TrenerDatabaseProvider
import com.example.trener.data.local.entity.BodyWeightEntryEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val runtimeValidationPrefs = "runtime_validation"
private const val dummyWeightInsertedKey = "dummy_weight_inserted"
private const val dummyWeightKg = 70.0
const val logTag = "TrenerApp"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seedBodyWeightEntryOnce()
        setContent { TrenerApp() }
    }

    private fun seedBodyWeightEntryOnce() {
        val preferences = getSharedPreferences(runtimeValidationPrefs, MODE_PRIVATE)
        if (preferences.getBoolean(dummyWeightInsertedKey, false)) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                TrenerDatabaseProvider
                    .getInstance(applicationContext)
                    .bodyWeightHistoryDao()
                    .insertBodyWeightEntry(
                        BodyWeightEntryEntity(
                            entryDateEpochDay = LocalDate.now().toEpochDay(),
                            weightKg = dummyWeightKg
                        )
                    )
            }.onSuccess {
                preferences.edit().putBoolean(dummyWeightInsertedKey, true).apply()
            }.onFailure { throwable ->
                if (throwable is SQLiteConstraintException) {
                    preferences.edit().putBoolean(dummyWeightInsertedKey, true).apply()
                } else {
                    Log.e(logTag, "Failed to insert initial body weight", throwable)
                }
            }
        }
    }
}
