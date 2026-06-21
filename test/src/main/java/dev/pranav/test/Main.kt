package dev.pranav.test

import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import java.io.File

fun main(args: Array<String>) {
    val baseTemplateFile =
        File("/Users/sandeeppurwar/AndroidStudioProjects/AppLock/decoy/build/intermediates/apk/debug/decoy-debug.apk")
    val decodeWorkDir = File(
        "/Users/sandeeppurwar/AndroidStudioProjects/AppLock/decoy/build/intermediates/apk/debug/",
        "apktool_workspace_koe.nichi"
    )
    val outputUnsignedApk = File(
        "/Users/sandeeppurwar/AndroidStudioProjects/AppLock/decoy/build/intermediates/apk/debug/",
        "unsigned_koe.nichi.apk"
    )

    if (decodeWorkDir.exists()) decodeWorkDir.deleteRecursively()
    if (outputUnsignedApk.exists()) outputUnsignedApk.delete()

    val config = Config("3.0.2").apply {
        setDecodeSources(Config.DecodeSources.NONE)
    }

    val decoder = ApkDecoder(
        baseTemplateFile,
        Config("3.0.2").apply {
            this.setDecodeSources(Config.DecodeSources.NONE)
        }
    )
    decoder.decode(decodeWorkDir)

    patchManifestPackageIdentity(decodeWorkDir, "koe.nichi")
    patchStringsValueLabel(decodeWorkDir, "Konichi")

    val builder = ApkBuilder(decodeWorkDir, config)
    builder.build(outputUnsignedApk)

    //baseTemplateFile.delete()
    //decodeWorkDir.deleteRecursively()
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

    println(content)
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
