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
    private val k8Grammar = "symbol = 0 1 2 3 4 5 6 7 8 9 + - / ÷ = . , % | ( ) : * x\n" +
        "leftpar = (\n" +
        "rightpar = )\n" +
        "currency_symbol = \$ R € ₹ £\n" +
        "character ::= identity(symbol)\n" +
        "            | identity(currency_symbol)\n" +
        "fractionless ::= identity(character)\n" +
        "               | fence (fractionless, leftpar, rightpar)\n" +
        "               | hpair(fractionless, fractionless)\n" +
        "fractionable ::= identity(character)\n" +
        "               | fence (fractionable, leftpar, rightpar)\n" +
        "               | hpair(fractionable, fractionable)\n" +
        "               | fraction(fractionless, fractionless)\n" +
        "expression ::= identity(character)\n" +
        "             | fence (expression, leftpar, rightpar)\n" +
        "             | hpair(expression, expression)\n" +
        "             | fraction(fractionable, fractionable)\n" +
        "start(expression)"

    @Throws(IllegalArgumentException::class, RuntimeException::class, IOException::class)
    fun build(eng : Engine, filePath : String) {
        val rab = eng.createRecognitionAssetsBuilder()
        rab.compile("Math Grammar",k8Grammar)
        val file: File = File(filePath, File.separator + fileName)
        rab.store(file.path)
    }
}
