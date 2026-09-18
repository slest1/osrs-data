val runeLiteVersion = "1.12.39"

dependencies {
    implementation(project(":questdata-schema"))

    // implementation, not compileOnly: the jar loaded at runtime needs these classes
    implementation("net.runelite:client:$runeLiteVersion")
    implementation("net.runelite:runelite-api:$runeLiteVersion")
    implementation("com.google.code.gson:gson:2.8.9")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

// src/test holds QuestStatesParity, a manual main class run by the parity task, not a test suite
tasks.named<Test>("test") {
    enabled = false
}

tasks.register<JavaExec>("export") {
    group = "questdata"
    description = "Export Quest Helper data to JSON"
    mainClass = "com.slest.questdata.Main"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("parity") {
    group = "questdata"
    description = "Check QuestStates.stateOf against QuestHelperQuest.getState"
    mainClass = "com.slest.questdata.QuestStatesParity"
    classpath = sourceSets["test"].runtimeClasspath
}
