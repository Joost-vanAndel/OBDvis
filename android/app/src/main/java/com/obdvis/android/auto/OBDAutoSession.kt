package com.obdvis.android.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class OBDAutoSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = AutoDashboardScreen(carContext)
}
