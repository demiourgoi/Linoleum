# Simulation file replayer

## Development 

Basic gradle targets

```bash
# This leaves in app/build/distributions/ we have .zip and .tar distributions of the app, 
# with wrapper scrips to launch it
./gradlew build

./gradlew test

# Runs using as entry point the `mainClass` defined app/build.gradle.kts
./gradlew run

./gradlew clean
```

Gradle build is configured in `app/build.gradle.kts` and `settings.gradle.kts`