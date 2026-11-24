package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Serializable symbol table produced by [FxDexKitResolver] for an FX Explorer APK build
 * whose obfuscated layout is not covered by the static 9012/9108 contracts, plus a pure-JVM
 * Dalvik descriptor parser so cache hits hydrate without loading `libdexkit.so`.
 */
data class FxSymbolManifest(
    val schemaVersion: Int = DexSymbolCache.SCHEMA_VERSION,
    val fingerprint: String = "",
    val classes: Map<String, String> = emptyMap(),
    val methods: Map<String, String> = emptyMap(),
    val fields: Map<String, String> = emptyMap(),
    val stringLists: Map<String, List<String>> = emptyMap(),
    val ints: Map<String, Int> = emptyMap()
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)
        root.put("fingerprint", fingerprint)
        root.put("classes", JSONObject().apply {
            for ((k, v) in classes) put(k, v)
        })
        root.put("methods", JSONObject().apply {
            for ((k, v) in methods) put(k, v)
        })
        root.put("fields", JSONObject().apply {
            for ((k, v) in fields) put(k, v)
        })
        root.put("stringLists", JSONObject().apply {
            for ((k, list) in stringLists) {
                put(k, JSONArray().apply {
                    for (item in list) put(item)
                })
            }
        })
        root.put("ints", JSONObject().apply {
            for ((k, v) in ints) put(k, v)
        })
        return root.toString()
    }

    fun isUsable(): Boolean {
        return classes.size >= 4 || methods.size >= 4
    }

    companion object {
        fun fromJson(raw: String): FxSymbolManifest? {
            if (raw.isBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val schema = root.optInt("schemaVersion", -1)
                val fp = root.optString("fingerprint", "")
                if (schema <= 0 || fp.isBlank()) return null

                val classesMap = linkedMapOf<String, String>()
                root.optJSONObject("classes")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.optString(k, "")
                        if (v.isNotEmpty()) classesMap[k] = v
                    }
                }

                val methodsMap = linkedMapOf<String, String>()
                root.optJSONObject("methods")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.optString(k, "")
                        if (v.isNotEmpty()) methodsMap[k] = v
                    }
                }

                val fieldsMap = linkedMapOf<String, String>()
                root.optJSONObject("fields")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.optString(k, "")
                        if (v.isNotEmpty()) fieldsMap[k] = v
                    }
                }

                val stringListsMap = linkedMapOf<String, List<String>>()
                root.optJSONObject("stringLists")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val arr = obj.optJSONArray(k) ?: continue
                        val list = ArrayList<String>(arr.length())
                        for (i in 0 until arr.length()) {
                            val item = arr.optString(i, "")
                            if (item.isNotEmpty()) list.add(item)
                        }
                        stringListsMap[k] = list
                    }
                }

                val intsMap = linkedMapOf<String, Int>()
                root.optJSONObject("ints")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        intsMap[k] = obj.optInt(k)
                    }
                }

                FxSymbolManifest(
                    schemaVersion = schema,
                    fingerprint = fp,
                    classes = classesMap,
                    methods = methodsMap,
                    fields = fieldsMap,
                    stringLists = stringListsMap,
                    ints = intsMap
                )
            }.getOrNull()
        }
    }
}

object FxSymbolKeys {
    // Integer constants / switch case IDs
    const val INT_REMOTE_THUMB_WORKER_CASE_ID = "remoteThumbWorkerCaseId"
    const val INT_MENU_CONTRIB_CASE_ID = "menuContribCaseId"
    const val INT_TEXT_VIEWER_WORKER_CASE_ID = "textViewerWorkerCaseId"
    const val INT_TEXT_EDIT_RESULT_CASE_ID = "textEditResultCaseId"
    const val INT_EXEC_WORKER_CASE_ID = "execWorkerCaseId"
    const val INT_EXEC_UI_RUNNABLE_CASE_ID = "execUiRunnableCaseId"

    // String lists
    const val LIST_REMOTE_ITEM_CLASSES = "remoteItemClasses"

    // Classes
    const val CLS_SIBLING_LOADER = "siblingLoaderClass"
    const val CLS_DIRECTORY_OPTION_STORE = "directoryOptionStoreClass"
    const val CLS_FILE_ITEM = "fileItemClass"
    const val CLS_FILE_ITEM_BASE = "fileItemBaseClass"
    const val CLS_DIR_ITEM = "dirItemClass"
    const val CLS_DIR_CATALOG = "dirCatalogClass"
    const val CLS_IMAGE_VIEWER_ACTIVITY = "imageViewerActivityClass"
    const val CLS_IMAGE_DISPLAY = "imageDisplayClass"
    const val CLS_SIBLING_CALLBACK = "siblingCallbackClass"
    const val CLS_DISPLAY_LISTENER = "displayListenerClass"
    const val CLS_VIEWER_BASE = "viewerBaseClass"
    const val CLS_THUMB_CALLBACK = "thumbCallbackClass"
    const val CLS_THUMB_GENERATOR = "thumbGeneratorClass"
    const val CLS_REMOTE_ITEM_MARKER = "remoteItemMarkerClass"
    const val CLS_CLOUD_THUMB_MARKER = "cloudThumbMarkerClass"
    const val CLS_FILE_CATALOG = "fileCatalogClass"
    const val CLS_FILE_DIR_NODE = "fileDirNodeClass"
    const val CLS_FILE_FILE_NODE = "fileFileNodeClass"
    const val CLS_FILE_NODE_BASE = "fileNodeBaseClass"
    const val CLS_USER_EXCEPTION = "userExceptionClass"
    const val CLS_CANCEL_EXCEPTION = "cancelExceptionClass"
    const val CLS_EXPLORER_ACTIVITY = "explorerActivityClass"
    const val CLS_FX_PATH = "fxPathClass"
    const val CLS_HOME_CONTENT_VIEW = "homeContentViewClass"
    const val CLS_MENU_CONTRIB = "menuContribClass"
    const val CLS_MENU_DIVIDER = "menuDividerClass"
    const val CLS_MENU_CLICK_LISTENER = "menuClickListenerInterface"
    const val CLS_TOUCH_DISPLAY = "touchClass"
    const val CLS_LOCAL_PATH_CLASS = "localPathClass"
    const val CLS_FX_DIALOG_STYLE_WITH_CLOSE_ENUM = "fxDialogStyleClass"
    const val CLS_FX_DIALOG_STYLE_WITH_CLOSE_FIELD = "fxDialogStyleWithCloseField"

    // Sibling & Viewer methods/fields
    const val MTD_ENUMERATE_SIBLINGS = "enumerateSiblings"
    const val FLD_SUPPORTED_MIMES = "supportedMimesField"
    const val MTD_GET_SORT_OPTIONS = "getSortOptions"
    const val FLD_SORT_PAIR_ORDER = "sortPairOrderField"
    const val FLD_SORT_PAIR_DESC = "sortPairDescField"
    const val MTD_SORT_ITEMS = "sortItems"
    const val MTD_LIST_CHILDREN = "listChildren"
    const val MTD_GET_MIME_TYPE = "getMimeType"
    const val MTD_GET_SIZE = "getSize"
    const val MTD_GET_PATH = "getPath"
    const val MTD_GET_NAME = "getName"
    const val MTD_GET_LAST_MODIFIED = "getLastModified"
    const val MTD_OPEN_INPUT_STREAM = "openInputStream"
    const val MTD_GET_CATALOG = "getCatalog"
    const val MTD_GET_CATALOG_TYPE = "getCatalogType"
    const val MTD_IS_REMOTE = "isRemoteMethod"
    const val FLD_VIEWER_SIBLING_ITEMS = "viewerSiblingItemsField"
    const val FLD_VIEWER_SORT_ORDER = "viewerSortOrderField"
    const val FLD_VIEWER_SORT_DESC = "viewerSortDescField"
    const val FLD_VIEWER_CURRENT_DISPLAY = "viewerCurrentDisplayField"
    const val FLD_VIEWER_CURRENT_HOLDER = "viewerCurrentHolderField"
    const val FLD_VIEWER_INITIAL_ITEM = "viewerInitialItemField"
    const val FLD_VIEWER_SIBLINGS_LOADED = "viewerSiblingsLoadedField"
    const val FLD_DISPLAY_SIBLING_INDEX = "displaySiblingIndexField"
    const val FLD_DISPLAY_SIBLING_COUNT = "displaySiblingCountField"
    const val FLD_DISPLAY_LISTENER = "displayListenerField"
    const val MTD_DISPLAY_COMMIT = "displayCommitMethod"
    const val MTD_SIBLING_CB_LOADED = "siblingCallbackLoadedMethod"
    const val MTD_SIBLING_CB_FAIL = "siblingCallbackFailMethod"
    const val FLD_SIBLING_CB_ACTIVITY = "siblingCallbackActivityField"
    const val MTD_DISPLAY_LISTENER_REQUEST = "displayListenerRequestMethod"
    const val MTD_DISPLAY_LISTENER_COMMIT = "displayListenerCommitMethod"
    const val FLD_DISPLAY_LISTENER_HOLDER = "displayListenerHolderField"
    const val FLD_RUNNER_DIR = "runnerDirField"
    const val FLD_RUNNER_CTX = "runnerCtxField"
    const val FLD_RUNNER_SORT_ORDER = "runnerSortOrderField"
    const val FLD_RUNNER_SORT_DESC = "runnerSortDescField"
    const val FLD_RUNNER_CURRENT_ITEM = "runnerCurrentItemField"
    const val FLD_RUNNER_CALLBACK_HOLDER = "runnerCallbackHolderField"
    const val FLD_ITEM_HOLDER_FILE_ITEM = "itemHolderFileItemField"
    const val FLD_ITEM_HOLDER_URI = "itemHolderUriField"

    // Viewer & Prefetch
    const val MTD_MIRROR_REMOTE_TEMP_FILE = "mirrorRemoteTempFile"
    const val MTD_VIEWER_ON_CREATE = "viewerOnCreate"
    const val MTD_VIEWER_ON_DESTROY = "viewerOnDestroy"
    const val MTD_VIEWER_LOAD_MEDIA_AND_BITMAP = "viewerLoadMediaAndBitmap"
    const val MTD_VIEWER_LOAD_MEDIA = "viewerLoadMedia"
    const val MTD_VIEWER_LOAD_BITMAP = "viewerLoadBitmap"
    const val MTD_VIEWER_LOAD_GIF = "viewerLoadGif"
    const val MTD_VIEWER_OPEN_ITEM_STREAM = "viewerOpenItemStream"
    const val MTD_VIEWER_DECODE_ITEM = "viewerDecodeItem"
    const val MTD_VIEWER_STREAM_PROVIDER_READ = "viewerStreamProviderRead"
    const val FLD_VIEWER_STREAM_PROVIDER_ITEM = "viewerStreamProviderItemField"
    const val MTD_VIEWER_OPEN_STREAM = "viewerOpenStreamMethod"
    const val CTR_TASK_THREAD = "taskThreadCtor"
    const val FLD_VIEWER_PROGRESS_BAR = "viewerProgressBarField"

    // Remote Thumbnails
    const val MTD_LOAD_THUMBNAIL = "loadThumbnail"
    const val MTD_THUMB_CB_ENQUEUE = "thumbCallbackEnqueueMethod"
    const val FLD_THUMB_TASK_ITEM = "thumbTaskItemField"
    const val FLD_THUMB_TASK_CONTEXT = "thumbTaskContextField"
    const val MTD_THUMB_TASK_GET_CACHE_FILE = "thumbTaskGetCacheFile"
    const val CTR_REMOTE_THUMB_TASK = "remoteThumbTaskCtor"
    const val FLD_REMOTE_THUMB_TASK_MIME = "remoteThumbTaskMimeField"
    const val MTD_REMOTE_THUMB_WORKER_RUN = "remoteThumbWorkerRun"
    const val FLD_REMOTE_THUMB_WORKER_CASE = "remoteThumbWorkerCaseField"
    const val FLD_REMOTE_THUMB_WORKER_OWNER = "remoteThumbWorkerOwnerField"
    const val FLD_REMOTE_THUMB_WORKER_RESULT = "remoteThumbWorkerResultField"
    const val FLD_THUMB_RESULT = "thumbResultField"
    const val MTD_THUMB_GEN_CREATE_FOR_FILE = "thumbGeneratorCreateForFile"
    const val FLD_THUMB_GEN_FLAGS = "thumbGeneratorFlagsField"
    const val FLD_THUMB_GEN_CONTEXT = "thumbGeneratorContextField"
    const val FLD_THUMB_GEN_STREAM = "thumbGeneratorStreamField"
    const val FLD_THUMB_GEN_OUT_FILE = "thumbGeneratorOutFileField"
    const val FLD_THUMB_GEN_SOURCE_PATH = "thumbGeneratorSourcePathField"
    const val MTD_THUMB_GEN_STREAM_READ = "thumbGeneratorStreamRead"
    const val CTR_THUMB_IMAGE_INFO = "thumbImageInfoCtor"
    const val MTD_THUMB_GEN_RUN = "thumbGeneratorRun"
    const val MTD_THUMB_GEN_EXCEEDS_CAP = "thumbGeneratorExceedsCap"
    const val MTD_THUMB_GEN_GENERATE = "thumbGeneratorGenerate"
    const val MTD_SETTINGS_GET_INSTANCE = "settingsGetInstance"
    const val MTD_SETTINGS_THUMBS_ENABLED = "settingsThumbsEnabled"
    const val MTD_SETTINGS_THUMBS_FOR_PATH = "settingsThumbsForPath"
    const val MTD_DROPBOX_SUPPORTS_THUMB = "dropboxSupportsThumb"
    const val FLD_DIM_WIDTH = "dimWidthField"
    const val FLD_DIM_HEIGHT = "dimHeightField"

    // Native Settings & Theme Palette
    const val MTD_MAIN_PREF_ON_CREATE = "mainPrefOnCreate"
    const val MTD_MAIN_PREF_ON_RESUME = "mainPrefOnResume"
    const val CTR_LABEL_PREFERENCE = "labelPreferenceCtor"
    const val CTR_FX_DIALOG = "fxDialogCtor"
    const val MTD_FX_DIALOG_SET_HEADER = "fxDialogSetHeader"
    const val MTD_FX_DIALOG_SET_DESCRIPTION = "fxDialogSetDescription"
    const val MTD_FX_DIALOG_GET_DEFAULT_LAYOUT = "fxDialogGetDefaultContentLayout"
    const val MTD_FX_DIALOG_ADD_LABELED_VIEW = "fxDialogAddLabeledView"
    const val MTD_FX_DIALOG_IS_BG_LIGHT = "fxDialogIsBackgroundLight"
    const val MTD_FX_DIALOG_DISMISS = "fxDialogDismiss"
    const val MTD_FX_DIALOG_SHOW = "fxDialogShow"
    const val FLD_FX_DIALOG_UI = "fxDialogUiField"
    const val MTD_UI_GET_FOR_CONTEXT = "uiGetForContext"
    const val FLD_UI_IS_WINDOW_LIGHT = "uiIsWindowLightField"
    const val FLD_UI_TEXT_PRIMARY = "uiTextPrimaryField"
    const val FLD_UI_TEXT_SECONDARY = "uiTextSecondaryField"
    const val FLD_UI_BOX_BG = "uiBoxBgField"
    const val FLD_UI_BOX_PRESSED_BG = "uiBoxPressedBgField"
    const val MTD_UI_WINDOW_BG = "uiWindowBgMethod"
    const val MTD_UI_TRIM_ACCENT = "uiTrimAccentMethod"
    const val MTD_UI_TRIM_BASE = "uiTrimBaseMethod"

    // Home Menu
    const val FLD_HOME_VIEW_ACTIVITY = "homeViewActivityField"
    const val FLD_HOME_VIEW_RES = "homeViewResField"
    const val FLD_MENU_CONTRIB_CASE = "menuContribCaseField"
    const val FLD_MENU_CONTRIB_OWNER = "menuContribOwnerField"
    const val FLD_MENU_CONTRIB_BG_LIGHT = "menuContribBgLightField"
    const val MTD_MENU_CONTRIB_POPULATE = "menuContribPopulateMethod"
    const val MTD_MENU_MODEL_ADD_ITEM = "menuModelAddItemMethod"
    const val MTD_MENU_MODEL_SIZE = "menuModelSizeMethod"
    const val MTD_MENU_MODEL_ITEM = "menuModelItemMethod"
    const val FLD_MENU_MODEL_ITEMS_LIST = "menuModelItemsListField"
    const val MTD_MENU_ITEM_GET_TITLE = "menuItemGetTitleMethod"
    const val CTR_MENU_DIVIDER = "menuDividerCtor"
    const val CTR_MENU_ACTION = "menuActionCtor"
    const val MTD_ACTION_ICONS_CREATE = "actionIconsCreateMethod"

    // Google Drive OAuth
    const val MTD_GDRIVE_AUTH_ON_CREATE = "authActivityOnCreate"
    const val MTD_GDRIVE_GET_CLIENT_ID_9012 = "getClientId9012"
    const val CTR_GDRIVE_TOKEN_CREDS_9012 = "tokenCredsCtor9012"
    const val CTR_GDRIVE_AUTH_REQ_9108 = "authReqBuilderCtor9108"
    const val FLD_GDRIVE_AUTH_REQ_CLIENT_ID_9108 = "authReqClientIdField9108"
    const val CTR_GDRIVE_TOKEN_CREDS_9108 = "tokenCredsCtor9108"
    const val FLD_GDRIVE_TOKEN_CREDS_CLIENT_ID_9108 = "tokenCredsClientIdField9108"
    const val FLD_GDRIVE_TOKEN_CREDS_SECRET_9108 = "tokenCredsClientSecretField9108"

    // AndroidData & FileCatalog
    const val MTD_FILE_CATALOG_OPEN_DIR = "fileCatalogOpenDir"
    const val MTD_FILE_CATALOG_RESOLVE_FILE = "fileCatalogResolveFile"
    const val FLD_FILE_CATALOG_SAF_URI = "fileCatalogSafUriField"
    const val MTD_FILE_DIR_LIST_CHILDREN = "fileDirListChildren"
    const val MTD_FILE_DIR_MKDIR = "fileDirMkdir"
    const val MTD_FILE_DIR_CHILD_AVAILABLE = "fileDirChildAvailable"
    const val MTD_FILE_DIR_DELETE_RECURSIVE = "fileDirDeleteRecursive"
    const val MTD_FILE_DIR_STAT_VOLUME = "fileDirStatVolume"
    const val MTD_FILE_DIR_CLEAR_CACHE = "fileDirClearCache"
    const val FLD_FILE_DIR_CACHED_FILES = "fileDirCachedFilesField"
    const val CTR_FILE_FILE = "fileFileCtor"
    const val MTD_FILE_FILE_STAT_SIZE = "fileFileStatSize"
    const val FLD_FILE_FILE_CACHED_SIZE = "fileFileCachedSizeField"
    const val MTD_FILE_FILE_OPEN_INPUT = "fileFileOpenInput"
    const val MTD_FILE_FILE_OPEN_OUTPUT_INTERNAL = "fileFileOpenOutputInternal"
    const val MTD_FILE_FILE_DELETE = "fileFileDelete"
    const val MTD_FILE_NODE_DELETE_SINGLE = "fileNodeDeleteSingle"
    const val MTD_FILE_NODE_RENAME = "fileNodeRename"
    const val MTD_FILE_NODE_MOVE_TO = "fileNodeMoveTo"
    const val MTD_FILE_NODE_GET_LAST_MODIFIED = "fileNodeGetLastModified"
    const val MTD_FILE_NODE_IS_READ_ONLY = "fileNodeIsReadOnly"
    const val MTD_FILE_NODE_SET_LAST_MODIFIED = "fileNodeSetLastModified"
    const val FLD_FILE_NODE_CATALOG = "fileNodeCatalogField"
    const val FLD_FILE_NODE_PATH = "fileNodePathField"
    const val FLD_FILE_NODE_JAVA_FILE = "fileNodeJavaFileField"
    const val FLD_FILE_NODE_METRICS = "fileNodeMetricsField"
    const val CTR_FX_PATH_CHILD = "fxPathChildCtor"
    const val MTD_FX_PATH_FIND_ELEMENT = "fxPathFindElement"
    const val MTD_HIDDEN_FILE_STORE_IS_HIDDEN = "hiddenFileStoreIsHidden"
    const val CTR_FILE_DIR_9012 = "fileDirCtor9012"

    // Bonus Limits
    const val MTD_TEXT_VIEWER_LOAD = "textViewerLoad"
    const val MTD_TEXT_VIEWER_APPLY = "textViewerApply"
    const val MTD_TEXT_VIEWER_OPEN_BINARY_XML = "textViewerOpenBinaryXml"
    const val MTD_TEXT_VIEWER_WORKER_RUN = "textViewerWorkerRun"
    const val FLD_TEXT_VIEWER_WORKER_CASE = "textViewerWorkerCaseField"
    const val FLD_TEXT_VIEWER_WORKER_ACTIVITY = "textViewerWorkerActivityField"
    const val CTR_TEXT_VIEWER_WORKER = "textViewerWorkerCtor"
    const val FLD_TEXT_VIEWER_LINES = "textViewerLinesField"
    const val FLD_TEXT_VIEWER_CHARSET = "textViewerCharsetField"
    const val FLD_TEXT_VIEWER_TRUNCATED = "textViewerTruncatedField"
    const val FLD_TEXT_VIEWER_UNSUPPORTED = "textViewerUnsupportedField"
    const val FLD_VIEWER_SUPPORT_HANDLER = "viewerSupportHandlerField"
    const val MTD_VIEWER_SUPPORT_SHOW_ERROR = "viewerSupportShowError"
    const val CTR_ENCODING_STREAM = "encodingStreamCtor"
    const val MTD_ENCODING_STREAM_DETECT = "encodingStreamDetect"
    const val MTD_ENCODING_STREAM_GET_CHARSET = "encodingStreamGetCharset"
    const val FLD_ENCODING_STREAM_CHARSET = "encodingStreamCharsetField"
    const val MTD_ENCODING_STREAM_GET_BUFFER = "encodingStreamGetBuffer"
    const val FLD_ENCODING_STREAM_BUFFER = "encodingStreamBufferField"
    const val MTD_MIME_PROBE_BYTES = "mimeProbeBytes"
    const val MTD_TEXT_EDIT_READ_STREAM = "textEditReadStream"
    const val CTR_TEXT_EDIT_RESULT = "textEditResultCtor"
    const val MTD_EXEC_LOAD_PREVIEW = "execLoadPreview"
    const val MTD_EXEC_APPLY_PREVIEW = "execApplyPreview"
    const val MTD_EXEC_WORKER_RUN = "execWorkerRun"
    const val FLD_EXEC_WORKER_CASE = "execWorkerCaseField"
    const val FLD_EXEC_WORKER_ARG1 = "execWorkerArg1Field"
    const val FLD_EXEC_WORKER_ARG2 = "execWorkerArg2Field"
    const val CTR_EXEC_UI_RUNNABLE = "execUiRunnableCtor"
    const val FLD_EXEC_LINES = "execLinesField"
    const val FLD_EXEC_CHARSET = "execCharsetField"
    const val FLD_EXEC_UNSUPPORTED = "execUnsupportedField"

    // Folder Lifecycle
    const val MTD_EXPLORER_ON_RESUME = "explorerOnResume"
    const val MTD_EXPLORER_ON_DESTROY = "explorerOnDestroy"
    const val FLD_WINDOW_MANAGER = "windowManagerField"
    const val FLD_WINDOW_MODELS = "windowModelsField"
    const val MTD_WINDOW_MODEL_CLOSE = "windowModelCloseMethod"
    const val MTD_WINDOW_MODEL_SET_PATH = "windowModelSetPathMethod"
    const val MTD_WINDOW_MODEL_CURRENT_CONTENT = "windowModelCurrentContentMethod"
    const val FLD_CONTENT_MODEL_PATH = "contentModelPathField"
    const val FLD_FX_PATH_ELEMENTS = "fxPathElementsField"
    const val MTD_FX_PATH_PARENT = "fxPathParentMethod"

    // Hardware Render & GIF
    const val MTD_HW_SET_IMAGE_BITMAP = "hwSetImageBitmap"
    const val MTD_HW_SET_IMAGE_MOVIE = "hwSetImageMovie"
    const val MTD_HW_SET_IMAGE_NULL = "hwSetImageNull"
    const val MTD_HW_SET_PRESCALE = "hwSetPrescale"
    const val MTD_HW_SET_TOUCH_ENABLED = "hwSetTouchEnabled"
    const val MTD_HW_ZOOM_FIT = "hwZoomFit"
    const val MTD_HW_ON_DRAW = "hwOnDraw"
    const val MTD_HW_ON_DETACHED = "hwOnDetachedFromWindow"
    const val MTD_HW_UPDATE_BOUNDS = "hwUpdateBoundsMethod"
    const val MTD_HW_ENFORCE_LAYER = "hwEnforceLayerOnBitmapMethod"
    const val FLD_HW_BITMAP = "hwBitmapField"
    const val FLD_HW_MOVIE = "hwMovieField"
    const val FLD_HW_MOVIE_PLAYING = "hwMoviePlayingField"
    const val FLD_HW_MOVIE_THREAD = "hwMovieThreadField"
    const val FLD_HW_MATRIX = "hwMatrixField"
    const val FLD_HW_PAINT = "hwPaintField"
    const val FLD_HW_CROP_AR = "hwCropArField"
    const val FLD_HW_CROP_RECT = "hwCropRectField"
    const val FLD_HW_SOFTWARE_LAYER = "hwSoftwareLayerFlagField"
    const val FLD_HW_DISPLAY_SIZE = "hwDisplaySizeField"
    const val FLD_HW_NEEDS_ZOOM_FIT = "hwNeedsZoomFitField"
    const val MTD_HW_DELIVER_BITMAP = "hwDeliverBitmapMethod"
    const val MTD_HW_DELIVER_MOVIE = "hwDeliverMovieMethod"
    const val MTD_HW_CLEAR_DISPLAY = "hwClearDisplayMethod"
    const val FLD_HW_DISPLAY_BITMAP = "hwDisplayBitmapField"
    const val FLD_HW_DISPLAY_MOVIE = "hwDisplayMovieField"
    const val FLD_HW_DISPLAY_DISPOSED = "hwDisplayDisposedField"
    const val FLD_HW_DISPLAY_FULL_LOADED = "hwDisplayFullLoadedField"
    const val FLD_HW_DISPLAY_PREVIEW_LOADED = "hwDisplayPreviewLoadedField"
    const val MTD_HW_APPLY_BITMAP_RESULT = "hwApplyBitmapResultMethod"
    const val CTR_HW_IMAGE_RESULT = "hwImageResultCtor"
    const val FLD_HW_LOAD_TOKEN = "hwLoadTokenField"
    const val FLD_HW_IS_GIF = "hwIsGifField"
    const val FLD_HW_ENTER_TRANSITION = "hwEnterTransitionField"
    const val FLD_HW_TRANSITION_BG_VIEW = "hwTransitionBgViewField"
    const val FLD_HW_TRANSITION_TOP_BAR = "hwTransitionTopBarField"
    const val FLD_HW_TRANSITION_BOTTOM_BAR = "hwTransitionBottomBarField"
    const val FLD_HW_TRANSITION_RUNNING = "hwTransitionRunningField"
    const val FLD_HW_TRANSITION_DONE = "hwTransitionDoneField"
    const val MTD_HW_GIF_ANIMATED_CHECK = "hwGifAnimatedCheckMethod"
    const val MTD_HW_LOCAL_PATH = "hwLocalPathMethod"
}

object DexSymbolCache {

    const val SCHEMA_VERSION = 1
    const val CACHE_FILE_NAME = "fxplus_dexkit_symbols.json"

    fun resolveCacheDir(dataDir: String?): File? {
        val clean = dataDir?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return File(clean, "cache")
    }

    fun resolveCacheFile(cacheDir: File?): File? {
        return cacheDir?.let { File(it, CACHE_FILE_NAME) }
    }

    fun computeFingerprint(
        versionCode: Long,
        targetAppInfo: ApplicationInfo?,
        moduleAppInfo: ApplicationInfo?
    ): String {
        val sourceDir = targetAppInfo?.sourceDir.orEmpty()
        val apkFile = sourceDir.takeIf { it.isNotEmpty() }?.let(::File)
        val apkSize = if (apkFile != null && apkFile.isFile) apkFile.length() else -1L
        val apkMtime = if (apkFile != null && apkFile.isFile) apkFile.lastModified() else -1L

        val splitDirs = targetAppInfo?.splitSourceDirs?.sorted()?.joinToString("|").orEmpty()
        val modDir = moduleAppInfo?.sourceDir.orEmpty()
        val modFile = modDir.takeIf { it.isNotEmpty() }?.let(::File)
        val modSize = if (modFile != null && modFile.isFile) modFile.length() else -1L
        val modMtime = if (modFile != null && modFile.isFile) modFile.lastModified() else -1L
        val modVerCode = readLongVersionCode(moduleAppInfo)

        return computeFingerprintFromValues(
            schemaVersion = SCHEMA_VERSION,
            targetVersionCode = versionCode,
            targetSourceDir = sourceDir,
            targetApkSize = apkSize,
            targetApkMtime = apkMtime,
            targetSplitDirs = splitDirs,
            moduleVersionCode = modVerCode,
            moduleSourceDir = modDir,
            moduleApkSize = modSize,
            moduleApkMtime = modMtime
        )
    }

    internal fun computeFingerprintFromValues(
        schemaVersion: Int,
        targetVersionCode: Long,
        targetSourceDir: String,
        targetApkSize: Long,
        targetApkMtime: Long,
        targetSplitDirs: String = "",
        moduleVersionCode: Long = 0L,
        moduleSourceDir: String = "",
        moduleApkSize: Long = -1L,
        moduleApkMtime: Long = -1L
    ): String {
        val payload = buildString {
            append("s=").append(schemaVersion)
            append("|tv=").append(targetVersionCode)
            append("|ts=").append(targetSourceDir)
            append("|tl=").append(targetApkSize)
            append("|tm=").append(targetApkMtime)
            append("|tp=").append(targetSplitDirs)
            append("|mv=").append(moduleVersionCode)
            append("|ms=").append(moduleSourceDir)
            append("|ml=").append(moduleApkSize)
            append("|mm=").append(moduleApkMtime)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun readLongVersionCode(appInfo: ApplicationInfo?): Long {
        if (appInfo == null) return 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return runCatching {
                val method = ApplicationInfo::class.java.getMethod("getLongVersionCode")
                (method.invoke(appInfo) as? Number)?.toLong() ?: 0L
            }.getOrDefault(0L)
        }
        return 0L
    }

    fun load(cacheDir: File?, expectedFingerprint: String): FxSymbolManifest? {
        if (expectedFingerprint.isBlank()) return null
        val file = resolveCacheFile(cacheDir) ?: return null
        if (!file.isFile || !file.canRead()) return null
        val raw = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val parsed = FxSymbolManifest.fromJson(raw) ?: return null
        if (parsed.schemaVersion != SCHEMA_VERSION || parsed.fingerprint != expectedFingerprint) {
            return null
        }
        return parsed
    }

    fun save(cacheDir: File?, manifest: FxSymbolManifest): Boolean {
        val target = resolveCacheFile(cacheDir) ?: return false
        return runCatching {
            val parent = target.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val tmp = File(parent, "$CACHE_FILE_NAME.tmp.${System.nanoTime()}")
            FileOutputStream(tmp).use { fos ->
                fos.write(manifest.toJson().toByteArray(StandardCharsets.UTF_8))
                fos.fd.sync()
            }
            if (target.exists()) {
                target.delete()
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target.isFile
        }.getOrDefault(false)
    }

    fun hasCache(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        val f1 = resolveCacheFile(appCtx.cacheDir)
        if (f1 != null && f1.isFile) return true
        val f2 = resolveCacheFile(resolveCacheDir(appCtx.applicationInfo?.dataDir))
        return f2 != null && f2.isFile
    }

    fun clear(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        var clearedAny = false
        val candidates = linkedSetOf<File>()
        resolveCacheFile(appCtx.cacheDir)?.let { candidates.add(it) }
        resolveCacheFile(resolveCacheDir(appCtx.applicationInfo?.dataDir))?.let { candidates.add(it) }
        for (file in candidates) {
            if (file.exists() && runCatching { file.delete() }.getOrDefault(false)) {
                clearedAny = true
            }
        }
        return clearedAny
    }

    fun descriptorToJavaClassName(descriptor: String): String {
        val trimmed = descriptor.trim()
        return if (trimmed.startsWith("L") && trimmed.endsWith(";") && trimmed.length >= 3) {
            trimmed.substring(1, trimmed.length - 1).replace('/', '.')
        } else {
            trimmed.replace('/', '.')
        }
    }

    fun resolveClass(cl: ClassLoader, nameOrDescriptor: String?): Class<*>? {
        if (nameOrDescriptor.isNullOrBlank()) return null
        val trimmed = nameOrDescriptor.trim()
        return if (trimmed.startsWith("[") || (trimmed.startsWith("L") && trimmed.endsWith(";")) || trimmed.length == 1) {
            resolveTypeDescriptor(cl, trimmed)
        } else {
            runCatching { Class.forName(trimmed, false, cl) }.getOrNull()
        }
    }

    fun resolveTypeDescriptor(cl: ClassLoader, desc: String): Class<*>? {
        if (desc.isEmpty()) return null
        return when (desc[0]) {
            'V' -> if (desc.length == 1) Void.TYPE else null
            'Z' -> if (desc.length == 1) Boolean::class.javaPrimitiveType else null
            'B' -> if (desc.length == 1) Byte::class.javaPrimitiveType else null
            'C' -> if (desc.length == 1) Char::class.javaPrimitiveType else null
            'S' -> if (desc.length == 1) Short::class.javaPrimitiveType else null
            'I' -> if (desc.length == 1) Int::class.javaPrimitiveType else null
            'F' -> if (desc.length == 1) Float::class.javaPrimitiveType else null
            'J' -> if (desc.length == 1) Long::class.javaPrimitiveType else null
            'D' -> if (desc.length == 1) Double::class.javaPrimitiveType else null
            '[' -> {
                val elem = resolveTypeDescriptor(cl, desc.substring(1)) ?: return null
                runCatching { java.lang.reflect.Array.newInstance(elem, 0).javaClass }.getOrNull()
            }
            'L' -> {
                if (!desc.endsWith(";") || desc.length < 3) return null
                val dotName = desc.substring(1, desc.length - 1).replace('/', '.')
                runCatching { Class.forName(dotName, false, cl) }.getOrNull()
            }
            else -> null
        }
    }

    fun splitParamDescriptors(paramsPart: String): List<String>? {
        val result = ArrayList<String>()
        var i = 0
        val len = paramsPart.length
        while (i < len) {
            val start = i
            while (i < len && paramsPart[i] == '[') {
                i++
            }
            if (i >= len) return null
            val ch = paramsPart[i]
            if (ch == 'L') {
                val semi = paramsPart.indexOf(';', i + 1)
                if (semi < 0) return null
                i = semi + 1
            } else if (ch in "ZBCSIFJD") {
                i++
            } else {
                return null
            }
            result.add(paramsPart.substring(start, i))
        }
        return result
    }

    fun resolveMethod(cl: ClassLoader, dexDescriptor: String?): Method? {
        if (dexDescriptor.isNullOrBlank()) return null
        val arrowIdx = dexDescriptor.indexOf("->")
        val openParen = dexDescriptor.indexOf('(', arrowIdx + 2)
        val closeParen = dexDescriptor.indexOf(')', openParen + 1)
        if (arrowIdx <= 0 || openParen <= arrowIdx + 2 || closeParen <= openParen) return null

        val ownerDesc = dexDescriptor.substring(0, arrowIdx)
        val methodName = dexDescriptor.substring(arrowIdx + 2, openParen)
        if (methodName == "<init>" || methodName == "<clinit>") return null
        val paramsPart = dexDescriptor.substring(openParen + 1, closeParen)
        val returnDesc = dexDescriptor.substring(closeParen + 1)

        val ownerClass = resolveTypeDescriptor(cl, ownerDesc) ?: return null
        val paramDescs = splitParamDescriptors(paramsPart) ?: return null
        val paramClasses = ArrayList<Class<*>>(paramDescs.size)
        for (pDesc in paramDescs) {
            val cls = resolveTypeDescriptor(cl, pDesc) ?: return null
            paramClasses.add(cls)
        }
        val expectedReturn = resolveTypeDescriptor(cl, returnDesc)
        val direct = runCatching {
            ownerClass.getDeclaredMethod(methodName, *paramClasses.toTypedArray()).apply {
                isAccessible = true
            }
        }.getOrNull()
        if (direct != null && (expectedReturn == null || direct.returnType == expectedReturn)) {
            return direct
        }
        if (expectedReturn != null) {
            return ownerClass.declaredMethods.firstOrNull { m ->
                m.name == methodName &&
                    m.returnType == expectedReturn &&
                    m.parameterTypes.contentEquals(paramClasses.toTypedArray())
            }?.apply { isAccessible = true }
        }
        return direct
    }

    fun resolveConstructor(cl: ClassLoader, dexDescriptor: String?): Constructor<*>? {
        if (dexDescriptor.isNullOrBlank()) return null
        val arrowIdx = dexDescriptor.indexOf("->")
        val openParen = dexDescriptor.indexOf('(', arrowIdx + 2)
        val closeParen = dexDescriptor.indexOf(')', openParen + 1)
        if (arrowIdx <= 0 || openParen <= arrowIdx + 2 || closeParen <= openParen) return null

        val ownerDesc = dexDescriptor.substring(0, arrowIdx)
        val methodName = dexDescriptor.substring(arrowIdx + 2, openParen)
        if (methodName != "<init>") return null
        val paramsPart = dexDescriptor.substring(openParen + 1, closeParen)

        val ownerClass = resolveTypeDescriptor(cl, ownerDesc) ?: return null
        val paramDescs = splitParamDescriptors(paramsPart) ?: return null
        val paramClasses = ArrayList<Class<*>>(paramDescs.size)
        for (pDesc in paramDescs) {
            val cls = resolveTypeDescriptor(cl, pDesc) ?: return null
            paramClasses.add(cls)
        }
        return runCatching {
            ownerClass.getDeclaredConstructor(*paramClasses.toTypedArray()).apply {
                isAccessible = true
            }
        }.getOrNull()
    }

    fun resolveField(cl: ClassLoader, dexDescriptor: String?): Field? {
        if (dexDescriptor.isNullOrBlank()) return null
        val arrowIdx = dexDescriptor.indexOf("->")
        val colonIdx = dexDescriptor.indexOf(':', arrowIdx + 2)
        if (arrowIdx <= 0 || colonIdx <= arrowIdx + 2) return null

        val ownerDesc = dexDescriptor.substring(0, arrowIdx)
        val fieldName = dexDescriptor.substring(arrowIdx + 2, colonIdx)
        val typeDesc = dexDescriptor.substring(colonIdx + 1)
        val ownerClass = resolveTypeDescriptor(cl, ownerDesc) ?: return null
        val field = runCatching {
            ownerClass.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull() ?: return null
        val expectedType = resolveTypeDescriptor(cl, typeDesc)
        if (expectedType != null && field.type != expectedType) {
            return null
        }
        return field
    }
}
