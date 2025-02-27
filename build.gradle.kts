buildscript {
    repositories {
        google()  // ✅ Required for Firebase & Google services
        mavenCentral()
    }

    dependencies {
        classpath("com.google.gms:google-services:4.3.15") // ✅ Add Google Services Plugin
    }
}
