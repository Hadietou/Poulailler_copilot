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

# --- Retrofit / OkHttp / Gson --------------------------------------------
# Sans ces règles, le build release (minifyEnabled) casse les appels réseau
# suspend de Retrofit (WeatherApiService, GeocodingApiService, BrevoApiService) :
# R8 supprime les informations de type générique (Signature) dont Retrofit a
# besoin pour lire le type de retour d'une fonction suspend, ce qui provoque
# un ClassCastException (Class cannot be cast to ParameterizedType) au premier
# appel réseau — par exemple la météo du dashboard qui reste bloquée sur "--".
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**

# Modèles désérialisés par Gson : les noms de champs doivent rester intacts,
# sinon les réponses JSON (météo, géocodage, Brevo) ne se mappent plus sur les
# propriétés Kotlin et les valeurs restent nulles silencieusement.
-keep class com.hadietou.poulailler.network.** { <fields>; }
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer