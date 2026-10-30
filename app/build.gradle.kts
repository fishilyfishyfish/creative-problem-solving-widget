import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 나이스 인증키: GitHub Secrets(NEIS_API_KEY) 또는 로컬 gradle 속성에서 읽는다.
// 비어 있어도 빌드는 되고, 앱 설정 화면에서 직접 입력할 수 있다.
val neisApiKey: String = (System.getenv("NEIS_API_KEY")
    ?: (project.findProperty("NEIS_API_KEY") as String?)
    ?: "").trim().replace("\"", "")

// 버전 번호 = GitHub Actions 실행 번호. 앱은 이 숫자로 새 버전인지 판단한다.
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// 업데이트를 받아올 저장소
val updateRepo: String = System.getenv("GITHUB_REPOSITORY") ?: "fishilyfishyfish/creative-problem-solving-widget"

android {
    namespace = "io.github.fishilyfishyfish.cpswidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.fishilyfishyfish.cpswidget"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "NEIS_API_KEY", "\"$neisApiKey\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    // 빌드마다 같은 키로 서명해야 덮어 설치(업데이트)가 된다.
    // 개인 과제용이라 키를 저장소에 같이 둔다.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/cps-widget.jks")
            storePassword = "cps-widget-key"
            keyAlias = "cps-widget"
            keyPassword = "cps-widget-key"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
