val gsonVersion = "2.11.0"

dependencies {
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
