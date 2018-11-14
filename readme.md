# Twitched API

API that powers the [Twitched] Roku app.

# Installing/Running

This web applet is powered by the Gradle build system and designed to
 run on Heroku.
 
## Building

**IDE: The project must be built with gradle first for the constants class to be generated and available to the IDE.**

```
./gradlew jar
```

The output will be at: `./server/build/libs/server-*.jar` and `./status/build/libs/status-*.jar`.
 
## Environment Variables

Most of the parameters are passed via environment variables.

### ALLOWED_CLIENT_ID

Random string that serves as a way for a client to identify itself

Multiple client ids can be defined by delimiting ids with a comma `,`.

### GRADLE_TASK

Heroku specific variable that defines what task should be used to build
 with Gradle

This should be set to "jar".
 
### REDIS_URL

Redis server URL

This should be in the following format:
 `redis://<username>:<password>@<server>:<port>`
 
### REDIS_URL_ENV

Optional

Changes the environment variable name to use instead of REDIS_URL.

For example, if set to EXT_REDIS_URL, the Redis URL will be pulled from EXT_REDIS_URL instead of the default REDIS_URL.
 
### REDIS_CONNECTIONS

Amount of redis connections allowed
 
### TWITCH_CLIENT_ID

The Twitch client ID that will be used to make request to the Twitch
 API
 
### TWITCH_CLIENT_SECRET

_Optional_

Client secret used to obtain an app token from the Twitch API

If no client secret is provided requests will not have an app access token and will be limited.

### TWITCH_NO_AUTH

Values: TRUE | FALSE

If this is set to TRUE, the ALLOWED_CLIENT_ID will be ignored and all
 requests will be allowed.
 
### SALT

Global salt to use when hashing sensitive data

### DEV_API

Values: TRUE | FALSE

Enabled dev api endpoints

### REDIRECT_URL

_Optional_

If specified, this URL will be used in Oauth request to Twitch that require a redirect url.
The URL will be crafted based the domain name and scheme used to access the site.
 
### AD_SERVER

_Optional_

Specify a list of ad servers that will be used when the ad server endpoint is called. The string should be a valid JSON
 object with the field ad_servers containing an array populated with one object per server. Each server object shoudl 
 contain a url string field and a countries array. Country strings are expected to be in ISO 3166-1 Alpha 2 format. 
 The country code "INT" is a special case that signifies that the server can be used internationally.
 
```json
{
  "ad_servers" : [
    {
      "url": "http://vast_ad_server.example.com?tag=0001",
      "countries": [
        "INT",
        "US"
      ]
    }
  ]
}
```

### REDIRECT_HTTP

_Optional_

Values: TRUE | FALSE

Default: TRUE

If set to true, HTTP traffic will be redirected to HTTPS.

### SQL_SSL

_Optional_

Values: TRUE | FALSE

Default: TRUE

If set to true, sql connections will required on the database connection.

### SQL_SCHEMA

_Optional_

Default: twitched

Main database scheme name.

### LOG_LEVEL

Values: SEVERE WARNING INFO CONFIG FINE FINER FINEST

Set the log level

The allowed values are the same as java.util.logging levels.

### TWITCHED_CONFIG

JSON object with Twitched configuration settings for apps.

```json
{
  "force_remote_hls": false
}
```

### BIF_QUEUE_ID

Queue id to use. Only one consumer is expected.
    
### GOOGLE_STORAGE_CREDENTIALS

Google JSON credentials

## Tests

### Roku BIF Generator Tests

By default tests for the Roku BIF generator will attempt to download a
 Twitch VOD to the temp directory. If the argument `-DBIF_CLEAR_CACHE=true`
 is not passed to gradle, the VOD will not be re-downloaded if it present
 on disk, This is useful for running performance tests back-to-back.

[Twitched]: https://www.twitched.org