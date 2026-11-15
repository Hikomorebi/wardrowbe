# DTO field names are part of the server API and must survive release shrinking.
-keep class site.hikomorebi.wardrobe.data.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
