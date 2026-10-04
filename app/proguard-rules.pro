# De UserService wordt door Shizuku op naam geladen in een ander proces, en AIDL-stubs worden via reflectie gebruikt
-keep class com.ericbruggema.taskbarstats.StatsUserService { *; }
-keep class com.ericbruggema.taskbarstats.IStatsService { *; }
-keep class com.ericbruggema.taskbarstats.IStatsService$Stub { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.**
# Systeemservices en widget-ontvanger staan in het manifest; AGP houdt die zelf, dit is extra zekerheid
-keep class com.ericbruggema.taskbarstats.StatsTileService { *; }
-keep class com.ericbruggema.taskbarstats.StatsWidget { *; }
