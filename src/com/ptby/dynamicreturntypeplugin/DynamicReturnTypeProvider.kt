package com.ptby.dynamicreturntypeplugin

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider2
import com.ptby.dynamicreturntypeplugin.config.ConfigState
import com.ptby.dynamicreturntypeplugin.config.ConfigStateContainer
import com.ptby.dynamicreturntypeplugin.gettype.GetTypeResponse
import com.ptby.dynamicreturntypeplugin.gettype.GetTypeResponseFactory
import com.ptby.dynamicreturntypeplugin.index.ClassConstantAnalyzer
import com.ptby.dynamicreturntypeplugin.index.FieldReferenceAnalyzer
import com.ptby.dynamicreturntypeplugin.index.LocalClassImpl
import com.ptby.dynamicreturntypeplugin.index.ReturnInitialisedSignatureConverter
import com.ptby.dynamicreturntypeplugin.index.VariableAnalyser
import com.ptby.dynamicreturntypeplugin.json.ConfigAnalyser
import com.ptby.dynamicreturntypeplugin.scanner.FunctionCallReturnTypeScanner
import com.ptby.dynamicreturntypeplugin.scanner.MethodCallReturnTypeScanner
import com.ptby.dynamicreturntypeplugin.signatureconversion.BySignatureSignatureSplitter
import com.ptby.dynamicreturntypeplugin.signatureconversion.CustomSignatureProcessor
import com.ptby.dynamicreturntypeplugin.typecalculation.CallReturnTypeCalculator

import java.util.ArrayList

import com.intellij.openapi.diagnostic.Logger.getInstance
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.impl.FunctionImpl
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.elements.Method

public class DynamicReturnTypeProvider : PhpTypeProvider2 {
    private val classConstantAnalyzer: ClassConstantAnalyzer
    private val getTypeResponseFactory: GetTypeResponseFactory
    private val returnInitialisedSignatureConverter: ReturnInitialisedSignatureConverter
    private val logger = getInstance("DynamicReturnTypePlugin")
    private val fieldReferenceAnalyzer: FieldReferenceAnalyzer
    private var variableAnalyser: VariableAnalyser

    {
        val configState = ConfigStateContainer.configState

        val configAnalyser = configState.configAnalyser
        fieldReferenceAnalyzer = FieldReferenceAnalyzer(configAnalyser)
        classConstantAnalyzer = ClassConstantAnalyzer()
        variableAnalyser = VariableAnalyser(configAnalyser, classConstantAnalyzer)
        returnInitialisedSignatureConverter = ReturnInitialisedSignatureConverter()
        variableAnalyser = VariableAnalyser(configAnalyser, classConstantAnalyzer)
        getTypeResponseFactory = createGetTypeResponseFactory(configAnalyser)

        //for ( moo in System.getenv()) {
        //    println( moo.key.toString() + "=" + moo.value.toString() )
        //}

    }

    class object {
        public val PLUGIN_IDENTIFIER_KEY: Char = "Ђ".toCharArray()[0]
        public val PLUGIN_IDENTIFIER_KEY_STRING: String = String(charArray(PLUGIN_IDENTIFIER_KEY))
    }


    private fun createGetTypeResponseFactory(configAnalyser: ConfigAnalyser): GetTypeResponseFactory {
        val callReturnTypeCalculator = CallReturnTypeCalculator()
        val functionCallReturnTypeScanner = FunctionCallReturnTypeScanner(callReturnTypeCalculator)
        val methodCallReturnTypeScanner = MethodCallReturnTypeScanner(callReturnTypeCalculator)

        return GetTypeResponseFactory(configAnalyser, methodCallReturnTypeScanner, functionCallReturnTypeScanner)
    }


    override fun getKey(): Char {
        return PLUGIN_IDENTIFIER_KEY
    }


    override fun getType(psiElement: PsiElement): String? {
        try {
            val dynamicReturnType = getTypeResponseFactory.createDynamicReturnType(psiElement)
            if (dynamicReturnType.isNull()) {
                return null
            }

            return dynamicReturnType.toString()
        } catch (e: Exception) {
            if (e !is ProcessCanceledException) {
                logger.error("Exception", e)
                e.printStackTrace()
            }
        }

        return null
    }


    override fun getBySignature(signature: String, project: Project): Collection<PhpNamedElement>? {
        var filteredSignature = signature

        if ( filteredSignature.contains("Ő")) {
            //#M#Ő#M#C\TestController.getƀservice_broker:getServiceWithoutMask:#K#C\DynamicReturnTypePluginTestEnvironment\TestClasses\TestService.CLASS_NAME
            filteredSignature = trySymfonyContainer(project, signature)
        }

        if (filteredSignature.contains("[]")) {
            val customList = ArrayList<PhpNamedElement>()
            customList.add(LocalClassImpl(PhpType().add(filteredSignature), project))

            return customList
        }

        val bySignatureSignatureSplitter = BySignatureSignatureSplitter()
        var bySignature: Collection<PhpNamedElement>? = null
        var lastFqnName = ""
        for (chainedSignature in bySignatureSignatureSplitter.createChainedSignatureList(filteredSignature)) {
            val newSignature = lastFqnName + chainedSignature
            bySignature = processSingleSignature(newSignature, project)

            if (bySignature != null && bySignature!!.iterator().hasNext()) {
                lastFqnName = "#M#C" + bySignature!!.iterator().next().getFQN()
            }
        }

        return bySignature
    }


    private fun processSingleSignature(signature: String, project: Project): Collection<PhpNamedElement>? {
        val bySignature: Collection<PhpNamedElement>?
        val customSignatureProcessor = CustomSignatureProcessor(
                returnInitialisedSignatureConverter,
                classConstantAnalyzer,
                fieldReferenceAnalyzer,
                variableAnalyser
        )

        bySignature = customSignatureProcessor.getBySignature(signature, project)
        return bySignature
    }


    private fun trySymfonyContainer(project: Project, signature: String): String {
        val startOfService = signature.indexOf("ƀ") + 1
        val endOfService = signature.indexOf(":", startOfService)
        if ( startOfService < 0 || endOfService < 0 ) {
            return signature
        }

        val service = signature.substring(startOfService, endOfService)
        val symfonyContainerLookup = SymfonyContainerLookup()
        val lookedUpReference = symfonyContainerLookup.lookup(project, service)
        if ( lookedUpReference == null ) {
            return signature;
        }

        val endOfServiceSeparator = signature.indexOf(":", endOfService)
        var methodCall = signature.substring(endOfServiceSeparator + 1)
        if ( !methodCall.contains("#") ) {
            methodCall = methodCall.replace(":", ":#K#C") + "."
        }

        val completedMethodCall = "#M#C\\" + lookedUpReference + ":" + methodCall

        //#M#C\DynamicReturnTypePluginTestEnvironment\TestClasses\ServiceBroker:getServiceWithoutMask:#K#C\DynamicReturnTypePluginTestEnvironment\TestClasses\TestService.
        //#M#C\DynamicReturnTypePluginTestEnvironment\TestClasses\ServiceBroker:getServiceWithoutMask:\DynamicReturnTypePluginTestEnvironment\TestClasses\TestService
        // println("completedMethodCall " + completedMethodCall)
        return completedMethodCall ;
    }
}
