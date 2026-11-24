package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import com.scluzlep.fxplusmodule.config.ConfigContract
import java.io.File
import java.io.InputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

enum class FxProfile {
    FX_9012,
    FX_9108,
    DEXKIT_DYNAMIC,
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
            DexMethodSpec("dd.l", "a", "(Ldd/f;)V", false),
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
            DexFieldSpec("v8.f", "f", "I", false),
            DexFieldSpec("v8.f", "i", "I", false),
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
            DexMethodSpec("jh.b", "<init>", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false),
            DexMethodSpec("kf.k", "a", "(Lkf/f;)V", false)
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
            DexFieldSpec("pa.c", "f", "I", false),
            DexFieldSpec("pa.c", "i", "I", false),
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
    val siblingCallbackFailMethod: Method? = null,
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
    val viewerOpenStreamMethod: Method? = null,
    val taskThreadCtor: Constructor<*>? = null,
    val viewerProgressBarField: Field? = null,
    val remoteItemClasses: List<Class<*>>
)

data class RemoteThumbBindings(
    val loadThumbnail: Method?,
    val thumbCallbackClass: Class<*>?,
    val thumbCallbackEnqueueMethod: Method? = null,
    val thumbTaskItemField: Field?,
    val thumbTaskContextField: Field?,
    val thumbTaskGetCacheFile: Method?,
    val remoteThumbTaskCtor: Constructor<*>?,
    val remoteThumbTaskMimeField: Field?,
    val remoteThumbWorkerRun: Method?,
    val remoteThumbWorkerCaseField: Field?,
    val remoteThumbWorkerCaseId: Int = 2,
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
    val dimWidthField: Field? = null,
    val dimHeightField: Field? = null,
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
    val menuContribCaseId: Int = 14,
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
    val textViewerWorkerCaseId: Int = 0,
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
    val textEditResultCaseId: Int = 7,
    val execLoadPreview: Method?,
    val execApplyPreview: Method?,
    val execWorkerRun: Method?,
    val execWorkerCaseField: Field?,
    val execWorkerCaseId: Int = 19,
    val execWorkerArg1Field: Field?,
    val execWorkerArg2Field: Field?,
    val execUiRunnableCtor: Constructor<*>?,
    val execUiRunnableCaseId: Int = 6,
    val execLinesField: Field?,
    val execCharsetField: Field?,
    val execUnsupportedField: Field?
)

data class HardwareRenderBindings(
    val touchClass: Class<*>?,
    val setImageBitmap: Method?,
    val setImageMovie: Method?,
    val setImageNull: Method?,
    val setPrescale: Method?,
    val setTouchEnabled: Method?,
    val zoomFit: Method?,
    val onDraw: Method?,
    val onDetachedFromWindow: Method?,
    val updateBoundsMethod: Method?,
    val enforceLayerOnBitmapMethod: Method?,
    val bitmapField: Field?,
    val movieField: Field?,
    val moviePlayingField: Field?,
    val movieThreadField: Field?,
    val matrixField: Field?,
    val paintField: Field?,
    val cropArField: Field?,
    val cropRectField: Field?,
    val softwareLayerFlagField: Field?,
    val displaySizeField: Field?,
    val displaySizeWidthField: Field?,
    val displaySizeHeightField: Field?,
    val needsZoomFitField: Field?,
    val imageDisplayClass: Class<*>?,
    val deliverBitmapMethod: Method?,
    val deliverMovieMethod: Method?,
    val clearDisplayMethod: Method?,
    val displayBitmapField: Field?,
    val displayMovieField: Field?,
    val displayDisposedField: Field?,
    val displayFullLoadedField: Field?,
    val displayPreviewLoadedField: Field?,
    val viewerClass: Class<*>?,
    val loadMediaMethod: Method?,
    val loadGifMethod: Method?,
    val applyBitmapResultMethod: Method?,
    val imageResultCtor: Constructor<*>?,
    val loadTokenField: Field?,
    val isGifField: Field?,
    val currentHolderField: Field?,
    val enterTransitionField: Field?,
    val transitionBgViewField: Field?,
    val transitionTopBarField: Field?,
    val transitionBottomBarField: Field?,
    val transitionRunningField: Field?,
    val transitionDoneField: Field?,
    val uiHandlerField: Field?,
    val holderFileItemField: Field?,
    val holderUriField: Field?,
    val gifAnimatedCheckMethod: Method?,
    val localPathClass: Class<*>?,
    val localPathMethod: Method?
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
    val googleDrive: GoogleDriveBindings,
    val hardwareRender: HardwareRenderBindings,
    val symbolResolutionSource: String = ""
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
        return resolve(
            classLoader = classLoader,
            versionCode = versionCode,
            targetAppInfo = null,
            moduleAppInfo = null,
            onLog = null
        )
    }

    fun resolve(
        classLoader: ClassLoader,
        versionCode: Long,
        targetAppInfo: ApplicationInfo? = null,
        moduleAppInfo: ApplicationInfo? = null,
        onLog: ((Int, String, Throwable?) -> Unit)? = null
    ): ResolvedSymbols {
        val profile = detectProfile(classLoader, versionCode)
        if (profile == FxProfile.FX_9012) {
            return resolve9012(classLoader, versionCode)
        }
        if (profile == FxProfile.FX_9108) {
            return resolve9108(classLoader, versionCode)
        }

        // Tier 2: persisted DexKit symbol manifest (zero native load overhead on cache hit).
        val cacheDir = DexSymbolCache.resolveCacheDir(targetAppInfo?.dataDir)
        val fingerprint = DexSymbolCache.computeFingerprint(versionCode, targetAppInfo, moduleAppInfo)
        val cached = DexSymbolCache.load(cacheDir, fingerprint)
        if (cached != null && cached.isUsable()) {
            onLog?.invoke(
                Log.INFO,
                "Loaded cached DexKit symbol manifest (v$versionCode, fp=${fingerprint.take(12)})",
                null
            )
            return hydrateFromManifest(classLoader, versionCode, cached, "dexkit:cache")
        }

        // Tier 3: live DexKit 2.3.0 scan + atomic manifest persistence.
        val scanned = FxDexKitResolver.scanAndBuildManifest(
            classLoader = classLoader,
            versionCode = versionCode,
            fingerprint = fingerprint,
            targetAppInfo = targetAppInfo,
            moduleAppInfo = moduleAppInfo,
            onLog = onLog
        )
        if (scanned != null && scanned.isUsable()) {
            val saved = DexSymbolCache.save(cacheDir, scanned)
            onLog?.invoke(
                Log.INFO,
                "DexKit scan succeeded (v$versionCode, classes=${scanned.classes.size}, methods=${scanned.methods.size}, fields=${scanned.fields.size}, saved=$saved)",
                null
            )
            return hydrateFromManifest(classLoader, versionCode, scanned, "dexkit:scan")
        }

        // Tier 4: safe structural fallback.
        onLog?.invoke(
            Log.WARN,
            "DexKit resolution unavailable for versionCode=$versionCode; falling back to public signatures",
            null
        )
        return resolveStructuralFallback(classLoader, versionCode)
    }

    internal fun hydrateFromManifest(
        cl: ClassLoader,
        versionCode: Long,
        manifest: FxSymbolManifest,
        source: String
    ): ResolvedSymbols {
        fun cls(key: String): Class<*>? = manifest.classes[key]?.let { DexSymbolCache.resolveClass(cl, it) }
        fun mtd(key: String): Method? = manifest.methods[key]?.let { DexSymbolCache.resolveMethod(cl, it) }
        fun ctor(key: String): Constructor<*>? = manifest.methods[key]?.let { DexSymbolCache.resolveConstructor(cl, it) }
        fun fld(key: String): Field? = manifest.fields[key]?.let { DexSymbolCache.resolveField(cl, it) }

        val viewerClass = cls(FxSymbolKeys.CLS_IMAGE_VIEWER_ACTIVITY)
            ?: loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity")
        val imageDisplayClass = cls(FxSymbolKeys.CLS_IMAGE_DISPLAY)
            ?: loadClass(cl, "nextapp.fx.ui.viewer.image.ImageDisplay")
        val dirCatalogClass = cls(FxSymbolKeys.CLS_DIR_CATALOG)
            ?: loadClass(cl, "nextapp.xf.dir.DirectoryCatalog")
        val dirOptStoreClass = cls(FxSymbolKeys.CLS_DIRECTORY_OPTION_STORE)
            ?: loadClass(cl, "nextapp.fx.dir.optionstore.DirectoryOptionStore")
        val fileCatalogClass = cls(FxSymbolKeys.CLS_FILE_CATALOG)
            ?: loadClass(cl, "nextapp.fx.dirimpl.file.FileCatalog")
        val explorerActivityClass = cls(FxSymbolKeys.CLS_EXPLORER_ACTIVITY)
            ?: loadClass(cl, "nextapp.fx.ui.ExplorerActivity")
        val homeContentViewClass = cls(FxSymbolKeys.CLS_HOME_CONTENT_VIEW)
            ?: loadClass(cl, "nextapp.fx.ui.homecontent.HomeContentView")
        val touchClass = cls(FxSymbolKeys.CLS_TOUCH_DISPLAY)
            ?: loadClass(cl, "nextapp.maui.ui.imageview.TouchImageDisplay")
        val mainPrefClass = loadClass(cl, "nextapp.fx.ui.fxsystem.MainPrefActivity")
        val labelPrefClass = loadClass(cl, "nextapp.maui.ui.preference.LabelPreference")
        val gdriveAuthClass = loadClass(cl, "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity")
        val viewerBaseClass = cls(FxSymbolKeys.CLS_VIEWER_BASE) ?: viewerClass?.superclass
        val siblingCbClass = cls(FxSymbolKeys.CLS_SIBLING_CALLBACK)

        val sibling = SiblingBindings(
            siblingLoaderClass = cls(FxSymbolKeys.CLS_SIBLING_LOADER),
            enumerateSiblings = mtd(FxSymbolKeys.MTD_ENUMERATE_SIBLINGS),
            supportedMimesField = fld(FxSymbolKeys.FLD_SUPPORTED_MIMES),
            directoryOptionStoreClass = dirOptStoreClass,
            getSortOptions = mtd(FxSymbolKeys.MTD_GET_SORT_OPTIONS),
            sortPairOrderField = fld(FxSymbolKeys.FLD_SORT_PAIR_ORDER),
            sortPairDescField = fld(FxSymbolKeys.FLD_SORT_PAIR_DESC),
            sortItems = mtd(FxSymbolKeys.MTD_SORT_ITEMS),
            fileItemClass = cls(FxSymbolKeys.CLS_FILE_ITEM),
            fileItemBaseClass = cls(FxSymbolKeys.CLS_FILE_ITEM_BASE),
            dirItemClass = cls(FxSymbolKeys.CLS_DIR_ITEM),
            listChildren = mtd(FxSymbolKeys.MTD_LIST_CHILDREN),
            getMimeType = mtd(FxSymbolKeys.MTD_GET_MIME_TYPE),
            getSize = mtd(FxSymbolKeys.MTD_GET_SIZE) ?: declaredMethod(cls(FxSymbolKeys.CLS_FILE_ITEM), "getSize"),
            getPath = mtd(FxSymbolKeys.MTD_GET_PATH) ?: declaredMethod(cls(FxSymbolKeys.CLS_FILE_ITEM_BASE), "getPath"),
            getName = mtd(FxSymbolKeys.MTD_GET_NAME) ?: declaredMethod(cls(FxSymbolKeys.CLS_FILE_ITEM_BASE), "getName"),
            getLastModified = mtd(FxSymbolKeys.MTD_GET_LAST_MODIFIED)
                ?: declaredMethod(cls(FxSymbolKeys.CLS_FILE_ITEM_BASE), "getLastModified"),
            openInputStream = mtd(FxSymbolKeys.MTD_OPEN_INPUT_STREAM),
            getCatalog = mtd(FxSymbolKeys.MTD_GET_CATALOG),
            dirCatalogClass = dirCatalogClass,
            getCatalogType = mtd(FxSymbolKeys.MTD_GET_CATALOG_TYPE)
                ?: declaredMethod(dirCatalogClass, "getType"),
            isRemoteMethod = mtd(FxSymbolKeys.MTD_IS_REMOTE),
            imageViewerActivityClass = viewerClass,
            viewerSiblingItemsField = fld(FxSymbolKeys.FLD_VIEWER_SIBLING_ITEMS),
            viewerSortOrderField = fld(FxSymbolKeys.FLD_VIEWER_SORT_ORDER),
            viewerSortDescField = fld(FxSymbolKeys.FLD_VIEWER_SORT_DESC),
            viewerCurrentDisplayField = fld(FxSymbolKeys.FLD_VIEWER_CURRENT_DISPLAY),
            viewerCurrentHolderField = fld(FxSymbolKeys.FLD_VIEWER_CURRENT_HOLDER),
            viewerInitialItemField = fld(FxSymbolKeys.FLD_VIEWER_INITIAL_ITEM),
            viewerSiblingsLoadedField = fld(FxSymbolKeys.FLD_VIEWER_SIBLINGS_LOADED),
            imageDisplayClass = imageDisplayClass,
            displaySiblingIndexField = fld(FxSymbolKeys.FLD_DISPLAY_SIBLING_INDEX),
            displaySiblingCountField = fld(FxSymbolKeys.FLD_DISPLAY_SIBLING_COUNT),
            displayListenerField = fld(FxSymbolKeys.FLD_DISPLAY_LISTENER),
            displayCommitMethod = mtd(FxSymbolKeys.MTD_DISPLAY_COMMIT),
            siblingCallbackClass = siblingCbClass,
            siblingCallbackLoadedMethod = mtd(FxSymbolKeys.MTD_SIBLING_CB_LOADED),
            siblingCallbackFailMethod = mtd(FxSymbolKeys.MTD_SIBLING_CB_FAIL)
                ?: declaredMethod(siblingCbClass, "b"),
            siblingCallbackActivityField = fld(FxSymbolKeys.FLD_SIBLING_CB_ACTIVITY),
            displayListenerClass = cls(FxSymbolKeys.CLS_DISPLAY_LISTENER),
            displayListenerRequestMethod = mtd(FxSymbolKeys.MTD_DISPLAY_LISTENER_REQUEST),
            displayListenerCommitMethod = mtd(FxSymbolKeys.MTD_DISPLAY_LISTENER_COMMIT),
            displayListenerHolderField = fld(FxSymbolKeys.FLD_DISPLAY_LISTENER_HOLDER),
            runnerDirField = fld(FxSymbolKeys.FLD_RUNNER_DIR),
            runnerCtxField = fld(FxSymbolKeys.FLD_RUNNER_CTX),
            runnerSortOrderField = fld(FxSymbolKeys.FLD_RUNNER_SORT_ORDER),
            runnerSortDescField = fld(FxSymbolKeys.FLD_RUNNER_SORT_DESC),
            runnerCurrentItemField = fld(FxSymbolKeys.FLD_RUNNER_CURRENT_ITEM),
            runnerCallbackHolderField = fld(FxSymbolKeys.FLD_RUNNER_CALLBACK_HOLDER),
            itemHolderFileItemField = fld(FxSymbolKeys.FLD_ITEM_HOLDER_FILE_ITEM),
            itemHolderUriField = fld(FxSymbolKeys.FLD_ITEM_HOLDER_URI)
        )

        val remoteItemClasses = manifest.stringLists[FxSymbolKeys.LIST_REMOTE_ITEM_CLASSES]
            .orEmpty()
            .mapNotNull { DexSymbolCache.resolveClass(cl, it) }

        val viewerProgressBarField = fld(FxSymbolKeys.FLD_VIEWER_PROGRESS_BAR)
            ?: viewerClass?.declaredFields?.singleOrNull { it.type == ProgressBar::class.java }
                ?.apply { isAccessible = true }

        val viewer = ViewerPrefetchBindings(
            viewerBaseClass = viewerBaseClass,
            mirrorRemoteTempFile = mtd(FxSymbolKeys.MTD_MIRROR_REMOTE_TEMP_FILE),
            viewerOnCreate = mtd(FxSymbolKeys.MTD_VIEWER_ON_CREATE)
                ?: declaredMethod(viewerClass, "onCreate", android.os.Bundle::class.java),
            viewerOnDestroy = mtd(FxSymbolKeys.MTD_VIEWER_ON_DESTROY)
                ?: viewerBaseClass?.let { declaredMethod(it, "onDestroy") },
            viewerLoadMediaAndBitmap = mtd(FxSymbolKeys.MTD_VIEWER_LOAD_MEDIA_AND_BITMAP),
            viewerLoadMedia = mtd(FxSymbolKeys.MTD_VIEWER_LOAD_MEDIA),
            viewerLoadBitmap = mtd(FxSymbolKeys.MTD_VIEWER_LOAD_BITMAP),
            viewerLoadGif = mtd(FxSymbolKeys.MTD_VIEWER_LOAD_GIF),
            viewerOpenItemStream = mtd(FxSymbolKeys.MTD_VIEWER_OPEN_ITEM_STREAM),
            viewerDecodeItem = mtd(FxSymbolKeys.MTD_VIEWER_DECODE_ITEM),
            viewerStreamProviderRead = mtd(FxSymbolKeys.MTD_VIEWER_STREAM_PROVIDER_READ),
            viewerStreamProviderItemField = fld(FxSymbolKeys.FLD_VIEWER_STREAM_PROVIDER_ITEM),
            viewerOpenStreamMethod = mtd(FxSymbolKeys.MTD_VIEWER_OPEN_STREAM),
            taskThreadCtor = ctor(FxSymbolKeys.CTR_TASK_THREAD),
            viewerProgressBarField = viewerProgressBarField,
            remoteItemClasses = remoteItemClasses
        )

        val thumbCallbackClass = cls(FxSymbolKeys.CLS_THUMB_CALLBACK)
        val thumbCallbackEnqueue = mtd(FxSymbolKeys.MTD_THUMB_CB_ENQUEUE)
            ?: thumbCallbackClass?.declaredMethods?.singleOrNull { it.parameterTypes.size == 1 }
                ?.apply { isAccessible = true }

        val remoteThumb = RemoteThumbBindings(
            loadThumbnail = mtd(FxSymbolKeys.MTD_LOAD_THUMBNAIL),
            thumbCallbackClass = thumbCallbackClass,
            thumbCallbackEnqueueMethod = thumbCallbackEnqueue,
            thumbTaskItemField = fld(FxSymbolKeys.FLD_THUMB_TASK_ITEM),
            thumbTaskContextField = fld(FxSymbolKeys.FLD_THUMB_TASK_CONTEXT),
            thumbTaskGetCacheFile = mtd(FxSymbolKeys.MTD_THUMB_TASK_GET_CACHE_FILE),
            remoteThumbTaskCtor = ctor(FxSymbolKeys.CTR_REMOTE_THUMB_TASK),
            remoteThumbTaskMimeField = fld(FxSymbolKeys.FLD_REMOTE_THUMB_TASK_MIME),
            remoteThumbWorkerRun = mtd(FxSymbolKeys.MTD_REMOTE_THUMB_WORKER_RUN),
            remoteThumbWorkerCaseField = fld(FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_CASE),
            remoteThumbWorkerCaseId = manifest.ints[FxSymbolKeys.INT_REMOTE_THUMB_WORKER_CASE_ID] ?: 2,
            remoteThumbWorkerOwnerField = fld(FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_OWNER),
            remoteThumbWorkerResultField = fld(FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_RESULT),
            thumbResultField = fld(FxSymbolKeys.FLD_THUMB_RESULT),
            thumbGeneratorCreateForFile = mtd(FxSymbolKeys.MTD_THUMB_GEN_CREATE_FOR_FILE),
            thumbGeneratorFlagsField = fld(FxSymbolKeys.FLD_THUMB_GEN_FLAGS),
            thumbGeneratorContextField = fld(FxSymbolKeys.FLD_THUMB_GEN_CONTEXT),
            thumbGeneratorStreamField = fld(FxSymbolKeys.FLD_THUMB_GEN_STREAM),
            thumbGeneratorOutFileField = fld(FxSymbolKeys.FLD_THUMB_GEN_OUT_FILE),
            thumbGeneratorSourcePathField = fld(FxSymbolKeys.FLD_THUMB_GEN_SOURCE_PATH),
            thumbGeneratorStreamRead = mtd(FxSymbolKeys.MTD_THUMB_GEN_STREAM_READ),
            thumbImageInfoCtor = ctor(FxSymbolKeys.CTR_THUMB_IMAGE_INFO),
            thumbGeneratorRun = mtd(FxSymbolKeys.MTD_THUMB_GEN_RUN),
            thumbGeneratorExceedsCap = mtd(FxSymbolKeys.MTD_THUMB_GEN_EXCEEDS_CAP),
            thumbGeneratorGenerate = mtd(FxSymbolKeys.MTD_THUMB_GEN_GENERATE),
            thumbGeneratorClass = cls(FxSymbolKeys.CLS_THUMB_GENERATOR),
            dimWidthField = fld(FxSymbolKeys.FLD_DIM_WIDTH),
            dimHeightField = fld(FxSymbolKeys.FLD_DIM_HEIGHT),
            settingsGetInstance = mtd(FxSymbolKeys.MTD_SETTINGS_GET_INSTANCE),
            settingsThumbsEnabled = mtd(FxSymbolKeys.MTD_SETTINGS_THUMBS_ENABLED),
            settingsThumbsForPath = mtd(FxSymbolKeys.MTD_SETTINGS_THUMBS_FOR_PATH),
            remoteItemMarkerClass = cls(FxSymbolKeys.CLS_REMOTE_ITEM_MARKER),
            cloudThumbMarkerClass = cls(FxSymbolKeys.CLS_CLOUD_THUMB_MARKER),
            dropboxSupportsThumb = mtd(FxSymbolKeys.MTD_DROPBOX_SUPPORTS_THUMB)
        )

        val fxDialogCtor = ctor(FxSymbolKeys.CTR_FX_DIALOG)
        val dialogClass = fxDialogCtor?.declaringClass ?: findDialogClass(cl)
        val resolvedDialogCtor = fxDialogCtor ?: dialogClass?.declaredConstructors?.firstOrNull { c ->
            val p = c.parameterTypes
            p.size == 2 && p[0] == Context::class.java && p[1].isEnum
        }?.apply { isAccessible = true }
        val fxDialogStyleWithClose = runCatching {
            fld(FxSymbolKeys.CLS_FX_DIALOG_STYLE_WITH_CLOSE_FIELD)?.get(null)
        }.getOrNull()
            ?: cls(FxSymbolKeys.CLS_FX_DIALOG_STYLE_WITH_CLOSE_ENUM)?.enumConstants?.getOrNull(1)
            ?: resolvedDialogCtor?.parameterTypes?.getOrNull(1)?.enumConstants?.getOrNull(1)

        val settings = NativeSettingsBindings(
            mainPrefOnCreate = mtd(FxSymbolKeys.MTD_MAIN_PREF_ON_CREATE)
                ?: declaredMethod(mainPrefClass, "onCreate", android.os.Bundle::class.java),
            mainPrefOnResume = mtd(FxSymbolKeys.MTD_MAIN_PREF_ON_RESUME)
                ?: declaredMethod(mainPrefClass, "onResume"),
            labelPreferenceCtor = ctor(FxSymbolKeys.CTR_LABEL_PREFERENCE)
                ?: declaredConstructor(labelPrefClass, Context::class.java),
            fxDialogCtor = resolvedDialogCtor,
            fxDialogDefaultWithClose = fxDialogStyleWithClose,
            fxDialogSetHeader = mtd(FxSymbolKeys.MTD_FX_DIALOG_SET_HEADER)
                ?: declaredMethod(dialogClass, "setHeader", CharSequence::class.java),
            fxDialogSetDescription = mtd(FxSymbolKeys.MTD_FX_DIALOG_SET_DESCRIPTION)
                ?: declaredMethod(dialogClass, "setDescription", CharSequence::class.java),
            fxDialogGetDefaultContentLayout = mtd(FxSymbolKeys.MTD_FX_DIALOG_GET_DEFAULT_LAYOUT)
                ?: declaredMethod(dialogClass, "getDefaultContentLayout"),
            fxDialogAddLabeledView = mtd(FxSymbolKeys.MTD_FX_DIALOG_ADD_LABELED_VIEW)
                ?: declaredMethod(dialogClass, "addLabeledView", CharSequence::class.java, View::class.java),
            fxDialogIsBackgroundLight = mtd(FxSymbolKeys.MTD_FX_DIALOG_IS_BG_LIGHT)
                ?: declaredMethod(dialogClass, "isBackgroundLight"),
            fxDialogDismiss = mtd(FxSymbolKeys.MTD_FX_DIALOG_DISMISS)
                ?: declaredMethod(dialogClass, "dismiss"),
            fxDialogShow = mtd(FxSymbolKeys.MTD_FX_DIALOG_SHOW)
                ?: dialogClass?.superclass?.let { declaredMethod(it, "show") },
            fxDialogUiField = fld(FxSymbolKeys.FLD_FX_DIALOG_UI)
                ?: declaredField(dialogClass, "ui"),
            uiGetForContext = mtd(FxSymbolKeys.MTD_UI_GET_FOR_CONTEXT),
            uiIsWindowLightField = fld(FxSymbolKeys.FLD_UI_IS_WINDOW_LIGHT),
            uiTextPrimaryField = fld(FxSymbolKeys.FLD_UI_TEXT_PRIMARY),
            uiTextSecondaryField = fld(FxSymbolKeys.FLD_UI_TEXT_SECONDARY),
            uiBoxBgField = fld(FxSymbolKeys.FLD_UI_BOX_BG),
            uiBoxPressedBgField = fld(FxSymbolKeys.FLD_UI_BOX_PRESSED_BG),
            uiWindowBgMethod = mtd(FxSymbolKeys.MTD_UI_WINDOW_BG),
            uiTrimAccentMethod = mtd(FxSymbolKeys.MTD_UI_TRIM_ACCENT),
            uiTrimBaseMethod = mtd(FxSymbolKeys.MTD_UI_TRIM_BASE)
        )

        val fileBaseClass = cls(FxSymbolKeys.CLS_FILE_NODE_BASE)
        val androidData = AndroidDataBindings(
            fileCatalogClass = fileCatalogClass,
            fileCatalogOpenDir = mtd(FxSymbolKeys.MTD_FILE_CATALOG_OPEN_DIR),
            fileCatalogResolveFile = mtd(FxSymbolKeys.MTD_FILE_CATALOG_RESOLVE_FILE),
            fileCatalogSafUriField = fld(FxSymbolKeys.FLD_FILE_CATALOG_SAF_URI),
            fileDirNodeClass = cls(FxSymbolKeys.CLS_FILE_DIR_NODE),
            fileDirListChildren = mtd(FxSymbolKeys.MTD_FILE_DIR_LIST_CHILDREN),
            fileDirMkdir = mtd(FxSymbolKeys.MTD_FILE_DIR_MKDIR),
            fileDirChildAvailable = mtd(FxSymbolKeys.MTD_FILE_DIR_CHILD_AVAILABLE),
            fileDirDeleteRecursive = mtd(FxSymbolKeys.MTD_FILE_DIR_DELETE_RECURSIVE),
            fileDirStatVolume = mtd(FxSymbolKeys.MTD_FILE_DIR_STAT_VOLUME),
            fileDirClearCache = mtd(FxSymbolKeys.MTD_FILE_DIR_CLEAR_CACHE),
            fileDirCachedFilesField = fld(FxSymbolKeys.FLD_FILE_DIR_CACHED_FILES),
            fileFileNodeClass = cls(FxSymbolKeys.CLS_FILE_FILE_NODE),
            fileFileCtor = ctor(FxSymbolKeys.CTR_FILE_FILE),
            fileFileStatSize = mtd(FxSymbolKeys.MTD_FILE_FILE_STAT_SIZE),
            fileFileCachedSizeField = fld(FxSymbolKeys.FLD_FILE_FILE_CACHED_SIZE),
            fileFileOpenInput = mtd(FxSymbolKeys.MTD_FILE_FILE_OPEN_INPUT),
            fileFileOpenOutputInternal = mtd(FxSymbolKeys.MTD_FILE_FILE_OPEN_OUTPUT_INTERNAL),
            fileFileDelete = mtd(FxSymbolKeys.MTD_FILE_FILE_DELETE),
            fileNodeBaseClass = fileBaseClass,
            fileNodeDeleteSingle = mtd(FxSymbolKeys.MTD_FILE_NODE_DELETE_SINGLE),
            fileNodeRename = mtd(FxSymbolKeys.MTD_FILE_NODE_RENAME),
            fileNodeMoveTo = mtd(FxSymbolKeys.MTD_FILE_NODE_MOVE_TO),
            fileNodeGetLastModified = mtd(FxSymbolKeys.MTD_FILE_NODE_GET_LAST_MODIFIED)
                ?: declaredMethod(fileBaseClass, "getLastModified"),
            fileNodeIsReadOnly = mtd(FxSymbolKeys.MTD_FILE_NODE_IS_READ_ONLY)
                ?: declaredMethod(fileBaseClass, "isReadOnly"),
            fileNodeSetLastModified = mtd(FxSymbolKeys.MTD_FILE_NODE_SET_LAST_MODIFIED),
            fileNodeCatalogField = fld(FxSymbolKeys.FLD_FILE_NODE_CATALOG),
            fileNodePathField = fld(FxSymbolKeys.FLD_FILE_NODE_PATH),
            fileNodeJavaFileField = fld(FxSymbolKeys.FLD_FILE_NODE_JAVA_FILE),
            fileNodeMetricsField = fld(FxSymbolKeys.FLD_FILE_NODE_METRICS),
            fxPathChildCtor = ctor(FxSymbolKeys.CTR_FX_PATH_CHILD),
            fxPathFindElement = mtd(FxSymbolKeys.MTD_FX_PATH_FIND_ELEMENT),
            hiddenFileStoreIsHidden = mtd(FxSymbolKeys.MTD_HIDDEN_FILE_STORE_IS_HIDDEN),
            fileDirCtor9012 = ctor(FxSymbolKeys.CTR_FILE_DIR_9012),
            userExceptionClass = cls(FxSymbolKeys.CLS_USER_EXCEPTION),
            cancelExceptionClass = cls(FxSymbolKeys.CLS_CANCEL_EXCEPTION)
        )

        val bonusLimits = BonusLimitsBindings(
            textViewerLoad = mtd(FxSymbolKeys.MTD_TEXT_VIEWER_LOAD),
            textViewerApply = mtd(FxSymbolKeys.MTD_TEXT_VIEWER_APPLY),
            textViewerOpenBinaryXml = mtd(FxSymbolKeys.MTD_TEXT_VIEWER_OPEN_BINARY_XML),
            textViewerWorkerRun = mtd(FxSymbolKeys.MTD_TEXT_VIEWER_WORKER_RUN),
            textViewerWorkerCaseField = fld(FxSymbolKeys.FLD_TEXT_VIEWER_WORKER_CASE),
            textViewerWorkerCaseId = manifest.ints[FxSymbolKeys.INT_TEXT_VIEWER_WORKER_CASE_ID] ?: 0,
            textViewerWorkerActivityField = fld(FxSymbolKeys.FLD_TEXT_VIEWER_WORKER_ACTIVITY),
            textViewerWorkerCtor = ctor(FxSymbolKeys.CTR_TEXT_VIEWER_WORKER),
            textViewerLinesField = fld(FxSymbolKeys.FLD_TEXT_VIEWER_LINES),
            textViewerCharsetField = fld(FxSymbolKeys.FLD_TEXT_VIEWER_CHARSET),
            textViewerTruncatedField = fld(FxSymbolKeys.FLD_TEXT_VIEWER_TRUNCATED),
            textViewerUnsupportedField = fld(FxSymbolKeys.FLD_TEXT_VIEWER_UNSUPPORTED),
            viewerSupportHandlerField = fld(FxSymbolKeys.FLD_VIEWER_SUPPORT_HANDLER),
            viewerSupportShowError = mtd(FxSymbolKeys.MTD_VIEWER_SUPPORT_SHOW_ERROR),
            encodingStreamCtor = ctor(FxSymbolKeys.CTR_ENCODING_STREAM),
            encodingStreamDetect = mtd(FxSymbolKeys.MTD_ENCODING_STREAM_DETECT),
            encodingStreamGetCharset = mtd(FxSymbolKeys.MTD_ENCODING_STREAM_GET_CHARSET),
            encodingStreamCharsetField = fld(FxSymbolKeys.FLD_ENCODING_STREAM_CHARSET),
            encodingStreamGetBuffer = mtd(FxSymbolKeys.MTD_ENCODING_STREAM_GET_BUFFER),
            encodingStreamBufferField = fld(FxSymbolKeys.FLD_ENCODING_STREAM_BUFFER),
            mimeProbeBytes = mtd(FxSymbolKeys.MTD_MIME_PROBE_BYTES),
            textEditReadStream = mtd(FxSymbolKeys.MTD_TEXT_EDIT_READ_STREAM),
            textEditResultCtor = ctor(FxSymbolKeys.CTR_TEXT_EDIT_RESULT),
            textEditResultCaseId = manifest.ints[FxSymbolKeys.INT_TEXT_EDIT_RESULT_CASE_ID] ?: 7,
            execLoadPreview = mtd(FxSymbolKeys.MTD_EXEC_LOAD_PREVIEW),
            execApplyPreview = mtd(FxSymbolKeys.MTD_EXEC_APPLY_PREVIEW),
            execWorkerRun = mtd(FxSymbolKeys.MTD_EXEC_WORKER_RUN),
            execWorkerCaseField = fld(FxSymbolKeys.FLD_EXEC_WORKER_CASE),
            execWorkerCaseId = manifest.ints[FxSymbolKeys.INT_EXEC_WORKER_CASE_ID] ?: 19,
            execWorkerArg1Field = fld(FxSymbolKeys.FLD_EXEC_WORKER_ARG1),
            execWorkerArg2Field = fld(FxSymbolKeys.FLD_EXEC_WORKER_ARG2),
            execUiRunnableCtor = ctor(FxSymbolKeys.CTR_EXEC_UI_RUNNABLE),
            execUiRunnableCaseId = manifest.ints[FxSymbolKeys.INT_EXEC_UI_RUNNABLE_CASE_ID] ?: 6,
            execLinesField = fld(FxSymbolKeys.FLD_EXEC_LINES),
            execCharsetField = fld(FxSymbolKeys.FLD_EXEC_CHARSET),
            execUnsupportedField = fld(FxSymbolKeys.FLD_EXEC_UNSUPPORTED)
        )

        val folderLifecycle = FolderLifecycleBindings(
            explorerActivityClass = explorerActivityClass,
            explorerOnResume = mtd(FxSymbolKeys.MTD_EXPLORER_ON_RESUME)
                ?: declaredMethod(explorerActivityClass, "onResume"),
            explorerOnDestroy = mtd(FxSymbolKeys.MTD_EXPLORER_ON_DESTROY)
                ?: declaredMethod(explorerActivityClass, "onDestroy"),
            windowManagerField = fld(FxSymbolKeys.FLD_WINDOW_MANAGER),
            windowModelsField = fld(FxSymbolKeys.FLD_WINDOW_MODELS),
            windowModelCloseMethod = mtd(FxSymbolKeys.MTD_WINDOW_MODEL_CLOSE),
            windowModelSetPathMethod = mtd(FxSymbolKeys.MTD_WINDOW_MODEL_SET_PATH),
            windowModelCurrentContentMethod = mtd(FxSymbolKeys.MTD_WINDOW_MODEL_CURRENT_CONTENT),
            contentModelPathField = fld(FxSymbolKeys.FLD_CONTENT_MODEL_PATH),
            fxPathClass = cls(FxSymbolKeys.CLS_FX_PATH),
            fxPathElementsField = fld(FxSymbolKeys.FLD_FX_PATH_ELEMENTS),
            fxPathParentMethod = mtd(FxSymbolKeys.MTD_FX_PATH_PARENT),
            dirCatalogClass = dirCatalogClass
        )

        val menuDividerClass = cls(FxSymbolKeys.CLS_MENU_DIVIDER)
        val menuDividerCtor = ctor(FxSymbolKeys.CTR_MENU_DIVIDER)
            ?: declaredConstructor(menuDividerClass)

        val homeMenu = HomeMenuBindings(
            homeContentViewClass = homeContentViewClass,
            homeViewActivityField = fld(FxSymbolKeys.FLD_HOME_VIEW_ACTIVITY)
                ?: declaredField(homeContentViewClass, "activity"),
            homeViewResField = fld(FxSymbolKeys.FLD_HOME_VIEW_RES)
                ?: declaredField(homeContentViewClass, "res"),
            menuContribClass = cls(FxSymbolKeys.CLS_MENU_CONTRIB),
            menuContribCaseField = fld(FxSymbolKeys.FLD_MENU_CONTRIB_CASE),
            menuContribCaseId = manifest.ints[FxSymbolKeys.INT_MENU_CONTRIB_CASE_ID] ?: 14,
            menuContribOwnerField = fld(FxSymbolKeys.FLD_MENU_CONTRIB_OWNER),
            menuContribBgLightField = fld(FxSymbolKeys.FLD_MENU_CONTRIB_BG_LIGHT),
            menuContribPopulateMethod = mtd(FxSymbolKeys.MTD_MENU_CONTRIB_POPULATE),
            menuModelAddItemMethod = mtd(FxSymbolKeys.MTD_MENU_MODEL_ADD_ITEM),
            menuModelSizeMethod = mtd(FxSymbolKeys.MTD_MENU_MODEL_SIZE),
            menuModelItemMethod = mtd(FxSymbolKeys.MTD_MENU_MODEL_ITEM),
            menuModelItemsListField = fld(FxSymbolKeys.FLD_MENU_MODEL_ITEMS_LIST),
            menuItemGetTitleMethod = mtd(FxSymbolKeys.MTD_MENU_ITEM_GET_TITLE),
            menuDividerClass = menuDividerClass,
            menuDividerCtor = menuDividerCtor,
            menuActionCtor = ctor(FxSymbolKeys.CTR_MENU_ACTION),
            menuClickListenerInterface = cls(FxSymbolKeys.CLS_MENU_CLICK_LISTENER),
            actionIconsCreateMethod = mtd(FxSymbolKeys.MTD_ACTION_ICONS_CREATE)
        )

        val googleDrive = GoogleDriveBindings(
            authActivityOnCreate = mtd(FxSymbolKeys.MTD_GDRIVE_AUTH_ON_CREATE)
                ?: declaredMethod(gdriveAuthClass, "onCreate", android.os.Bundle::class.java),
            getClientId9012 = mtd(FxSymbolKeys.MTD_GDRIVE_GET_CLIENT_ID_9012),
            tokenCredsCtor9012 = ctor(FxSymbolKeys.CTR_GDRIVE_TOKEN_CREDS_9012),
            authReqBuilderCtor9108 = ctor(FxSymbolKeys.CTR_GDRIVE_AUTH_REQ_9108),
            authReqClientIdField9108 = fld(FxSymbolKeys.FLD_GDRIVE_AUTH_REQ_CLIENT_ID_9108),
            tokenCredsCtor9108 = ctor(FxSymbolKeys.CTR_GDRIVE_TOKEN_CREDS_9108),
            tokenCredsClientIdField9108 = fld(FxSymbolKeys.FLD_GDRIVE_TOKEN_CREDS_CLIENT_ID_9108),
            tokenCredsClientSecretField9108 = fld(FxSymbolKeys.FLD_GDRIVE_TOKEN_CREDS_SECRET_9108)
        )

        val hardwareRender = HardwareRenderBindings(
            touchClass = touchClass,
            setImageBitmap = mtd(FxSymbolKeys.MTD_HW_SET_IMAGE_BITMAP)
                ?: declaredMethod(touchClass, "setImageBitmap", Bitmap::class.java),
            setImageMovie = mtd(FxSymbolKeys.MTD_HW_SET_IMAGE_MOVIE)
                ?: declaredMethod(touchClass, "setImageMovie", Movie::class.java),
            setImageNull = mtd(FxSymbolKeys.MTD_HW_SET_IMAGE_NULL)
                ?: declaredMethod(touchClass, "setImageNull"),
            setPrescale = mtd(FxSymbolKeys.MTD_HW_SET_PRESCALE)
                ?: declaredMethod(touchClass, "setPrescale", Float::class.javaPrimitiveType),
            setTouchEnabled = mtd(FxSymbolKeys.MTD_HW_SET_TOUCH_ENABLED)
                ?: declaredMethod(touchClass, "setTouchEnabled", Boolean::class.javaPrimitiveType),
            zoomFit = mtd(FxSymbolKeys.MTD_HW_ZOOM_FIT)
                ?: declaredMethod(touchClass, "zoomFit"),
            onDraw = mtd(FxSymbolKeys.MTD_HW_ON_DRAW)
                ?: declaredMethod(touchClass, "onDraw", Canvas::class.java),
            onDetachedFromWindow = mtd(FxSymbolKeys.MTD_HW_ON_DETACHED)
                ?: declaredMethod(touchClass, "onDetachedFromWindow"),
            updateBoundsMethod = mtd(FxSymbolKeys.MTD_HW_UPDATE_BOUNDS),
            enforceLayerOnBitmapMethod = mtd(FxSymbolKeys.MTD_HW_ENFORCE_LAYER),
            bitmapField = fld(FxSymbolKeys.FLD_HW_BITMAP)
                ?: touchClass?.declaredFields?.singleOrNull { it.type == Bitmap::class.java }?.apply { isAccessible = true },
            movieField = fld(FxSymbolKeys.FLD_HW_MOVIE)
                ?: touchClass?.declaredFields?.singleOrNull { it.type == Movie::class.java }?.apply { isAccessible = true },
            moviePlayingField = fld(FxSymbolKeys.FLD_HW_MOVIE_PLAYING),
            movieThreadField = fld(FxSymbolKeys.FLD_HW_MOVIE_THREAD)
                ?: touchClass?.declaredFields?.singleOrNull { it.type == Thread::class.java }?.apply { isAccessible = true },
            matrixField = fld(FxSymbolKeys.FLD_HW_MATRIX)
                ?: touchClass?.declaredFields?.singleOrNull { it.type == Matrix::class.java }?.apply { isAccessible = true },
            paintField = fld(FxSymbolKeys.FLD_HW_PAINT),
            cropArField = fld(FxSymbolKeys.FLD_HW_CROP_AR),
            cropRectField = fld(FxSymbolKeys.FLD_HW_CROP_RECT)
                ?: touchClass?.declaredFields?.singleOrNull { it.type == Rect::class.java }?.apply { isAccessible = true },
            softwareLayerFlagField = fld(FxSymbolKeys.FLD_HW_SOFTWARE_LAYER),
            displaySizeField = fld(FxSymbolKeys.FLD_HW_DISPLAY_SIZE),
            displaySizeWidthField = fld(FxSymbolKeys.FLD_DIM_WIDTH),
            displaySizeHeightField = fld(FxSymbolKeys.FLD_DIM_HEIGHT),
            needsZoomFitField = fld(FxSymbolKeys.FLD_HW_NEEDS_ZOOM_FIT),
            imageDisplayClass = imageDisplayClass,
            deliverBitmapMethod = mtd(FxSymbolKeys.MTD_HW_DELIVER_BITMAP),
            deliverMovieMethod = mtd(FxSymbolKeys.MTD_HW_DELIVER_MOVIE),
            clearDisplayMethod = mtd(FxSymbolKeys.MTD_HW_CLEAR_DISPLAY),
            displayBitmapField = fld(FxSymbolKeys.FLD_HW_DISPLAY_BITMAP)
                ?: imageDisplayClass?.declaredFields?.singleOrNull { it.type == Bitmap::class.java }?.apply { isAccessible = true },
            displayMovieField = fld(FxSymbolKeys.FLD_HW_DISPLAY_MOVIE)
                ?: imageDisplayClass?.declaredFields?.singleOrNull { it.type == Movie::class.java }?.apply { isAccessible = true },
            displayDisposedField = fld(FxSymbolKeys.FLD_HW_DISPLAY_DISPOSED),
            displayFullLoadedField = fld(FxSymbolKeys.FLD_HW_DISPLAY_FULL_LOADED),
            displayPreviewLoadedField = fld(FxSymbolKeys.FLD_HW_DISPLAY_PREVIEW_LOADED),
            viewerClass = viewerClass,
            loadMediaMethod = mtd(FxSymbolKeys.MTD_VIEWER_LOAD_MEDIA)
                ?: mtd(FxSymbolKeys.MTD_VIEWER_LOAD_MEDIA_AND_BITMAP),
            loadGifMethod = mtd(FxSymbolKeys.MTD_VIEWER_LOAD_GIF),
            applyBitmapResultMethod = mtd(FxSymbolKeys.MTD_HW_APPLY_BITMAP_RESULT),
            imageResultCtor = ctor(FxSymbolKeys.CTR_HW_IMAGE_RESULT),
            loadTokenField = fld(FxSymbolKeys.FLD_HW_LOAD_TOKEN),
            isGifField = fld(FxSymbolKeys.FLD_HW_IS_GIF),
            currentHolderField = fld(FxSymbolKeys.FLD_VIEWER_CURRENT_HOLDER),
            enterTransitionField = fld(FxSymbolKeys.FLD_HW_ENTER_TRANSITION),
            transitionBgViewField = fld(FxSymbolKeys.FLD_HW_TRANSITION_BG_VIEW),
            transitionTopBarField = fld(FxSymbolKeys.FLD_HW_TRANSITION_TOP_BAR),
            transitionBottomBarField = fld(FxSymbolKeys.FLD_HW_TRANSITION_BOTTOM_BAR),
            transitionRunningField = fld(FxSymbolKeys.FLD_HW_TRANSITION_RUNNING),
            transitionDoneField = fld(FxSymbolKeys.FLD_HW_TRANSITION_DONE),
            uiHandlerField = fld(FxSymbolKeys.FLD_VIEWER_SUPPORT_HANDLER),
            holderFileItemField = fld(FxSymbolKeys.FLD_ITEM_HOLDER_FILE_ITEM),
            holderUriField = fld(FxSymbolKeys.FLD_ITEM_HOLDER_URI),
            gifAnimatedCheckMethod = mtd(FxSymbolKeys.MTD_HW_GIF_ANIMATED_CHECK),
            localPathClass = cls(FxSymbolKeys.CLS_LOCAL_PATH_CLASS),
            localPathMethod = mtd(FxSymbolKeys.MTD_HW_LOCAL_PATH)
        )

        return ResolvedSymbols(
            profile = FxProfile.DEXKIT_DYNAMIC,
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
            googleDrive = googleDrive,
            hardwareRender = hardwareRender,
            symbolResolutionSource = source
        )
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
            siblingCallbackFailMethod = declaredMethod(siblingCbImplClass, "b"),
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
            viewerOpenStreamMethod = declaredMethod(viewerBaseClass, "W"),
            taskThreadCtor = declaredConstructor(loadClass(cl, "i9.e"), Class::class.java, String::class.java, Runnable::class.java),
            viewerProgressBarField = declaredField(viewerClass, "R5"),
            remoteItemClasses = remoteItemClasses
        )

        val thumbRouterClass = loadClass(cl, "dd.h")
        val thumbCallbackClass = loadClass(cl, "dd.l")
        val thumbCallbackPayloadClass = loadClass(cl, "dd.f")
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
            thumbCallbackEnqueueMethod = declaredMethod(thumbCallbackClass, "a", thumbCallbackPayloadClass),
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
            dimWidthField = declaredField(imgDimClass, "f"),
            dimHeightField = declaredField(imgDimClass, "i"),
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

        val touchClass = loadClass(cl, "nextapp.maui.ui.imageview.TouchImageDisplay")
        val transitionClass = loadClass(cl, "nextapp.fx.ui.viewer.image.f0")
        val gifProbeClass = loadClass(cl, "a9.e")
        val localInterface = loadClass(cl, "ue.a0")
        val localPathProvider = loadClass(cl, "ue.b0")

        val hardwareRender = HardwareRenderBindings(
            touchClass = touchClass,
            setImageBitmap = declaredMethod(touchClass, "setImageBitmap", Bitmap::class.java),
            setImageMovie = declaredMethod(touchClass, "setImageMovie", Movie::class.java),
            setImageNull = declaredMethod(touchClass, "setImageNull"),
            setPrescale = declaredMethod(touchClass, "setPrescale", Float::class.javaPrimitiveType),
            setTouchEnabled = declaredMethod(touchClass, "setTouchEnabled", Boolean::class.javaPrimitiveType),
            zoomFit = declaredMethod(touchClass, "zoomFit"),
            onDraw = declaredMethod(touchClass, "onDraw", Canvas::class.java),
            onDetachedFromWindow = declaredMethod(touchClass, "onDetachedFromWindow"),
            updateBoundsMethod = declaredMethod(
                touchClass,
                "w",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ),
            enforceLayerOnBitmapMethod = declaredMethod(touchClass, "g", Bitmap::class.java),
            bitmapField = declaredField(touchClass, "Q5"),
            movieField = declaredField(touchClass, "R5"),
            moviePlayingField = declaredField(touchClass, "S5"),
            movieThreadField = declaredField(touchClass, "f"),
            matrixField = declaredField(touchClass, "V5"),
            paintField = declaredField(touchClass, "H5"),
            cropArField = declaredField(touchClass, "f5"),
            cropRectField = declaredField(touchClass, "c5"),
            softwareLayerFlagField = declaredField(touchClass, "h5"),
            displaySizeField = declaredField(touchClass, "k5"),
            displaySizeWidthField = remoteThumb.dimWidthField,
            displaySizeHeightField = remoteThumb.dimHeightField,
            needsZoomFitField = declaredField(touchClass, "l5"),
            imageDisplayClass = imageDisplayClass,
            deliverBitmapMethod = declaredMethod(
                imageDisplayClass,
                "U",
                Bitmap::class.java,
                Boolean::class.javaPrimitiveType
            ),
            deliverMovieMethod = declaredMethod(imageDisplayClass, "V", Movie::class.java),
            clearDisplayMethod = declaredMethod(imageDisplayClass, "W"),
            displayBitmapField = declaredField(imageDisplayClass, "a6"),
            displayMovieField = declaredField(imageDisplayClass, "b6"),
            displayDisposedField = declaredField(imageDisplayClass, "f6"),
            displayFullLoadedField = declaredField(imageDisplayClass, "g6"),
            displayPreviewLoadedField = declaredField(imageDisplayClass, "h6"),
            viewerClass = viewerClass,
            loadMediaMethod = viewer.viewerLoadMedia,
            loadGifMethod = viewer.viewerLoadGif,
            applyBitmapResultMethod = null,
            imageResultCtor = null,
            loadTokenField = declaredField(viewerClass, "x5"),
            isGifField = declaredField(viewerClass, "P5"),
            currentHolderField = sibling.viewerCurrentHolderField,
            enterTransitionField = declaredField(viewerClass, "y5"),
            transitionBgViewField = declaredField(transitionClass, "c"),
            transitionTopBarField = declaredField(transitionClass, "d"),
            transitionBottomBarField = declaredField(transitionClass, "e"),
            transitionRunningField = declaredField(transitionClass, "h"),
            transitionDoneField = declaredField(transitionClass, "g"),
            uiHandlerField = bonusLimits.viewerSupportHandlerField,
            holderFileItemField = sibling.itemHolderFileItemField,
            holderUriField = sibling.itemHolderUriField,
            gifAnimatedCheckMethod = declaredMethod(gifProbeClass, "a", InputStream::class.java),
            localPathClass = localInterface,
            localPathMethod = declaredMethod(localPathProvider, "h")
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
            googleDrive = googleDrive,
            hardwareRender = hardwareRender,
            symbolResolutionSource = "static:9012"
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
            viewerOpenStreamMethod = declaredMethod(viewerBaseClass, "w"),
            taskThreadCtor = declaredConstructor(loadClass(cl, "bb.d"), Class::class.java, String::class.java, Runnable::class.java),
            viewerProgressBarField = declaredField(viewerClass, "N2"),
            remoteItemClasses = remoteItemClasses
        )

        val thumbRouterClass = loadClass(cl, "wg.e")
        val thumbCallbackClass = loadClass(cl, "kf.k")
        val thumbCallbackPayloadClass = loadClass(cl, "kf.f")
        val thumbCacheClass = loadClass(cl, "kf.d")
        val thumbBaseClass = loadClass(cl, "kf.b")
        val remoteThumbTaskClass = loadClass(cl, "kf.e")
        val remoteThumbWorkerClass = loadClass(cl, "be.k0")
        val thumbGenClass = loadClass(cl, "pc.c")
        val thumbStreamSrcClass = loadClass(cl, "db.d")
        val thumbInfoClass = loadClass(cl, "nc.j")
        val imgDimClass = loadClass(cl, "pa.c")
        val settingsClass = loadClass(cl, "mb.l")
        val dropboxClass = loadClass(cl, "ad.c")
        val resultBoxClass = loadClass(cl, "a4.c")

        val remoteThumb = RemoteThumbBindings(
            loadThumbnail = declaredMethod(thumbRouterClass, "j", Context::class.java, fileItemBaseClass, Int::class.javaPrimitiveType, thumbCallbackClass, thumbCacheClass, Boolean::class.javaPrimitiveType),
            thumbCallbackClass = thumbCallbackClass,
            thumbCallbackEnqueueMethod = declaredMethod(thumbCallbackClass, "a", thumbCallbackPayloadClass),
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
            dimWidthField = declaredField(imgDimClass, "f"),
            dimHeightField = declaredField(imgDimClass, "i"),
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

        val touchClass = loadClass(cl, "nextapp.maui.ui.imageview.TouchImageDisplay")
        val imageResultClass = loadClass(cl, "ua.a")
        val transitionClass = loadClass(cl, "nextapp.fx.ui.viewer.image.u")
        val gifProbeClass = loadClass(cl, "ua.b")
        val localInterface = loadClass(cl, "kh.c0")
        val localPathProvider = loadClass(cl, "kh.d0")

        val hardwareRender = HardwareRenderBindings(
            touchClass = touchClass,
            setImageBitmap = declaredMethod(touchClass, "setImageBitmap", Bitmap::class.java),
            setImageMovie = declaredMethod(touchClass, "setImageMovie", Movie::class.java),
            setImageNull = declaredMethod(touchClass, "setImageNull"),
            setPrescale = declaredMethod(touchClass, "setPrescale", Float::class.javaPrimitiveType),
            setTouchEnabled = declaredMethod(touchClass, "setTouchEnabled", Boolean::class.javaPrimitiveType),
            zoomFit = declaredMethod(touchClass, "zoomFit"),
            onDraw = declaredMethod(touchClass, "onDraw", Canvas::class.java),
            onDetachedFromWindow = declaredMethod(touchClass, "onDetachedFromWindow"),
            updateBoundsMethod = declaredMethod(
                touchClass,
                "h",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ),
            enforceLayerOnBitmapMethod = null,
            bitmapField = declaredField(touchClass, "N2"),
            movieField = declaredField(touchClass, "O2"),
            moviePlayingField = declaredField(touchClass, "P2"),
            movieThreadField = declaredField(touchClass, "f"),
            matrixField = declaredField(touchClass, "S2"),
            paintField = declaredField(touchClass, "E2"),
            cropArField = declaredField(touchClass, "c2"),
            cropRectField = declaredField(touchClass, "Z1"),
            softwareLayerFlagField = declaredField(touchClass, "e2"),
            displaySizeField = declaredField(touchClass, "h2"),
            displaySizeWidthField = remoteThumb.dimWidthField,
            displaySizeHeightField = remoteThumb.dimHeightField,
            needsZoomFitField = declaredField(touchClass, "i2"),
            imageDisplayClass = imageDisplayClass,
            deliverBitmapMethod = null,
            deliverMovieMethod = null,
            clearDisplayMethod = declaredMethod(imageDisplayClass, "l"),
            displayBitmapField = declaredField(imageDisplayClass, "W2"),
            displayMovieField = declaredField(imageDisplayClass, "X2"),
            displayDisposedField = declaredField(imageDisplayClass, "b3"),
            displayFullLoadedField = declaredField(imageDisplayClass, "c3"),
            displayPreviewLoadedField = declaredField(imageDisplayClass, "d3"),
            viewerClass = viewerClass,
            loadMediaMethod = viewer.viewerLoadMediaAndBitmap,
            loadGifMethod = null,
            applyBitmapResultMethod = declaredMethod(
                viewerClass,
                "F",
                imageDisplayClass,
                itemHolderClass,
                imageResultClass,
                Boolean::class.javaPrimitiveType
            ),
            imageResultCtor = declaredConstructor(
                imageResultClass,
                Bitmap::class.java,
                Float::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ),
            loadTokenField = declaredField(viewerClass, "t2"),
            isGifField = declaredField(viewerClass, "L2"),
            currentHolderField = sibling.viewerCurrentHolderField,
            enterTransitionField = declaredField(viewerClass, "u2"),
            transitionBgViewField = declaredField(transitionClass, "c"),
            transitionTopBarField = declaredField(transitionClass, "d"),
            transitionBottomBarField = declaredField(transitionClass, "e"),
            transitionRunningField = declaredField(transitionClass, "h"),
            transitionDoneField = declaredField(transitionClass, "g"),
            uiHandlerField = bonusLimits.viewerSupportHandlerField,
            holderFileItemField = sibling.itemHolderFileItemField,
            holderUriField = sibling.itemHolderUriField,
            gifAnimatedCheckMethod = declaredMethod(gifProbeClass, "f", InputStream::class.java),
            localPathClass = localInterface,
            localPathMethod = declaredMethod(localPathProvider, "d")
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
            googleDrive = googleDrive,
            hardwareRender = hardwareRender,
            symbolResolutionSource = "static:9108"
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

        val viewerProgressBarField = viewerClass?.declaredFields
            ?.singleOrNull { it.type == ProgressBar::class.java }
            ?.apply { isAccessible = true }

        val viewer = ViewerPrefetchBindings(
            viewerBaseClass = viewerClass?.superclass,
            mirrorRemoteTempFile = null,
            viewerOnCreate = declaredMethod(viewerClass, "onCreate", android.os.Bundle::class.java),
            viewerOnDestroy = viewerClass?.superclass?.let { declaredMethod(it, "onDestroy") },
            viewerLoadMediaAndBitmap = null, viewerLoadMedia = null, viewerLoadBitmap = null,
            viewerLoadGif = null, viewerOpenItemStream = null, viewerDecodeItem = null,
            viewerStreamProviderRead = null, viewerStreamProviderItemField = null,
            viewerOpenStreamMethod = null, taskThreadCtor = null,
            viewerProgressBarField = viewerProgressBarField,
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

        val touchClass = loadClass(cl, "nextapp.maui.ui.imageview.TouchImageDisplay")
        val bitmapField = touchClass?.declaredFields?.filter { it.type == Bitmap::class.java }
            ?.singleOrNull()?.apply { isAccessible = true }
        val matrixField = touchClass?.declaredFields?.filter { it.type == Matrix::class.java }
            ?.singleOrNull()?.apply { isAccessible = true }
        val rectField = touchClass?.declaredFields?.filter { it.type == Rect::class.java }
            ?.singleOrNull()?.apply { isAccessible = true }

        val hardwareRender = HardwareRenderBindings(
            touchClass = touchClass,
            setImageBitmap = declaredMethod(touchClass, "setImageBitmap", Bitmap::class.java),
            setImageMovie = declaredMethod(touchClass, "setImageMovie", Movie::class.java),
            setImageNull = declaredMethod(touchClass, "setImageNull"),
            setPrescale = declaredMethod(touchClass, "setPrescale", Float::class.javaPrimitiveType),
            setTouchEnabled = declaredMethod(touchClass, "setTouchEnabled", Boolean::class.javaPrimitiveType),
            zoomFit = declaredMethod(touchClass, "zoomFit"),
            onDraw = declaredMethod(touchClass, "onDraw", Canvas::class.java),
            onDetachedFromWindow = declaredMethod(touchClass, "onDetachedFromWindow"),
            updateBoundsMethod = null,
            enforceLayerOnBitmapMethod = null,
            bitmapField = bitmapField,
            movieField = null,
            moviePlayingField = null,
            movieThreadField = null,
            matrixField = matrixField,
            paintField = null,
            cropArField = null,
            cropRectField = rectField,
            softwareLayerFlagField = null,
            displaySizeField = null,
            displaySizeWidthField = null,
            displaySizeHeightField = null,
            needsZoomFitField = null,
            imageDisplayClass = sibling.imageDisplayClass,
            deliverBitmapMethod = null,
            deliverMovieMethod = null,
            clearDisplayMethod = null,
            displayBitmapField = null,
            displayMovieField = null,
            displayDisposedField = null,
            displayFullLoadedField = null,
            displayPreviewLoadedField = null,
            viewerClass = viewerClass,
            loadMediaMethod = null,
            loadGifMethod = null,
            applyBitmapResultMethod = null,
            imageResultCtor = null,
            loadTokenField = null,
            isGifField = null,
            currentHolderField = null,
            enterTransitionField = null,
            transitionBgViewField = null,
            transitionTopBarField = null,
            transitionBottomBarField = null,
            transitionRunningField = null,
            transitionDoneField = null,
            uiHandlerField = null,
            holderFileItemField = null,
            holderUriField = null,
            gifAnimatedCheckMethod = null,
            localPathClass = null,
            localPathMethod = null
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
            googleDrive = googleDrive,
            hardwareRender = hardwareRender,
            symbolResolutionSource = "fallback"
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
