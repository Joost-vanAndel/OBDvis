package com.obdvis.android.ui
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RedesignRenderTest(private val width: Int, private val height: Int, private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = width, screenHeight = height, density = Density.MEDIUM, fontScale = scale,
        orientation = if (width > height) com.android.resources.ScreenOrientation.LANDSCAPE else com.android.resources.ScreenOrientation.PORTRAIT),
        theme = "android:Theme.Material.NoActionBar", showSystemUi = false)
    @Test fun overview() { paparazzi.snapshot { OverviewRedesignPreview() } }
    @Test fun health() { paparazzi.snapshot { HealthRedesignPreview() } }
    @Test fun dtcs() { paparazzi.snapshot { DtcRedesignPreview() } }
    @Test fun missingData() { paparazzi.snapshot { EmptyOverviewRedesignPreview() } }
    @Test fun sensors() { paparazzi.snapshot { SensorsRedesignPreview() } }
    @Test fun permission() { paparazzi.snapshot { PermissionRedesignPreview() } }
    @Test fun settings() { paparazzi.snapshot { SettingsRedesignPreview() } }
    @Test fun summary() { paparazzi.snapshot { SummaryRedesignPreview() } }
    @Test fun history() { paparazzi.snapshot { HistoryRedesignPreview() } }
    @Test fun about() { paparazzi.snapshot { AboutRedesignPreview() } }
    @Test fun fuelTrim() { paparazzi.snapshot { FuelTrimRedesignPreview() } }
    @Test fun connection() { paparazzi.snapshot { ConnectionRedesignPreview() } }
    @Test fun connectionError() { paparazzi.snapshot { ConnectionErrorRedesignPreview() } }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-{2}x")
        fun sizes() = listOf(arrayOf(320, 900, 1f), arrayOf(375, 900, 1f), arrayOf(414, 900, 1f),
            arrayOf(768, 1024, 1f), arrayOf(320, 1100, 1.5f), arrayOf(768, 375, 1f))
    }
}
