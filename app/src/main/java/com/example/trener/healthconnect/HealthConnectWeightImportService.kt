package com.example.trener.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.trener.data.local.TrenerDatabase
import com.example.trener.data.local.entity.BodyWeightEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

sealed interface HealthConnectWeightImportResult {
    data class Imported(val importedDays: Int) : HealthConnectWeightImportResult
    data object NoData : HealthConnectWeightImportResult
    data object PermissionMissing : HealthConnectWeightImportResult
    data object Unavailable : HealthConnectWeightImportResult
    data class Failed(val throwable: Throwable) : HealthConnectWeightImportResult
}

object HealthConnectWeightImportService {
    private const val logTag = "HealthConnectWeightImport"
    private const val providerPackageName = "com.google.android.apps.healthdata"
    private val readWeightPermission = HealthPermission.getReadPermission(WeightRecord::class)
    private val syncMutex = Mutex()

    suspend fun syncLatestWeight(context: Context, database: TrenerDatabase): Boolean {
        return when (
            importWeightHistory(
                context = context,
                database = database,
                startEpochDay = LocalDate.now().toEpochDay(),
                endEpochDay = LocalDate.now().toEpochDay()
            )
        ) {
            is HealthConnectWeightImportResult.Imported -> true
            else -> false
        }
    }

    suspend fun importWeightHistory(
        context: Context,
        database: TrenerDatabase,
        startEpochDay: Long,
        endEpochDay: Long
    ): HealthConnectWeightImportResult {
        return syncMutex.withLock {
            withContext(Dispatchers.IO) {
                if (!isHealthConnectAvailable(context)) {
                    return@withContext HealthConnectWeightImportResult.Unavailable
                }

                val client = createHealthConnectClient(context)
                    ?: return@withContext HealthConnectWeightImportResult.Unavailable

                val grantedPermissions = runCatching {
                    client.permissionController.getGrantedPermissions()
                }.getOrNull() ?: return@withContext HealthConnectWeightImportResult.PermissionMissing

                if (readWeightPermission !in grantedPermissions) {
                    return@withContext HealthConnectWeightImportResult.PermissionMissing
                }

                val normalizedRange = normalizeWeightRange(startEpochDay, endEpochDay)
                val records = runCatching {
                    readWeightRecords(
                        client = client,
                        startEpochDay = normalizedRange.first,
                        endEpochDay = normalizedRange.last
                    )
                }.getOrElse { throwable ->
                    Log.d(logTag, "Health Connect weight import failed", throwable)
                    return@withContext HealthConnectWeightImportResult.Failed(throwable)
                }

                if (records.isEmpty()) {
                    return@withContext HealthConnectWeightImportResult.NoData
                }

                val selectedRecords = pickOneRecordPerDay(records)
                if (selectedRecords.isEmpty()) {
                    return@withContext HealthConnectWeightImportResult.NoData
                }

                val bodyWeightDao = database.bodyWeightHistoryDao()
                selectedRecords.forEach { entry ->
                    bodyWeightDao.upsertBodyWeightEntry(entry)
                }

                HealthConnectWeightImportResult.Imported(selectedRecords.size)
            }
        }
    }

    private suspend fun readWeightRecords(
        client: HealthConnectClient,
        startEpochDay: Long,
        endEpochDay: Long
    ): List<WeightRecord> {
        val zoneId = ZoneId.systemDefault()
        val normalizedRange = normalizeWeightRange(startEpochDay, endEpochDay)
        val startInstant = LocalDate.ofEpochDay(normalizedRange.first)
            .atStartOfDay(zoneId)
            .toInstant()
        val endInstant = LocalDate.ofEpochDay(normalizedRange.last)
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()

        val collectedRecords = mutableListOf<WeightRecord>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    ascendingOrder = true,
                    pageSize = 500,
                    pageToken = pageToken.orEmpty()
                )
            )
            collectedRecords += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrBlank())

        return collectedRecords.filter { record ->
            val recordDate = record.time.atZone(record.zoneOffset ?: zoneId).toLocalDate()
            val epochDay = recordDate.toEpochDay()
            epochDay in normalizedRange
        }
    }

    private fun pickOneRecordPerDay(records: List<WeightRecord>): List<BodyWeightEntryEntity> {
        val zoneId = ZoneId.systemDefault()
        val comparator = compareBy<WeightRecord>(
            { it.time },
            { it.metadata.lastModifiedTime },
            { it.metadata.id }
        )

        return records
            .groupBy { record ->
                record.time.atZone(record.zoneOffset ?: zoneId).toLocalDate().toEpochDay()
            }
            .mapNotNull { (epochDay, dayRecords) ->
                val selectedRecord = dayRecords.maxWithOrNull(comparator) ?: return@mapNotNull null
                BodyWeightEntryEntity(
                    entryDateEpochDay = epochDay,
                    weightKg = selectedRecord.weight.inKilograms
                )
            }
            .sortedBy { it.entryDateEpochDay }
    }

    private fun isHealthConnectAvailable(context: Context): Boolean {
        val companion = HealthConnectClient.Companion
        val apiSupported = invokeBoolean(companion, "isApiSupported") ?: true
        if (!apiSupported) {
            return false
        }

        val providerAvailable = invokeBoolean(
            companion,
            "isProviderAvailable",
            context,
            providerPackageName
        ) ?: invokeBoolean(companion, "isProviderAvailable", context)
            ?: invokeBoolean(companion, "isProviderAvailable")
            ?: false

        return providerAvailable
    }

    private fun createHealthConnectClient(context: Context): HealthConnectClient? {
        val companion = HealthConnectClient.Companion
        return invokeCompat(companion, "getOrCreate", context, providerPackageName)
            ?: invokeCompat(companion, "getOrCreate", context)
            ?: invokeCompat(companion, "getOrCreate", context, listOf(providerPackageName))
    }

    private fun normalizeWeightRange(
        startEpochDay: Long,
        endEpochDay: Long
    ): LongRange {
        val normalizedStart = minOf(startEpochDay, endEpochDay)
        val normalizedEnd = maxOf(startEpochDay, endEpochDay)
        return normalizedStart..normalizedEnd
    }

    private fun invokeBoolean(target: Any, methodName: String, vararg args: Any?): Boolean? {
        return invokeCompat<Any?>(target, methodName, *args)?.let { result ->
            when (result) {
                is Boolean -> result
                else -> null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invokeCompat(target: Any, methodName: String, vararg args: Any?): T? {
        val method = target.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName &&
                candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.zip(args).all { (parameterType, argument) ->
                    argument == null || isCompatible(parameterType, argument)
                }
        } ?: return null

        return runCatching {
            method.invoke(target, *args)
        }.getOrNull() as? T
    }

    private fun isCompatible(parameterType: Class<*>, argument: Any): Boolean {
        return when {
            parameterType.isPrimitive -> primitiveWrapper(parameterType)?.isInstance(argument) == true
            parameterType.isInstance(argument) -> true
            parameterType.isAssignableFrom(argument.javaClass) -> true
            else -> false
        }
    }

    private fun primitiveWrapper(parameterType: Class<*>): Class<*>? {
        return when (parameterType) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            else -> null
        }
    }
}
