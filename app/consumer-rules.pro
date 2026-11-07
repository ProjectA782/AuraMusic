# Keep the LastFM singleton and all its public/private members
-keep class com.aura.lastfm.LastFM { *; }
-keep class com.aura.lastfm.LastFM$LastFmException { *; }

# Keep serializable model classes used for JSON parsing
-keep class com.aura.lastfm.models.** { *; }
