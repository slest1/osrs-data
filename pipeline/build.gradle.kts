val gsonVersion = "2.11.0"

dependencies {
    implementation(project(":questdata-schema"))
    implementation("com.google.code.gson:gson:$gsonVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.register<JavaExec>("fetch") {
    group = "pipeline"
    description = "Resolve, download and verify the Quest Helper jar the plugin hub serves"
    mainClass = "com.slest.osrsdata.pipeline.FetchMain"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("bundle") {
    group = "pipeline"
    description = "Trim exporter output into bundle format v1 with a manifest"
    mainClass = "com.slest.osrsdata.pipeline.BundleMain"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}
