package com.obdvis.android

import android.app.Application

class OBDVisApp : Application() {
    val sharedAutoState = SharedAutoState()
}
