# release 開啟 R8(enable-r8-shrink)。規則的理由見 openspec 的 enable-r8-shrink/design.md D1。

# youtubedl-android 0.18.1 的 AAR 沒有附 consumer rules。它用 Jackson 把 yt-dlp 與 GitHub 的 JSON
# 轉成物件(依欄位名稱與註解反射)。這類錯誤只會在執行期出現,所以整包保留,不改名也不刪除。
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }

# Jackson 的選用擴充引用了 Android 沒有的 JDK 類別(只有 R8 實際報出的這三個)。
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# Jackson 要讀泛型與註解。
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# youtubedl-android 解壓內建 Python 用 commons-compress 的 ZipFile;ExtraFieldUtils 以反射建立各種 ExtraField
# (AsiExtraField 等)。R8 會把它們改成無法實例化,全新安裝第一次呼叫 yt-dlp 就閃退
# (2026-11-22 mobile-v0.1.0 實機:ExceptionInInitializerError / "is not a concrete class")。
# 已解壓過 Python 的舊安裝不會再走這條路,所以覆蓋安裝看不出來,要用全新安裝驗。
-keep class org.apache.commons.compress.archivers.zip.** { *; }
