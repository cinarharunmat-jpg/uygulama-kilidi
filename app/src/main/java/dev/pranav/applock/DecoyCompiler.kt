package dev.pranav.applock

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.android.ApktoolAndroid.AAPT2_LIBRARY_NAME
import com.android.apksig.ApkSigner
import org.conscrypt.Conscrypt
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate

class ApktoolDecoyCompiler(private val context: Context) {

    init {
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

    /**
     * Executes an in-app decode, patch, and build routine utilizing core native apktool-lib objects.
     */
    fun compileDecoy(targetPackage: String, targetLabel: String, targetIcon: Drawable): File {
        val baseTemplateFile = File(context.getExternalFilesDir(null), "base_template.apk")
        val decodeWorkDir =
            File(context.getExternalFilesDir(null), "apktool_workspace_${targetPackage}")
        val outputUnsignedApk =
            File(context.getExternalFilesDir(null), "unsigned_${targetPackage}.apk")
        val finalSignedApk = File(context.getExternalFilesDir(null), "decoy_${targetPackage}.apk")

        if (decodeWorkDir.exists()) decodeWorkDir.deleteRecursively()
        if (outputUnsignedApk.exists()) outputUnsignedApk.delete()
        if (finalSignedApk.exists()) finalSignedApk.delete()

        context.assets.open("decoy.apk").use { input ->
            FileOutputStream(baseTemplateFile).use { output -> input.copyTo(output) }
        }

        val config = Config("3.0.2").apply {
            val aaptBinary =
                File(context.applicationInfo.nativeLibraryDir, AAPT2_LIBRARY_NAME)

            setAaptBinary(aaptBinary.absolutePath)
            val frameworkDir = File(context.filesDir, "apktool/framework")

            if (!frameworkDir.exists()) {
                frameworkDir.mkdirs()
            }
            frameworkDirectory = frameworkDir.absolutePath
            frameworkTag = "1"

            setDecodeSources(Config.DecodeSources.NONE)
        }

        val decoder = ApkDecoder(
            baseTemplateFile,
            config
        )
        decoder.decode(decodeWorkDir)

        patchManifestPackageIdentity(decodeWorkDir, targetPackage)
        patchStringsValueLabel(decodeWorkDir, targetLabel)
        patchIconAsset(decodeWorkDir, targetPackage)

        val builder = ApkBuilder(decodeWorkDir, config)
        builder.build(outputUnsignedApk)

        signApkFile(outputUnsignedApk, finalSignedApk)

        //baseTemplateFile.delete()
        //outputUnsignedApk.delete()
        //decodeWorkDir.deleteRecursively()

        return finalSignedApk
    }

    /**
     * Replaces package identifier attributes inside the decompiled plain text AndroidManifest.xml
     */
    private fun patchManifestPackageIdentity(workDir: File, targetPackage: String) {
        val manifestFile = File(workDir, "AndroidManifest.xml")
        var content = manifestFile.readText()
        content = content.replace(
            "package=\"dev.pranav.decoy\"",
            "package=\"${targetPackage}.decoy\""
        )
        manifestFile.writeText(content)
    }

    /**
     * Substitutes placeholder application name parameters inside the decompiled strings layout
     */
    private fun patchStringsValueLabel(workDir: File, targetLabel: String) {
        val stringsFile = File(workDir, "res/values/strings.xml")
        if (!stringsFile.exists()) return
        var content = stringsFile.readText()
        content = content.replace("decoy", targetLabel)
        stringsFile.writeText(content)
    }

    /**
     * Overwrites layout icon graphic matrices with target configuration bytes
     */
    private fun patchIconAsset(workDir: File, targetPackage: String) {
        val resDir = File(workDir, "res")
        if (!resDir.exists()) return

        // 1. Fetch the absolute true live app icon directly from the OS PackageManager
        val pm = context.packageManager
        val targetIcon: Drawable = try {
            val appInfo = pm.getApplicationInfo(targetPackage, 0)
            pm.getApplicationIcon(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback to a default system image if something goes sideways
            pm.defaultActivityIcon
        }

        // 2. Clear out ALL legacy ic_launcher references across all directories to avoid compilation collision bugs
        resDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name.startsWith("mipmap")) {
                dir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("ic_launcher")) {
                        file.delete()
                    }
                }
            }
        }

        // 3. Establish targeted work subdirectories
        val mipmapAnyDpiDir = File(resDir, "mipmap-anydpi-v26")
        val mipmapHdpiDir = File(resDir, "mipmap-xxhdpi")

        if (!mipmapAnyDpiDir.exists()) mipmapAnyDpiDir.mkdirs()
        if (!mipmapHdpiDir.exists()) mipmapHdpiDir.mkdirs()

        // 4. Branch rendering strategy depending on whether the target app uses an Adaptive Icon
        if (targetIcon is AdaptiveIconDrawable) {
            // Re-separate the target foreground layer artwork from its background canvas container
            val foregroundDrawable = targetIcon.foreground
            val backgroundDrawable = targetIcon.background

            val foregroundFile = File(mipmapAnyDpiDir, "ic_launcher_foreground.png")
            val backgroundFile = File(mipmapAnyDpiDir, "ic_launcher_background.png")

            saveDrawableToPngFile(foregroundDrawable, foregroundFile)
            saveDrawableToPngFile(backgroundDrawable, backgroundFile)

            // Inject an authentic compiled adaptive XML container manifest pointing to our clean layer assets
            val adaptiveXmlFile = File(mipmapAnyDpiDir, "ic_launcher.xml")
            val xmlStructure = """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@mipmap/ic_launcher_background" />
                    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
                </adaptive-icon>
            """.trimIndent()
            adaptiveXmlFile.writeText(xmlStructure)

            // Generate flat fallback legacy bitmap in standard high-density tier for safety
            val legacyFile = File(mipmapHdpiDir, "ic_launcher.png")
            saveDrawableToPngFile(targetIcon, legacyFile)

        } else {
            // Target app relies on classic old-school flat drawings; drop directly to legacy pipeline
            val legacyFile = File(mipmapHdpiDir, "ic_launcher.png")
            saveDrawableToPngFile(targetIcon, legacyFile)
        }
    }

    private fun saveDrawableToPngFile(drawable: Drawable, outputFile: File) {
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            // Adaptive icon layers scale cleanly; enforce a baseline resolution format (typically 512x512)
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512

            val bmp = createBitmap(width, height)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }

        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    /**
     * Signs the unsigned binary container leveraging embedded application keystore assets
     */
    private fun signApkFile(unsignedApk: File, outputSignedApk: File) {
        try {
            val keystore = KeyStore.getInstance("PKCS12")
            context.assets.open("testkey.jks").use { stream ->
                keystore.load(stream, "testkey".toCharArray())
            }

            val privateKey =
                keystore.getKey("testkey", "testkey".toCharArray()) as PrivateKey
            val certificate = keystore.getCertificate("testkey") as X509Certificate

            val signerConfig =
                ApkSigner.SignerConfig.Builder("testkey", privateKey, listOf(certificate))
                    .build()

            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(outputSignedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setMinSdkVersion(26)
                .build()

            apkSigner.sign()
        } catch (e: Exception) {
            throw IllegalStateException("Programmatic signature processing execution failed", e)
        }
    }
}
