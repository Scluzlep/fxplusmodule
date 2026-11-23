package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

interface FxHookInstaller {
    fun install(id: String, executable: Executable?, hooker: XposedInterface.Hooker)
    fun logInfo(message: String)
    fun logWarn(message: String, error: Throwable? = null)
    fun logDebug(message: String)
}

enum class FxUserErrorKind {
    ACCESS,
    READ,
    WRITE,
    EXISTS
}

internal object FxHookSupport {

    private val KNOWN_FACTORY_NAMES_9012 = mapOf(
        FxUserErrorKind.ACCESS to listOf("P", "q0", "c0"),
        FxUserErrorKind.READ to listOf("c0", "P"),
        FxUserErrorKind.WRITE to listOf("q0", "P"),
        FxUserErrorKind.EXISTS to listOf("l", "P")
    )

    private val KNOWN_FACTORY_NAMES_9108 = mapOf(
        FxUserErrorKind.ACCESS to listOf("q", "y", "v"),
        FxUserErrorKind.READ to listOf("v", "q"),
        FxUserErrorKind.WRITE to listOf("y", "q"),
        FxUserErrorKind.EXISTS to listOf("f", "q")
    )

    private val GENERIC_FACTORY_NAMES = mapOf(
        FxUserErrorKind.ACCESS to listOf("P", "q", "q0", "y"),
        FxUserErrorKind.READ to listOf("c0", "v", "P", "q"),
        FxUserErrorKind.WRITE to listOf("q0", "y", "P", "q"),
        FxUserErrorKind.EXISTS to listOf("l", "f", "P", "q")
    )

    fun resolveStringId(context: Context, name: String): Int {
        return context.resources.getIdentifier(name, "string", context.packageName)
    }

    internal fun selectUserExceptionFactory(
        userExClass: Class<*>,
        kind: FxUserErrorKind = FxUserErrorKind.ACCESS
    ): Method? {
        val candidates = when (userExClass.name) {
            "se.l" -> KNOWN_FACTORY_NAMES_9012.getValue(kind)
            "hh.l" -> KNOWN_FACTORY_NAMES_9108.getValue(kind)
            else -> GENERIC_FACTORY_NAMES.getValue(kind)
        }
        val twoArgFactories = userExClass.declaredMethods.filter { m ->
            Modifier.isStatic(m.modifiers) &&
                m.returnType == userExClass &&
                m.parameterTypes.size == 2 &&
                Throwable::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                m.parameterTypes[1] == String::class.java
        }
        for (preferredName in candidates) {
            val hit = twoArgFactories.firstOrNull { it.name == preferredName }
            if (hit != null) return hit
        }
        return twoArgFactories.minByOrNull { it.name }
    }

    fun buildUserException(
        userExClass: Class<*>?,
        message: String,
        kind: FxUserErrorKind = FxUserErrorKind.ACCESS
    ): Throwable {
        if (userExClass != null) {
            runCatching {
                val ctor = userExClass.declaredConstructors
                    .filter { c ->
                        val p = c.parameterTypes
                        p.size == 1 && (p[0] == String::class.java || p[0] == CharSequence::class.java)
                    }
                    .minByOrNull { it.parameterTypes[0].name }
                if (ctor != null) {
                    ctor.isAccessible = true
                    return ctor.newInstance(message) as Throwable
                }
            }
            val throwFactory = selectUserExceptionFactory(userExClass, kind)
            if (throwFactory != null) {
                throwFactory.isAccessible = true
                runCatching {
                    val cause = java.io.IOException(message)
                    return throwFactory.invoke(null, cause, message) as Throwable
                }
            }
        }
        return java.io.IOException(message)
    }

    fun isFxCancelException(symbols: ResolvedSymbols, throwable: Throwable): Boolean {
        val cancelClass = symbols.androidData.cancelExceptionClass
        return cancelClass != null && cancelClass.isInstance(throwable)
    }

    fun formatThrowable(t: Throwable): String {
        return "${t.javaClass.simpleName}: ${t.message ?: ""}"
    }

    fun logExceptionSafe(tag: String, msg: String, t: Throwable) {
        Log.w(tag, msg, t)
    }
}
