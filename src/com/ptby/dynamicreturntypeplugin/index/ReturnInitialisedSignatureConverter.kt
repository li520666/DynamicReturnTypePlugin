package com.ptby.dynamicreturntypeplugin.index

import com.intellij.openapi.project.Project
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.impl.FunctionImpl
import com.ptby.dynamicreturntypeplugin.signatureconversion.CustomMethodCallSignature

public class ReturnInitialisedSignatureConverter {


    public fun convertSignatureToClassSignature(signature: CustomMethodCallSignature,
                                                project: Project): CustomMethodCallSignature {
        val phpIndex = PhpIndex.getInstance(project)

        val cleanedVariableSignature = signature.className.substring(2)
        val bySignature = phpIndex.getBySignature(cleanedVariableSignature)
        if (bySignature.size() == 0) {
            return signature
        }

        val firstSignatureMatch = bySignature.iterator().next() as FunctionImpl
        return CustomMethodCallSignature.new(
                "#M#C" + firstSignatureMatch.getType(),
                signature.method,
                signature.desiredParameter
        )
    }
}