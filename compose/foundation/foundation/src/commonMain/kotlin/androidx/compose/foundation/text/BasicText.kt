/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.foundation.text

import androidx.compose.foundation.text.modifiers.SelectableTextAnnotatedStringElement
import androidx.compose.foundation.text.modifiers.SelectionController
import androidx.compose.foundation.text.modifiers.TextAnnotatedStringElement
import androidx.compose.foundation.text.modifiers.TextStringSimpleElement
import androidx.compose.foundation.text.modifiers.hasLinks
import androidx.compose.foundation.text.selection.LocalSelectionRegistrar
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionRegistrar
import androidx.compose.foundation.text.selection.hasSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.floor

/**
 * Basic element that displays text and provides semantics / accessibility information.
 * Typically you will instead want to use [androidx.compose.material.Text], which is
 * a higher level Text element that contains semantics and consumes style information from a theme.
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object that callback provides contains paragraph information, size of the
 * text, baselines and other details. The callback can be used to add additional decoration or
 * functionality to the text. For example, to draw selection around the text.
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. It is required that 1 <= [minLines] <= [maxLines].
 * @param minLines The minimum height in terms of minimum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines].
 * @param color Overrides the text color provided in [style]
 */
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    color: ColorProducer? = null
) {
    validateMinMaxLines(
        minLines = minLines,
        maxLines = maxLines
    )
    val selectionRegistrar = LocalSelectionRegistrar.current
    val selectionController = if (selectionRegistrar != null) {
        val backgroundSelectionColor = LocalTextSelectionColors.current.backgroundColor
        val selectableId =
            rememberSaveable(selectionRegistrar, saver = selectionIdSaver(selectionRegistrar)) {
                selectionRegistrar.nextSelectableId()
            }
        remember(selectableId, selectionRegistrar, backgroundSelectionColor) {
            SelectionController(
                selectableId,
                selectionRegistrar,
                backgroundSelectionColor
            )
        }
    } else {
        null
    }
    val finalModifier = if (selectionController != null || onTextLayout != null) {
        modifier
            // TODO(b/274781644): Remove this graphicsLayer
            .graphicsLayer()
            .textModifier(
                AnnotatedString(text = text),
                style = style,
                onTextLayout = onTextLayout,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
                fontFamilyResolver = LocalFontFamilyResolver.current,
                placeholders = null,
                onPlaceholderLayout = null,
                selectionController = selectionController,
                color = color
            )
    } else {
        modifier
            // TODO(b/274781644): Remove this graphicsLayer
            .graphicsLayer() then TextStringSimpleElement(
            text = text,
            style = style,
            fontFamilyResolver = LocalFontFamilyResolver.current,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            color = color
        )
    }
    Layout(finalModifier, EmptyMeasurePolicy)
}

/**
 * Basic element that displays text and provides semantics / accessibility information.
 * Typically you will instead want to use [androidx.compose.material.Text], which is
 * a higher level Text element that contains semantics and consumes style information from a theme.
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object that callback provides contains paragraph information, size of the
 * text, baselines and other details. The callback can be used to add additional decoration or
 * functionality to the text. For example, to draw selection around the text.
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. It is required that 1 <= [minLines] <= [maxLines].
 * @param minLines The minimum height in terms of minimum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines].
 * @param inlineContent A map store composables that replaces certain ranges of the text. It's
 * used to insert composables into text layout. Check [InlineTextContent] for more information.
 * @param color Overrides the text color provided in [style]
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    color: ColorProducer? = null
) {
    validateMinMaxLines(
        minLines = minLines,
        maxLines = maxLines
    )
    val selectionRegistrar = LocalSelectionRegistrar.current
    val selectionController = if (selectionRegistrar != null) {
        val backgroundSelectionColor = LocalTextSelectionColors.current.backgroundColor
        val selectableId =
            rememberSaveable(selectionRegistrar, saver = selectionIdSaver(selectionRegistrar)) {
                selectionRegistrar.nextSelectableId()
            }
        remember(selectableId, selectionRegistrar, backgroundSelectionColor) {
            SelectionController(
                selectableId,
                selectionRegistrar,
                backgroundSelectionColor
            )
        }
    } else {
        null
    }
    val hasInlineContent = text.hasInlineContent()
    val hasLinks = text.hasLinks()
    if (!hasInlineContent && !hasLinks) {
        // this is the same as text: String, use all the early exits
        Layout(
            modifier = modifier
                // TODO(b/274781644): Remove this graphicsLayer
                .graphicsLayer()
                .textModifier(
                    text = text,
                    style = style,
                    onTextLayout = onTextLayout,
                    overflow = overflow,
                    softWrap = softWrap,
                    maxLines = maxLines,
                    minLines = minLines,
                    fontFamilyResolver = LocalFontFamilyResolver.current,
                    placeholders = null,
                    onPlaceholderLayout = null,
                    selectionController = selectionController,
                    color = color
                ),
            EmptyMeasurePolicy
        )
    } else {
        LayoutWithLinksAndInlineContent(
            modifier = modifier,
            text = text,
            onTextLayout = onTextLayout,
            hasLinks = hasLinks,
            hasInlineContent = hasInlineContent,
            inlineContent = inlineContent,
            style = style,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            fontFamilyResolver = LocalFontFamilyResolver.current,
            selectionController = selectionController,
            color = color
        )
    }
}

@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        minLines = 1,
        maxLines = maxLines
    )
}

@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        minLines = 1,
        maxLines = maxLines,
        inlineContent = inlineContent
    )
}

@Deprecated("Maintained for binary compat", level = DeprecationLevel.HIDDEN)
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1
) = BasicText(text, modifier, style, onTextLayout, overflow, softWrap, maxLines, minLines)

@Deprecated("Maintained for binary compat", level = DeprecationLevel.HIDDEN)
@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf()
) = BasicText(
    text = text,
    modifier = modifier,
    style = style,
    onTextLayout = onTextLayout,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    minLines = minLines,
    inlineContent = inlineContent
)

/**
 * A custom saver that won't save if no selection is active.
 */
private fun selectionIdSaver(selectionRegistrar: SelectionRegistrar?) = Saver<Long, Long>(
    save = { if (selectionRegistrar.hasSelection(it)) it else null },
    restore = { it }
)

internal expect fun Modifier.textPointerHoverIcon(selectionRegistrar: SelectionRegistrar?): Modifier

private object EmptyMeasurePolicy : MeasurePolicy {
    private val placementBlock: Placeable.PlacementScope.() -> Unit = {}
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        return layout(constraints.maxWidth, constraints.maxHeight, placementBlock = placementBlock)
    }
}

/** Measure policy for inline content and links */
private class TextMeasurePolicy(
    private val placements: () -> List<Rect?>?
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        // inline content
        val inlineContentMeasurables = measurables.fastFilter {
            it.parentData !is TextRangeLayoutModifier
        }
        val inlineContentToPlace = placements()?.fastMapIndexedNotNull { index, rect ->
            // PlaceholderRect will be null if it's ellipsized. In that case, the corresponding
            // inline children won't be measured or placed.
            rect?.let {
                Pair(
                    inlineContentMeasurables[index].measure(
                        Constraints(
                            maxWidth = floor(it.width).toInt(),
                            maxHeight = floor(it.height).toInt()
                        )
                    ),
                    IntOffset(it.left.fastRoundToInt(), it.top.fastRoundToInt())
                )
            }
        }

        // links
        val linksMeasurables = measurables.fastFilter {
            it.parentData is TextRangeLayoutModifier
        }
        val linksToPlace = measureWithTextRangeMeasureConstraints(linksMeasurables)

        return layout(constraints.maxWidth, constraints.maxHeight) {
            // inline content
            inlineContentToPlace?.fastForEach { (placeable, position) ->
                placeable.place(position)
            }
            // links
            linksToPlace.fastForEach { (placeable, measureResult) ->
                placeable.place(measureResult?.invoke() ?: IntOffset.Zero)
            }
        }
    }
}

/** Measure policy for links only */
private object LinksTextMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val linksToPlace = measureWithTextRangeMeasureConstraints(measurables)
        return layout(constraints.maxWidth, constraints.maxHeight) {
            linksToPlace.fastForEach { (placeable, measureResult) ->
                placeable.place(measureResult?.invoke() ?: IntOffset.Zero)
            }
        }
    }
}

private fun measureWithTextRangeMeasureConstraints(
    measurables: List<Measurable>
): List<Pair<Placeable, (() -> IntOffset)?>> {
    val textRangeLayoutMeasureScope = TextRangeLayoutMeasureScope()
    return measurables.fastMapIndexedNotNull { _, measurable ->
        val rangeMeasurePolicy = (measurable.parentData as TextRangeLayoutModifier).measurePolicy
        val rangeMeasureResult = with(rangeMeasurePolicy) {
            textRangeLayoutMeasureScope.measure()
        }
        val placeable = measurable.measure(
            Constraints.fixed(rangeMeasureResult.width, rangeMeasureResult.height)
        )
        Pair(placeable, rangeMeasureResult.place)
    }
}

private fun Modifier.textModifier(
    text: AnnotatedString,
    style: TextStyle,
    onTextLayout: ((TextLayoutResult) -> Unit)?,
    overflow: TextOverflow,
    softWrap: Boolean,
    maxLines: Int,
    minLines: Int,
    fontFamilyResolver: FontFamily.Resolver,
    placeholders: List<AnnotatedString.Range<Placeholder>>?,
    onPlaceholderLayout: ((List<Rect?>) -> Unit)?,
    selectionController: SelectionController?,
    color: ColorProducer?
): Modifier {
    if (selectionController == null) {
        val staticTextModifier = TextAnnotatedStringElement(
            text,
            style,
            fontFamilyResolver,
            onTextLayout,
            overflow,
            softWrap,
            maxLines,
            minLines,
            placeholders,
            onPlaceholderLayout,
            null,
            color
        )
        return this then Modifier /* selection position */ then staticTextModifier
    } else {
        val selectableTextModifier = SelectableTextAnnotatedStringElement(
            text,
            style,
            fontFamilyResolver,
            onTextLayout,
            overflow,
            softWrap,
            maxLines,
            minLines,
            placeholders,
            onPlaceholderLayout,
            selectionController,
            color
        )
        return this then selectionController.modifier then selectableTextModifier
    }
}

@Composable
private fun LayoutWithLinksAndInlineContent(
    modifier: Modifier,
    text: AnnotatedString,
    onTextLayout: ((TextLayoutResult) -> Unit)?,
    hasLinks: Boolean,
    hasInlineContent: Boolean,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    style: TextStyle,
    overflow: TextOverflow,
    softWrap: Boolean,
    maxLines: Int,
    minLines: Int,
    fontFamilyResolver: FontFamily.Resolver,
    selectionController: SelectionController?,
    color: ColorProducer?
) {
    val textScope = if (hasLinks) {
        remember(text) { TextLinkScope(text) }
    } else null

    // do the inline content allocs
    val (placeholders, inlineComposables) = if (hasInlineContent) {
        text.resolveInlineContent(
            inlineContent = inlineContent
        )
    } else Pair(null, null)

    val measuredPlaceholderPositions = if (hasInlineContent) {
        remember<MutableState<List<Rect?>?>> { mutableStateOf(null) }
    } else null

    val onPlaceholderLayout: ((List<Rect?>) -> Unit)? = if (hasInlineContent) {
        { measuredPlaceholderPositions?.value = it }
    } else null

    Layout(
        content = {
            textScope?.LinksComposables()
            inlineComposables?.let {
                InlineChildren(text = text, inlineContents = it)
            }
        },
        modifier = modifier
            // TODO(b/274781644): Remove this graphicsLayer
            .graphicsLayer()
            .textModifier(
                text = text,
                style = style,
                onTextLayout = {
                    textScope?.textLayoutResult = it
                    onTextLayout?.invoke(it)
                },
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
                fontFamilyResolver = fontFamilyResolver,
                placeholders = placeholders,
                onPlaceholderLayout = onPlaceholderLayout,
                selectionController = selectionController,
                color = color
            ),
        measurePolicy = if (!hasInlineContent) {
            LinksTextMeasurePolicy
        } else {
            TextMeasurePolicy { measuredPlaceholderPositions?.value }
        }
    )
}
