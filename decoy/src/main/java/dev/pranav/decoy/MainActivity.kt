package dev.pranav.decoy

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val intent = Intent().apply {
            component = ComponentName(
                "dev.pranav.applock",
                "dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity"
            )
            putExtra("locked_package", this@MainActivity.packageName.substringBefore(".decoy"))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
