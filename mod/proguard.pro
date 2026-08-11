-dontshrink
-dontoptimize
-dontwarn
-dontnote
-forceprocessing
-useuniqueclassmembernames
-repackageclasses 'de.minecraft.rival.client.internal'
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,Record
-adaptresourcefilecontents META-INF/mods.toml
-keepnames @net.minecraftforge.fml.common.Mod class *
-keep @net.minecraftforge.fml.common.Mod class * { public <init>(); }
-keepclassmembers class de.minecraft.rival.client.RivalIconPack { *; }
-keepclassmembers class de.minecraft.rival.client.RivalTitleScreen { *; }
-keepclassmembers class de.minecraft.rival.client.RivalTitleScreen$* { *; }
-keepclassmembers class * {
    @net.minecraftforge.eventbus.api.SubscribeEvent <methods>;
}
-printmapping build/obfuscation-map.txt
