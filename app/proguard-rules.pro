# BroTimer only ships debug builds today (assembleDebug is what installs over adb).
# These rules exist so a release build does not silently strip the alarm entry points,
# which are reached by the system, never by our own code.
-keep class com.brotimer.alarm.AlarmReceiver { *; }
-keep class com.brotimer.alarm.BootReceiver { *; }
-keep class com.brotimer.alarm.AlarmService { *; }
-keep class com.brotimer.alarm.AlarmActivity { *; }
