/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.assessment

import com.myscript.iink.Engine
import java.io.File
import java.io.IOException


class MathGrammarK8DynamicRes
{
    private val fileName = "math-grm-standardK8.res"
    private val k8Grammar = """symbol = 0 1 2 3 4 5 6 7 8 9 + - / ÷ = . , % | ( ) : * x
leftpar = (
rightpar = )
currency_symbol = $ R € ₹ £
character ::= identity(symbol)
            | identity(currency_symbol)
fractionless ::= identity(character)
               | fence (fractionless, leftpar, rightpar)
               | hpair(fractionless, fractionless)
fractionable ::= identity(character)
               | fence (fractionable, leftpar, rightpar)
               | hpair(fractionable, fractionable)
               | fraction(fractionless, fractionless)
expression ::= identity(character)
             | fence (expression, leftpar, rightpar)
             | hpair(expression, expression)
             | fraction(fractionable, fractionable)
start(expression)"""

    @Throws(IllegalArgumentException::class, RuntimeException::class, IOException::class)
    fun build(eng : Engine, filePath : String) {
        val rab = eng.createRecognitionAssetsBuilder()
        rab.compile("Math Grammar",k8Grammar)
        val file: File = File(filePath, File.separator + fileName)
        rab.store(file.path)
    }
}
