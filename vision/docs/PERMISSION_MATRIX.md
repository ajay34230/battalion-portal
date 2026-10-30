# Permission Matrix

| Feature | Permission / mechanism | Requested when | Revocable |
|---|---|---|---|
| Voice input | RECORD_AUDIO | first mic use | Yes |
| Foreground voice service | FOREGROUND_SERVICE(+_MICROPHONE on 14+), POST_NOTIFICATIONS (13+) | wake-word enabled | Yes |
| Network / downloads | INTERNET, ACCESS_NETWORK_STATE | install | No (normal) |
| Contacts / calls | READ_CONTACTS, CALL_PHONE (or dial intent without permission) | first use | Yes |
| SMS draft | SENDTO intent (no permission) | on use | n/a |
| Notifications | NotificationListenerService (special access) | user enables in Settings | Yes |
| UI control | AccessibilityService (special access) | user enables | Yes |
| App listing | `<queries>` launcher intent | install | n/a |
| Alarms | SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM policy-dependent | first reminder | Yes |
| Boot persistence | RECEIVE_BOOT_COMPLETED | install | n/a |
| Files | Scoped storage / SAF picker (no MANAGE_EXTERNAL_STORAGE) | on use | Yes |
| Termux | `com.termux.permission.RUN_COMMAND` | user enables | Yes |
| Gmail | OAuth consent, scope gmail.readonly (+compose only if needed) | on connect | Yes |
