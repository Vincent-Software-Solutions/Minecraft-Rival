-dontshrink
-dontoptimize
-dontwarn
-dontnote
-forceprocessing
-useuniqueclassmembernames
-repackageclasses 'de.minecraft.rival.client.internal'
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,Record
-adaptresourcefilecontents fabric.mod.json
-keepnames class de.minecraft.rival.client.RivalClient
-keepclassmembers class de.minecraft.rival.client.RivalClient {
    public void onInitializeClient();
}
-keepclassmembers class * implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type type();
}
-printmapping build/obfuscation-map.txt
