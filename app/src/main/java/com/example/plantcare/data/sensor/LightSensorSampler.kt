package com.example.plantcare.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Singleton
class LightSensorSampler @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    fun hasSensor(): Boolean = lightSensor != null

    suspend fun sample(
        durationMillis: Long = DEFAULT_DURATION_MS,
        onReading: (Float) -> Unit = {}
    ): LightSampleResult = withContext(Dispatchers.Main) {
        val manager = sensorManager ?: return@withContext LightSampleResult.SensorMissing
        val sensor = lightSensor ?: return@withContext LightSampleResult.SensorMissing
        suspendCancellableCoroutine<LightSampleResult> { cont ->
            val readings = mutableListOf<Float>()
            val handler = Handler(Looper.getMainLooper())
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values.firstOrNull() ?: return
                    readings += lux
                    onReading(lux)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            val completeRunnable = Runnable {
                manager.unregisterListener(listener)
                if (!cont.isCompleted) {
                    if (readings.isEmpty()) {
                        cont.resume(LightSampleResult.Failed)
                    } else {
                        val avg = readings.average()
                        cont.resume(
                            LightSampleResult.Success(
                                averageLux = avg,
                                sampleCount = readings.size,
                                durationMillis = durationMillis
                            )
                        )
                    }
                }
            }

            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            handler.postDelayed(completeRunnable, durationMillis.coerceAtLeast(0L))

            cont.invokeOnCancellation {
                handler.removeCallbacks(completeRunnable)
                manager.unregisterListener(listener)
            }
        }
    }

    companion object {
        const val DEFAULT_DURATION_MS = 5_000L
    }
}

sealed class LightSampleResult {
    data class Success(
        val averageLux: Double,
        val sampleCount: Int,
        val durationMillis: Long
    ) : LightSampleResult()

    data object SensorMissing : LightSampleResult()
    data object Failed : LightSampleResult()
}

