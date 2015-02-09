package com.ptby.dynamicreturntypeplugin.signature_processingv2

import com.intellij.openapi.project.Project
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import java.util.ArrayList
import com.ptby.dynamicreturntypeplugin.index.LocalClassImpl
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.PhpIndex
import com.ptby.dynamicreturntypeplugin.signatureconversion.SignatureMatcher
import com.ptby.dynamicreturntypeplugin.index.ClassConstantAnalyzer
import com.ptby.dynamicreturntypeplugin.config.ClassMethodConfigKt
import com.ptby.dynamicreturntypeplugin.signatureconversion.CustomSignatureProcessor
import com.ptby.dynamicreturntypeplugin.signatureconversion.CustomMethodCallSignature

public class ReturnValueFromParametersProcessor(private val signatureMatcher: SignatureMatcher,
                                                private val classConstantAnalyzer: ClassConstantAnalyzer,
                                                private val customSignatureProcessor: CustomSignatureProcessor
                                                ) {


    fun getReturnValue(project: Project,
                       classMethodConfigKt: ClassMethodConfigKt,
                       classCall : ClassCall,
                       parameters: Array<String>,
                       phpIndex : PhpIndex): ReturnType {
        val selectedParameter = parameters[classMethodConfigKt.parameterIndex]
        val treatedParameter = classMethodConfigKt.formatBeforeLookup(selectedParameter)

        if ( treatedParameter.contains("|")) {
            return ReturnType( createMultiTypedFromMask(treatedParameter, project) )
        }


        val customMethodCallSignature = CustomMethodCallSignature.new(
                "#M#C" + classCall.fqnClass,
                classCall.method,
                treatedParameter
        )


        val collection = customSignatureProcessor.processSignature(phpIndex,
                                                                   customMethodCallSignature,
                                                                   project,
                                                                   customMethodCallSignature.rawStringSignature)

        return ReturnType( collection )
    }


    private fun createMultiTypedFromMask(formattedSignature: String, project: Project): Collection<PhpNamedElement>? {
        val customList = ArrayList<PhpNamedElement>()
        formattedSignature.split("\\|").reverse().forEach { type ->
            customList.add(LocalClassImpl(PhpType().add("#C" + type.trimLeading("#K#C").trimTrailing(".")), project))
        }

        return customList
    }
}


data class ReturnType(  val phpNamedElements: Collection<PhpNamedElement>?) {
    private var fqnClassName : String? = null

    fun getClassName() : String {
        if( fqnClassName == null ){
            fqnClassName = ""
            if( phpNamedElements != null && hasFoundReturnType() ){
                val phpNamedElement = phpNamedElements.iterator().next()
                fqnClassName = if( phpNamedElement.getFQN() == null ){
                    ""
                }else{
                    phpNamedElement.getFQN()
                }
            }
        }

        return fqnClassName as String
    }

    fun hasFoundReturnType() : Boolean {
        return phpNamedElements != null && phpNamedElements.size() > 0
    }

}