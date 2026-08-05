package com.arthapilot.app

import android.app.Application
import com.arthapilot.app.data.local.PassphraseProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ArthaApp : Application() {

    @Inject
    lateinit var passphraseProvider: PassphraseProvider

    override fun onCreate() {
        super.onCreate()
        // Warm the encrypted DB passphrase off the main thread so the first
        // ViewModel construction never blocks on Keystore/DataStore IO.
        Thread {
            runCatching { passphraseProvider.blockingGetOrCreate() }
        }.start()
    }
}
