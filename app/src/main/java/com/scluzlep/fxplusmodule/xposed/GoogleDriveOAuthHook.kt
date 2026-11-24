package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.scluzlep.fxplusmodule.config.ConfigContract
import java.security.MessageDigest
import java.util.Locale
import java.util.WeakHashMap

/**
 * Fixes Google Drive login and API refresh on FX (`9012` & `9108`) after Google blocked
 * NextApp's built-in OAuth Client ID (`DEFAULT_CLIENT_ID`), by allowing users to supply a
 * personal Google Cloud Android OAuth Client ID (and optional Client Secret) and injecting
 * an FX-themed configuration card into `GoogleDriveAuthActivity`.
 */
object GoogleDriveOAuthHook {

    const val KEY_GOOGLE_DRIVE_CLIENT_ID = "google_drive_client_id"
    const val KEY_GOOGLE_DRIVE_CLIENT_SECRET = "google_drive_client_secret"
    const val DEFAULT_CLIENT_ID = "907044817113-8d3ccj149pvkhj2ru91scjaahpr8v1e1.apps.googleusercontent.com"

    private const val HELPER_CARD_TAG = "fxplus_gdrive_oauth_card"
    private const val STATUS_VIEW_TAG = "fxplus_gdrive_oauth_status"

    @Volatile
    private var appContext: Context? = null

    private val savedWarningText = WeakHashMap<TextView, Pair<CharSequence, Int>>()
    private val savedSignInText = WeakHashMap<TextView, CharSequence>()

    fun attachContext(context: Context) {
        val ctx = context.applicationContext ?: context
        appContext = ctx
    }

    fun getCustomClientId(context: Context): String {
        val prefs = (context.applicationContext ?: context)
            .getSharedPreferences(ConfigContract.PREFS_NAME_LOCAL, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GOOGLE_DRIVE_CLIENT_ID, "")?.trim().orEmpty()
    }

    fun getCustomClientSecret(context: Context): String {
        val prefs = (context.applicationContext ?: context)
            .getSharedPreferences(ConfigContract.PREFS_NAME_LOCAL, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GOOGLE_DRIVE_CLIENT_SECRET, "")?.trim().orEmpty()
    }

    fun saveCustomOAuthConfig(context: Context, clientId: String, clientSecret: String) {
        val appCtx = context.applicationContext ?: context
        attachContext(appCtx)
        val prefs = appCtx.getSharedPreferences(ConfigContract.PREFS_NAME_LOCAL, Context.MODE_PRIVATE)
        val prevStamp = runCatching { prefs.getLong(ConfigContract.KEY_UPDATED_AT_MS, 0L) }.getOrDefault(0L)
        val nextStamp = maxOf(System.currentTimeMillis(), prevStamp + 1L).coerceAtLeast(1L)
        prefs.edit()
            .putString(KEY_GOOGLE_DRIVE_CLIENT_ID, clientId.trim())
            .putString(KEY_GOOGLE_DRIVE_CLIENT_SECRET, clientSecret.trim())
            .putLong(ConfigContract.KEY_UPDATED_AT_MS, nextStamp)
            .commit()
    }

    @Suppress("DEPRECATION")
    fun getFxSigningSha1(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(
                ConfigContract.TARGET_FX_PACKAGE,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = pkgInfo.signingInfo ?: return null
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            val certBytes = signatures?.lastOrNull()?.toByteArray()
                ?: signingInfo.apkContentsSigners?.firstOrNull()?.toByteArray()
                ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { byte ->
                String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
            }
        }.getOrNull()
    }

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val gd = symbols.googleDrive

        installer.install("gdrive:AuthActivity.onCreate", gd.authActivityOnCreate) { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity ?: return@install result
            attachContext(activity)
            runCatching {
                injectAuthHelperUi(symbols, activity)
            }.onFailure { err ->
                installer.logWarn("Failed to inject Google Drive OAuth helper UI", err)
            }
            result
        }

        when (symbols.profile) {
            FxProfile.FX_9012 -> install9012(gd, installer)
            FxProfile.FX_9108 -> install9108(gd, installer)
            FxProfile.STRUCTURAL_FALLBACK -> Unit
        }
    }

    private fun install9012(gd: GoogleDriveBindings, installer: FxHookInstaller) {
        installer.install("gdrive:9012:getClientId", gd.getClientId9012) { chain ->
            val ctx = (chain.getArg(0) as? Context)?.also { attachContext(it) } ?: appContext
            val customId = ctx?.let { getCustomClientId(it) }.orEmpty()
            if (customId.isNotEmpty()) {
                return@install customId
            }
            chain.proceed()
        }

        installer.install("gdrive:9012:tokenCredsCtor", gd.tokenCredsCtor9012) { chain ->
            val ctx = appContext ?: return@install chain.proceed()
            val customId = getCustomClientId(ctx)
            if (customId.isEmpty()) return@install chain.proceed()

            val arg0 = chain.getArg(0) as? String
            val arg1 = chain.getArg(1) as? String
            val arg2 = chain.getArg(2) as? String
            if (arg0 == DEFAULT_CLIENT_ID || arg0 == customId) {
                val customSecret = getCustomClientSecret(ctx)
                val nextId = if (arg0 == DEFAULT_CLIENT_ID) customId else arg0
                val nextSecret = if (customSecret.isNotEmpty()) customSecret else arg1
                if (nextId != arg0 || nextSecret != arg1) {
                    return@install chain.proceed(arrayOf(nextId, nextSecret, arg2))
                }
            }
            chain.proceed()
        }
    }

    private fun install9108(gd: GoogleDriveBindings, installer: FxHookInstaller) {
        installer.install("gdrive:9108:authReqCtor", gd.authReqBuilderCtor9108) { chain ->
            val result = chain.proceed()
            val instance = chain.thisObject ?: return@install result
            val ctx = appContext ?: return@install result
            val customId = getCustomClientId(ctx)
            if (customId.isNotEmpty()) {
                runCatching {
                    gd.authReqClientIdField9108?.set(instance, customId)
                }.onFailure { err ->
                    installer.logWarn("Failed to override gg.d.a client_id", err)
                }
            }
            result
        }

        installer.install("gdrive:9108:tokenCredsCtor", gd.tokenCredsCtor9108) { chain ->
            val ctx = appContext ?: return@install chain.proceed()
            val customId = getCustomClientId(ctx)
            if (customId.isEmpty()) return@install chain.proceed()

            val arg0 = chain.getArg(0)
            val arg1 = chain.getArg(1) as? String
            val arg2 = chain.getArg(2) as? String
            val arg3 = chain.getArg(3) as? String
            if (arg1 == DEFAULT_CLIENT_ID) {
                val customSecret = getCustomClientSecret(ctx)
                val nextSecret = if (customSecret.isNotEmpty()) customSecret else arg2
                val res = chain.proceed(arrayOf(arg0, customId, nextSecret, arg3))
                val instance = chain.thisObject
                if (instance != null && (arg0 as? Int) == 1) {
                    runCatching {
                        if (gd.tokenCredsClientIdField9108?.get(instance) != customId) {
                            gd.tokenCredsClientIdField9108?.set(instance, customId)
                        }
                        if (customSecret.isNotEmpty() && gd.tokenCredsClientSecretField9108?.get(instance) != customSecret) {
                            gd.tokenCredsClientSecretField9108?.set(instance, customSecret)
                        }
                    }
                }
                return@install res
            }
            chain.proceed()
        }
    }

    internal fun showConfigDialog(
        activity: Activity,
        palette: FxDialogPalette,
        onSaved: () -> Unit = {}
    ) {
        attachContext(activity)
        val currentId = getCustomClientId(activity)
        val currentSecret = getCustomClientSecret(activity)
        val sha1 = getFxSigningSha1(activity)

        val scroll = ScrollView(activity)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 16)
            setPadding(pad, dp(activity, 10), pad, dp(activity, 8))
        }
        scroll.addView(container)

        val guideCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 12)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 10).toFloat()
                setColor(palette.cardBg)
                setStroke(dp(activity, 1), palette.divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(activity, 12)
            }
        }

        guideCard.addView(TextView(activity).apply {
            text = "Google Cloud Console 配置说明"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
            setPadding(0, 0, 0, dp(activity, 4))
        })
        guideCard.addView(TextView(activity).apply {
            text = buildString {
                append("内置 Client ID 已被 Google 停用。请在 Google Cloud Console 启用 Google Drive API，")
                append("并创建「Android」类型 OAuth 客户端 ID（填写以下两项）：\n")
                append("1. 软件包名称：${ConfigContract.TARGET_FX_PACKAGE}\n")
                append("2. SHA-1 证书指纹：${sha1 ?: "未能读取"}\n")
                append("回调 URI：${ConfigContract.TARGET_FX_PACKAGE}:/oauth2redirect\n")
                append("若 OAuth 同意屏幕处于测试状态，请将登录邮箱加入测试用户。")
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.textSecondary)
            setLineSpacing(dp(activity, 2).toFloat(), 1.0f)
        })

        val copyRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(activity, 8), 0, 0)
        }
        copyRow.addView(FxSettingsInjectorHook.makeThemedButton(
            context = activity,
            label = "复制包名",
            primary = false,
            palette = palette
        ) {
            copyToClipboard(activity, "FX Package", ConfigContract.TARGET_FX_PACKAGE)
            Toast.makeText(activity, "已复制包名：${ConfigContract.TARGET_FX_PACKAGE}", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 6)
            }
        })
        copyRow.addView(FxSettingsInjectorHook.makeThemedButton(
            context = activity,
            label = "复制 SHA-1",
            primary = false,
            palette = palette
        ) {
            if (sha1 != null) {
                copyToClipboard(activity, "FX SHA-1", sha1)
                Toast.makeText(activity, "已复制 FX 签名 SHA-1", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "无法读取 FX 签名 SHA-1", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 6)
            }
        })
        guideCard.addView(copyRow)
        container.addView(guideCard)

        container.addView(TextView(activity).apply {
            text = "OAuth Client ID（留空则恢复内置）"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding(0, 0, 0, dp(activity, 4))
        })
        val clientIdInput = FxSettingsInjectorHook.makeThemedInputBox(
            context = activity,
            palette = palette,
            hintText = "xxxx.apps.googleusercontent.com",
            initialText = currentId
        ).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        container.addView(clientIdInput)

        container.addView(TextView(activity).apply {
            text = "Client Secret（可选，Android 类型留空）"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding(0, dp(activity, 10), 0, dp(activity, 4))
        })
        val clientSecretInput = FxSettingsInjectorHook.makeThemedInputBox(
            context = activity,
            palette = palette,
            hintText = "仅桌面端/Web 客户端需要，Android 类型请留空",
            initialText = currentSecret
        ).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        container.addView(clientSecretInput)

        val themeRes = if (palette.isLight) {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        }
        val dialog = AlertDialog.Builder(activity, themeRes)
            .setTitle("Google Drive 自定义 OAuth")
            .setView(scroll)
            .setNeutralButton("清除") { _, _ ->
                saveCustomOAuthConfig(activity, "", "")
                Toast.makeText(activity, "已清除自定义 Client ID", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val cleanId = clientIdInput.text?.toString()?.trim().orEmpty()
                val cleanSecret = clientSecretInput.text?.toString()?.trim().orEmpty()
                if (cleanId.isNotEmpty() && cleanId.any { it.isWhitespace() }) {
                    Toast.makeText(activity, "Client ID 不能包含空白字符", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveCustomOAuthConfig(activity, cleanId, cleanSecret)
                val msg = if (cleanId.isNotEmpty()) {
                    "已保存自定义 Client ID，重启或重新登录后生效"
                } else {
                    "已恢复内置 Client ID 配置"
                }
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .create()
        dialog.show()
        FxSettingsInjectorHook.styleAlertDialog(dialog, palette)
    }

    private fun injectAuthHelperUi(symbols: ResolvedSymbols, activity: Activity) {
        val contentLinear = findScrollContentLayout(activity) ?: return
        val palette = FxSettingsInjectorHook.resolveLivePalette(symbols, activity, dialogObj = null)

        val existing = contentLinear.findViewWithTag<LinearLayout>(HELPER_CARD_TAG)
        if (existing != null) {
            refreshAuthCardAndHints(activity, contentLinear, existing, palette)
            return
        }

        val card = LinearLayout(activity).apply {
            tag = HELPER_CARD_TAG
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 12)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 10).toFloat()
                setColor(palette.cardBg)
                setStroke(dp(activity, 1), palette.divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val marginH = dp(activity, 10)
                setMargins(marginH, dp(activity, 4), marginH, dp(activity, 8))
            }
        }

        card.addView(TextView(activity).apply {
            text = "FxPlusModule · Google Drive OAuth"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
            setPadding(0, 0, 0, dp(activity, 4))
        })

        val statusView = TextView(activity).apply {
            tag = STATUS_VIEW_TAG
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(activity, 8))
        }
        card.addView(statusView)

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val configBtn = FxSettingsInjectorHook.makeThemedButton(
            context = activity,
            label = "配置 Client ID",
            primary = true,
            palette = palette
        ) {
            showConfigDialog(activity, palette) {
                refreshAuthCardAndHints(activity, contentLinear, card, palette)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 6)
            }
        }
        val copyShaBtn = FxSettingsInjectorHook.makeThemedButton(
            context = activity,
            label = "复制 FX 签名 SHA-1",
            primary = false,
            palette = palette
        ) {
            val sha1 = getFxSigningSha1(activity)
            if (sha1 != null) {
                copyToClipboard(activity, "FX SHA-1", sha1)
                Toast.makeText(activity, "已复制 SHA-1：$sha1", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "无法读取 FX 签名 SHA-1", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 6)
            }
        }
        btnRow.addView(configBtn)
        btnRow.addView(copyShaBtn)
        card.addView(btnRow)

        val formIndex = findFormLayoutIndex(contentLinear)
        val insertIndex = if (formIndex >= 0) formIndex else minOf(1, contentLinear.childCount)
        contentLinear.addView(card, insertIndex)

        refreshAuthCardAndHints(activity, contentLinear, card, palette)
    }

    private fun refreshAuthCardAndHints(
        activity: Activity,
        contentLinear: LinearLayout,
        card: LinearLayout,
        palette: FxDialogPalette
    ) {
        val customId = getCustomClientId(activity)
        val statusView = card.findViewWithTag<TextView>(STATUS_VIEW_TAG)
        if (statusView != null) {
            if (customId.isNotEmpty()) {
                statusView.text = "已启用自定义 Client ID：${maskClientId(customId)}"
                statusView.setTextColor(palette.textPrimary)
            } else {
                statusView.text = "内置 OAuth 已被 Google 停用，可配置自定义 Android Client ID"
                statusView.setTextColor(palette.textSecondary)
            }
        }
        updateNativeWarningAndButton(activity, contentLinear, palette, customId)
    }

    private fun updateNativeWarningAndButton(
        activity: Activity,
        contentLinear: LinearLayout,
        palette: FxDialogPalette,
        customId: String
    ) {
        val warningResId = FxHookSupport.resolveStringId(activity, "google_drive_restriction_warning")
        val origWarningStr = if (warningResId != 0) runCatching { activity.getString(warningResId) }.getOrNull() else null
        val signInResId = FxHookSupport.resolveStringId(activity, "google_drive_sign_in_with_google")
        val origSignInStr = if (signInResId != 0) runCatching { activity.getString(signInResId) }.getOrNull() else null

        for (i in 0 until contentLinear.childCount) {
            val child = contentLinear.getChildAt(i)
            if (child.tag == HELPER_CARD_TAG) continue
            if (child is TextView && origWarningStr != null) {
                val cached = savedWarningText[child]
                if (cached != null || child.text?.toString() == origWarningStr) {
                    val original = cached ?: (child.text to child.currentTextColor).also {
                        savedWarningText[child] = it
                    }
                    if (customId.isNotEmpty()) {
                        child.text = "已启用自定义 OAuth Client ID，将替代内置停用凭证进行登录。"
                        child.setTextColor(palette.accent)
                    } else {
                        child.text = original.first
                        child.setTextColor(original.second)
                    }
                }
            } else if (child is LinearLayout && origSignInStr != null) {
                for (j in 0 until child.childCount) {
                    val inner = child.getChildAt(j) as? TextView ?: continue
                    val cached = savedSignInText[inner]
                    if (cached != null || inner.text?.toString() == origSignInStr) {
                        val original = cached ?: inner.text.also { savedSignInText[inner] = it }
                        inner.text = if (customId.isNotEmpty()) {
                            "$original (自定义 OAuth)"
                        } else {
                            original
                        }
                    }
                }
            }
        }
    }

    private fun findScrollContentLayout(activity: Activity): LinearLayout? {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val scroll = findFirstScrollView(root) ?: return null
        return scroll.getChildAt(0) as? LinearLayout
    }

    private fun findFirstScrollView(group: ViewGroup): ScrollView? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is ScrollView) return child
            if (child is ViewGroup) {
                val nested = findFirstScrollView(child)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun findFormLayoutIndex(contentLinear: LinearLayout): Int {
        for (i in 0 until contentLinear.childCount) {
            val child = contentLinear.getChildAt(i)
            if (child is LinearLayout && child.tag != HELPER_CARD_TAG) {
                return i
            }
        }
        return -1
    }

    private fun maskClientId(clientId: String): String {
        if (clientId.length <= 24) return clientId
        return "${clientId.take(12)}...${clientId.takeLast(10)}"
    }

    private fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun dp(context: Context, value: Int): Int = FxSettingsInjectorHook.dp(context, value)
}
