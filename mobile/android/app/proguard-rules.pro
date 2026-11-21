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
