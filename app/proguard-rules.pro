# Add project specific ProGuard rules here.

# WebNex default release keep rules for AndroidX Startup initializers.
-keep class androidx.startup.InitializationProvider
-keep class androidx.emoji2.text.EmojiCompatInitializer { *; }
-keep class androidx.lifecycle.ProcessLifecycleInitializer { *; }
-keep class androidx.profileinstaller.ProfileInstallerInitializer { *; }
