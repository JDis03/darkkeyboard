plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

group = "org.dark.keyboard.editor"
version = "1.0.0"

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "keyboard-editor.js"
            }
            binaries.executable()
            
            // Disable Node.js download - use system Node.js if available
            // or skip webpack for now
            testTask {
                enabled = false
            }
        }
        
        // Use system Node.js if available
        nodejs {
            
        }
    }
    
    sourceSets {
        val jsMain by getting {
            dependencies {
                // Compose for Web
                implementation(compose.html.core)
                implementation(compose.runtime)
                
                // Shared module (models, serialization, validation)
                implementation(project(":shared"))
            }
        }
    }
}

compose {
    // Required for Compose for Web
}
