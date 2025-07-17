# IPTV-Recorder

Record IPTV from a URL or a M3U file.

## Features
- This tool is for Recording legal IPTV streams from a URL or M3U file
- Please do not support piracy and then you can use this as much as you want under MIT Licens
- Configurable via `config.properties` or environment variables
- Logging to file and console, with log level support
- Graceful shutdown and resource management
- Modern Java 21 codebase
- Interactive channel selection with paging
- Scheduled recording with start/stop times
- Email notifications for errors and recording status

## Usage

### Build
```
mvn clean package
```

### Run

#### Interactive Mode (Recommended)
```
java -jar target/iptv-recorder-1.0.0.jar [config.properties]
```

#### Special Scenario Mode (Direct Search and Record)
```
java -jar target/iptv-recorder-1.0.0.jar config.properties "search_string" "start_time" "stop_time"
```

**Search Functionality:**
- **All words in the search phrase must match** (e.g., "MXGP FHD" will only match channels containing both "MXGP" AND "FHD")
- If multiple channels match, the **first match is automatically selected**
- If the channel's `tvg-name` contains a time (e.g., "20:00"), it will **replace the provided start time**
- Search is case-insensitive and matches partial words

**Examples:**
```bash
# Interactive mode with default config.properties
java -jar target/iptv-recorder-1.0.0.jar

# Interactive mode with custom config file
java -jar target/iptv-recorder-1.0.0.jar my-config.properties

# Direct search and record mode - simple channel name
java -jar target/iptv-recorder-1.0.0.jar config.properties "SVT1" "20:00" "21:00"

# Direct search and record mode - phrase with multiple words
java -jar target/iptv-recorder-1.0.0.jar config.properties "MXGP FHD" "19:00" "21:00"

# Direct search and record mode - channel with time in tvg-name
# If channel tvg-name contains "SVT1 20:30 News", start time will be 20:30 even if 19:00 is the start parameter
java -jar target/iptv-recorder-1.0.0.jar config.properties "SVT1 News" "19:00" "21:00"
```

### CLI Options
- `--help` or `-h`: Print usage and exit
- `--config <file>`: Specify config file path

### Configuration
All options are in `config.properties` (or can be overridden by environment variables):

| Property         | Description                                      | Default                     | Required?                  |
|------------------|--------------------------------------------------|-----------------------------|----------------------------|
| destinationPath  | Directory for recordings                         | ./recordings                | Yes                        |
| url              | IPTV service URL (if useM3UFile=false)           |                             | Yes                        |
| useFFMPEG        | true/false, use ffmpeg for recording (Linux only)| false                       | Yes                        |
| useM3UFile       | true/false, use a local M3U file                 | false                       | Yes                        |
| m3uFile          | Path to M3U file (if useM3UFile=true)            |                             | Yes, if useM3UFile=true    |
| recRetries       | Number of retries for scheduled recording        | 5                           | Yes                        |
| recRetriesDelay  | Delay (in seconds) between retries               | 60                          | Yes                        |
| logFile          | Path to log file                                 | iptv-recorder.log           | No                         |
| timezone         | Timezone for date/time operations                | Europe/Stockholm            | No                         |
| 24_hour_clock    | Use 24-hour clock format                         | true                        | No                         |
| GROUP_TITLE      | Filter channels by group title (pipe-separated)  |                             | No                         |
| SENDMAIL         | Enable email notifications                       | false                       | No                         |
| SENDTO           | Email address to send notifications to           |                             | Yes, if SENDMAIL=true      |
| SENTFROM         | Email address to send from                       |                             | Yes, if SENDMAIL=true      |
| SMTPHOST         | SMTP server host                                 | smtp.gmail.com              | Yes, if SENDMAIL=true      |
| SMTPPORT         | SMTP server port                                 | 465                         | Yes, if SENDMAIL=true      |
| APPPASSWD        | App password for email authentication            |                             | Yes, if SENDMAIL=true      |

**Note:**
- All required parameters must be set, otherwise the program will not start.
- You can override any property with an environment variable of the same name (case-insensitive).

**M3U files are handled in memory or as temporary files. `destinationPath` is only for recordings.**

### Recording Modes

#### Interactive Mode
1. Program starts and loads channel list
2. User selects channel from paginated list
3. User enters start and stop times
4. Recording starts automatically at scheduled time

#### Special Scenario Mode
1. Program searches for channel matching search string (all words must match)
2. If multiple matches found, automatically selects the first match (this is if you want to run the program in crontab)
3. If channel's tvg-name contains a time (HH:MM format), it will replace the provided start time
4. Uses provided start and stop times (or extracted time from tvg-name)
5. Starts recording immediately if start time has passed
6. Sends email notification about recording status

### Logging
- All logs go to both console and the file specified by `logFile`.
- Log file location can be set in config or with the `LOGFILE` environment variable.
- Log levels: INFO, DEBUG, WARNING, ERROR.

### Email Notifications
When `SENDMAIL=true` is configured:
- Error notifications with stack traces
- Recording scheduled notifications
- Search failure notifications
- All exceptions are buffered and sent in summary emails

### Resource Management
- Uses Java `ExecutorService` for concurrency.
- Graceful shutdown: all threads and resources are closed on exit.
- Automatic retry mechanism for failed recordings: the number of attempts and delay between attempts are fully configurable via `recRetries` and `recRetriesDelay` in `config.properties`.

### Troubleshooting
- Check the log file for errors.
- Ensure all required config values are set (see above table).
- For ffmpeg recording, ensure ffmpeg is installed and available in PATH (Linux only).
- Verify email settings if using notifications.
- Check timezone settings for correct recording times.

### Contribution Guidelines
- Fork the repo and create a feature branch.
- Write clear commit messages and add Javadoc to public methods/classes.
- Run `mvn clean package` and ensure all tests pass before submitting a PR.

### License
MIT License

### Developer
The developer is not responsible for how this tool is used. It is the user’s responsibility to ensure that any recorded content does not violate copyright laws.
