package app.testlab.example

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.testlab.sdk.TestLabSDK

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle deep link from TestLab app
        handleDeepLink(intent?.data)

        // Declare total screen count for coverage tracking (optional)
        TestLabSDK.registerTotalScreenCount(5)

        findViewById<Button>(R.id.btnTrackEvent).setOnClickListener {
            TestLabSDK.trackEvent("button_tapped", mapOf("id" to "btn_track"))
        }

        findViewById<Button>(R.id.btnNavigateDetail).setOnClickListener {
            TestLabSDK.trackScreen("DetailScreen")
            // navigate to detail screen...
        }

        val coverage = TestLabSDK.getScreenCoverage()
        if (coverage != null) {
            findViewById<TextView>(R.id.tvCoverage).text = "Coverage: %.0f%%".format(coverage)
        }
    }

    private fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        val fromTestLab = uri.getQueryParameter("from") == "testlab"
        val testerId = uri.getQueryParameter("tester")

        if (fromTestLab) TestLabSDK.launchedFromTestLab()
        if (testerId != null) TestLabSDK.identify(testerId)
    }
}
