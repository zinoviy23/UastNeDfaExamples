@file:Suppress("UnstableApiUsage")

package zinoviy23.uastNeDfaExamples.buttonVisualisation

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.AttributesTransformerPresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiJavaPatterns.psiMethod
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.ui.layout.panel
import org.jetbrains.uast.*
import org.jetbrains.uast.analysis.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.awt.*
import javax.swing.JComponent

private const val J_BUTTON_CLASS = "javax.swing.JButton"
private const val J_ABSTRACT_BUTTON = "javax.swing.AbstractButton"
private const val J_COMPONENT = "javax.swing.JComponent"
private const val J_COLOR = "java.awt.Color"

private val jComponentMethod = psiMethod().definedInClass(J_COMPONENT)

class ButtonInlayProvider : InlayHintsProvider<NoSettings> {
    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        file.toUElement() ?: return null
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val uVariable = element.toUElement() as? ULocalVariable ?: return true
                if (!uVariable.type.equalsToText(J_BUTTON_CLASS)) return true

                val offset = element.textRange.startOffset
                val line = editor.document.getLineNumber(offset)
                val startOffset = editor.document.getLineStartOffset(line)
                val column = offset - startOffset

                var lastReference: USimpleNameReferenceExpression? = null
                uVariable.getContainingUMethod()?.accept(object : AbstractUastVisitor() {
                    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                        if (node.resolveToUElement() == uVariable) {
                            lastReference = node
                        }
                        return true
                    }
                })
                val calculateValueForVariable = lastReference?.let {
                    UNeDfaValueEvaluator(ButtonInfoEvaluationStrategy).calculateContainingBuilderValue(
                        it,
                        UNeDfaConfiguration(builderEvaluators = listOf(ButtonBuilderEvaluator)),
                        fallbackWithCurrentElement = true
                    )
                }
                val variants = calculateValueForVariable?.flatten()?.map { it.toInlay(factory) }?.takeUnless { it.isEmpty() } ?: return true
                val preview = factory.join(variants) {
                    factory.text(" | ")
                }

                sink.addBlockElement(
                    offset = element.textOffset,
                    relatesToPrecedingText = false,
                    showAbove = true,
                    priority = 10,
                    presentation = factory.seq(
                        factory.textSpacePlaceholder(column, true),
                        factory.smallTextWithoutBackground("Preview: "),
                        preview,
                    )
                )

                return true
            }
        }
    }

    override val key: SettingsKey<NoSettings>
        get() = KEY

    override val name: String
        get() = "JButton inlay hint"

    override val previewText: String?
        get() = null

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = panel {}
    }

    override fun createSettings(): NoSettings = NoSettings()

    companion object {
        private val KEY = SettingsKey<NoSettings>("jbutton.inlay.hint")
    }
}

private sealed class ColorInfo {
    data class JavaColor(val color: Color) : ColorInfo()

    object Unknown : ColorInfo()

    companion object {
        fun fromReference(reference: UReferenceExpression): ColorInfo {
            val field = reference.resolve() as? PsiField ?: return Unknown
            if (field.containingClass?.qualifiedName != J_COLOR) return Unknown
            if (!field.hasModifier(JvmModifier.STATIC)) return Unknown

            val color = Color::class.java.fields.find { it.name == field.name }?.get(null) as? Color ?: return Unknown
            return JavaColor(color)
        }
    }
}

private data class ButtonInfo(
    val text: PartiallyKnownString,
    val backgroundColor: Set<ColorInfo>,
    val foregroundColor: Set<ColorInfo>
) {
    fun flatten() = flatten(text)
        .flatMap { textVariant ->
            backgroundColor.flatMap { backgroundColorVariant ->
                foregroundColor.map { foregroundColorVariant ->
                    ButtonInfo(textVariant, setOf(backgroundColorVariant), setOf(foregroundColorVariant))
                }
            }
        }

    fun toInlay(factory: InlayPresentationFactory): InlayPresentation {
        return factory.container(
            AttributesTransformerPresentation(factory.text(text.segments.joinToString(separator = "") {
                when (it) {
                    is StringEntry.Unknown -> "<???>"
                    is StringEntry.Known -> it.value
                }
            })) {
                it.clone().apply {
                    foregroundColor = (this@ButtonInfo.foregroundColor.single() as? ColorInfo.JavaColor)?.color ?: Color.BLACK
                }
            },
            background = (this@ButtonInfo.backgroundColor.single() as? ColorInfo.JavaColor)?.color ?: Color.WHITE,
            padding = InlayPresentationFactory.Padding(5, 5, 5, 5),
            roundedCorners = InlayPresentationFactory.RoundedCorners(2, 2),
            backgroundAlpha = 1.0f
        )
    }
}

private object ButtonInfoEvaluationStrategy : UNeDfaValueEvaluator.UValueEvaluatorStrategy<ButtonInfo> {
    override fun constructUnknownValue(element: UElement): ButtonInfo {
        return ButtonInfo(PartiallyKnownString.empty, emptySet(), emptySet())
    }

    override fun constructValueFromList(element: UElement, values: List<ButtonInfo>?): ButtonInfo {
        val combinedText = UStringEvaluationStrategy.constructValueFromList(element, values?.map { it.text })
        val combinedBackgroundColor = values?.flatMap { it.backgroundColor.or(ColorInfo.Unknown) }?.toSet().orEmpty()
        val combinedForegroundColor = values?.flatMap { it.foregroundColor.or(ColorInfo.Unknown) }?.toSet().orEmpty()

        return ButtonInfo(combinedText, combinedBackgroundColor, combinedForegroundColor)
    }
}

private fun <T> Collection<T>.or(vararg elements: T): Collection<T> {
    if (isNotEmpty()) return this
    return listOf(*elements)
}

private object ButtonBuilderEvaluator : BuilderLikeExpressionEvaluator<ButtonInfo> {
    override val allowSideEffects: Boolean
        get() = true

    override val buildMethod: ElementPattern<PsiMethod>
        get() = PlatformPatterns.or(
            psiMethod().definedInClass(J_ABSTRACT_BUTTON).withName("setText"),
            jComponentMethod.withName("setForeground"),
            jComponentMethod.withName("setBackground"),
        )

    override val dslBuildMethodDescriptor: DslLikeMethodDescriptor<ButtonInfo>?
        get() = null

    override val methodDescriptions: Map<ElementPattern<PsiMethod>, BuilderMethodEvaluator<ButtonInfo>>
        get() = mapOf(
            psiMethod().definedInClass(J_ABSTRACT_BUTTON).withName("setText") to
                    BuilderMethodEvaluator { call, value, _, _, _ ->
                        val text = calculateStringParameter(call)
                        value?.copy(text = text) ?: ButtonInfo(text, emptySet(), emptySet())
                    },
            jComponentMethod.withName("setForeground") to
                    BuilderMethodEvaluator { call, value, _, _, _ ->
                        val color = (call.getArgumentForParameter(0) as? UReferenceExpression)?.let { ColorInfo.fromReference(it) } ?: ColorInfo.Unknown
                        value?.copy(foregroundColor = setOf(color)) ?: ButtonInfo(PartiallyKnownString.empty, emptySet(), setOf(color))
                    },
            jComponentMethod.withName("setBackground") to
                    BuilderMethodEvaluator { call, value, _, _, _ ->
                        val color = (call.getArgumentForParameter(0) as? UReferenceExpression)?.let { ColorInfo.fromReference(it) } ?: ColorInfo.Unknown
                        value?.copy(backgroundColor = setOf(color)) ?: ButtonInfo(PartiallyKnownString.empty, setOf(color), emptySet())
                    }
        )
}

private fun flatten(pks: PartiallyKnownString) =
    mutableListOf<PartiallyKnownString>().also { collectStrings(PartiallyKnownString.empty, pks.segments, it) }

@Suppress("NAME_SHADOWING")
private fun collectStrings(
    currentString: PartiallyKnownString,
    segments: List<StringEntry>,
    result: MutableList<PartiallyKnownString>
) {
    var currentString = currentString
    var segments = segments
    while (segments.isNotEmpty()) {
        val currentSegment = segments.first()
        val possibleValues = (currentSegment as? StringEntry.Unknown)?.possibleValues?.toList()
        segments = segments.drop(1)
        if (possibleValues.isNullOrEmpty()) {
            currentString = PartiallyKnownString(currentString.segments + currentSegment)
        }
        else {
            for (value in possibleValues) {
                collectStrings(PartiallyKnownString(currentString.segments + value.segments), segments, result)
            }
            return
        }
    }
    result += currentString
}

private fun stringEvaluationConfiguration(file: PsiFile?): UNeDfaConfiguration<PartiallyKnownString> =
    UNeDfaConfiguration(
        usagesSearchScope = file?.let { LocalSearchScope(file) } ?: LocalSearchScope.EMPTY,
        parameterUsagesDepth = 2,
        builderEvaluators = listOf(UStringBuilderEvaluator)
    )

private fun calculateStringParameter(call: UCallExpression, parameterIndex: Int = 0) =
    call.getArgumentForParameter(parameterIndex)?.let { argument ->
        UStringEvaluator().calculateValue(argument, stringEvaluationConfiguration(argument.sourcePsi?.containingFile))
            ?: UStringEvaluationStrategy.constructUnknownValue(argument)
    } ?: UStringEvaluationStrategy.constructUnknownValue(call)