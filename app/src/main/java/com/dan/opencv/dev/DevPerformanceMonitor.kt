package com.dan.opencv.dev

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock

data class MemoryMetrics(
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val heapAllocatedMb: Long,
    val nativeHeapAllocatedMb: Long
)

data class BatteryMetrics(
    val levelPercent: Int,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val isCharging: Boolean
)

data class BenchmarkRecord(
    val timestampMs: Long = System.currentTimeMillis(),
    val tag: String,
    val durationMs: Long,
    val details: String = ""
)

object DevPerformanceMonitor {

    private val records = mutableListOf<BenchmarkRecord>()

    fun getMemoryMetrics(): MemoryMetrics {
        val runtime = Runtime.getRuntime()
        val heapAllocated = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMax = runtime.maxMemory() / (1024 * 1024)
        val totalHeap = runtime.totalMemory() / (1024 * 1024)
        val nativeHeap = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

        return MemoryMetrics(
            heapUsedMb = heapAllocated,
            heapMaxMb = heapMax,
            heapAllocatedMb = totalHeap,
            nativeHeapAllocatedMb = nativeHeap
        )
    }

    fun getBatteryMetrics(context: Context): BatteryMetrics {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return BatteryMetrics(0, 0f, 0, false)

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryMetrics(
            levelPercent = percent,
            temperatureCelsius = temperature,
            voltageMv = voltage,
            isCharging = isCharging
        )
    }

    inline fun <T> measureAndRecord(tag: String, details: String = "", block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            val duration = SystemClock.elapsedRealtime() - start
            record(tag, duration, details)
        }
    }

    @Synchronized
    fun record(tag: String, durationMs: Long, details: String = "") {
        records.add(0, BenchmarkRecord(tag = tag, durationMs = durationMs, details = details))
        if (records.size > 50) {
            records.removeAt(records.lastIndex)
        }
    }

    @Synchronized
    fun getRecentBenchmarks(): List<BenchmarkRecord> = records.toList()

    @Synchronized
    fun clearBenchmarks() {
        records.clear()
    }
}
