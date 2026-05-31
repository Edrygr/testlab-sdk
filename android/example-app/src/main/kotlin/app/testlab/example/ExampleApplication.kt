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
                apiKey = "tl_live_xxxxxxxxxxxx",
                appId = "app.testlab.example",
                debug = BuildConfig.DEBUG
            )
        )
    }
}
