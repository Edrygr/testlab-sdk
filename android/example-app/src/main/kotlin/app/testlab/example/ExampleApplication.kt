package app.testlab.example

import android.app.Application
import app.testlab.sdk.TestLabConfig
import app.testlab.sdk.TestLabSDK

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        TestLabSDK.init(
            context = this,
            config = TestLabConfig(
                apiKey = BuildConfig.TESTLAB_API_KEY,
                appId = BuildConfig.TESTLAB_APP_ID,
                debug = BuildConfig.TESTLAB_DEBUG
            )
        )
    }
}
