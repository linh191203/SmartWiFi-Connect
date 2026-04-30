# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Retrofit/OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Keep field names for Gson JSON deserialization.
# Only the API response/request DTOs actually go through Gson reflection;
# internal data classes and local DB models do NOT need to be preserved.
-keepclassmembers class com.example.smartwificonnect.data.ParsedWifiData { <fields>; }
-keepclassmembers class com.example.smartwificonnect.data.AiValidateData { <fields>; }
-keepclassmembers class com.example.smartwificonnect.data.FuzzyMatchData { <fields>; }
-keepclassmembers class com.example.smartwificonnect.data.FuzzyNetworkPayload { <fields>; }
-keepclassmembers class com.example.smartwificonnect.data.SaveNetworkRequest { <fields>; }
-keepclassmembers class com.example.smartwificonnect.data.HealthData { <fields>; }
-keepclassmembers class com.example.smartwificonnect.data.FuzzyMatchItem { <fields>; }

# Keep generic envelope wrappers (they are parameterized — keep all members)
-keep class com.example.smartwificonnect.data.ApiEnvelope { *; }
-keep class com.example.smartwificonnect.data.ApiEnvelope$* { *; }