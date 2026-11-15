package site.hikomorebi.wardrobe

import android.app.Application
import site.hikomorebi.wardrobe.data.*

class WardrobeApplication : Application() {
    lateinit var auth: SessionManager
        private set

    lateinit var repository: WardrobeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        auth = SessionManager(SecureSessionStorage(this), BuildConfig.DEBUG)
        repository = WardrobeRepository(auth)
        PhotoProcessor.clean(this)
    }
}
