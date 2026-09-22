package dev.pranav.applock.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

fun Context.isAccessibilityServiceEnabled(): Boolean {
    val accessibilityServiceName =
        "$packageName/$packageName.services.AppLockAccessibilityService"
    val enabledServices = Settings.Secure.getString(
        contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    if (enabledServices?.contains(accessibilityServiceName) == true) {
        return true
    } else {
        if (enabledServices?.contains("$packageName/.services.AppLockAccessibilityService") == true) {
            return true
        }
    }
    return false
}

/**
 * ANDROID KENDİLİĞİNDEN KAPATABİLİR: her APK güncellemesinden sonra (ve bazı OEM'lerde arka
 * planda öldürülünce) sistem Erişilebilirlik Hizmeti iznini otomatik kapatıyor -- bu, normal bir
 * Service gibi kod içinden `startService()` ile GERİ AÇILAMAZ (BootReceiver'daki eski deneme bu
 * yüzden işe yaramıyordu, sistem yalnızca kendi bağladığı/ayarladığı servisi kabul ediyor).
 *
 * Bu fonksiyon `WRITE_SECURE_SETTINGS` iznine sahipse (yalnızca `adb shell pm grant ...` ile
 * verilebilir, normal kurulumda otomatik gelmez) izni doğrudan geri yazar. İzin verilmemişse
 * SecurityException'ı yutar ve false döner (uygulama çökmez, sadece onarım başarısız olur).
 */
fun Context.repairAccessibilityServiceIfNeeded(): Boolean {
    if (isAccessibilityServiceEnabled()) return true

    val serviceString = "$packageName/$packageName.services.AppLockAccessibilityService"
    return try {
        val current = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val servicesSet = (current ?: "").split(':').filter { it.isNotBlank() }.toMutableSet()
        servicesSet.add(serviceString)
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            servicesSet.joinToString(":")
        )
        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Log.i("AccessibilityRepair", "Erişilebilirlik hizmeti kendiliğinden onarıldı.")
        true
    } catch (e: SecurityException) {
        Log.w(
            "AccessibilityRepair",
            "Onarılamadı -- WRITE_SECURE_SETTINGS izni yok (adb ile verilmemiş): ${e.message}"
        )
        false
    } catch (e: Exception) {
        Log.e("AccessibilityRepair", "Onarım sırasında beklenmeyen hata", e)
        false
    }
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

fun Context.enableAccessibilityServiceWithShizuku(serviceComponentName: ComponentName): Boolean {
    val TAG = "ShizukuAccessibilityStarter"
    val serviceString = serviceComponentName.flattenToString()

    if (!Shizuku.pingBinder()) {
        Log.e(TAG, "Shizuku is not available or permission denied.")
        return false
    }

    try {
        val getCurrentCommand = "settings get secure enabled_accessibility_services"
        val currentServices = exec(getCurrentCommand).first()

        val servicesSet = currentServices.split(':')
            .filter { it.isNotBlank() }
            .toMutableSet()

        if (servicesSet.contains(serviceString)) {
            Log.i(TAG, "Service '$serviceString' is already enabled.")
            return true
        }

        servicesSet.add(serviceString)
        val newServicesList = servicesSet.joinToString(":")

        val enableServiceCommand =
            "settings put secure enabled_accessibility_services $newServicesList"
        val enableGlobalCommand = "settings put secure accessibility_enabled 1"

        exec(enableServiceCommand, enableGlobalCommand)

        Log.i(TAG, "Successfully enabled service: $serviceString")
        return true

    } catch (e: Exception) {
        Log.e(
            TAG,
            "Failed to enable Accessibility Service with Shizuku for $serviceString: ${e.message}"
        )
        e.printStackTrace()
        return false
    }
}

private fun exec(vararg command: String): List<String> {
    val output = mutableListOf<String>()
    if (Shizuku.pingBinder()) {
        Log.i("ShizukuPermissionHandler", "Shizuku is running")
    }
    val m = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
    )
    m.isAccessible = true
    val process =
        m.invoke(null, arrayOf("sh", "-c", *command), null, "/") as ShizukuRemoteProcess
    process.apply {
        waitFor()
        Log.i("ShizukuPermissionHandler", "Process exited with code ${exitValue()}")
        inputStream.bufferedReader().use {
            output.addAll(it.readLines())
        }
        errorStream.bufferedReader().use {
            output.addAll(it.readLines().map { "error: it" })
        }
    }
    return output
}
