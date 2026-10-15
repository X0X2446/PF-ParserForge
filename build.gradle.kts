plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register<Exec>("buildForgeDex") {
    workingDir(rootDir)
    commandLine("bash", "tools/build-forge-dex.sh")
    inputs.files(
        fileTree("parser-core/src/main/java"),
        fileTree("parser-core/src/ext"),
        file("tools/build-forge-dex.sh"),
    )
    outputs.file("parser-core/src/main/resources/mihon/forge-source.dex")
}
