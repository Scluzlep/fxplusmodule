package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.scluzlep.fxplusmodule.config.ConfigContract
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

enum class FxProfile {
    FX_9012,
    FX_9108,
    STRUCTURAL_FALLBACK
}

data class DexMethodSpec(
    val className: String,
    val methodName: String,
    val descriptor: String,
    val isStatic: Boolean
)

data class DexFieldSpec(
    val className: String,
    val fieldName: String,
    val typeDescriptor: String,
    val isStatic: Boolean
)

data class VersionContract(
    val versionCode: Long,
    val classes: List<String>,
    val methods: List<DexMethodSpec>,
    val fields: List<DexFieldSpec>
)

/**
 * Pure-JVM DEX contract registry shared between [FxSymbolResolver] and
 * `DualApkDexContractTest` so every verified class, method descriptor,
 * and field type stays synchronized across runtime hooks and APK verification.
 */
object FxDexContracts {

    val contract9012: VersionContract by lazy { build9012Contract() }
    val contract9108: VersionContract by lazy { build9108Contract() }

    private fun build9012Contract(): VersionContract {
        val classes = linkedSetOf(
            "nextapp.fx.ui.viewer.image.k0",
            "nextapp.fx.ui.viewer.image.k0\$a",
            "nextapp.xf.dir.DirectoryCatalog",
            "nextapp.xf.dir.DirectoryCatalog\$b",
            "ue.m",
            "ue.h",
            "ue.g",
            "ue.n",
            "ue.n\$g",
            "ue.n\$j",
            "nextapp.fx.dir.optionstore.DirectoryOptionStore",
            "ee.a",
            "nextapp.fx.ui.viewer.image.ImageViewerActivity",
            "nextapp.fx.ui.viewer.image.ImageViewerActivity\$j",
            "nextapp.fx.ui.viewer.image.ImageViewerActivity\$g",
            "nextapp.fx.ui.viewer.image.ImageViewerActivity\$b",
            "nextapp.fx.ui.viewer.image.ImageDisplay",
            "nextapp.maui.ui.imageview.TouchImageDisplay",
            "a9.c",
            "a9.e",
            "dd.h",
            "dd.d",
            "dd.d\$a",
            "dd.j",
            "dd.b",
            "dd.c",
            "dd.l",
            "ya.f",
            "oe.a",
            "u9.h",
            "d9.a",
            "nextapp.fx.plus.dirimpl.dropbox.c",
            "ue.g0",
            "ue.j0",
            "ue.a0",
            "fa.e",
            "nextapp.fx.ui.fxsystem.MainPrefActivity",
            "nextapp.fx.ui.activitysupport.e",
            "nextapp.fx.ui.fxsystem.pref.a",
            "nextapp.maui.ui.preference.LabelPreference",
            "nextapp.fx.ui.widget.k",
            "nextapp.fx.ui.widget.m0",
            "nextapp.fx.ui.widget.k\$f",
            "xc.f",
            "nextapp.fx.ui.viewer.TextViewerActivity",
            "nextapp.fx.ui.activitysupport.j",
            "nextapp.fx.ui.viewer.ExecActivity",
            "nextapp.fx.ui.textedit.k",
            "nextapp.fx.ui.textedit.k\$d",
            "pc.a",
            "h9.i",
            "se.l",
            "i9.d",
            "nextapp.fx.dirimpl.file.FileCatalog",
            "nextapp.fx.dirimpl.file.a",
            "nextapp.fx.dirimpl.file.b",
            "nextapp.fx.dirimpl.file.c",
            "se.f",
            "nextapp.fx.dir.optionstore.HiddenFileStore",
            "nextapp.fx.plus.dirimpl.smb.d",
            "nextapp.fx.plus.dirimpl.smb.legacy.d",
            "nextapp.fx.plus.dirimpl.webdav.c",
            "nextapp.fx.plus.dirimpl.ftp.e",
            "nextapp.fx.plus.dirimpl.ssh.g",
            "nextapp.fx.plus.dirimpl.googledrive.e",
            "nextapp.fx.plus.dirimpl.onedrive.d",
            "nextapp.fx.ui.ExplorerActivity",
            "nextapp.fx.ui.content.r",
            "nextapp.fx.ui.content.h0",
            "nextapp.fx.ui.content.e2",
            "nextapp.fx.ui.content.e0",
            "v8.f",
            "nextapp.fx.ui.homecontent.HomeContentView",
            "nextapp.fx.ui.homecontent.HomeContentView\$d",
            "nextapp.fx.ui.content.j0",
            "le.t",
            "le.f0",
            "le.r",
            "le.b\$a",
            "le.z",
            "le.b0",
            "nextapp.fx.ui.res.ActionIcons",
            "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity",
            "nextapp.fx.plus.dirimpl.googledrive.b\$a",
            "ic.e\$a",
            "i9.e"
        ).toList()

        val methods = listOf(
            // Sibling swipe & DirectoryCatalog
            DexMethodSpec("nextapp.fx.ui.viewer.image.k0", "b", "(Lue/g;Landroid/content/Context;Lue/n\$g;ZLue/h;Lnextapp/fx/ui/viewer/image/k0\$a;)V", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.k0", "c", "(Landroid/content/Context;Lue/h;Lue/n\$g;ZLnextapp/fx/ui/viewer/image/k0\$a;)V", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.k0\$a", "a", "([Lue/h;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.k0\$a", "b", "()V", false),
            DexMethodSpec("nextapp.xf.dir.DirectoryCatalog", "getType", "()Lnextapp/xf/dir/DirectoryCatalog\$b;", false),
            DexMethodSpec("nextapp.xf.dir.DirectoryCatalog", "P", "(Lse/f;)Lue/g;", false),
            DexMethodSpec("nextapp.xf.dir.DirectoryCatalog\$b", "b", "()Z", false),
            DexMethodSpec("ue.m", "i", "()Lnextapp/xf/dir/DirectoryCatalog;", false),
            DexMethodSpec("ue.m", "getLastModified", "()J", false),
            DexMethodSpec("ue.m", "getName", "()Ljava/lang/String;", false),
            DexMethodSpec("ue.m", "getParent", "()Lue/g;", false),
            DexMethodSpec("ue.m", "getPath", "()Lse/f;", false),
            DexMethodSpec("ue.m", "isReadOnly", "()Z", false),
            DexMethodSpec("ue.m", "y0", "(Landroid/content/Context;Ljava/lang/String;)V", false),
            DexMethodSpec("ue.m", "u", "(Landroid/content/Context;Lse/f;)Z", false),
            DexMethodSpec("ue.m", "m0", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("ue.h", "D", "()Ljava/lang/String;", false),
            DexMethodSpec("ue.h", "getSize", "()J", false),
            DexMethodSpec("ue.h", "j", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("ue.h", "p1", "(Landroid/content/Context;J)Ljava/io/OutputStream;", false),
            DexMethodSpec("ue.g", "s1", "(Landroid/content/Context;I)[Lue/m;", false),
            DexMethodSpec("ue.g", "d1", "(Landroid/content/Context;Ljava/lang/CharSequence;Z)Lue/g;", false),
            DexMethodSpec("ue.g", "S", "(Landroid/content/Context;Ljava/lang/CharSequence;)Lue/h;", false),
            DexMethodSpec("ue.g", "H0", "(Landroid/content/Context;Ljava/lang/CharSequence;)Z", false),
            DexMethodSpec("ue.g", "B0", "()V", false),
            DexMethodSpec("ue.n", "o", "([Lue/m;Lue/n\$g;ZZ)V", true),
            DexMethodSpec("ue.n\$g", "o", "(I)Lue/n\$g;", true),
            DexMethodSpec("nextapp.fx.dir.optionstore.DirectoryOptionStore", "e", "(Lue/m;)Lue/n\$j;", true),

            // ImageViewer & prefetch
            DexMethodSpec("ee.a", "T", "(Lue/h;J)V", false),
            DexMethodSpec("ee.a", "U", "()Ljava/io/File;", false),
            DexMethodSpec("ee.a", "W", "()Ljava/io/InputStream;", false),
            DexMethodSpec("ee.a", "Y", "()Landroid/net/Uri;", false),
            DexMethodSpec("ee.a", "getItem", "()Lue/h;", false),
            DexMethodSpec("ee.a", "onDestroy", "()V", false),
            DexMethodSpec("ee.a", "finalize", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "onCreate", "(Landroid/os/Bundle;)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "s1", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "c2", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "U1", "(Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;Lnextapp/fx/ui/viewer/image/ImageDisplay;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "p1", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "j1", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "k1", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "l1", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "d2", "(Landroid/content/Context;Lue/h;I)La9/c\$a;", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "V1", "(Lue/h;Landroid/content/Context;)Ljava/io/InputStream;", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "b2", "(Ljava/lang/String;)Landroidx/exifinterface/media/a;", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "r1", "(Landroidx/exifinterface/media/a;)I", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "u1", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;La9/c\$a;Z)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$j", "a", "(Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;)Landroid/net/Uri;", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$j", "b", "(Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;)Lue/h;", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$g", "a", "([Lue/h;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$g", "b", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$b", "a", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$b", "c", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "X", "(II)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "H", "(I)Lnextapp/fx/ui/viewer/image/ImageDisplay;", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setRemoteMirrorProvider", "(Lnextapp/maui/ui/imageview/TouchImageDisplay\$c;)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setImageBitmap", "(Landroid/graphics/Bitmap;)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setImageMovie", "(Landroid/graphics/Movie;)V", false),
            DexMethodSpec("a9.c", "g", "(Landroid/content/Context;Lk9/g;IIFI)La9/c\$a;", true),
            DexMethodSpec("a9.e", "a", "(Ljava/io/InputStream;)Z", true),

            // Remote thumbnails
            DexMethodSpec("dd.h", "b", "(Landroid/content/Context;Lue/m;ILdd/l;Ldd/c;Z)V", true),
            DexMethodSpec("dd.d", "<init>", "(Landroid/content/Context;Lue/h;ILdd/c;Ljava/lang/String;)V", false),
            DexMethodSpec("dd.d", "e", "()Ljava/io/File;", false),
            DexMethodSpec("dd.d\$a", "j", "()V", false),
            DexMethodSpec("dd.j", "e", "()Ljava/io/File;", false),
            DexMethodSpec("dd.b", "a", "()V", false),
            DexMethodSpec("dd.b", "b", "(Ldd/l;)V", false),
            DexMethodSpec("dd.b", "d", "(Landroid/content/Context;Lue/h;Ljava/lang/String;Ljava/lang/String;)Ljava/io/File;", true),
            DexMethodSpec("dd.c", "<init>", "()V", false),
            DexMethodSpec("ya.f", "i", "(Landroid/content/Context;Ljava/lang/String;Ljava/io/File;)Lya/f;", true),
            DexMethodSpec("ya.f", "k", "(Lv8/f;)Z", true),
            DexMethodSpec("ya.f", "c", "(Lk9/g;Ljava/io/File;Ljava/lang/String;)Lwa/i;", true),
            DexMethodSpec("ya.f", "d", "()Lya/f;", false),
            DexMethodSpec("ya.f", "g", "()Lwa/i;", false),
            DexMethodSpec("k9.g", "read", "()Ljava/io/InputStream;", false),
            DexMethodSpec("wa.i", "<init>", "(Ljava/lang/String;IILjava/lang/String;)V", false),
            DexMethodSpec("v8.i", "c", "(Ljava/lang/Object;)V", false),
            DexMethodSpec("u9.h", "d", "(Landroid/content/Context;)Lu9/h;", true),
            DexMethodSpec("u9.h", "v1", "()Z", false),
            DexMethodSpec("u9.h", "w1", "(Ljava/lang/String;)Z", false),
            DexMethodSpec("d9.a", "b", "()Z", true),
            DexMethodSpec("nextapp.fx.plus.dirimpl.dropbox.c", "w", "()Z", false),

            // Native settings UI
            DexMethodSpec("nextapp.fx.ui.fxsystem.MainPrefActivity", "onCreate", "(Landroid/os/Bundle;)V", false),
            DexMethodSpec("nextapp.fx.ui.fxsystem.MainPrefActivity", "onResume", "()V", false),
            DexMethodSpec("nextapp.fx.ui.fxsystem.pref.a", "b", "(Landroid/content/Context;Landroid/preference/PreferenceGroup;IIILandroid/preference/Preference\$OnPreferenceClickListener;)Lnextapp/maui/ui/preference/LabelPreference;", true),
            DexMethodSpec("nextapp.maui.ui.preference.LabelPreference", "<init>", "(Landroid/content/Context;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.k", "<init>", "(Landroid/content/Context;Lnextapp/fx/ui/widget/k\$f;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.k", "setHeader", "(Ljava/lang/CharSequence;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.k", "setDescription", "(Ljava/lang/CharSequence;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.k", "getDefaultContentLayout", "()Landroid/widget/LinearLayout;", false),
            DexMethodSpec("nextapp.fx.ui.widget.k", "addLabeledView", "(Ljava/lang/CharSequence;Landroid/view/View;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.k", "isBackgroundLight", "()Z", false),
            DexMethodSpec("nextapp.fx.ui.widget.k", "dismiss", "()V", false),
            DexMethodSpec("nextapp.fx.ui.widget.m0", "show", "()V", false),
            DexMethodSpec("xc.f", "e", "(Landroid/content/Context;)Lxc/f;", true),

            // Bonus text limits
            DexMethodSpec("nextapp.fx.ui.viewer.TextViewerActivity", "w0", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.TextViewerActivity", "u0", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.TextViewerActivity", "I0", "(Ljava/io/InputStream;)Ljava/io/Reader;", true),
            DexMethodSpec("nextapp.fx.ui.activitysupport.j", "J", "(I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.ExecActivity", "d2", "(Lue/h;)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.ExecActivity", "c2", "()V", false),
            DexMethodSpec("nextapp.fx.ui.textedit.k", "c", "(Ljava/io/InputStream;Ljava/lang/String;)Lnextapp/fx/ui/textedit/k\$d;", true),
            DexMethodSpec("nextapp.fx.ui.textedit.k\$d", "<init>", "(Ljava/lang/CharSequence;Ljava/lang/String;)V", false),
            DexMethodSpec("pc.a", "<init>", "(Ljava/io/InputStream;)V", false),
            DexMethodSpec("pc.a", "d", "()Ljava/lang/String;", false),
            DexMethodSpec("pc.a", "g", "()[B", false),
            DexMethodSpec("h9.i", "c", "([B)Ljava/lang/String;", true),

            // Android/data & FileCatalog
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "<init>", "(Landroid/content/Context;Lh9/t;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "<init>", "(Landroid/os/Parcel;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "P", "(Lse/f;)Lue/g;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "A", "(Lse/f;)Ljava/io/File;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "X", "(Landroid/content/Context;)Ljava/lang/String;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "<init>", "(Lnextapp/fx/dirimpl/file/FileCatalog;Lse/f;Ljava/io/File;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "s1", "(Landroid/content/Context;I)[Lue/m;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "d1", "(Landroid/content/Context;Ljava/lang/CharSequence;Z)Lue/g;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "H0", "(Landroid/content/Context;Ljava/lang/CharSequence;)Z", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "S", "(Landroid/content/Context;Ljava/lang/CharSequence;)Lue/h;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "m0", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "b", "(Landroid/content/Context;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.a", "B0", "()V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "<init>", "(Lnextapp/fx/dirimpl/file/FileCatalog;Lse/f;Ljava/io/File;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "b", "(Landroid/content/Context;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "getSize", "()J", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "j", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "F0", "(Landroid/content/Context;Z)Ljava/io/OutputStream;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "p1", "(Landroid/content/Context;J)Ljava/io/OutputStream;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "a1", "(Landroid/content/Context;JJ)Ljava/io/OutputStream;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "m0", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.b", "D", "()Ljava/lang/String;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "T", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "y0", "(Landroid/content/Context;Ljava/lang/String;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "u", "(Landroid/content/Context;Lse/f;)Z", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "getLastModified", "()J", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "isReadOnly", "()Z", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "h", "()Ljava/lang/String;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "v0", "()Ljava/io/File;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.c", "J", "(Landroid/content/Context;J)V", false),
            DexMethodSpec("se.f", "<init>", "(Lse/f;Ljava/lang/String;)V", false),
            DexMethodSpec("se.f", "c", "(Lse/f;Ljava/lang/String;)Lse/f;", true),
            DexMethodSpec("se.f", "A", "(Ljava/lang/Class;)Ljava/lang/Object;", false),
            DexMethodSpec("nextapp.fx.dir.optionstore.HiddenFileStore", "c", "(Ljava/lang/String;)Z", true),
            DexMethodSpec("se.l", "P", "(Ljava/lang/Throwable;Ljava/lang/String;)Lse/l;", true),
            DexMethodSpec("se.l", "c0", "(Ljava/lang/Throwable;Ljava/lang/String;)Lse/l;", true),
            DexMethodSpec("se.l", "q0", "(Ljava/lang/Throwable;Ljava/lang/String;)Lse/l;", true),
            DexMethodSpec("se.l", "l", "(Ljava/lang/Throwable;Ljava/lang/String;)Lse/l;", true),

            // Remote FileItems
            DexMethodSpec("nextapp.fx.plus.dirimpl.smb.d", "j", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("nextapp.fx.plus.dirimpl.smb.legacy.d", "j", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("nextapp.fx.plus.dirimpl.webdav.c", "j", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("nextapp.fx.plus.dirimpl.ftp.e", "j", "(Landroid/content/Context;)Ljava/io/InputStream;", false),

            // Hardware render & GIF v1.1 + Folder lifecycle
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "g", "(Landroid/graphics/Bitmap;)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "w", "(IIZ)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setImageNull", "()V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setPrescale", "(F)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "zoomFit", "()V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "onDraw", "(Landroid/graphics/Canvas;)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "onDetachedFromWindow", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "U", "(Landroid/graphics/Bitmap;Z)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "V", "(Landroid/graphics/Movie;)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "W", "()V", false),
            DexMethodSpec("nextapp.fx.ui.ExplorerActivity", "onResume", "()V", false),
            DexMethodSpec("nextapp.fx.ui.ExplorerActivity", "onDestroy", "()V", false),
            DexMethodSpec("nextapp.fx.ui.content.h0", "s", "(Lnextapp/fx/ui/content/e2;)V", false),
            DexMethodSpec("nextapp.fx.ui.content.h0", "d", "()Ljava/util/Collection;", false),
            DexMethodSpec("nextapp.fx.ui.content.e2", "s", "(Lse/f;)V", false),
            DexMethodSpec("nextapp.fx.ui.content.e2", "c", "()Lnextapp/fx/ui/content/e0;", false),
            DexMethodSpec("nextapp.fx.ui.content.e2", "getPath", "()Lse/f;", false),
            DexMethodSpec("nextapp.fx.ui.content.e0", "getPath", "()Lse/f;", false),
            DexMethodSpec("se.f", "C", "()Lse/f;", false),
            DexMethodSpec("se.f", "g", "()[Ljava/lang/Object;", false),

            // Live FX theme palette, Home overflow menu, and Google Drive OAuth (v1.3)
            DexMethodSpec("xc.f", "e", "(Landroid/content/Context;)Lxc/f;", true),
            DexMethodSpec("xc.f", "P", "()I", false),
            DexMethodSpec("xc.f", "N", "()I", false),
            DexMethodSpec("xc.f", "O", "()I", false),
            DexMethodSpec("i9.e", "<init>", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Runnable;)V", false),
            DexMethodSpec("nextapp.fx.ui.homecontent.HomeContentView\$d", "p", "(Lle/t;Lle/t;Lnextapp/fx/ui/content/k0;)V", false),
            DexMethodSpec("le.t", "f", "(Lle/z;)V", false),
            DexMethodSpec("le.t", "size", "()I", false),
            DexMethodSpec("le.t", "item", "(I)Lle/z;", false),
            DexMethodSpec("le.b0", "getTitle", "()Ljava/lang/CharSequence;", false),
            DexMethodSpec("le.f0", "<init>", "()V", false),
            DexMethodSpec("le.r", "<init>", "(Ljava/lang/CharSequence;Landroid/graphics/drawable/Drawable;Lle/b\$a;)V", false),
            DexMethodSpec("nextapp.fx.ui.res.ActionIcons", "d", "(Landroid/content/res/Resources;Ljava/lang/String;Z)Landroid/graphics/drawable/Drawable;", true),
            DexMethodSpec("nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity", "onCreate", "(Landroid/os/Bundle;)V", false),
            DexMethodSpec("nextapp.fx.plus.dirimpl.googledrive.b\$a", "c", "(Landroid/content/Context;)Ljava/lang/String;", true),
            DexMethodSpec("ic.e\$a", "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false)
        )

        val fields = listOf(
            DexFieldSpec("nextapp.fx.ui.viewer.image.k0", "a", "Ljava/util/Collection;", true),
            DexFieldSpec("nextapp.xf.dir.DirectoryCatalog\$b", "a5", "Lnextapp/xf/dir/DirectoryCatalog\$b;", true),
            DexFieldSpec("ue.n\$j", "a", "Z", false),
            DexFieldSpec("ue.n\$j", "b", "Lue/n\$g;", false),
            DexFieldSpec("ee.a", "q5", "Ljava/io/File;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "u5", "Lnextapp/fx/ui/viewer/image/ImageDisplay;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "v5", "Lnextapp/fx/ui/viewer/image/ImageViewerActivity\$j;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "A5", "Lue/h;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "C5", "[Lue/h;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "N5", "Landroidx/exifinterface/media/a;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "Q5", "Lue/n\$g;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "M5", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$j", "a", "Lue/h;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$j", "b", "Landroid/net/Uri;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$g", "a", "Lnextapp/fx/ui/viewer/image/ImageDisplay;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$g", "b", "Lnextapp/fx/ui/viewer/image/ImageViewerActivity;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity\$b", "a", "Lnextapp/fx/ui/viewer/image/ImageViewerActivity;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "c6", "I", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "d6", "I", false),
            DexFieldSpec("dd.d", "f", "Ljava/lang/String;", false),
            DexFieldSpec("dd.b", "c", "Lue/h;", false),
            DexFieldSpec("dd.b", "d", "Landroid/content/Context;", false),
            DexFieldSpec("dd.d\$a", "f5", "Lv8/i;", false),
            DexFieldSpec("ya.f", "g", "I", true),
            DexFieldSpec("u9.h", "b", "Landroid/content/SharedPreferences;", false),
            DexFieldSpec("nextapp.fx.ui.widget.k", "ui", "Lxc/f;", false),
            DexFieldSpec("nextapp.fx.ui.widget.k\$f", "Y", "Lnextapp/fx/ui/widget/k\$f;", true),
            DexFieldSpec("nextapp.fx.ui.widget.k\$f", "Z", "Lnextapp/fx/ui/widget/k\$f;", true),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "w5", "Ljava/util/List;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "x5", "Ljava/lang/String;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "y5", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "z5", "Z", false),
            DexFieldSpec("nextapp.fx.ui.activitysupport.j", "g5", "Landroid/os/Handler;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.ExecActivity", "v5", "Ljava/util/List;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.ExecActivity", "I5", "Ljava/lang/String;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.ExecActivity", "K5", "Z", false),
            DexFieldSpec("nextapp.fx.ui.textedit.k\$d", "a", "Ljava/lang/String;", false),
            DexFieldSpec("nextapp.fx.ui.textedit.k\$d", "b", "Ljava/lang/CharSequence;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.FileCatalog", "f", "Ljava/io/File;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.FileCatalog", "Y", "Lh9/t;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.FileCatalog", "a5", "Landroid/net/Uri;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.a", "a5", "[Ljava/io/File;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.b", "a5", "J", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.b", "b5", "Z", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.c", "f", "Lnextapp/fx/dirimpl/file/FileCatalog;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.c", "i", "Lse/f;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.c", "X", "Ljava/io/File;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.c", "Y", "J", false),

            // Hardware render & GIF v1.1 + Folder lifecycle
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "Q5", "Landroid/graphics/Bitmap;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "R5", "Landroid/graphics/Movie;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "S5", "Z", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "f", "Ljava/lang/Thread;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "V5", "Landroid/graphics/Matrix;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "H5", "Landroid/graphics/Paint;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "f5", "F", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "c5", "Landroid/graphics/Rect;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "h5", "Z", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "j5", "Lv8/f;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "k5", "Lv8/f;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "l5", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "a6", "Landroid/graphics/Bitmap;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "b6", "Landroid/graphics/Movie;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "f6", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "g6", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "h6", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "x5", "I", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "P5", "Z", false),
            DexFieldSpec("nextapp.fx.ui.content.r", "r5", "Lnextapp/fx/ui/content/h0;", false),
            DexFieldSpec("nextapp.fx.ui.content.h0", "f", "Ljava/util/List;", false),
            DexFieldSpec("nextapp.fx.ui.content.e0", "Y", "Lse/f;", false),
            DexFieldSpec("se.f", "f", "[Ljava/lang/Object;", false),

            // Live FX theme palette, Home overflow menu, and Google Drive OAuth (v1.3)
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "R5", "Landroid/widget/ProgressBar;", false),
            DexFieldSpec("nextapp.fx.ui.widget.k", "ui", "Lxc/f;", false),
            DexFieldSpec("xc.f", "j", "Z", false),
            DexFieldSpec("xc.f", "l", "I", false),
            DexFieldSpec("xc.f", "k", "I", false),
            DexFieldSpec("xc.f", "x", "I", false),
            DexFieldSpec("xc.f", "y", "I", false),
            DexFieldSpec("nextapp.fx.ui.homecontent.HomeContentView", "activity", "Lnextapp/fx/ui/content/r;", false),
            DexFieldSpec("nextapp.fx.ui.homecontent.HomeContentView", "res", "Landroid/content/res/Resources;", false),
            DexFieldSpec("nextapp.fx.ui.homecontent.HomeContentView\$d", "d", "Lnextapp/fx/ui/homecontent/HomeContentView;", false),
            DexFieldSpec("nextapp.fx.ui.content.j0", "b", "Z", false),
            DexFieldSpec("ic.e\$a", "a", "Ljava/lang/String;", false),
            DexFieldSpec("ic.e\$a", "b", "Ljava/lang/String;", false)
        )

        return VersionContract(ConfigContract.FX_VERSION_CODE_9012, classes, methods, fields)
    }

    private fun build9108Contract(): VersionContract {
        val classes = linkedSetOf(
            "nextapp.fx.ui.viewer.image.b0",
            "nextapp.fx.ui.viewer.image.g",
            "nextapp.fx.ui.viewer.image.c0",
            "nextapp.xf.dir.DirectoryCatalog",
            "a0.f",
            "kh.j",
            "kh.e",
            "kh.d",
            "kh.q",
            "kh.o",
            "kh.p",
            "nextapp.fx.dir.optionstore.DirectoryOptionStore",
            "qg.b",
            "nextapp.fx.ui.viewer.image.ImageViewerActivity",
            "nextapp.fx.ui.viewer.image.h",
            "nextapp.fx.ui.viewer.image.o",
            "nextapp.fx.ui.viewer.image.c",
            "nextapp.fx.ui.viewer.image.l",
            "nextapp.fx.ui.viewer.image.ImageDisplay",
            "nextapp.maui.ui.imageview.TouchImageDisplay",
            "ua.b",
            "wg.e",
            "kf.e",
            "be.k0",
            "kf.i",
            "kf.b",
            "kf.d",
            "kf.k",
            "pc.c",
            "ch.a",
            "mb.l",
            "xa.a",
            "ad.c",
            "kh.f0",
            "kh.h0",
            "kh.c0",
            "wb.e",
            "nextapp.fx.ui.fxsystem.MainPrefActivity",
            "af.e",
            "tf.c",
            "nextapp.maui.ui.preference.LabelPreference",
            "nextapp.fx.ui.widget.n",
            "nextapp.fx.ui.widget.g0",
            "nextapp.fx.ui.widget.m",
            "ef.g",
            "pg.f0",
            "nextapp.fx.ui.viewer.TextViewerActivity",
            "af.h",
            "nextapp.fx.plus.ui.audio.w",
            "pg.k",
            "nextapp.fx.ui.viewer.ExecActivity",
            "mg.n",
            "k2.y",
            "ue.a",
            "ab.i",
            "hh.l",
            "bb.b",
            "nextapp.fx.dirimpl.file.FileCatalog",
            "ec.a",
            "ec.d",
            "ec.e",
            "hh.f",
            "nextapp.fx.dir.optionstore.HiddenFileStore",
            "fd.j",
            "gd.d",
            "hd.e",
            "bd.f",
            "jd.d",
            "cd.g",
            "dd.e",
            "nextapp.fx.ui.ExplorerActivity",
            "nextapp.fx.ui.content.k",
            "nextapp.fx.ui.content.w",
            "nextapp.fx.ui.content.c1",
            "nextapp.fx.ui.content.p",
            "pa.c",
            "nextapp.fx.ui.homecontent.HomeContentView",
            "be.p",
            "nextapp.fx.ui.content.b0",
            "yg.r",
            "yg.y",
            "yg.p",
            "yg.b",
            "yg.u",
            "yg.w",
            "nextapp.fx.ui.res.ActionIcons",
            "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity",
            "gg.d",
            "jh.b",
            "d4.b",
            "bb.d"
        ).toList()

        val methods = listOf(
            // Sibling swipe & DirectoryCatalog
            DexMethodSpec("nextapp.fx.ui.viewer.image.b0", "run", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.g", "run", "()V", false),
            DexMethodSpec("nextapp.xf.dir.DirectoryCatalog", "getType", "()I", false),
            DexMethodSpec("nextapp.xf.dir.DirectoryCatalog", "g0", "(Lhh/f;)Lkh/d;", false),
            DexMethodSpec("a0.f", "j", "(I)Z", true),
            DexMethodSpec("kh.j", "e", "()Lnextapp/xf/dir/DirectoryCatalog;", false),
            DexMethodSpec("kh.j", "getLastModified", "()J", false),
            DexMethodSpec("kh.j", "getName", "()Ljava/lang/String;", false),
            DexMethodSpec("kh.j", "getParent", "()Lkh/d;", false),
            DexMethodSpec("kh.j", "getPath", "()Lhh/f;", false),
            DexMethodSpec("kh.j", "isReadOnly", "()Z", false),
            DexMethodSpec("kh.j", "e0", "(Landroid/content/Context;Ljava/lang/String;)V", false),
            DexMethodSpec("kh.j", "a0", "(Landroid/content/Context;Lhh/f;)Z", false),
            DexMethodSpec("kh.j", "Y", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("kh.e", "w", "()Ljava/lang/String;", false),
            DexMethodSpec("kh.e", "getSize", "()J", false),
            DexMethodSpec("kh.e", "f", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("kh.e", "C0", "(Landroid/content/Context;J)Ljava/io/OutputStream;", false),
            DexMethodSpec("kh.d", "F0", "(Landroid/content/Context;I)[Lkh/j;", false),
            DexMethodSpec("kh.d", "w0", "(Landroid/content/Context;Ljava/lang/CharSequence;Z)Lkh/d;", false),
            DexMethodSpec("kh.d", "F", "(Landroid/content/Context;Ljava/lang/CharSequence;)Lkh/e;", false),
            DexMethodSpec("kh.d", "j0", "(Landroid/content/Context;Ljava/lang/CharSequence;)Z", false),
            DexMethodSpec("kh.d", "h0", "()V", false),
            DexMethodSpec("kh.q", "a", "([Lkh/j;Lkh/o;ZZ)V", true),
            DexMethodSpec("kh.o", "b", "(I)Lkh/o;", true),
            DexMethodSpec("nextapp.fx.dir.optionstore.DirectoryOptionStore", "c", "(Lkh/j;)Lkh/p;", true),

            // ImageViewer & prefetch
            DexMethodSpec("qg.b", "u", "(Lkh/e;)V", false),
            DexMethodSpec("qg.b", "v", "()Ljava/io/File;", false),
            DexMethodSpec("qg.b", "w", "()Ljava/io/InputStream;", false),
            DexMethodSpec("qg.b", "x", "()Lkh/e;", false),
            DexMethodSpec("qg.b", "z", "()Landroid/net/Uri;", false),
            DexMethodSpec("qg.b", "onDestroy", "()V", false),
            DexMethodSpec("qg.b", "finalize", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "onCreate", "(Landroid/os/Bundle;)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "E", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "H", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/o;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "C", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/o;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "I", "(Lnextapp/fx/ui/viewer/image/ImageViewerActivity;Lkh/e;I)Lua/a;", true),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "F", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;Lnextapp/fx/ui/viewer/image/o;Lua/a;Z)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.h", "run", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.o", "<init>", "(Lkh/e;)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.c", "read", "()Ljava/io/InputStream;", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.l", "e", "(Lnextapp/fx/ui/viewer/image/ImageDisplay;I)V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.l", "b", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "k", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "i", "(I)Lnextapp/fx/ui/viewer/image/ImageDisplay;", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setRemoteMirrorProvider", "(Lch/f;)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setImageBitmap", "(Landroid/graphics/Bitmap;)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setImageMovie", "(Landroid/graphics/Movie;)V", false),
            DexMethodSpec("ua.b", "h", "(Ldb/d;IFI)Lua/a;", true),
            DexMethodSpec("ua.b", "f", "(Ljava/io/InputStream;)Z", true),

            // Remote thumbnails
            DexMethodSpec("wg.e", "j", "(Landroid/content/Context;Lkh/j;ILkf/k;Lkf/d;Z)V", true),
            DexMethodSpec("kf.e", "<init>", "(Landroid/content/Context;Lkh/e;ILkf/d;Ljava/lang/String;)V", false),
            DexMethodSpec("kf.e", "d", "()Ljava/io/File;", false),
            DexMethodSpec("be.k0", "f", "()V", false),
            DexMethodSpec("kf.i", "d", "()Ljava/io/File;", false),
            DexMethodSpec("kf.b", "a", "()V", false),
            DexMethodSpec("kf.b", "b", "(Lkf/k;)V", false),
            DexMethodSpec("kf.b", "c", "(Landroid/content/Context;Lkh/e;Ljava/lang/String;Ljava/lang/String;)Ljava/io/File;", true),
            DexMethodSpec("pc.c", "c", "(Landroid/content/Context;Ljava/lang/String;Ljava/io/File;)Lpc/c;", true),
            DexMethodSpec("pc.c", "a", "()Lnc/j;", false),
            DexMethodSpec("db.d", "read", "()Ljava/io/InputStream;", false),
            DexMethodSpec("nc.j", "<init>", "(IILjava/lang/String;Ljava/lang/String;)V", false),
            DexMethodSpec("mb.l", "d", "(Landroid/content/Context;)Lmb/l;", true),
            DexMethodSpec("mb.l", "u", "()Z", false),
            DexMethodSpec("mb.l", "v", "(Ljava/lang/String;)Z", false),
            DexMethodSpec("ad.c", "q", "()Z", false),

            // Native settings UI
            DexMethodSpec("nextapp.fx.ui.fxsystem.MainPrefActivity", "onCreate", "(Landroid/os/Bundle;)V", false),
            DexMethodSpec("nextapp.fx.ui.fxsystem.MainPrefActivity", "onResume", "()V", false),
            DexMethodSpec("tf.c", "a", "(Landroid/content/Context;Landroid/preference/PreferenceGroup;IIILandroid/preference/Preference\$OnPreferenceClickListener;)Lnextapp/maui/ui/preference/LabelPreference;", true),
            DexMethodSpec("nextapp.maui.ui.preference.LabelPreference", "<init>", "(Landroid/content/Context;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.n", "<init>", "(Landroid/content/Context;Lnextapp/fx/ui/widget/m;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.n", "setHeader", "(Ljava/lang/CharSequence;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.n", "setDescription", "(Ljava/lang/CharSequence;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.n", "getDefaultContentLayout", "()Landroid/widget/LinearLayout;", false),
            DexMethodSpec("nextapp.fx.ui.widget.n", "addLabeledView", "(Ljava/lang/CharSequence;Landroid/view/View;)V", false),
            DexMethodSpec("nextapp.fx.ui.widget.n", "isBackgroundLight", "()Z", false),
            DexMethodSpec("nextapp.fx.ui.widget.n", "dismiss", "()V", false),
            DexMethodSpec("nextapp.fx.ui.widget.g0", "show", "()V", false),
            DexMethodSpec("ef.g", "b", "(Landroid/content/Context;)Lef/g;", true),

            // Bonus text limits
            DexMethodSpec("pg.f0", "<init>", "(Lnextapp/fx/ui/viewer/TextViewerActivity;I)V", false),
            DexMethodSpec("pg.f0", "run", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.TextViewerActivity", "B", "(Lue/a;)Ljava/io/StringReader;", true),
            DexMethodSpec("af.h", "p", "(I)V", false),
            DexMethodSpec("nextapp.fx.plus.ui.audio.w", "run", "()V", false),
            DexMethodSpec("pg.k", "<init>", "(Lnextapp/fx/ui/viewer/ExecActivity;I)V", false),
            DexMethodSpec("mg.n", "l", "(Ljava/io/InputStream;Ljava/lang/String;)Lk2/y;", true),
            DexMethodSpec("k2.y", "<init>", "(Ljava/lang/Object;ILjava/lang/Object;)V", false),
            DexMethodSpec("ue.a", "<init>", "(Ljava/io/InputStream;)V", false),
            DexMethodSpec("ue.a", "c", "()V", false),
            DexMethodSpec("ab.i", "c", "([B)Ljava/lang/String;", true),

            // Android/data & FileCatalog
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "<init>", "(Landroid/content/Context;Lab/t;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "<init>", "(Landroid/os/Parcel;)V", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "g0", "(Lhh/f;)Lkh/d;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "K", "(Lhh/f;)Ljava/io/File;", false),
            DexMethodSpec("nextapp.fx.dirimpl.file.FileCatalog", "L", "(Landroid/content/Context;)Ljava/lang/String;", false),
            DexMethodSpec("ec.a", "F0", "(Landroid/content/Context;I)[Lkh/j;", false),
            DexMethodSpec("ec.a", "w0", "(Landroid/content/Context;Ljava/lang/CharSequence;Z)Lkh/d;", false),
            DexMethodSpec("ec.a", "j0", "(Landroid/content/Context;Ljava/lang/CharSequence;)Z", false),
            DexMethodSpec("ec.a", "F", "(Landroid/content/Context;Ljava/lang/CharSequence;)Lkh/e;", false),
            DexMethodSpec("ec.a", "Y", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("ec.a", "a", "(Landroid/content/Context;)V", false),
            DexMethodSpec("ec.a", "h0", "()V", false),
            DexMethodSpec("ec.d", "<init>", "(Lnextapp/fx/dirimpl/file/FileCatalog;Lhh/f;Ljava/io/File;)V", false),
            DexMethodSpec("ec.d", "a", "(Landroid/content/Context;)V", false),
            DexMethodSpec("ec.d", "getSize", "()J", false),
            DexMethodSpec("ec.d", "f", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("ec.d", "W", "(Landroid/content/Context;Z)Ljava/io/OutputStream;", false),
            DexMethodSpec("ec.d", "C0", "(Landroid/content/Context;J)Ljava/io/OutputStream;", false),
            DexMethodSpec("ec.d", "t0", "(Landroid/content/Context;JJ)Ljava/io/OutputStream;", false),
            DexMethodSpec("ec.d", "Y", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("ec.d", "w", "()Ljava/lang/String;", false),
            DexMethodSpec("ec.e", "H", "(Landroid/content/Context;Z)V", false),
            DexMethodSpec("ec.e", "e0", "(Landroid/content/Context;Ljava/lang/String;)V", false),
            DexMethodSpec("ec.e", "a0", "(Landroid/content/Context;Lhh/f;)Z", false),
            DexMethodSpec("ec.e", "getLastModified", "()J", false),
            DexMethodSpec("ec.e", "isReadOnly", "()Z", false),
            DexMethodSpec("ec.e", "d", "()Ljava/lang/String;", false),
            DexMethodSpec("hh.f", "<init>", "(Lhh/f;Ljava/lang/String;)V", false),
            DexMethodSpec("hh.f", "E", "(Ljava/lang/Class;)Ljava/lang/Object;", false),
            DexMethodSpec("nextapp.fx.dir.optionstore.HiddenFileStore", "a", "(Ljava/lang/String;)Z", true),
            DexMethodSpec("hh.l", "q", "(Ljava/lang/Exception;Ljava/lang/String;)Lhh/l;", true),
            DexMethodSpec("hh.l", "v", "(Ljava/lang/Exception;Ljava/lang/String;)Lhh/l;", true),
            DexMethodSpec("hh.l", "y", "(Ljava/lang/Exception;Ljava/lang/String;)Lhh/l;", true),
            DexMethodSpec("hh.l", "f", "(Ljava/lang/Exception;Ljava/lang/String;)Lhh/l;", true),

            // Remote FileItems
            DexMethodSpec("fd.j", "f", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("hd.e", "f", "(Landroid/content/Context;)Ljava/io/InputStream;", false),
            DexMethodSpec("bd.f", "f", "(Landroid/content/Context;)Ljava/io/InputStream;", false),

            // Hardware render & GIF v1.1 + Folder lifecycle
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "h", "(IIZ)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setImageNull", "()V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "setPrescale", "(F)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "zoomFit", "()V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "onDraw", "(Landroid/graphics/Canvas;)V", false),
            DexMethodSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "onDetachedFromWindow", "()V", false),
            DexMethodSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "l", "()V", false),
            DexMethodSpec("nextapp.fx.ui.ExplorerActivity", "onResume", "()V", false),
            DexMethodSpec("nextapp.fx.ui.ExplorerActivity", "onDestroy", "()V", false),
            DexMethodSpec("nextapp.fx.ui.content.w", "l", "(Lnextapp/fx/ui/content/c1;)V", false),
            DexMethodSpec("nextapp.fx.ui.content.c1", "v", "(Lhh/f;)V", false),
            DexMethodSpec("nextapp.fx.ui.content.c1", "g", "()Lnextapp/fx/ui/content/p;", false),
            DexMethodSpec("hh.f", "G", "()Lhh/f;", false),

            // Live FX theme palette, Home overflow menu, and Google Drive OAuth (v1.3)
            DexMethodSpec("ef.g", "b", "(Landroid/content/Context;)Lef/g;", true),
            DexMethodSpec("ef.g", "w", "()I", false),
            DexMethodSpec("ef.g", "u", "()I", false),
            DexMethodSpec("ef.g", "v", "()I", false),
            DexMethodSpec("bb.d", "<init>", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Runnable;)V", false),
            DexMethodSpec("be.p", "m", "(Lyg/r;Lyg/r;I)V", false),
            DexMethodSpec("yg.r", "d", "(Lyg/u;)V", false),
            DexMethodSpec("yg.w", "getTitle", "()Ljava/lang/CharSequence;", false),
            DexMethodSpec("yg.y", "isVisible", "()Z", false),
            DexMethodSpec("yg.p", "<init>", "(Ljava/lang/CharSequence;Landroid/graphics/drawable/Drawable;Lyg/b;)V", false),
            DexMethodSpec("nextapp.fx.ui.res.ActionIcons", "b", "(Landroid/content/res/Resources;Ljava/lang/String;I)Lsa/h;", true),
            DexMethodSpec("nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity", "onCreate", "(Landroid/os/Bundle;)V", false),
            DexMethodSpec("gg.d", "<init>", "(Ld4/b;Landroid/net/Uri;)V", false),
            DexMethodSpec("jh.b", "<init>", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false)
        )

        val fields = listOf(
            DexFieldSpec("nextapp.fx.ui.viewer.image.b0", "f", "Lkh/d;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.b0", "i", "Landroid/content/Context;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.b0", "X", "Lkh/o;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.b0", "Y", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.b0", "Z", "Lkh/e;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.b0", "X1", "Lnextapp/fx/ui/viewer/image/o;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.c0", "a", "Ljava/util/Collection;", true),
            DexFieldSpec("kh.p", "a", "Z", false),
            DexFieldSpec("kh.p", "b", "Lkh/o;", false),
            DexFieldSpec("qg.b", "m2", "Ljava/io/File;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "q2", "Lnextapp/fx/ui/viewer/image/ImageDisplay;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "r2", "Lnextapp/fx/ui/viewer/image/o;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "w2", "Lkh/e;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "y2", "[Lkh/e;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "x2", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "J2", "Lm0/g;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "M2", "Lkh/o;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "I2", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.h", "f", "I", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.h", "i", "Lnextapp/fx/ui/viewer/image/ImageViewerActivity;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.h", "X", "Lnextapp/fx/ui/viewer/image/ImageDisplay;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.h", "Y", "I", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.h", "Z", "Ljava/lang/Object;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.o", "a", "Ljava/lang/Object;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.o", "b", "Ljava/lang/Object;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.c", "f", "Ljava/lang/Object;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.c", "i", "Ljava/lang/Object;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.l", "f", "Landroid/view/KeyEvent\$Callback;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "Y2", "I", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "Z2", "I", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "V2", "Lnextapp/fx/ui/viewer/image/ImageDisplay;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "g3", "Lnextapp/fx/ui/viewer/image/l;", false),
            DexFieldSpec("kf.e", "f", "Ljava/lang/String;", false),
            DexFieldSpec("kf.b", "c", "Lkh/e;", false),
            DexFieldSpec("kf.b", "d", "Landroid/content/Context;", false),
            DexFieldSpec("be.k0", "c2", "I", false),
            DexFieldSpec("be.k0", "d2", "Ljava/lang/Object;", false),
            DexFieldSpec("be.k0", "e2", "Ljava/lang/Object;", false),
            DexFieldSpec("pc.c", "g", "I", true),
            DexFieldSpec("mb.l", "b", "Landroid/content/SharedPreferences;", false),
            DexFieldSpec("xa.a", "b", "Z", true),
            DexFieldSpec("nextapp.fx.ui.widget.n", "ui", "Lef/g;", false),
            DexFieldSpec("nextapp.fx.ui.widget.m", "Y", "Lnextapp/fx/ui/widget/m;", true),
            DexFieldSpec("nextapp.fx.ui.widget.m", "Z", "Lnextapp/fx/ui/widget/m;", true),
            DexFieldSpec("pg.f0", "f", "I", false),
            DexFieldSpec("pg.f0", "i", "Lnextapp/fx/ui/viewer/TextViewerActivity;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "s2", "Ljava/util/ArrayList;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "t2", "Ljava/lang/String;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "u2", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.TextViewerActivity", "v2", "Z", false),
            DexFieldSpec("af.h", "c2", "Landroid/os/Handler;", false),
            DexFieldSpec("nextapp.fx.plus.ui.audio.w", "f", "I", false),
            DexFieldSpec("nextapp.fx.plus.ui.audio.w", "i", "Ljava/lang/Object;", false),
            DexFieldSpec("nextapp.fx.plus.ui.audio.w", "X", "Ljava/lang/Object;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.ExecActivity", "r2", "Ljava/util/ArrayList;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.ExecActivity", "D2", "Ljava/lang/String;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.ExecActivity", "F2", "Z", false),
            DexFieldSpec("k2.y", "f", "I", false),
            DexFieldSpec("k2.y", "i", "Ljava/lang/Object;", false),
            DexFieldSpec("k2.y", "X", "Ljava/lang/Object;", false),
            DexFieldSpec("ue.a", "Z", "Ljava/lang/String;", false),
            DexFieldSpec("ue.a", "f", "[B", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.FileCatalog", "f", "Ljava/io/File;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.FileCatalog", "Y", "Lab/t;", false),
            DexFieldSpec("nextapp.fx.dirimpl.file.FileCatalog", "X1", "Landroid/net/Uri;", false),
            DexFieldSpec("ec.a", "X1", "[Ljava/io/File;", false),
            DexFieldSpec("ec.d", "X1", "J", false),
            DexFieldSpec("ec.d", "Y1", "Z", false),
            DexFieldSpec("ec.e", "f", "Lnextapp/fx/dirimpl/file/FileCatalog;", false),
            DexFieldSpec("ec.e", "i", "Lhh/f;", false),
            DexFieldSpec("ec.e", "X", "Ljava/io/File;", false),
            DexFieldSpec("ec.e", "Y", "J", false),

            // Hardware render & GIF v1.1 + Folder lifecycle
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "N2", "Landroid/graphics/Bitmap;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "O2", "Landroid/graphics/Movie;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "P2", "Z", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "f", "Ljava/lang/Thread;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "S2", "Landroid/graphics/Matrix;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "E2", "Landroid/graphics/Paint;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "c2", "F", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "Z1", "Landroid/graphics/Rect;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "e2", "Z", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "g2", "Lpa/c;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "h2", "Lpa/c;", false),
            DexFieldSpec("nextapp.maui.ui.imageview.TouchImageDisplay", "i2", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "W2", "Landroid/graphics/Bitmap;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "X2", "Landroid/graphics/Movie;", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "b3", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "c3", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageDisplay", "d3", "Z", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "t2", "I", false),
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "L2", "Z", false),
            DexFieldSpec("nextapp.fx.ui.content.k", "n2", "Lnextapp/fx/ui/content/w;", false),
            DexFieldSpec("nextapp.fx.ui.content.w", "f", "Ljava/util/ArrayList;", false),
            DexFieldSpec("nextapp.fx.ui.content.p", "Y", "Lhh/f;", false),
            DexFieldSpec("hh.f", "f", "[Ljava/lang/Object;", false),

            // Live FX theme palette, Home overflow menu, and Google Drive OAuth (v1.3)
            DexFieldSpec("nextapp.fx.ui.viewer.image.ImageViewerActivity", "N2", "Landroid/widget/ProgressBar;", false),
            DexFieldSpec("nextapp.fx.ui.widget.n", "ui", "Lef/g;", false),
            DexFieldSpec("ef.g", "j", "Z", false),
            DexFieldSpec("ef.g", "l", "I", false),
            DexFieldSpec("ef.g", "k", "I", false),
            DexFieldSpec("ef.g", "v", "I", false),
            DexFieldSpec("ef.g", "w", "I", false),
            DexFieldSpec("nextapp.fx.ui.homecontent.HomeContentView", "activity", "Lnextapp/fx/ui/content/k;", false),
            DexFieldSpec("nextapp.fx.ui.homecontent.HomeContentView", "res", "Landroid/content/res/Resources;", false),
            DexFieldSpec("be.p", "c", "I", false),
            DexFieldSpec("be.p", "d", "Lnextapp/fx/ui/content/u;", false),
            DexFieldSpec("nextapp.fx.ui.content.b0", "b", "Z", false),
            DexFieldSpec("yg.r", "d", "Ljava/util/ArrayList;", false),
            DexFieldSpec("gg.d", "a", "Ljava/lang/String;", false),
            DexFieldSpec("jh.b", "a", "Ljava/lang/String;", false),
            DexFieldSpec("jh.b", "b", "Ljava/lang/String;", false)
        )

        return VersionContract(ConfigContract.FX_VERSION_CODE_9108, classes, methods, fields)
    }
}

data class SiblingBindings(
    val siblingLoaderClass: Class<*>?,
    val enumerateSiblings: Method?,
    val supportedMimesField: Field?,
    val directoryOptionStoreClass: Class<*>?,
    val getSortOptions: Method?,
    val sortPairOrderField: Field?,
    val sortPairDescField: Field?,
    val sortItems: Method?,
    val fileItemClass: Class<*>?,
    val fileItemBaseClass: Class<*>?,
    val dirItemClass: Class<*>?,
    val listChildren: Method?,
    val getMimeType: Method?,
    val getSize: Method?,
    val getPath: Method?,
    val getName: Method?,
    val getLastModified: Method?,
    val openInputStream: Method?,
    val getCatalog: Method?,
    val dirCatalogClass: Class<*>?,
    val getCatalogType: Method?,
    val isRemoteMethod: Method?,
    val imageViewerActivityClass: Class<*>?,
    val viewerSiblingItemsField: Field?,
    val viewerSortOrderField: Field?,
    val viewerSortDescField: Field?,
    val viewerCurrentDisplayField: Field?,
    val viewerCurrentHolderField: Field?,
    val viewerInitialItemField: Field?,
    val viewerSiblingsLoadedField: Field?,
    val imageDisplayClass: Class<*>?,
    val displaySiblingIndexField: Field?,
    val displaySiblingCountField: Field?,
    val displayListenerField: Field?,
    val displayCommitMethod: Method?,
    val siblingCallbackClass: Class<*>?,
    val siblingCallbackLoadedMethod: Method?,
    val siblingCallbackActivityField: Field?,
    val displayListenerClass: Class<*>?,
    val displayListenerRequestMethod: Method?,
    val displayListenerCommitMethod: Method?,
    val displayListenerHolderField: Field?,
    val runnerDirField: Field?,
    val runnerCtxField: Field?,
    val runnerSortOrderField: Field?,
    val runnerSortDescField: Field?,
    val runnerCurrentItemField: Field?,
    val runnerCallbackHolderField: Field?,
    val itemHolderFileItemField: Field?,
    val itemHolderUriField: Field?
)

data class ViewerPrefetchBindings(
    val viewerBaseClass: Class<*>?,
    val mirrorRemoteTempFile: Method?,
    val viewerOnCreate: Method?,
    val viewerOnDestroy: Method?,
    val viewerLoadMediaAndBitmap: Method?,
    val viewerLoadMedia: Method?,
    val viewerLoadBitmap: Method?,
    val viewerLoadGif: Method?,
    val viewerOpenItemStream: Method?,
    val viewerDecodeItem: Method?,
    val viewerStreamProviderRead: Method?,
    val viewerStreamProviderItemField: Field?,
    val remoteItemClasses: List<Class<*>>
)

data class RemoteThumbBindings(
    val loadThumbnail: Method?,
    val thumbCallbackClass: Class<*>?,
    val thumbTaskItemField: Field?,
    val thumbTaskContextField: Field?,
    val thumbTaskGetCacheFile: Method?,
    val remoteThumbTaskCtor: Constructor<*>?,
    val remoteThumbTaskMimeField: Field?,
    val remoteThumbWorkerRun: Method?,
    val remoteThumbWorkerCaseField: Field?,
    val remoteThumbWorkerOwnerField: Field?,
    val remoteThumbWorkerResultField: Field?,
    val thumbResultField: Field?,
    val thumbGeneratorCreateForFile: Method?,
    val thumbGeneratorFlagsField: Field?,
    val thumbGeneratorContextField: Field?,
    val thumbGeneratorStreamField: Field?,
    val thumbGeneratorOutFileField: Field?,
    val thumbGeneratorSourcePathField: Field?,
    val thumbGeneratorStreamRead: Method?,
    val thumbImageInfoCtor: Constructor<*>?,
    val thumbGeneratorRun: Method?,
    val thumbGeneratorExceedsCap: Method?,
    val thumbGeneratorGenerate: Method?,
    val thumbGeneratorClass: Class<*>?,
    val settingsGetInstance: Method?,
    val settingsThumbsEnabled: Method?,
    val settingsThumbsForPath: Method?,
    val remoteItemMarkerClass: Class<*>?,
    val cloudThumbMarkerClass: Class<*>?,
    val dropboxSupportsThumb: Method?
)

data class NativeSettingsBindings(
    val mainPrefOnCreate: Method?,
    val mainPrefOnResume: Method?,
    val labelPreferenceCtor: Constructor<*>?,
    val fxDialogCtor: Constructor<*>?,
    val fxDialogDefaultWithClose: Any?,
    val fxDialogSetHeader: Method?,
    val fxDialogSetDescription: Method?,
    val fxDialogGetDefaultContentLayout: Method?,
    val fxDialogAddLabeledView: Method?,
    val fxDialogIsBackgroundLight: Method?,
    val fxDialogDismiss: Method?,
    val fxDialogShow: Method?,
    val fxDialogUiField: Field?,
    val uiGetForContext: Method?,
    val uiIsWindowLightField: Field?,
    val uiTextPrimaryField: Field?,
    val uiTextSecondaryField: Field?,
    val uiBoxBgField: Field?,
    val uiBoxPressedBgField: Field?,
    val uiWindowBgMethod: Method?,
    val uiTrimAccentMethod: Method?,
    val uiTrimBaseMethod: Method?
)

data class HomeMenuBindings(
    val homeContentViewClass: Class<*>?,
    val homeViewActivityField: Field?,
    val homeViewResField: Field?,
    val menuContribClass: Class<*>?,
    val menuContribCaseField: Field?,
    val menuContribOwnerField: Field?,
    val menuContribBgLightField: Field?,
    val menuContribPopulateMethod: Method?,
    val menuModelAddItemMethod: Method?,
    val menuModelSizeMethod: Method?,
    val menuModelItemMethod: Method?,
    val menuModelItemsListField: Field?,
    val menuItemGetTitleMethod: Method?,
    val menuDividerClass: Class<*>?,
    val menuDividerCtor: Constructor<*>?,
    val menuActionCtor: Constructor<*>?,
    val menuClickListenerInterface: Class<*>?,
    val actionIconsCreateMethod: Method?
)

data class GoogleDriveBindings(
    val authActivityOnCreate: Method?,
    val getClientId9012: Method?,
    val tokenCredsCtor9012: Constructor<*>?,
    val authReqBuilderCtor9108: Constructor<*>?,
    val authReqClientIdField9108: Field?,
    val tokenCredsCtor9108: Constructor<*>?,
    val tokenCredsClientIdField9108: Field?,
    val tokenCredsClientSecretField9108: Field?
)

data class AndroidDataBindings(
    val fileCatalogClass: Class<*>?,
    val fileCatalogOpenDir: Method?,
    val fileCatalogResolveFile: Method?,
    val fileCatalogSafUriField: Field?,
    val fileDirNodeClass: Class<*>?,
    val fileDirListChildren: Method?,
    val fileDirMkdir: Method?,
    val fileDirChildAvailable: Method?,
    val fileDirDeleteRecursive: Method?,
    val fileDirStatVolume: Method?,
    val fileDirClearCache: Method?,
    val fileDirCachedFilesField: Field?,
    val fileFileNodeClass: Class<*>?,
    val fileFileCtor: Constructor<*>?,
    val fileFileStatSize: Method?,
    val fileFileCachedSizeField: Field?,
    val fileFileOpenInput: Method?,
    val fileFileOpenOutputInternal: Method?,
    val fileFileDelete: Method?,
    val fileNodeBaseClass: Class<*>?,
    val fileNodeDeleteSingle: Method?,
    val fileNodeRename: Method?,
    val fileNodeMoveTo: Method?,
    val fileNodeGetLastModified: Method?,
    val fileNodeIsReadOnly: Method?,
    val fileNodeSetLastModified: Method?,
    val fileNodeCatalogField: Field?,
    val fileNodePathField: Field?,
    val fileNodeJavaFileField: Field?,
    val fileNodeMetricsField: Field?,
    val fxPathChildCtor: Constructor<*>?,
    val fxPathFindElement: Method?,
    val hiddenFileStoreIsHidden: Method?,
    val fileDirCtor9012: Constructor<*>?,
    val userExceptionClass: Class<*>?,
    val cancelExceptionClass: Class<*>?
)

data class BonusLimitsBindings(
    val textViewerLoad: Method?,
    val textViewerApply: Method?,
    val textViewerOpenBinaryXml: Method?,
    val textViewerWorkerRun: Method?,
    val textViewerWorkerCaseField: Field?,
    val textViewerWorkerActivityField: Field?,
    val textViewerWorkerCtor: Constructor<*>?,
    val textViewerLinesField: Field?,
    val textViewerCharsetField: Field?,
    val textViewerTruncatedField: Field?,
    val textViewerUnsupportedField: Field?,
    val viewerSupportHandlerField: Field?,
    val viewerSupportShowError: Method?,
    val encodingStreamCtor: Constructor<*>?,
    val encodingStreamDetect: Method?,
    val encodingStreamGetCharset: Method?,
    val encodingStreamCharsetField: Field?,
    val encodingStreamGetBuffer: Method?,
    val encodingStreamBufferField: Field?,
    val mimeProbeBytes: Method?,
    val textEditReadStream: Method?,
    val textEditResultCtor: Constructor<*>?,
    val execLoadPreview: Method?,
    val execApplyPreview: Method?,
    val execWorkerRun: Method?,
    val execWorkerCaseField: Field?,
    val execWorkerArg1Field: Field?,
    val execWorkerArg2Field: Field?,
    val execUiRunnableCtor: Constructor<*>?,
    val execLinesField: Field?,
    val execCharsetField: Field?,
    val execUnsupportedField: Field?
)

data class FolderLifecycleBindings(
    val explorerActivityClass: Class<*>?,
    val explorerOnResume: Method?,
    val explorerOnDestroy: Method?,
    val windowManagerField: Field?,
    val windowModelsField: Field?,
    val windowModelCloseMethod: Method?,
    val windowModelSetPathMethod: Method?,
    val windowModelCurrentContentMethod: Method?,
    val contentModelPathField: Field?,
    val fxPathClass: Class<*>?,
    val fxPathElementsField: Field?,
    val fxPathParentMethod: Method?,
    val dirCatalogClass: Class<*>?
)

data class ResolvedSymbols(
    val profile: FxProfile,
    val versionCode: Long,
    val classLoader: ClassLoader,
    val sibling: SiblingBindings,
    val viewer: ViewerPrefetchBindings,
    val remoteThumb: RemoteThumbBindings,
    val settings: NativeSettingsBindings,
    val androidData: AndroidDataBindings,
    val bonusLimits: BonusLimitsBindings,
    val folderLifecycle: FolderLifecycleBindings,
    val homeMenu: HomeMenuBindings,
    val googleDrive: GoogleDriveBindings
)

/**
 * Resolves dual-APK verified reflection handles for FX `9012` (`9.0.1.2`) and
 * `9108` (`9.1.0.8`), with structural fallback when an unknown version is loaded.
 */
object FxSymbolResolver {

    fun detectVersionCode(context: Context?, appInfo: ApplicationInfo?): Long {
        if (context != null) {
            val fromPm = runCatching {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val info: PackageInfo = pm.getPackageInfo(ConfigContract.TARGET_FX_PACKAGE, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            }.getOrNull()
            if (fromPm != null && fromPm > 0L) return fromPm
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appInfo != null) {
            val fromAppInfo = runCatching {
                val method = ApplicationInfo::class.java.getMethod("getLongVersionCode")
                (method.invoke(appInfo) as? Number)?.toLong()
            }.getOrNull()
            if (fromAppInfo != null && fromAppInfo > 0L) return fromAppInfo
        }
        return 0L
    }

    fun detectProfile(classLoader: ClassLoader, versionCode: Long): FxProfile {
        return when (versionCode) {
            ConfigContract.FX_VERSION_CODE_9012 -> FxProfile.FX_9012
            ConfigContract.FX_VERSION_CODE_9108 -> FxProfile.FX_9108
            else -> {
                val has9108 = hasClass(classLoader, "nextapp.fx.ui.viewer.image.b0") &&
                    hasClass(classLoader, "ec.a")
                val has9012 = hasClass(classLoader, "nextapp.fx.ui.viewer.image.k0") &&
                    hasClass(classLoader, "nextapp.fx.dirimpl.file.a")
                when {
                    has9108 -> FxProfile.FX_9108
                    has9012 -> FxProfile.FX_9012
                    else -> FxProfile.STRUCTURAL_FALLBACK
                }
            }
        }
    }

    fun resolve(classLoader: ClassLoader, versionCode: Long): ResolvedSymbols {
        val profile = detectProfile(classLoader, versionCode)
        return when (profile) {
            FxProfile.FX_9012 -> resolve9012(classLoader, versionCode)
            FxProfile.FX_9108 -> resolve9108(classLoader, versionCode)
            FxProfile.STRUCTURAL_FALLBACK -> resolveStructuralFallback(classLoader, versionCode)
        }
    }

    private fun resolve9012(cl: ClassLoader, versionCode: Long): ResolvedSymbols {
        val k0Class = loadClass(cl, "nextapp.fx.ui.viewer.image.k0")
        val k0CallbackClass = loadClass(cl, "nextapp.fx.ui.viewer.image.k0\$a")
        val dirCatalogClass = loadClass(cl, "nextapp.xf.dir.DirectoryCatalog")
        val dirCatalogTypeClass = loadClass(cl, "nextapp.xf.dir.DirectoryCatalog\$b")
        val fileItemBaseClass = loadClass(cl, "ue.m")
        val fileItemClass = loadClass(cl, "ue.h")
        val dirItemClass = loadClass(cl, "ue.g")
        val sortUtilsClass = loadClass(cl, "ue.n")
        val sortOrderClass = loadClass(cl, "ue.n\$g")
        val sortPairClass = loadClass(cl, "ue.n\$j")
        val dirOptStoreClass = loadClass(cl, "nextapp.fx.dir.optionstore.DirectoryOptionStore")
        val viewerBaseClass = loadClass(cl, "ee.a")
        val viewerClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity")
        val itemHolderClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity\$j")
        val siblingCbImplClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity\$g")
        val displayListenerClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity\$b")
        val imageDisplayClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageDisplay")

        val sibling = SiblingBindings(
            siblingLoaderClass = k0Class,
            enumerateSiblings = declaredMethod(k0Class, "b", dirItemClass, Context::class.java, sortOrderClass, Boolean::class.javaPrimitiveType, fileItemClass, k0CallbackClass),
            supportedMimesField = declaredField(k0Class, "a"),
            directoryOptionStoreClass = dirOptStoreClass,
            getSortOptions = declaredMethod(dirOptStoreClass, "e", fileItemBaseClass),
            sortPairOrderField = declaredField(sortPairClass, "b"),
            sortPairDescField = declaredField(sortPairClass, "a"),
            sortItems = declaredMethod(sortUtilsClass, "o", java.lang.reflect.Array.newInstance(fileItemBaseClass ?: Any::class.java, 0)::class.java, sortOrderClass, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
            fileItemClass = fileItemClass,
            fileItemBaseClass = fileItemBaseClass,
            dirItemClass = dirItemClass,
            listChildren = declaredMethod(dirItemClass, "s1", Context::class.java, Int::class.javaPrimitiveType),
            getMimeType = declaredMethod(fileItemClass, "D"),
            getSize = declaredMethod(fileItemClass, "getSize"),
            getPath = declaredMethod(fileItemBaseClass, "getPath"),
            getName = declaredMethod(fileItemBaseClass, "getName"),
            getLastModified = declaredMethod(fileItemBaseClass, "getLastModified"),
            openInputStream = declaredMethod(fileItemClass, "j", Context::class.java),
            getCatalog = declaredMethod(fileItemBaseClass, "i"),
            dirCatalogClass = dirCatalogClass,
            getCatalogType = declaredMethod(dirCatalogClass, "getType"),
            isRemoteMethod = declaredMethod(dirCatalogTypeClass, "b"),
            imageViewerActivityClass = viewerClass,
            viewerSiblingItemsField = declaredField(viewerClass, "C5"),
            viewerSortOrderField = declaredField(viewerClass, "Q5"),
            viewerSortDescField = declaredField(viewerClass, "M5"),
            viewerCurrentDisplayField = declaredField(viewerClass, "u5"),
            viewerCurrentHolderField = declaredField(viewerClass, "v5"),
            viewerInitialItemField = declaredField(viewerClass, "A5"),
            viewerSiblingsLoadedField = declaredField(viewerClass, "B5"),
            imageDisplayClass = imageDisplayClass,
            displaySiblingIndexField = declaredField(imageDisplayClass, "c6"),
            displaySiblingCountField = declaredField(imageDisplayClass, "d6"),
            displayListenerField = null,
            displayCommitMethod = null,
            siblingCallbackClass = siblingCbImplClass,
            siblingCallbackLoadedMethod = declaredMethod(siblingCbImplClass, "a", java.lang.reflect.Array.newInstance(fileItemClass ?: Any::class.java, 0)::class.java, Int::class.javaPrimitiveType),
            siblingCallbackActivityField = declaredField(siblingCbImplClass, "b"),
            displayListenerClass = displayListenerClass,
            displayListenerRequestMethod = declaredMethod(displayListenerClass, "a", imageDisplayClass, Int::class.javaPrimitiveType),
            displayListenerCommitMethod = declaredMethod(displayListenerClass, "c", imageDisplayClass, Int::class.javaPrimitiveType),
            displayListenerHolderField = declaredField(displayListenerClass, "a"),
            runnerDirField = null,
            runnerCtxField = null,
            runnerSortOrderField = null,
            runnerSortDescField = null,
            runnerCurrentItemField = null,
            runnerCallbackHolderField = null,
            itemHolderFileItemField = declaredField(itemHolderClass, "a"),
            itemHolderUriField = declaredField(itemHolderClass, "b")
        )

        val remoteItemClasses = listOfNotNull(
            loadClass(cl, "nextapp.fx.plus.dirimpl.smb.d"),
            loadClass(cl, "nextapp.fx.plus.dirimpl.smb.legacy.d"),
            loadClass(cl, "nextapp.fx.plus.dirimpl.webdav.c"),
            loadClass(cl, "nextapp.fx.plus.dirimpl.ftp.e"),
            loadClass(cl, "nextapp.fx.plus.dirimpl.ssh.g"),
            loadClass(cl, "nextapp.fx.plus.dirimpl.googledrive.e"),
            loadClass(cl, "nextapp.fx.plus.dirimpl.onedrive.d"),
            loadClass(cl, "nextapp.fx.plus.dirimpl.dropbox.c")
        )

        val viewer = ViewerPrefetchBindings(
            viewerBaseClass = viewerBaseClass,
            mirrorRemoteTempFile = declaredMethod(viewerBaseClass, "T", fileItemClass, Long::class.javaPrimitiveType),
            viewerOnCreate = declaredMethod(viewerClass, "onCreate", android.os.Bundle::class.java),
            viewerOnDestroy = declaredMethod(viewerBaseClass, "onDestroy"),
            viewerLoadMediaAndBitmap = null,
            viewerLoadMedia = declaredMethod(viewerClass, "j1", imageDisplayClass, itemHolderClass, Int::class.javaPrimitiveType),
            viewerLoadBitmap = declaredMethod(viewerClass, "k1", imageDisplayClass, itemHolderClass, Int::class.javaPrimitiveType),
            viewerLoadGif = declaredMethod(viewerClass, "l1", imageDisplayClass, itemHolderClass, Int::class.javaPrimitiveType),
            viewerOpenItemStream = declaredMethod(viewerClass, "V1", fileItemClass, Context::class.java),
            viewerDecodeItem = declaredMethod(viewerClass, "d2", Context::class.java, fileItemClass, Int::class.javaPrimitiveType),
            viewerStreamProviderRead = null,
            viewerStreamProviderItemField = null,
            remoteItemClasses = remoteItemClasses
        )

        val thumbRouterClass = loadClass(cl, "dd.h")
        val thumbCallbackClass = loadClass(cl, "dd.l")
        val thumbCacheClass = loadClass(cl, "dd.c")
        val thumbBaseClass = loadClass(cl, "dd.b")
        val remoteThumbTaskClass = loadClass(cl, "dd.d")
        val remoteThumbWorkerClass = loadClass(cl, "dd.d\$a")
        val thumbGenClass = loadClass(cl, "ya.f")
        val thumbStreamSrcClass = loadClass(cl, "k9.g")
        val thumbInfoClass = loadClass(cl, "wa.i")
        val imgDimClass = loadClass(cl, "v8.f")
        val settingsClass = loadClass(cl, "u9.h")
        val dropboxClass = loadClass(cl, "nextapp.fx.plus.dirimpl.dropbox.c")

        val remoteThumb = RemoteThumbBindings(
            loadThumbnail = declaredMethod(thumbRouterClass, "b", Context::class.java, fileItemBaseClass, Int::class.javaPrimitiveType, thumbCallbackClass, thumbCacheClass, Boolean::class.javaPrimitiveType),
            thumbCallbackClass = thumbCallbackClass,
            thumbTaskItemField = declaredField(thumbBaseClass, "c"),
            thumbTaskContextField = declaredField(thumbBaseClass, "d"),
            thumbTaskGetCacheFile = declaredMethod(thumbBaseClass, "d", Context::class.java, fileItemClass, String::class.java, String::class.java),
            remoteThumbTaskCtor = declaredConstructor(remoteThumbTaskClass, Context::class.java, fileItemClass, Int::class.javaPrimitiveType, thumbCacheClass, String::class.java),
            remoteThumbTaskMimeField = declaredField(remoteThumbTaskClass, "f"),
            remoteThumbWorkerRun = declaredMethod(remoteThumbWorkerClass, "j"),
            remoteThumbWorkerCaseField = null,
            remoteThumbWorkerOwnerField = declaredField(remoteThumbWorkerClass, "this\$0")
                ?: remoteThumbWorkerClass?.declaredFields?.firstOrNull { it.type == remoteThumbTaskClass }?.apply { isAccessible = true },
            remoteThumbWorkerResultField = declaredField(remoteThumbWorkerClass, "f5"),
            thumbResultField = declaredField(loadClass(cl, "v8.i"), "a"),
            thumbGeneratorCreateForFile = declaredMethod(thumbGenClass, "i", Context::class.java, String::class.java, java.io.File::class.java),
            thumbGeneratorFlagsField = declaredField(thumbGenClass, "a"),
            thumbGeneratorContextField = declaredField(thumbGenClass, "b"),
            thumbGeneratorStreamField = declaredField(thumbGenClass, "c"),
            thumbGeneratorOutFileField = declaredField(thumbGenClass, "e"),
            thumbGeneratorSourcePathField = declaredField(thumbGenClass, "f"),
            thumbGeneratorStreamRead = declaredMethod(thumbStreamSrcClass, "read"),
            thumbImageInfoCtor = declaredConstructor(thumbInfoClass, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java),
            thumbGeneratorRun = declaredMethod(thumbGenClass, "g"),
            thumbGeneratorExceedsCap = declaredMethod(thumbGenClass, "k", imgDimClass),
            thumbGeneratorGenerate = declaredMethod(thumbGenClass, "c", thumbStreamSrcClass, java.io.File::class.java, String::class.java),
            thumbGeneratorClass = thumbGenClass,
            settingsGetInstance = declaredMethod(settingsClass, "d", Context::class.java),
            settingsThumbsEnabled = declaredMethod(settingsClass, "v1"),
            settingsThumbsForPath = declaredMethod(settingsClass, "w1", String::class.java),
            remoteItemMarkerClass = loadClass(cl, "ue.g0"),
            cloudThumbMarkerClass = loadClass(cl, "ue.j0"),
            dropboxSupportsThumb = declaredMethod(dropboxClass, "w")
        )

        val mainPrefClass = loadClass(cl, "nextapp.fx.ui.fxsystem.MainPrefActivity")
        val labelPrefClass = loadClass(cl, "nextapp.maui.ui.preference.LabelPreference")
        val fxDialogClass = loadClass(cl, "nextapp.fx.ui.widget.k")
        val fxDialogBaseClass = loadClass(cl, "nextapp.fx.ui.widget.m0")
        val fxDialogStyleClass = loadClass(cl, "nextapp.fx.ui.widget.k\$f")
        val defaultWithClose = runCatching { declaredField(fxDialogStyleClass, "Z")?.get(null) }.getOrNull()
        val uiThemeClass = loadClass(cl, "xc.f")

        val settings = NativeSettingsBindings(
            mainPrefOnCreate = declaredMethod(mainPrefClass, "onCreate", android.os.Bundle::class.java),
            mainPrefOnResume = declaredMethod(mainPrefClass, "onResume"),
            labelPreferenceCtor = declaredConstructor(labelPrefClass, Context::class.java),
            fxDialogCtor = declaredConstructor(fxDialogClass, Context::class.java, fxDialogStyleClass),
            fxDialogDefaultWithClose = defaultWithClose,
            fxDialogSetHeader = declaredMethod(fxDialogClass, "setHeader", CharSequence::class.java),
            fxDialogSetDescription = declaredMethod(fxDialogClass, "setDescription", CharSequence::class.java),
            fxDialogGetDefaultContentLayout = declaredMethod(fxDialogClass, "getDefaultContentLayout"),
            fxDialogAddLabeledView = declaredMethod(fxDialogClass, "addLabeledView", CharSequence::class.java, android.view.View::class.java),
            fxDialogIsBackgroundLight = declaredMethod(fxDialogClass, "isBackgroundLight"),
            fxDialogDismiss = declaredMethod(fxDialogClass, "dismiss"),
            fxDialogShow = declaredMethod(fxDialogBaseClass, "show"),
            fxDialogUiField = declaredField(fxDialogClass, "ui"),
            uiGetForContext = declaredMethod(uiThemeClass, "e", Context::class.java),
            uiIsWindowLightField = declaredField(uiThemeClass, "j"),
            uiTextPrimaryField = declaredField(uiThemeClass, "l"),
            uiTextSecondaryField = declaredField(uiThemeClass, "k"),
            uiBoxBgField = declaredField(uiThemeClass, "x"),
            uiBoxPressedBgField = declaredField(uiThemeClass, "y"),
            uiWindowBgMethod = declaredMethod(uiThemeClass, "P"),
            uiTrimAccentMethod = declaredMethod(uiThemeClass, "N"),
            uiTrimBaseMethod = declaredMethod(uiThemeClass, "O")
        )

        val fileCatalogClass = loadClass(cl, "nextapp.fx.dirimpl.file.FileCatalog")
        val fileDirClass = loadClass(cl, "nextapp.fx.dirimpl.file.a")
        val fileFileClass = loadClass(cl, "nextapp.fx.dirimpl.file.b")
        val fileBaseClass = loadClass(cl, "nextapp.fx.dirimpl.file.c")
        val fxPathClass = loadClass(cl, "se.f")
        val hiddenStoreClass = loadClass(cl, "nextapp.fx.dir.optionstore.HiddenFileStore")

        val androidData = AndroidDataBindings(
            fileCatalogClass = fileCatalogClass,
            fileCatalogOpenDir = declaredMethod(fileCatalogClass, "P", fxPathClass),
            fileCatalogResolveFile = declaredMethod(fileCatalogClass, "A", fxPathClass),
            fileCatalogSafUriField = declaredField(fileCatalogClass, "a5"),
            fileDirNodeClass = fileDirClass,
            fileDirListChildren = declaredMethod(fileDirClass, "s1", Context::class.java, Int::class.javaPrimitiveType),
            fileDirMkdir = declaredMethod(fileDirClass, "d1", Context::class.java, CharSequence::class.java, Boolean::class.javaPrimitiveType),
            fileDirChildAvailable = declaredMethod(fileDirClass, "H0", Context::class.java, CharSequence::class.java),
            fileDirDeleteRecursive = declaredMethod(fileDirClass, "m0", Context::class.java, Boolean::class.javaPrimitiveType),
            fileDirStatVolume = declaredMethod(fileDirClass, "b", Context::class.java),
            fileDirClearCache = declaredMethod(fileDirClass, "B0"),
            fileDirCachedFilesField = declaredField(fileDirClass, "a5"),
            fileFileNodeClass = fileFileClass,
            fileFileCtor = declaredConstructor(fileFileClass, fileCatalogClass, fxPathClass, java.io.File::class.java),
            fileFileStatSize = declaredMethod(fileFileClass, "b", Context::class.java),
            fileFileCachedSizeField = declaredField(fileFileClass, "a5"),
            fileFileOpenInput = declaredMethod(fileFileClass, "j", Context::class.java),
            fileFileOpenOutputInternal = declaredMethod(fileFileClass, "F0", Context::class.java, Boolean::class.javaPrimitiveType),
            fileFileDelete = declaredMethod(fileFileClass, "m0", Context::class.java, Boolean::class.javaPrimitiveType),
            fileNodeBaseClass = fileBaseClass,
            fileNodeDeleteSingle = declaredMethod(fileBaseClass, "T", Context::class.java, Boolean::class.javaPrimitiveType),
            fileNodeRename = declaredMethod(fileBaseClass, "y0", Context::class.java, String::class.java),
            fileNodeMoveTo = declaredMethod(fileBaseClass, "u", Context::class.java, fxPathClass),
            fileNodeGetLastModified = declaredMethod(fileBaseClass, "getLastModified"),
            fileNodeIsReadOnly = declaredMethod(fileBaseClass, "isReadOnly"),
            fileNodeSetLastModified = declaredMethod(fileBaseClass, "J", Context::class.java, Long::class.javaPrimitiveType),
            fileNodeCatalogField = declaredField(fileBaseClass, "f"),
            fileNodePathField = declaredField(fileBaseClass, "i"),
            fileNodeJavaFileField = declaredField(fileBaseClass, "X"),
            fileNodeMetricsField = declaredField(fileBaseClass, "Y"),
            fxPathChildCtor = declaredConstructor(fxPathClass, fxPathClass, String::class.java),
            fxPathFindElement = declaredMethod(fxPathClass, "A", Class::class.java),
            hiddenFileStoreIsHidden = declaredMethod(hiddenStoreClass, "c", String::class.java),
            fileDirCtor9012 = declaredConstructor(fileDirClass, fileCatalogClass, fxPathClass, java.io.File::class.java),
            userExceptionClass = loadClass(cl, "se.l"),
            cancelExceptionClass = loadClass(cl, "i9.d")
        )

        val textViewerClass = loadClass(cl, "nextapp.fx.ui.viewer.TextViewerActivity")
        val viewerSupportClass = loadClass(cl, "nextapp.fx.ui.activitysupport.j")
        val encodingStreamClass = loadClass(cl, "pc.a")
        val mimeProbeClass = loadClass(cl, "h9.i")
        val textEditLoaderClass = loadClass(cl, "nextapp.fx.ui.textedit.k")
        val textEditResultClass = loadClass(cl, "nextapp.fx.ui.textedit.k\$d")
        val execClass = loadClass(cl, "nextapp.fx.ui.viewer.ExecActivity")

        val bonusLimits = BonusLimitsBindings(
            textViewerLoad = declaredMethod(textViewerClass, "w0"),
            textViewerApply = declaredMethod(textViewerClass, "u0"),
            textViewerOpenBinaryXml = declaredMethod(textViewerClass, "I0", java.io.InputStream::class.java),
            textViewerWorkerRun = null,
            textViewerWorkerCaseField = null,
            textViewerWorkerActivityField = null,
            textViewerWorkerCtor = null,
            textViewerLinesField = declaredField(textViewerClass, "w5"),
            textViewerCharsetField = declaredField(textViewerClass, "x5"),
            textViewerTruncatedField = declaredField(textViewerClass, "y5"),
            textViewerUnsupportedField = declaredField(textViewerClass, "z5"),
            viewerSupportHandlerField = declaredField(viewerSupportClass, "g5"),
            viewerSupportShowError = declaredMethod(viewerSupportClass, "J", Int::class.javaPrimitiveType),
            encodingStreamCtor = declaredConstructor(encodingStreamClass, java.io.InputStream::class.java),
            encodingStreamDetect = null,
            encodingStreamGetCharset = declaredMethod(encodingStreamClass, "d"),
            encodingStreamCharsetField = null,
            encodingStreamGetBuffer = declaredMethod(encodingStreamClass, "g"),
            encodingStreamBufferField = null,
            mimeProbeBytes = declaredMethod(mimeProbeClass, "c", ByteArray::class.java),
            textEditReadStream = declaredMethod(textEditLoaderClass, "c", java.io.InputStream::class.java, String::class.java),
            textEditResultCtor = declaredConstructor(textEditResultClass, CharSequence::class.java, String::class.java),
            execLoadPreview = declaredMethod(execClass, "d2", fileItemClass),
            execApplyPreview = declaredMethod(execClass, "c2"),
            execWorkerRun = null,
            execWorkerCaseField = null,
            execWorkerArg1Field = null,
            execWorkerArg2Field = null,
            execUiRunnableCtor = null,
            execLinesField = declaredField(execClass, "v5"),
            execCharsetField = declaredField(execClass, "I5"),
            execUnsupportedField = declaredField(execClass, "K5")
        )

        val explorerActivityClass = loadClass(cl, "nextapp.fx.ui.ExplorerActivity")
        val contentHostClass = loadClass(cl, "nextapp.fx.ui.content.r")
        val windowManagerClass = loadClass(cl, "nextapp.fx.ui.content.h0")
        val windowModelClass = loadClass(cl, "nextapp.fx.ui.content.e2")
        val contentModelClass = loadClass(cl, "nextapp.fx.ui.content.e0")

        val folderLifecycle = FolderLifecycleBindings(
            explorerActivityClass = explorerActivityClass,
            explorerOnResume = declaredMethod(explorerActivityClass, "onResume"),
            explorerOnDestroy = declaredMethod(explorerActivityClass, "onDestroy"),
            windowManagerField = declaredField(contentHostClass, "r5"),
            windowModelsField = declaredField(windowManagerClass, "f"),
            windowModelCloseMethod = declaredMethod(windowManagerClass, "s", windowModelClass),
            windowModelSetPathMethod = declaredMethod(windowModelClass, "s", fxPathClass),
            windowModelCurrentContentMethod = declaredMethod(windowModelClass, "c"),
            contentModelPathField = declaredField(contentModelClass, "Y"),
            fxPathClass = fxPathClass,
            fxPathElementsField = declaredField(fxPathClass, "f"),
            fxPathParentMethod = declaredMethod(fxPathClass, "C"),
            dirCatalogClass = dirCatalogClass
        )

        val homeContentViewClass = loadClass(cl, "nextapp.fx.ui.homecontent.HomeContentView")
        val homeMenuContribClass = loadClass(cl, "nextapp.fx.ui.homecontent.HomeContentView\$d")
        val menuContribBaseClass = loadClass(cl, "nextapp.fx.ui.content.j0")
        val menuModelClass = loadClass(cl, "le.t")
        val menuItemBaseInterface = loadClass(cl, "le.z")
        val menuItemTitledInterface = loadClass(cl, "le.b0")
        val menuDividerClass = loadClass(cl, "le.f0")
        val menuActionClass = loadClass(cl, "le.r")
        val menuClickListenerInterface = loadClass(cl, "le.b\$a")
        val menuOrderEnumClass = loadClass(cl, "nextapp.fx.ui.content.k0")
        val actionIconsClass = loadClass(cl, "nextapp.fx.ui.res.ActionIcons")

        val homeMenu = HomeMenuBindings(
            homeContentViewClass = homeContentViewClass,
            homeViewActivityField = declaredField(homeContentViewClass, "activity"),
            homeViewResField = declaredField(homeContentViewClass, "res"),
            menuContribClass = homeMenuContribClass,
            menuContribCaseField = null,
            menuContribOwnerField = declaredField(homeMenuContribClass, "d")
                ?: declaredField(homeMenuContribClass, "this\$0")
                ?: homeMenuContribClass?.declaredFields?.firstOrNull { it.type == homeContentViewClass }
                    ?.apply { isAccessible = true },
            menuContribBgLightField = declaredField(menuContribBaseClass, "b"),
            menuContribPopulateMethod = declaredMethod(homeMenuContribClass, "p", menuModelClass, menuModelClass, menuOrderEnumClass),
            menuModelAddItemMethod = declaredMethod(menuModelClass, "f", menuItemBaseInterface),
            menuModelSizeMethod = declaredMethod(menuModelClass, "size"),
            menuModelItemMethod = declaredMethod(menuModelClass, "item", Int::class.javaPrimitiveType),
            menuModelItemsListField = null,
            menuItemGetTitleMethod = declaredMethod(menuItemTitledInterface, "getTitle"),
            menuDividerClass = menuDividerClass,
            menuDividerCtor = declaredConstructor(menuDividerClass),
            menuActionCtor = declaredConstructor(
                menuActionClass,
                CharSequence::class.java,
                android.graphics.drawable.Drawable::class.java,
                menuClickListenerInterface
            ),
            menuClickListenerInterface = menuClickListenerInterface,
            actionIconsCreateMethod = declaredMethod(
                actionIconsClass,
                "d",
                android.content.res.Resources::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
        )

        val gdriveAuthActivityClass = loadClass(cl, "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity")
        val gdriveIdHolderClass = loadClass(cl, "nextapp.fx.plus.dirimpl.googledrive.b\$a")
        val gdriveTokenCredsClass = loadClass(cl, "ic.e\$a")

        val googleDrive = GoogleDriveBindings(
            authActivityOnCreate = declaredMethod(gdriveAuthActivityClass, "onCreate", android.os.Bundle::class.java),
            getClientId9012 = declaredMethod(gdriveIdHolderClass, "c", Context::class.java),
            tokenCredsCtor9012 = declaredConstructor(gdriveTokenCredsClass, String::class.java, String::class.java, String::class.java),
            authReqBuilderCtor9108 = null,
            authReqClientIdField9108 = null,
            tokenCredsCtor9108 = null,
            tokenCredsClientIdField9108 = null,
            tokenCredsClientSecretField9108 = null
        )

        return ResolvedSymbols(
            profile = FxProfile.FX_9012,
            versionCode = versionCode,
            classLoader = cl,
            sibling = sibling,
            viewer = viewer,
            remoteThumb = remoteThumb,
            settings = settings,
            androidData = androidData,
            bonusLimits = bonusLimits,
            folderLifecycle = folderLifecycle,
            homeMenu = homeMenu,
            googleDrive = googleDrive
        )
    }

    private fun resolve9108(cl: ClassLoader, versionCode: Long): ResolvedSymbols {
        val b0Class = loadClass(cl, "nextapp.fx.ui.viewer.image.b0")
        val dirCatalogClass = loadClass(cl, "nextapp.xf.dir.DirectoryCatalog")
        val typeHelperClass = loadClass(cl, "a0.f")
        val fileItemBaseClass = loadClass(cl, "kh.j")
        val fileItemClass = loadClass(cl, "kh.e")
        val dirItemClass = loadClass(cl, "kh.d")
        val sortUtilsClass = loadClass(cl, "kh.q")
        val sortOrderClass = loadClass(cl, "kh.o")
        val sortPairClass = loadClass(cl, "kh.p")
        val dirOptStoreClass = loadClass(cl, "nextapp.fx.dir.optionstore.DirectoryOptionStore")
        val viewerBaseClass = loadClass(cl, "qg.b")
        val viewerClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity")
        val itemHolderClass = loadClass(cl, "nextapp.fx.ui.viewer.image.o")
        val displayListenerClass = loadClass(cl, "nextapp.fx.ui.viewer.image.l")
        val streamProviderClass = loadClass(cl, "nextapp.fx.ui.viewer.image.c")
        val imageDisplayClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageDisplay")
        val mimesHolderClass = loadClass(cl, "nextapp.fx.ui.viewer.image.c0")

        val sibling = SiblingBindings(
            siblingLoaderClass = b0Class,
            enumerateSiblings = declaredMethod(b0Class, "run"),
            supportedMimesField = declaredField(mimesHolderClass, "a"),
            directoryOptionStoreClass = dirOptStoreClass,
            getSortOptions = declaredMethod(dirOptStoreClass, "c", fileItemBaseClass),
            sortPairOrderField = declaredField(sortPairClass, "b"),
            sortPairDescField = declaredField(sortPairClass, "a"),
            sortItems = declaredMethod(sortUtilsClass, "a", java.lang.reflect.Array.newInstance(fileItemBaseClass ?: Any::class.java, 0)::class.java, sortOrderClass, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
            fileItemClass = fileItemClass,
            fileItemBaseClass = fileItemBaseClass,
            dirItemClass = dirItemClass,
            listChildren = declaredMethod(dirItemClass, "F0", Context::class.java, Int::class.javaPrimitiveType),
            getMimeType = declaredMethod(fileItemClass, "w"),
            getSize = declaredMethod(fileItemClass, "getSize"),
            getPath = declaredMethod(fileItemBaseClass, "getPath"),
            getName = declaredMethod(fileItemBaseClass, "getName"),
            getLastModified = declaredMethod(fileItemBaseClass, "getLastModified"),
            openInputStream = declaredMethod(fileItemClass, "f", Context::class.java),
            getCatalog = declaredMethod(fileItemBaseClass, "e"),
            dirCatalogClass = dirCatalogClass,
            getCatalogType = declaredMethod(dirCatalogClass, "getType"),
            isRemoteMethod = declaredMethod(typeHelperClass, "j", Int::class.javaPrimitiveType),
            imageViewerActivityClass = viewerClass,
            viewerSiblingItemsField = declaredField(viewerClass, "y2"),
            viewerSortOrderField = declaredField(viewerClass, "M2"),
            viewerSortDescField = declaredField(viewerClass, "I2"),
            viewerCurrentDisplayField = declaredField(viewerClass, "q2"),
            viewerCurrentHolderField = declaredField(viewerClass, "r2"),
            viewerInitialItemField = declaredField(viewerClass, "w2"),
            viewerSiblingsLoadedField = declaredField(viewerClass, "x2"),
            imageDisplayClass = imageDisplayClass,
            displaySiblingIndexField = declaredField(imageDisplayClass, "Y2"),
            displaySiblingCountField = declaredField(imageDisplayClass, "Z2"),
            displayListenerField = declaredField(imageDisplayClass, "g3"),
            displayCommitMethod = declaredMethod(imageDisplayClass, "k"),
            siblingCallbackClass = null,
            siblingCallbackLoadedMethod = null,
            siblingCallbackActivityField = null,
            displayListenerClass = displayListenerClass,
            displayListenerRequestMethod = declaredMethod(displayListenerClass, "e", imageDisplayClass, Int::class.javaPrimitiveType),
            displayListenerCommitMethod = declaredMethod(displayListenerClass, "b"),
            displayListenerHolderField = declaredField(displayListenerClass, "f"),
            runnerDirField = declaredField(b0Class, "f"),
            runnerCtxField = declaredField(b0Class, "i"),
            runnerSortOrderField = declaredField(b0Class, "X"),
            runnerSortDescField = declaredField(b0Class, "Y"),
            runnerCurrentItemField = declaredField(b0Class, "Z"),
            runnerCallbackHolderField = declaredField(b0Class, "X1"),
            itemHolderFileItemField = declaredField(itemHolderClass, "a"),
            itemHolderUriField = declaredField(itemHolderClass, "b")
        )

        val remoteItemClasses = listOfNotNull(
            loadClass(cl, "fd.j"),
            loadClass(cl, "gd.d"),
            loadClass(cl, "hd.e"),
            loadClass(cl, "bd.f"),
            loadClass(cl, "jd.d"),
            loadClass(cl, "cd.g"),
            loadClass(cl, "dd.e"),
            loadClass(cl, "ad.c")
        )

        val viewer = ViewerPrefetchBindings(
            viewerBaseClass = viewerBaseClass,
            mirrorRemoteTempFile = declaredMethod(viewerBaseClass, "u", fileItemClass),
            viewerOnCreate = declaredMethod(viewerClass, "onCreate", android.os.Bundle::class.java),
            viewerOnDestroy = declaredMethod(viewerBaseClass, "onDestroy"),
            viewerLoadMediaAndBitmap = declaredMethod(viewerClass, "C", imageDisplayClass, itemHolderClass, Int::class.javaPrimitiveType),
            viewerLoadMedia = null,
            viewerLoadBitmap = null,
            viewerLoadGif = null,
            viewerOpenItemStream = null,
            viewerDecodeItem = declaredMethod(viewerClass, "I", viewerClass, fileItemClass, Int::class.javaPrimitiveType),
            viewerStreamProviderRead = declaredMethod(streamProviderClass, "read"),
            viewerStreamProviderItemField = declaredField(streamProviderClass, "i"),
            remoteItemClasses = remoteItemClasses
        )

        val thumbRouterClass = loadClass(cl, "wg.e")
        val thumbCallbackClass = loadClass(cl, "kf.k")
        val thumbCacheClass = loadClass(cl, "kf.d")
        val thumbBaseClass = loadClass(cl, "kf.b")
        val remoteThumbTaskClass = loadClass(cl, "kf.e")
        val remoteThumbWorkerClass = loadClass(cl, "be.k0")
        val thumbGenClass = loadClass(cl, "pc.c")
        val thumbStreamSrcClass = loadClass(cl, "db.d")
        val thumbInfoClass = loadClass(cl, "nc.j")
        val settingsClass = loadClass(cl, "mb.l")
        val dropboxClass = loadClass(cl, "ad.c")
        val resultBoxClass = loadClass(cl, "a4.c")

        val remoteThumb = RemoteThumbBindings(
            loadThumbnail = declaredMethod(thumbRouterClass, "j", Context::class.java, fileItemBaseClass, Int::class.javaPrimitiveType, thumbCallbackClass, thumbCacheClass, Boolean::class.javaPrimitiveType),
            thumbCallbackClass = thumbCallbackClass,
            thumbTaskItemField = declaredField(thumbBaseClass, "c"),
            thumbTaskContextField = declaredField(thumbBaseClass, "d"),
            thumbTaskGetCacheFile = declaredMethod(thumbBaseClass, "c", Context::class.java, fileItemClass, String::class.java, String::class.java),
            remoteThumbTaskCtor = declaredConstructor(remoteThumbTaskClass, Context::class.java, fileItemClass, Int::class.javaPrimitiveType, thumbCacheClass, String::class.java),
            remoteThumbTaskMimeField = declaredField(remoteThumbTaskClass, "f"),
            remoteThumbWorkerRun = declaredMethod(remoteThumbWorkerClass, "f"),
            remoteThumbWorkerCaseField = declaredField(remoteThumbWorkerClass, "c2"),
            remoteThumbWorkerOwnerField = declaredField(remoteThumbWorkerClass, "e2"),
            remoteThumbWorkerResultField = declaredField(remoteThumbWorkerClass, "d2"),
            thumbResultField = declaredField(resultBoxClass, "f"),
            thumbGeneratorCreateForFile = declaredMethod(thumbGenClass, "c", Context::class.java, String::class.java, java.io.File::class.java),
            thumbGeneratorFlagsField = declaredField(thumbGenClass, "a"),
            thumbGeneratorContextField = declaredField(thumbGenClass, "b"),
            thumbGeneratorStreamField = declaredField(thumbGenClass, "c"),
            thumbGeneratorOutFileField = declaredField(thumbGenClass, "e"),
            thumbGeneratorSourcePathField = declaredField(thumbGenClass, "f"),
            thumbGeneratorStreamRead = declaredMethod(thumbStreamSrcClass, "read"),
            thumbImageInfoCtor = declaredConstructor(thumbInfoClass, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java, String::class.java),
            thumbGeneratorRun = declaredMethod(thumbGenClass, "a"),
            thumbGeneratorExceedsCap = null,
            thumbGeneratorGenerate = declaredMethod(thumbGenClass, "a"),
            thumbGeneratorClass = thumbGenClass,
            settingsGetInstance = declaredMethod(settingsClass, "d", Context::class.java),
            settingsThumbsEnabled = declaredMethod(settingsClass, "u"),
            settingsThumbsForPath = declaredMethod(settingsClass, "v", String::class.java),
            remoteItemMarkerClass = loadClass(cl, "kh.f0"),
            cloudThumbMarkerClass = loadClass(cl, "kh.h0"),
            dropboxSupportsThumb = declaredMethod(dropboxClass, "q")
        )

        val mainPrefClass = loadClass(cl, "nextapp.fx.ui.fxsystem.MainPrefActivity")
        val labelPrefClass = loadClass(cl, "nextapp.maui.ui.preference.LabelPreference")
        val fxDialogClass = loadClass(cl, "nextapp.fx.ui.widget.n")
        val fxDialogBaseClass = loadClass(cl, "nextapp.fx.ui.widget.g0")
        val fxDialogStyleClass = loadClass(cl, "nextapp.fx.ui.widget.m")
        val defaultWithClose = runCatching { declaredField(fxDialogStyleClass, "Z")?.get(null) }.getOrNull()
        val uiThemeClass = loadClass(cl, "ef.g")

        val settings = NativeSettingsBindings(
            mainPrefOnCreate = declaredMethod(mainPrefClass, "onCreate", android.os.Bundle::class.java),
            mainPrefOnResume = declaredMethod(mainPrefClass, "onResume"),
            labelPreferenceCtor = declaredConstructor(labelPrefClass, Context::class.java),
            fxDialogCtor = declaredConstructor(fxDialogClass, Context::class.java, fxDialogStyleClass),
            fxDialogDefaultWithClose = defaultWithClose,
            fxDialogSetHeader = declaredMethod(fxDialogClass, "setHeader", CharSequence::class.java),
            fxDialogSetDescription = declaredMethod(fxDialogClass, "setDescription", CharSequence::class.java),
            fxDialogGetDefaultContentLayout = declaredMethod(fxDialogClass, "getDefaultContentLayout"),
            fxDialogAddLabeledView = declaredMethod(fxDialogClass, "addLabeledView", CharSequence::class.java, android.view.View::class.java),
            fxDialogIsBackgroundLight = declaredMethod(fxDialogClass, "isBackgroundLight"),
            fxDialogDismiss = declaredMethod(fxDialogClass, "dismiss"),
            fxDialogShow = declaredMethod(fxDialogBaseClass, "show"),
            fxDialogUiField = declaredField(fxDialogClass, "ui"),
            uiGetForContext = declaredMethod(uiThemeClass, "b", Context::class.java),
            uiIsWindowLightField = declaredField(uiThemeClass, "j"),
            uiTextPrimaryField = declaredField(uiThemeClass, "l"),
            uiTextSecondaryField = declaredField(uiThemeClass, "k"),
            uiBoxBgField = declaredField(uiThemeClass, "v"),
            uiBoxPressedBgField = declaredField(uiThemeClass, "w"),
            uiWindowBgMethod = declaredMethod(uiThemeClass, "w"),
            uiTrimAccentMethod = declaredMethod(uiThemeClass, "u"),
            uiTrimBaseMethod = declaredMethod(uiThemeClass, "v")
        )

        val fileCatalogClass = loadClass(cl, "nextapp.fx.dirimpl.file.FileCatalog")
        val fileDirClass = loadClass(cl, "ec.a")
        val fileFileClass = loadClass(cl, "ec.d")
        val fileBaseClass = loadClass(cl, "ec.e")
        val fxPathClass = loadClass(cl, "hh.f")
        val hiddenStoreClass = loadClass(cl, "nextapp.fx.dir.optionstore.HiddenFileStore")

        val androidData = AndroidDataBindings(
            fileCatalogClass = fileCatalogClass,
            fileCatalogOpenDir = declaredMethod(fileCatalogClass, "g0", fxPathClass),
            fileCatalogResolveFile = declaredMethod(fileCatalogClass, "K", fxPathClass),
            fileCatalogSafUriField = declaredField(fileCatalogClass, "X1"),
            fileDirNodeClass = fileDirClass,
            fileDirListChildren = declaredMethod(fileDirClass, "F0", Context::class.java, Int::class.javaPrimitiveType),
            fileDirMkdir = declaredMethod(fileDirClass, "w0", Context::class.java, CharSequence::class.java, Boolean::class.javaPrimitiveType),
            fileDirChildAvailable = declaredMethod(fileDirClass, "j0", Context::class.java, CharSequence::class.java),
            fileDirDeleteRecursive = declaredMethod(fileDirClass, "Y", Context::class.java, Boolean::class.javaPrimitiveType),
            fileDirStatVolume = declaredMethod(fileDirClass, "a", Context::class.java),
            fileDirClearCache = declaredMethod(fileDirClass, "h0"),
            fileDirCachedFilesField = declaredField(fileDirClass, "X1"),
            fileFileNodeClass = fileFileClass,
            fileFileCtor = declaredConstructor(fileFileClass, fileCatalogClass, fxPathClass, java.io.File::class.java),
            fileFileStatSize = declaredMethod(fileFileClass, "a", Context::class.java),
            fileFileCachedSizeField = declaredField(fileFileClass, "X1"),
            fileFileOpenInput = declaredMethod(fileFileClass, "f", Context::class.java),
            fileFileOpenOutputInternal = declaredMethod(fileFileClass, "W", Context::class.java, Boolean::class.javaPrimitiveType),
            fileFileDelete = declaredMethod(fileFileClass, "Y", Context::class.java, Boolean::class.javaPrimitiveType),
            fileNodeBaseClass = fileBaseClass,
            fileNodeDeleteSingle = declaredMethod(fileBaseClass, "H", Context::class.java, Boolean::class.javaPrimitiveType),
            fileNodeRename = declaredMethod(fileBaseClass, "e0", Context::class.java, String::class.java),
            fileNodeMoveTo = declaredMethod(fileBaseClass, "a0", Context::class.java, fxPathClass),
            fileNodeGetLastModified = declaredMethod(fileBaseClass, "getLastModified"),
            fileNodeIsReadOnly = declaredMethod(fileBaseClass, "isReadOnly"),
            fileNodeSetLastModified = null,
            fileNodeCatalogField = declaredField(fileBaseClass, "f"),
            fileNodePathField = declaredField(fileBaseClass, "i"),
            fileNodeJavaFileField = declaredField(fileBaseClass, "X"),
            fileNodeMetricsField = declaredField(fileBaseClass, "Y"),
            fxPathChildCtor = declaredConstructor(fxPathClass, fxPathClass, String::class.java),
            fxPathFindElement = declaredMethod(fxPathClass, "E", Class::class.java),
            hiddenFileStoreIsHidden = declaredMethod(hiddenStoreClass, "a", String::class.java),
            fileDirCtor9012 = null,
            userExceptionClass = loadClass(cl, "hh.l"),
            cancelExceptionClass = loadClass(cl, "bb.b")
        )

        val textViewerClass = loadClass(cl, "nextapp.fx.ui.viewer.TextViewerActivity")
        val textViewerWorkerClass = loadClass(cl, "pg.f0")
        val viewerSupportClass = loadClass(cl, "af.h")
        val encodingStreamClass = loadClass(cl, "ue.a")
        val mimeProbeClass = loadClass(cl, "ab.i")
        val textEditLoaderClass = loadClass(cl, "mg.n")
        val textEditResultClass = loadClass(cl, "k2.y")
        val execClass = loadClass(cl, "nextapp.fx.ui.viewer.ExecActivity")
        val execWorkerClass = loadClass(cl, "nextapp.fx.plus.ui.audio.w")
        val execUiRunnableClass = loadClass(cl, "pg.k")

        val bonusLimits = BonusLimitsBindings(
            textViewerLoad = null,
            textViewerApply = null,
            textViewerOpenBinaryXml = declaredMethod(textViewerClass, "B", encodingStreamClass),
            textViewerWorkerRun = declaredMethod(textViewerWorkerClass, "run"),
            textViewerWorkerCaseField = declaredField(textViewerWorkerClass, "f"),
            textViewerWorkerActivityField = declaredField(textViewerWorkerClass, "i"),
            textViewerWorkerCtor = declaredConstructor(textViewerWorkerClass, textViewerClass, Int::class.javaPrimitiveType),
            textViewerLinesField = declaredField(textViewerClass, "s2"),
            textViewerCharsetField = declaredField(textViewerClass, "t2"),
            textViewerTruncatedField = declaredField(textViewerClass, "u2"),
            textViewerUnsupportedField = declaredField(textViewerClass, "v2"),
            viewerSupportHandlerField = declaredField(viewerSupportClass, "c2"),
            viewerSupportShowError = declaredMethod(viewerSupportClass, "p", Int::class.javaPrimitiveType),
            encodingStreamCtor = declaredConstructor(encodingStreamClass, java.io.InputStream::class.java),
            encodingStreamDetect = declaredMethod(encodingStreamClass, "c"),
            encodingStreamGetCharset = null,
            encodingStreamCharsetField = declaredField(encodingStreamClass, "Z"),
            encodingStreamGetBuffer = null,
            encodingStreamBufferField = declaredField(encodingStreamClass, "f"),
            mimeProbeBytes = declaredMethod(mimeProbeClass, "c", ByteArray::class.java),
            textEditReadStream = declaredMethod(textEditLoaderClass, "l", java.io.InputStream::class.java, String::class.java),
            textEditResultCtor = declaredConstructor(textEditResultClass, Any::class.java, Int::class.javaPrimitiveType, Any::class.java),
            execLoadPreview = null,
            execApplyPreview = null,
            execWorkerRun = declaredMethod(execWorkerClass, "run"),
            execWorkerCaseField = declaredField(execWorkerClass, "f"),
            execWorkerArg1Field = declaredField(execWorkerClass, "i"),
            execWorkerArg2Field = declaredField(execWorkerClass, "X"),
            execUiRunnableCtor = declaredConstructor(execUiRunnableClass, execClass, Int::class.javaPrimitiveType),
            execLinesField = declaredField(execClass, "r2"),
            execCharsetField = declaredField(execClass, "D2"),
            execUnsupportedField = declaredField(execClass, "F2")
        )

        val explorerActivityClass = loadClass(cl, "nextapp.fx.ui.ExplorerActivity")
        val contentHostClass = loadClass(cl, "nextapp.fx.ui.content.k")
        val windowManagerClass = loadClass(cl, "nextapp.fx.ui.content.w")
        val windowModelClass = loadClass(cl, "nextapp.fx.ui.content.c1")
        val contentModelClass = loadClass(cl, "nextapp.fx.ui.content.p")

        val folderLifecycle = FolderLifecycleBindings(
            explorerActivityClass = explorerActivityClass,
            explorerOnResume = declaredMethod(explorerActivityClass, "onResume"),
            explorerOnDestroy = declaredMethod(explorerActivityClass, "onDestroy"),
            windowManagerField = declaredField(contentHostClass, "n2"),
            windowModelsField = declaredField(windowManagerClass, "f"),
            windowModelCloseMethod = declaredMethod(windowManagerClass, "l", windowModelClass),
            windowModelSetPathMethod = declaredMethod(windowModelClass, "v", fxPathClass),
            windowModelCurrentContentMethod = declaredMethod(windowModelClass, "g"),
            contentModelPathField = declaredField(contentModelClass, "Y"),
            fxPathClass = fxPathClass,
            fxPathElementsField = declaredField(fxPathClass, "f"),
            fxPathParentMethod = declaredMethod(fxPathClass, "G"),
            dirCatalogClass = dirCatalogClass
        )

        val homeContentViewClass = loadClass(cl, "nextapp.fx.ui.homecontent.HomeContentView")
        val menuContribClass = loadClass(cl, "be.p")
        val menuContribBaseClass = loadClass(cl, "nextapp.fx.ui.content.b0")
        val menuModelClass = loadClass(cl, "yg.r")
        val menuItemBaseInterface = loadClass(cl, "yg.u")
        val menuItemTitledInterface = loadClass(cl, "yg.w")
        val menuDividerClass = loadClass(cl, "yg.y")
        val menuActionClass = loadClass(cl, "yg.p")
        val menuClickListenerInterface = loadClass(cl, "yg.b")
        val actionIconsClass = loadClass(cl, "nextapp.fx.ui.res.ActionIcons")

        val homeMenu = HomeMenuBindings(
            homeContentViewClass = homeContentViewClass,
            homeViewActivityField = declaredField(homeContentViewClass, "activity"),
            homeViewResField = declaredField(homeContentViewClass, "res"),
            menuContribClass = menuContribClass,
            menuContribCaseField = declaredField(menuContribClass, "c"),
            menuContribOwnerField = declaredField(menuContribClass, "d"),
            menuContribBgLightField = declaredField(menuContribBaseClass, "b"),
            menuContribPopulateMethod = declaredMethod(menuContribClass, "m", menuModelClass, menuModelClass, Int::class.javaPrimitiveType),
            menuModelAddItemMethod = declaredMethod(menuModelClass, "d", menuItemBaseInterface),
            menuModelSizeMethod = null,
            menuModelItemMethod = null,
            menuModelItemsListField = declaredField(menuModelClass, "d"),
            menuItemGetTitleMethod = declaredMethod(menuItemTitledInterface, "getTitle"),
            menuDividerClass = menuDividerClass,
            menuDividerCtor = declaredConstructor(menuDividerClass),
            menuActionCtor = declaredConstructor(
                menuActionClass,
                CharSequence::class.java,
                android.graphics.drawable.Drawable::class.java,
                menuClickListenerInterface
            ),
            menuClickListenerInterface = menuClickListenerInterface,
            actionIconsCreateMethod = declaredMethod(
                actionIconsClass,
                "b",
                android.content.res.Resources::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
        )

        val gdriveAuthActivityClass = loadClass(cl, "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity")
        val authReqBuilderClass = loadClass(cl, "gg.d")
        val oauthServiceConfigClass = loadClass(cl, "d4.b")
        val tokenCredsClass = loadClass(cl, "jh.b")

        val googleDrive = GoogleDriveBindings(
            authActivityOnCreate = declaredMethod(gdriveAuthActivityClass, "onCreate", android.os.Bundle::class.java),
            getClientId9012 = null,
            tokenCredsCtor9012 = null,
            authReqBuilderCtor9108 = declaredConstructor(authReqBuilderClass, oauthServiceConfigClass, android.net.Uri::class.java),
            authReqClientIdField9108 = declaredField(authReqBuilderClass, "a"),
            tokenCredsCtor9108 = declaredConstructor(
                tokenCredsClass,
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                String::class.java
            ),
            tokenCredsClientIdField9108 = declaredField(tokenCredsClass, "a"),
            tokenCredsClientSecretField9108 = declaredField(tokenCredsClass, "b")
        )

        return ResolvedSymbols(
            profile = FxProfile.FX_9108,
            versionCode = versionCode,
            classLoader = cl,
            sibling = sibling,
            viewer = viewer,
            remoteThumb = remoteThumb,
            settings = settings,
            androidData = androidData,
            bonusLimits = bonusLimits,
            folderLifecycle = folderLifecycle,
            homeMenu = homeMenu,
            googleDrive = googleDrive
        )
    }

    /**
     * Structural fallback for unknown future FX builds: resolves un-obfuscated public
     * components (e.g. `MainPrefActivity`, `ImageViewerActivity`, `FileCatalog`) safely
     * by signature shape and leaves unverified obfuscated targets null.
     */
    private fun resolveStructuralFallback(cl: ClassLoader, versionCode: Long): ResolvedSymbols {
        val mainPrefClass = loadClass(cl, "nextapp.fx.ui.fxsystem.MainPrefActivity")
        val labelPrefClass = loadClass(cl, "nextapp.maui.ui.preference.LabelPreference")
        val viewerClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity")
        val fileCatalogClass = loadClass(cl, "nextapp.fx.dirimpl.file.FileCatalog")

        val dialogClass = findDialogClass(cl)
        val dialogCtor = dialogClass?.declaredConstructors?.firstOrNull { ctor ->
            val p = ctor.parameterTypes
            p.size == 2 && p[0] == Context::class.java && p[1].isEnum
        }
        val dialogStyleWithClose = dialogCtor?.parameterTypes?.getOrNull(1)?.enumConstants?.getOrNull(1)

        val settings = NativeSettingsBindings(
            mainPrefOnCreate = declaredMethod(mainPrefClass, "onCreate", android.os.Bundle::class.java),
            mainPrefOnResume = declaredMethod(mainPrefClass, "onResume"),
            labelPreferenceCtor = declaredConstructor(labelPrefClass, Context::class.java),
            fxDialogCtor = dialogCtor,
            fxDialogDefaultWithClose = dialogStyleWithClose,
            fxDialogSetHeader = declaredMethod(dialogClass, "setHeader", CharSequence::class.java),
            fxDialogSetDescription = declaredMethod(dialogClass, "setDescription", CharSequence::class.java),
            fxDialogGetDefaultContentLayout = declaredMethod(dialogClass, "getDefaultContentLayout"),
            fxDialogAddLabeledView = declaredMethod(dialogClass, "addLabeledView", CharSequence::class.java, android.view.View::class.java),
            fxDialogIsBackgroundLight = declaredMethod(dialogClass, "isBackgroundLight"),
            fxDialogDismiss = declaredMethod(dialogClass, "dismiss"),
            fxDialogShow = dialogClass?.superclass?.let { declaredMethod(it, "show") },
            fxDialogUiField = declaredField(dialogClass, "ui"),
            uiGetForContext = null,
            uiIsWindowLightField = null,
            uiTextPrimaryField = null,
            uiTextSecondaryField = null,
            uiBoxBgField = null,
            uiBoxPressedBgField = null,
            uiWindowBgMethod = null,
            uiTrimAccentMethod = null,
            uiTrimBaseMethod = null
        )

        val sibling = SiblingBindings(
            siblingLoaderClass = null, enumerateSiblings = null, supportedMimesField = null,
            directoryOptionStoreClass = loadClass(cl, "nextapp.fx.dir.optionstore.DirectoryOptionStore"),
            getSortOptions = null, sortPairOrderField = null, sortPairDescField = null,
            sortItems = null, fileItemClass = null, fileItemBaseClass = null, dirItemClass = null,
            listChildren = null, getMimeType = null, getSize = null, getPath = null, getName = null,
            getLastModified = null, openInputStream = null, getCatalog = null,
            dirCatalogClass = loadClass(cl, "nextapp.xf.dir.DirectoryCatalog"),
            getCatalogType = null, isRemoteMethod = null, imageViewerActivityClass = viewerClass,
            viewerSiblingItemsField = null, viewerSortOrderField = null, viewerSortDescField = null,
            viewerCurrentDisplayField = null, viewerCurrentHolderField = null, viewerInitialItemField = null,
            viewerSiblingsLoadedField = null,
            imageDisplayClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageDisplay"),
            displaySiblingIndexField = null, displaySiblingCountField = null, displayListenerField = null, displayCommitMethod = null,
            siblingCallbackClass = null, siblingCallbackLoadedMethod = null, siblingCallbackActivityField = null,
            displayListenerClass = null, displayListenerRequestMethod = null, displayListenerCommitMethod = null,
            displayListenerHolderField = null, runnerDirField = null, runnerCtxField = null,
            runnerSortOrderField = null, runnerSortDescField = null, runnerCurrentItemField = null,
            runnerCallbackHolderField = null, itemHolderFileItemField = null, itemHolderUriField = null
        )

        val viewer = ViewerPrefetchBindings(
            viewerBaseClass = viewerClass?.superclass,
            mirrorRemoteTempFile = null,
            viewerOnCreate = declaredMethod(viewerClass, "onCreate", android.os.Bundle::class.java),
            viewerOnDestroy = viewerClass?.superclass?.let { declaredMethod(it, "onDestroy") },
            viewerLoadMediaAndBitmap = null, viewerLoadMedia = null, viewerLoadBitmap = null,
            viewerLoadGif = null, viewerOpenItemStream = null, viewerDecodeItem = null,
            viewerStreamProviderRead = null, viewerStreamProviderItemField = null,
            remoteItemClasses = emptyList()
        )

        val remoteThumb = RemoteThumbBindings(
            loadThumbnail = null, thumbCallbackClass = null, thumbTaskItemField = null,
            thumbTaskContextField = null, thumbTaskGetCacheFile = null, remoteThumbTaskCtor = null,
            remoteThumbTaskMimeField = null, remoteThumbWorkerRun = null, remoteThumbWorkerCaseField = null,
            remoteThumbWorkerOwnerField = null, remoteThumbWorkerResultField = null, thumbResultField = null,
            thumbGeneratorCreateForFile = null, thumbGeneratorFlagsField = null,
            thumbGeneratorContextField = null, thumbGeneratorStreamField = null,
            thumbGeneratorOutFileField = null, thumbGeneratorSourcePathField = null,
            thumbGeneratorStreamRead = null, thumbImageInfoCtor = null,
            thumbGeneratorRun = null,
            thumbGeneratorExceedsCap = null, thumbGeneratorGenerate = null, thumbGeneratorClass = null,
            settingsGetInstance = null, settingsThumbsEnabled = null, settingsThumbsForPath = null,
            remoteItemMarkerClass = null, cloudThumbMarkerClass = null, dropboxSupportsThumb = null
        )

        val androidData = AndroidDataBindings(
            fileCatalogClass = fileCatalogClass, fileCatalogOpenDir = null, fileCatalogResolveFile = null,
            fileCatalogSafUriField = null, fileDirNodeClass = null, fileDirListChildren = null,
            fileDirMkdir = null, fileDirChildAvailable = null, fileDirDeleteRecursive = null,
            fileDirStatVolume = null, fileDirClearCache = null, fileDirCachedFilesField = null,
            fileFileNodeClass = null, fileFileCtor = null, fileFileStatSize = null,
            fileFileCachedSizeField = null, fileFileOpenInput = null, fileFileOpenOutputInternal = null,
            fileFileDelete = null, fileNodeBaseClass = null, fileNodeDeleteSingle = null,
            fileNodeRename = null, fileNodeMoveTo = null, fileNodeGetLastModified = null,
            fileNodeIsReadOnly = null, fileNodeSetLastModified = null, fileNodeCatalogField = null,
            fileNodePathField = null, fileNodeJavaFileField = null, fileNodeMetricsField = null,
            fxPathChildCtor = null, fxPathFindElement = null, hiddenFileStoreIsHidden = null, fileDirCtor9012 = null,
            userExceptionClass = null, cancelExceptionClass = null
        )

        val bonusLimits = BonusLimitsBindings(
            textViewerLoad = null, textViewerApply = null, textViewerOpenBinaryXml = null,
            textViewerWorkerRun = null, textViewerWorkerCaseField = null, textViewerWorkerActivityField = null,
            textViewerWorkerCtor = null, textViewerLinesField = null, textViewerCharsetField = null,
            textViewerTruncatedField = null, textViewerUnsupportedField = null,
            viewerSupportHandlerField = null, viewerSupportShowError = null, encodingStreamCtor = null,
            encodingStreamDetect = null, encodingStreamGetCharset = null, encodingStreamCharsetField = null,
            encodingStreamGetBuffer = null, encodingStreamBufferField = null, mimeProbeBytes = null,
            textEditReadStream = null, textEditResultCtor = null, execLoadPreview = null,
            execApplyPreview = null, execWorkerRun = null, execWorkerCaseField = null,
            execWorkerArg1Field = null, execWorkerArg2Field = null, execUiRunnableCtor = null,
            execLinesField = null, execCharsetField = null, execUnsupportedField = null
        )

        val explorerActivityClass = loadClass(cl, "nextapp.fx.ui.ExplorerActivity")
        val folderLifecycle = FolderLifecycleBindings(
            explorerActivityClass = explorerActivityClass,
            explorerOnResume = declaredMethod(explorerActivityClass, "onResume"),
            explorerOnDestroy = declaredMethod(explorerActivityClass, "onDestroy"),
            windowManagerField = null,
            windowModelsField = null,
            windowModelCloseMethod = null,
            windowModelSetPathMethod = null,
            windowModelCurrentContentMethod = null,
            contentModelPathField = null,
            fxPathClass = null,
            fxPathElementsField = null,
            fxPathParentMethod = null,
            dirCatalogClass = sibling.dirCatalogClass
        )

        val homeMenu = HomeMenuBindings(
            homeContentViewClass = loadClass(cl, "nextapp.fx.ui.homecontent.HomeContentView"),
            homeViewActivityField = null,
            homeViewResField = null,
            menuContribClass = null,
            menuContribCaseField = null,
            menuContribOwnerField = null,
            menuContribBgLightField = null,
            menuContribPopulateMethod = null,
            menuModelAddItemMethod = null,
            menuModelSizeMethod = null,
            menuModelItemMethod = null,
            menuModelItemsListField = null,
            menuItemGetTitleMethod = null,
            menuDividerClass = null,
            menuDividerCtor = null,
            menuActionCtor = null,
            menuClickListenerInterface = null,
            actionIconsCreateMethod = null
        )

        val gdriveAuthActivityClass = loadClass(cl, "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity")
        val googleDrive = GoogleDriveBindings(
            authActivityOnCreate = declaredMethod(gdriveAuthActivityClass, "onCreate", android.os.Bundle::class.java),
            getClientId9012 = null,
            tokenCredsCtor9012 = null,
            authReqBuilderCtor9108 = null,
            authReqClientIdField9108 = null,
            tokenCredsCtor9108 = null,
            tokenCredsClientIdField9108 = null,
            tokenCredsClientSecretField9108 = null
        )

        return ResolvedSymbols(
            profile = FxProfile.STRUCTURAL_FALLBACK,
            versionCode = versionCode,
            classLoader = cl,
            sibling = sibling,
            viewer = viewer,
            remoteThumb = remoteThumb,
            settings = settings,
            androidData = androidData,
            bonusLimits = bonusLimits,
            folderLifecycle = folderLifecycle,
            homeMenu = homeMenu,
            googleDrive = googleDrive
        )
    }

    private fun findDialogClass(cl: ClassLoader): Class<*>? {
        for (suffix in listOf("k", "n", "l", "m", "o")) {
            val c = loadClass(cl, "nextapp.fx.ui.widget.$suffix") ?: continue
            if (declaredMethod(c, "getDefaultContentLayout") != null &&
                declaredMethod(c, "isBackgroundLight") != null
            ) {
                return c
            }
        }
        return null
    }

    private fun hasClass(cl: ClassLoader, name: String): Boolean = loadClass(cl, name) != null

    private fun loadClass(cl: ClassLoader, name: String): Class<*>? {
        return runCatching { Class.forName(name, false, cl) }.getOrNull()
    }

    private fun declaredMethod(clazz: Class<*>?, name: String, vararg params: Class<*>?): Method? {
        if (clazz == null) return null
        if (params.any { it == null }) return null
        @Suppress("UNCHECKED_CAST")
        val nonNullParams = params as Array<Class<*>>
        return runCatching {
            clazz.getDeclaredMethod(name, *nonNullParams).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun declaredConstructor(clazz: Class<*>?, vararg params: Class<*>?): Constructor<*>? {
        if (clazz == null) return null
        if (params.any { it == null }) return null
        @Suppress("UNCHECKED_CAST")
        val nonNullParams = params as Array<Class<*>>
        return runCatching {
            clazz.getDeclaredConstructor(*nonNullParams).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun declaredField(clazz: Class<*>?, name: String): Field? {
        if (clazz == null) return null
        return runCatching {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        }.getOrNull()
    }
}
