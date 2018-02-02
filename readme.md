# Twitched API

API that powers the [Twitched] Roku app.

# Installing/Running

This web applet is powered by the Gradle build system and designed to
 run on Heroku.
 
## Building

```
./gradlew jar
```

The output will be at: `./build/libs/TwitchUnofficialAPI-*.jar`.
 
## Environment Variables

Most of the parameters are passed via environment variables.

### ALLOWED_CLIENT_ID

Random string that serves as a way for a client to identify itself

### GRADLE_TASK

Heroku specific variable that defines what task should be used to build
 with Gradle

This should be set to "jar".
 
### REDIS_URL

Redis server URL

This should be in the following format:
 `redis://<username>:<password>@<server>:<port>`
 
### REDIS_CONNECTIONS

Amount of redis connections allowed
 
### TWITCH_CLIENT_ID

The Twitch client ID that will be used to make request to the Twitch
 API
 
### TWITCH_CLIENT_SECRET

Client secret used to obtain an app token from the Twitch API

### TWITCH_NO_AUTH

Values: TRUE | FALSE

If this is set to TRUE, the ALLOWED_CLIENT_ID will be ignored and all
 requests will be allowed.
 
# Parameters

CLI parameters

### -log \<level\>

Set the log level

The allowed values are the same as java.util.logging levels.

Values: SEVERE WARNING INFO CONFIG FINE FINER FINEST

[Twitched]: https://www.twitched.org