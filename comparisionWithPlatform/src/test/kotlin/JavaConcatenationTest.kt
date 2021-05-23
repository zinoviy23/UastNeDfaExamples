@file:Suppress("UnstableApiUsage")

import com.intellij.psi.PsiExpression
import com.intellij.psi.util.CachedValuesManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.CachedValuesManagerImpl
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.intellij.plugins.intelliLang.Configuration
import org.intellij.plugins.intelliLang.util.SubstitutedExpressionEvaluationHelper
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.analysis.UStringEvaluator
import org.jetbrains.uast.getUastParentOfType
import kotlin.system.measureTimeMillis
import kotlin.test.fail as kFail

class JavaConcatenationTest : LightJavaCodeInsightFixtureTestCase() {
    private val expression: UExpression
        get() = file.findElementAt(myFixture.caretOffset)
            .getUastParentOfType<UReturnExpression>()?.returnExpression
            ?: kFail("cannot find expression")

    fun `test literal`() = doTest(
        """
            class MyFile {
              String s() {
                return /*<caret>*/ "mySimpleString";
              }
            }
        """.trimIndent(),
        "mySimpleString"
    )

    fun `test simple concatenation`() = doTest(
        """
            class MyFile {            
              String s() {
                return /*<caret>*/ "aaa" + "bbb" + "ccc";
              }
            }
        """.trimIndent(),
        "aaabbbccc"
    )

    fun `test concatenation with static final field`() = doTest(
        """
            class MyFile {
                public static final String a = "aaa";
                public static final String b = "bbb";
                public static final String d = "ddd";
                
                String s() {
                    return /*<caret>*/ a + b + "ccc" + d;
                }
            }
        """.trimIndent(),
        "aaabbbcccddd"
    )

    fun `test concatenation with final field`() = doTest(
        """
            class MyFile {
                private final String a = "aaa";
                private final String b = "bbb";
                private final String d = "ddd";
            
                String s() {
                    return /*<caret>*/ a + b + "ccc" + d;
                }
            }
        """.trimIndent(),
        "aaabbbcccddd"
    )

    fun `test concatenation with final var`() = doTest(
        """
            class MyFile {
                String s() {
                    final String a = "aaa";
                    final String b = "bbb";
                    final String d = "ddd";
                    return /*<caret>*/ a + b + "ccc" + d;
                }
            }
        """.trimIndent(),
        "aaabbbcccddd"
    )

    fun `test concatenation with var`() = doTest(
        """
            class MyFile {
                String s() {
                    String a = "aaa";
                    String b = "bbb";
                    String d = "ddd";
                    return /*<caret>*/ a + b + "ccc" + d;
                }
            }
        """.trimIndent(),
        "aaabbbcccddd"
    )

    fun `test concatenation with reassignment`() = doTest(
        """
            class MyFile {
                String s() {
                    String a = "aaa";
                    String b = "bbb";
                    String d = "ddd";
                    
                    String aa = a;
                    String bb = b;
                    String dd = d;
                    return /*<caret>*/ aa + bb + "ccc" + dd;
                }
            }
        """.trimIndent(),
        "aaabbbcccddd"
    )

    fun `test concatenation with many reassignments`() = doTest(
        """
            class MyFile {
                String s() {
                    String s0 = "|";
                    ${(1..100).joinToString(separator = "; ") { """String s$it = s${it - 1} + "a$it"""" }}
                    return /*<caret>*/ s100 + "|";
                }
            }
        """.trimIndent(),
        (1..100).joinToString(separator = "", prefix = "|", postfix = "|") { "a$it" }
    )

    fun `test many concatenations`() = doTest(
        """
            class MyFile {
              String s() {
                return /*<caret>*/ ${(1..500).joinToString(separator = " + ") { """ "a$it" """ }}
              }
            }
        """.trimIndent(),
        (1..500).joinToString(separator = "") { "a$it" }
    )

    fun `test concatenations of many variables`() = doTest(
        """
            class MyFile {
              String s() {
                ${(1..500).joinToString(separator = "\n") { """String s$it = "a$it"; """ }}
                return /*<caret>*/ ${(1..500).joinToString(separator = " + ") { """ s$it """ }}
              }
            }
        """.trimIndent(),
        (1..500).joinToString(separator = "") { "a$it" }
    )

    private val testName: String
        get() {
            return getTestName(true).trim()
        }

    private fun doTest(
        @Language("Java", prefix = """@SuppressWarnings("ALL")""") source: String,
        result: String,
        times: Int = 10000
    ) {
        myFixture.configureByText("MyFile.java", source)

        myFixture.doHighlighting()

        val evaluators = mapOf(
            "Java[option=OFF]" to { evaluateWith(Configuration.DfaOption.OFF) },
            "Java[option=ASSIGNMENT]" to { evaluateWith(Configuration.DfaOption.ASSIGNMENTS) },
            "Java[option=RESOLVE]" to { evaluateWith(Configuration.DfaOption.RESOLVE) },
            "Java[option=DFA]" to { evaluateWith(Configuration.DfaOption.DFA) },
            "UNeDfa" to { UStringEvaluator().calculateValue(expression)?.valueIfKnown }
        )

        evaluators.values.forEach { it() }

        val results: Map<String, Double?>
        val allTime = measureTimeMillis {
            results = evaluators.mapValues { (_, evaluator) ->
                kotlin.runCatching {
                    (1..times).map {
                        psiManager.dropPsiCaches()
                        (CachedValuesManager.getManager(project) as CachedValuesManagerImpl).clearCachedValues()

                        measureTimeMillis {
                            TestCase.assertEquals(result, evaluator())
                        }
                    }
                }.getOrNull()?.average()
            }
        }


        if (!isHeaderPrinted) {
            isHeaderPrinted = true
            println(results.entries.joinToString(
                separator = " & ",
                prefix = " ".repeat(44),
                postfix = """ \\"""
            ) { "%27s".format(it.key) })
        }
        println(results.entries.joinToString(
            separator = " & ",
            prefix = "%44s".format("$testName & "),
            postfix = """ \\ took time = $allTime ms"""
        ) {
            it.value?.let { time ->
                "%24.4f ms".format(time)
            } ?: "%27s".format("null")
        })
    }

    private fun evaluateWith(option: Configuration.DfaOption) =
        SubstitutedExpressionEvaluationHelper(project).computeExpression(
            expression.sourcePsi as PsiExpression,
            option,
            false,
            mutableListOf()
        )

    companion object {
        private var isHeaderPrinted = false
    }
}