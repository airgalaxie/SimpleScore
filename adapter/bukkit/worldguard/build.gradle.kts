dependencies {
    api(project(":core"))

    implementation(project(":adapter:bukkit:worldguard:v6"))
    implementation(project(":adapter:bukkit:worldguard:v7"))

    compileOnly(libs.bukkit)
}
