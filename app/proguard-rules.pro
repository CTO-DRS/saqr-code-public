# v0.1 ships without minification. Rules kept for when R8 is enabled in v0.2.
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-keep class org.eclipse.jgit.** { *; }
