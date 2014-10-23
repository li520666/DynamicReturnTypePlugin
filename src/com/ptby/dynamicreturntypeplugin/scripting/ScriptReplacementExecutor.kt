// require(url:'https://scripting.dev.java.net', jar:'groovy-engine.jar')
package com.ptby.dynamicreturntypeplugin.scripting

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.ptby.dynamicreturntypeplugin.scripting.api.ExecutingScriptApi

import javax.script.Invocable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

public class ScriptReplacementExecutor [throws(javaClass<ScriptException>())]( scriptLanguage: String,
                                                                               public val phpCallReferenceInfo: PhpCallReferenceInfo,
                                                                               public val callableScriptConfiguration: CallableScriptConfiguration) {
    private val invocable: Invocable
    private val scriptSignatureParser: ScriptSignatureParser


    {
        val executingScriptApi = ExecutingScriptApi(this)

        val manager = ScriptEngineManager()
        var engine = manager.getEngineByName(scriptLanguage)
        if (engine == null) {

            val scriptEngineJarLoader = CustomScriptEngineJarLoader.createScriptEngineJarLoader(scriptLanguage)
            engine = scriptEngineJarLoader.tryLoadingFromCustomPath()

            if ( engine == null ) {
                throw ScriptException(
                        "Script engine '" + scriptLanguage + "' was not created. Relevant jar may not be in classpath."
                )
            }
        }

        engine.eval(callableScriptConfiguration.code)
        engine.put("api", executingScriptApi)

        invocable = engine as Invocable
        scriptSignatureParser = ScriptSignatureParser()
    }


    public fun executeAndReplace(currentValue: String): String {
        val parsedSignature = scriptSignatureParser.parseSignature(currentValue)
        if (parsedSignature == null) {
            return ""
        }

        try {
            val result = invocable.invokeFunction(
                    callableScriptConfiguration.call,
                    parsedSignature.namespace,
                    parsedSignature.returnClassName
            )
            return parsedSignature.prefix + result
        } catch (e: ScriptException) {
            val message = "Error executing " + callableScriptConfiguration.call + " in " +
                    callableScriptConfiguration.fileLocation + "\n" + e.getMessage()

            Notifications.Bus.notify(createWarningNotification(message))
        } catch (e: NoSuchMethodException) {
            val message = "No such method " + callableScriptConfiguration.call + " in " +
                    callableScriptConfiguration.fileLocation + "\n" + e.getMessage()

            Notifications.Bus.notify(createWarningNotification(message))
        }

        return ""
    }


    private fun createWarningNotification(message: String): Notification {
        return Notification("DynamicReturnTypePlugin", "Script file error", message, NotificationType.WARNING)
    }

    class object {
        public val SCRIPT_LANGUAGE_JAVASCRIPT: String = "JavaScript"
        public val SCRIPT_LANGUAGE_GROOVY: String = "groovy"
    }
}
