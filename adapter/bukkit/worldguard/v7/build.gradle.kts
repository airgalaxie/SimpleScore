dependencies {
    api(project(":adapter:bukkit:worldguard:common"))

    compileOnly(libs.bukkit)
    compileOnly(libs.worldguardV7) {
        exclude(module = "bukkit")
    }
}
