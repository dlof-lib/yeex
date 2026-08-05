package com.yeex.dlof

import android.app.Application
import com.google.firebase.FirebaseApp

class YeexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
