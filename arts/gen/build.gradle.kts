// Sandbox module for the `generate-art` skill.
// Inherits the `:gart` dependency and `src/<pkg>/` layout from arts/build.gradle.kts.
// Generated pieces live in src/gen/ and are rendered headlessly to PNG.
dependencies {
    implementation(project(":gart-box2d"))
}
