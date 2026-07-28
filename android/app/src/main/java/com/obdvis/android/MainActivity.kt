package com.obdvis.android

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import kotlin.math.cos
import kotlin.math.sin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.obdvis.android.AppSettings
import com.obdvis.android.NotificationHelper
import com.obdvis.android.domain.health.SavedDrive
import com.obdvis.android.domain.model.ConnectionState
import com.obdvis.android.ui.DevicePickerScreen
import com.obdvis.android.ui.DashboardScreen
import com.obdvis.android.ui.DriveHistoryScreen
import com.obdvis.android.ui.MainViewModel
import com.obdvis.android.ui.PermissionScreen
import com.obdvis.android.ui.PostDriveScreen
import com.obdvis.android.ui.requiredPermissions
import com.obdvis.android.ui.searchForDtc

class MainActivity : ComponentActivity() {

    private val appSettings: AppSettings by lazy { AppSettings(this) }
    private val notificationHelper: NotificationHelper by lazy { NotificationHelper(this) }

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(
                    appSettings,
                    notificationHelper,
                    application,
                ) as T
        }
    }

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var rotationVectorSensor: Sensor? = null

    // Rotation matrix R: transforms phone-frame vectors to world frame (X=East, Y=North, Z=Up).
    // Both sensor callbacks run on the same handler thread so no lock needed.
    private val rotationMatrix = FloatArray(9) { if (it % 4 == 0) 1f else 0f } // identity

    private val rotationVectorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val gForceSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val R = rotationMatrix
            // Project phone-frame acceleration into world horizontal plane (East, North).
            val worldEast  = R[0] * ax + R[1] * ay + R[2] * az
            val worldNorth = R[3] * ax + R[4] * ay + R[5] * az

            // Extract car heading (azimuth) from rotation matrix so we can rotate
            // world-frame (East/North) into car-frame (lateral/longitudinal).
            // Azimuth = 0 → facing North, π/2 → facing East.
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            val heading = orientation[0]
            val cosH = cos(heading)
            val sinH = sin(heading)

            // Forward (longitudinal) = projection onto car's heading direction.
            // Right (lateral)        = projection onto car's right direction.
            viewModel.updateGForce(
                lateralG      = -(worldEast * cosH - worldNorth * sinH) / 9.81f,
                longitudinalG = -(worldEast * sinH + worldNorth * cosH) / 9.81f,
            )
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    override fun onResume() {
        super.onResume()
        viewModel.setForegrounded(true)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        linearAccelSensor?.let {
            sensorManager.registerListener(gForceSensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(rotationVectorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.setForegrounded(false)
        sensorManager.unregisterListener(gForceSensorListener)
        sensorManager.unregisterListener(rotationVectorListener)
    }

    override fun onDestroy() {
        notificationHelper.cancelStandingNotification()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var permissionsGranted by remember {
                mutableStateOf(hasPermissions())
            }

            val connectionState by viewModel.connectionState.collectAsState()
            val postDriveData by viewModel.postDriveData.collectAsState()
            val postDriveCsvContent by viewModel.postDriveCsvContent.collectAsState()
            val driveHistory by viewModel.driveHistory.collectAsState()
            var showDriveHistory by remember { mutableStateOf(false) }
            var selectedSavedDrive by remember {
                mutableStateOf<SavedDrive?>(null)
            }

            when {
                !permissionsGranted -> {
                    PermissionScreen(onPermissionsGranted = { permissionsGranted = true })
                }

                postDriveData != null -> {
                    val currentSummary = postDriveData!!
                    PostDriveScreen(
                        data = currentSummary,
                        csvContent = postDriveCsvContent,
                        onDismiss = viewModel::dismissPostDrive,
                        onSearchDtc = this::searchForDtc,
                    )
                }

                selectedSavedDrive != null -> {
                    val savedDrive = selectedSavedDrive!!
                    PostDriveScreen(
                        data = savedDrive.summary,
                        csvContent = savedDrive.csvContent,
                        title = "Saved Drive",
                        onDismiss = { selectedSavedDrive = null },
                        onSearchDtc = this::searchForDtc,
                        onDelete = {
                            viewModel.deleteSavedDrive(savedDrive.id)
                            selectedSavedDrive = null
                        },
                    )
                }

                showDriveHistory -> {
                    DriveHistoryScreen(
                        drives = driveHistory,
                        onBack = { showDriveHistory = false },
                        onOpen = { selectedSavedDrive = it },
                        onDelete = { viewModel.deleteSavedDrive(it.id) },
                    )
                }

                connectionState is ConnectionState.Connected -> {
                    DashboardScreen(viewModel = viewModel)
                }

                else -> {
                    DevicePickerScreen(
                        viewModel = viewModel,
                        historyCount = driveHistory.size,
                        onOpenHistory = { showDriveHistory = true },
                    )
                }
            }

        }
    }

    private fun hasPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
