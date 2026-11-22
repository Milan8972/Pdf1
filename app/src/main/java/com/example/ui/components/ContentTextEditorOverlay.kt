package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.EditableTextBlock
import com.example.engine.TextAlignOption
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.roundToInt

private val SelectionBlue = Color(0xFF1D8CF8)
private val DashedBlue = Color(0xFF2563EB)

private enum class TextFormatSubPanel { NONE, SIZE, FONT, COLOR, SPACING }

@Composable
fun ContentBlocksOverlay(
    blocks: List<EditableTextBlock>,
    selectedBlockId: String?,
    showGuides: Boolean,
    containerWidthDp: Dp,
    containerHeightDp: Dp,
    onSelectBlock: (String?) -> Unit,
    onUpdateBlock: (EditableTextBlock, Boolean) -> Unit,
    onDeleteBlock: (EditableTextBlock) -> Unit,
    onResetBlock: (EditableTextBlock) -> Unit
) {
    val density = LocalDensity.current
    val widthPx = with(density) { containerWidthDp.toPx() }.coerceAtLeast(1f)
    val heightPx = with(density) { containerHeightDp.toPx() }.coerceAtLeast(1f)
    // Scale PDF points (595pt page width) into visual screen sp accurately
    val ptToVisualSp = (containerWidthDp.value / 595f).coerceIn(0.45f, 1.35f)

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Background masks for any modified or deleted blocks (covers original PDF text cleanly)
        blocks.filter { (it.isModified || it.isDeleted) && !it.isUserAdded }.forEach { block ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (block.origNormX * widthPx - 2f).roundToInt().coerceAtLeast(0),
                            (block.origNormY * heightPx - 2f).roundToInt().coerceAtLeast(0)
                        )
                    }
                    .size(
                        width = (block.origNormWidth * containerWidthDp.value).dp + 4.dp,
                        height = (block.origNormHeight * containerHeightDp.value).dp + 4.dp
                    )
                    .background(Color(block.bgColorArgb))
            )
        }

        // 2. Dashed blue guides for unselected blocks (when Edit Text mode is active)
        if (showGuides) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 6f), 0f)
                blocks.filterNot { it.isDeleted || it.id == selectedBlockId }.forEach { block ->
                    drawRect(
                        color = DashedBlue.copy(alpha = 0.78f),
                        topLeft = Offset(block.normX * size.width, block.normY * size.height),
                        size = Size(block.normWidth * size.width, block.normHeight * size.height),
                        style = Stroke(width = 1.25.dp.toPx(), pathEffect = dash)
                    )
                }
            }
        }

        // 3. Interactive text blocks (tap to edit inline, solid blue border + circular handles when selected)
        blocks.filterNot { it.isDeleted }.forEach { block ->
            val isSelected = showGuides && block.id == selectedBlockId
            val boxW = (block.normWidth * containerWidthDp.value).coerceAtLeast(48f).dp
            val boxH = (block.normHeight * containerHeightDp.value).coerceAtLeast(22f).dp
            val visualFontSp = (block.fontSizePt * ptToVisualSp).coerceIn(8f, 44f).sp
            val visualLineHeightSp = (block.fontSizePt * ptToVisualSp * block.lineSpacing).coerceIn(10f, 64f).sp

            val composeFontFamily = when (block.fontFamilyName) {
                "Serif" -> FontFamily.Serif
                "Mono" -> FontFamily.Monospace
                "Space Grotesk" -> SpaceGroteskFontFamily
                "Inter" -> InterFontFamily
                else -> FontFamily.SansSerif
            }
            val composeTextAlign = when (block.align) {
                TextAlignOption.LEFT -> TextAlign.Start
                TextAlignOption.CENTER -> TextAlign.Center
                TextAlignOption.RIGHT -> TextAlign.End
                TextAlignOption.JUSTIFY -> TextAlign.Justify
            }
            val textStyle = TextStyle(
                color = Color(block.textColorArgb),
                fontSize = visualFontSp,
                lineHeight = visualLineHeightSp,
                fontWeight = if (block.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (block.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = composeFontFamily,
                textAlign = composeTextAlign
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (block.normX * widthPx).roundToInt(),
                            (block.normY * heightPx).roundToInt()
                        )
                    }
                    .width(boxW)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(Color(block.bgColorArgb))
                                .border(1.6.dp, SelectionBlue, RoundedCornerShape(3.dp))
                        } else if (block.isModified || block.isUserAdded) {
                            Modifier.background(Color(block.bgColorArgb))
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (showGuides && !isSelected) {
                            Modifier.clickable { onSelectBlock(block.id) }
                        } else Modifier
                    )
                    .testTag("content_text_block_${block.id.take(8)}")
            ) {
                if (isSelected) {
                    val focusRequester = remember(block.id) { FocusRequester() }
                    LaunchedEffect(block.id) {
                        runCatching { focusRequester.requestFocus() }
                    }
                    BasicTextField(
                        value = block.text,
                        onValueChange = { updated ->
                            onUpdateBlock(
                                block.copy(text = updated, isModified = true),
                                false
                            )
                        },
                        textStyle = textStyle,
                        cursorBrush = SolidColor(BrandCoral),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                            .focusRequester(focusRequester)
                            .testTag("content_inline_text_field")
                    )
                } else if (block.isModified || block.isUserAdded) {
                    Text(
                        text = block.text,
                        style = textStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                    )
                } else {
                    // Transparent tap target matching exact PDF text box height
                    Spacer(modifier = Modifier.fillMaxWidth().height(boxH))
                }
            }

            // Left & right blue circular resize handles + top move/delete pill when selected
            if (isSelected) {
                val leftHandleX = (block.normX * widthPx - with(density) { 10.dp.toPx() }).roundToInt()
                val rightHandleX = ((block.normX + block.normWidth) * widthPx - with(density) { 10.dp.toPx() }).roundToInt()
                val midY = ((block.normY + block.normHeight / 2f) * heightPx - with(density) { 10.dp.toPx() }).roundToInt()

                // Left circular blue resize handle
                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftHandleX, midY) }
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(SelectionBlue)
                        .border(2.dp, Color.White, CircleShape)
                        .pointerInput(block.id, block.normX, block.normWidth) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val dx = drag.x / widthPx
                                val newX = (block.normX + dx).coerceIn(0.01f, block.normX + block.normWidth - 0.10f)
                                val newW = (block.normWidth - (newX - block.normX)).coerceIn(0.10f, 0.96f)
                                onUpdateBlock(
                                    block.copy(normX = newX, normWidth = newW, isModified = true),
                                    false
                                )
                            }
                        }
                )

                // Right circular blue resize handle
                Box(
                    modifier = Modifier
                        .offset { IntOffset(rightHandleX, midY) }
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(SelectionBlue)
                        .border(2.dp, Color.White, CircleShape)
                        .pointerInput(block.id, block.normX, block.normWidth) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val dx = drag.x / widthPx
                                val newW = (block.normWidth + dx).coerceIn(0.10f, 0.98f - block.normX)
                                onUpdateBlock(
                                    block.copy(normWidth = newW, isModified = true),
                                    false
                                )
                            }
                        }
                )

                // Compact floating move / reset / delete pill right above the selected block
                val actionBarY = (block.normY * heightPx - with(density) { 34.dp.toPx() })
                    .roundToInt()
                    .coerceAtLeast(4)
                val actionBarX = (block.normX * widthPx)
                    .roundToInt()
                    .coerceIn(4, (widthPx - with(density) { 128.dp.toPx() }).roundToInt().coerceAtLeast(4))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, SelectionBlue.copy(alpha = 0.5f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.offset { IntOffset(actionBarX, actionBarY) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .pointerInput(block.id, block.normX, block.normY) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        onUpdateBlock(
                                            block.copy(
                                                normX = (block.normX + drag.x / widthPx).coerceIn(0.01f, 0.92f - block.normWidth.coerceAtMost(0.9f)),
                                                normY = (block.normY + drag.y / heightPx).coerceIn(0.01f, 0.94f),
                                                isModified = true
                                            ),
                                            false
                                        )
                                    }
                                }
                        ) {
                            Icon(Icons.Rounded.OpenWith, contentDescription = "Drag to move text block", tint = SelectionBlue, modifier = Modifier.size(16.dp))
                        }
                        if (block.isModified && !block.isUserAdded) {
                            IconButton(
                                onClick = { onResetBlock(block) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset text block", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(
                            onClick = { onDeleteBlock(block) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete text block", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContentTextFormattingToolbar(
    selectedBlock: EditableTextBlock?,
    blockCountOnPage: Int,
    onUpdateSelected: (EditableTextBlock) -> Unit,
    onAddNewTextBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = LocalPaperPilotColors.current
    var openPanel by remember(selectedBlock?.id) { mutableStateOf(TextFormatSubPanel.NONE) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Expandable sub-panel (Font Size / Font Family / Color / Line Spacing)
        AnimatedVisibility(visible = selectedBlock != null && openPanel != TextFormatSubPanel.NONE) {
            selectedBlock?.let { block ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, extra.subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (openPanel) {
                        TextFormatSubPanel.SIZE -> {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Font size", style = MaterialTheme.typography.labelMedium)
                                    Text("${block.fontSizePt.roundToInt()} pt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = block.fontSizePt,
                                    onValueChange = { onUpdateSelected(block.copy(fontSizePt = it, isModified = true)) },
                                    valueRange = 8f..48f
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf(10f, 12f, 14f, 16f, 20f, 24f, 32f, 40f)) { sz ->
                                        FilterChip(
                                            selected = block.fontSizePt.roundToInt() == sz.toInt(),
                                            onClick = { onUpdateSelected(block.copy(fontSizePt = sz, isModified = true)) },
                                            label = { Text("${sz.toInt()}") }
                                        )
                                    }
                                }
                            }
                        }

                        TextFormatSubPanel.FONT -> {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(listOf("Sans", "Inter", "Space Grotesk", "Serif", "Mono")) { fam ->
                                    FilterChip(
                                        selected = block.fontFamilyName == fam,
                                        onClick = { onUpdateSelected(block.copy(fontFamilyName = fam, isModified = true)) },
                                        label = { Text(fam) }
                                    )
                                }
                            }
                        }

                        TextFormatSubPanel.COLOR -> {
                            val textSwatches = listOf(
                                AndroidColor.parseColor("#1A1523"),
                                AndroidColor.parseColor("#FFFFFF"),
                                AndroidColor.parseColor("#D03A44"),
                                AndroidColor.parseColor("#FF6B6B"),
                                AndroidColor.parseColor("#1D4ED8"),
                                AndroidColor.parseColor("#0F766E"),
                                AndroidColor.parseColor("#10B981"),
                                AndroidColor.parseColor("#8B5CF6"),
                                AndroidColor.parseColor("#F59E0B"),
                                AndroidColor.parseColor("#64748B")
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text("Text color", style = MaterialTheme.typography.labelSmall, color = extra.mutedText)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(textSwatches) { argb ->
                                        val isSel = block.textColorArgb == argb
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color(argb))
                                                .border(
                                                    width = if (isSel) 2.5.dp else 1.dp,
                                                    color = if (isSel) SelectionBlue else extra.subtleBorder,
                                                    shape = CircleShape
                                                )
                                                .clickable { onUpdateSelected(block.copy(textColorArgb = argb, isModified = true)) }
                                        )
                                    }
                                }
                            }
                        }

                        TextFormatSubPanel.SPACING -> {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item { Text("Line spacing:", style = MaterialTheme.typography.labelMedium) }
                                items(listOf(1.0f, 1.2f, 1.4f, 1.6f, 1.8f)) { sp ->
                                    FilterChip(
                                        selected = kotlin.math.abs(block.lineSpacing - sp) < 0.05f,
                                        onClick = { onUpdateSelected(block.copy(lineSpacing = sp, isModified = true)) },
                                        label = { Text("${sp}x") }
                                    )
                                }
                            }
                        }

                        TextFormatSubPanel.NONE -> Unit
                    }
                }
            }
        }

        // Main Pill Formatting Toolbar (sits directly above keyboard when editing!)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extra.subtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (selectedBlock == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (blockCountOnPage > 0) "Tap any dashed blue text block to edit inline ($blockCountOnPage detected)"
                        else "Tap + Add text to place an editable text block",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.mutedText,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onAddNewTextBlock,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("content_add_text_block_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add text", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag("content_text_format_bar")
                ) {
                    // TT (Font Size)
                    item {
                        FormatCirclePill(
                            active = openPanel == TextFormatSubPanel.SIZE,
                            onClick = { openPanel = if (openPanel == TextFormatSubPanel.SIZE) TextFormatSubPanel.NONE else TextFormatSubPanel.SIZE },
                            testTag = "format_size_btn"
                        ) {
                            Text("TT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    // AA (Font Family)
                    item {
                        FormatCirclePill(
                            active = openPanel == TextFormatSubPanel.FONT,
                            onClick = { openPanel = if (openPanel == TextFormatSubPanel.FONT) TextFormatSubPanel.NONE else TextFormatSubPanel.FONT },
                            testTag = "format_font_btn"
                        ) {
                            Text("AA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Color Ring Dot
                    item {
                        FormatCirclePill(
                            active = openPanel == TextFormatSubPanel.COLOR,
                            onClick = { openPanel = if (openPanel == TextFormatSubPanel.COLOR) TextFormatSubPanel.NONE else TextFormatSubPanel.COLOR },
                            testTag = "format_color_btn"
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .padding(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color(selectedBlock.textColorArgb))
                                )
                            }
                        }
                    }

                    // Grouped B | I pill
                    item {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, extra.subtleBorder)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedBlock.bold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { onUpdateSelected(selectedBlock.copy(bold = !selectedBlock.bold, isModified = true)) }
                                        .testTag("format_bold_btn")
                                ) {
                                    Icon(
                                        Icons.Rounded.FormatBold,
                                        contentDescription = "Bold",
                                        tint = if (selectedBlock.bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedBlock.italic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { onUpdateSelected(selectedBlock.copy(italic = !selectedBlock.italic, isModified = true)) }
                                        .testTag("format_italic_btn")
                                ) {
                                    Icon(
                                        Icons.Rounded.FormatItalic,
                                        contentDescription = "Italic",
                                        tint = if (selectedBlock.italic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Grouped 4-way Alignment pill (Left | Center | Right | Justify)
                    item {
                        val alignItems = listOf(
                            TextAlignOption.LEFT to Icons.AutoMirrored.Rounded.FormatAlignLeft,
                            TextAlignOption.CENTER to Icons.Rounded.FormatAlignCenter,
                            TextAlignOption.RIGHT to Icons.AutoMirrored.Rounded.FormatAlignRight,
                            TextAlignOption.JUSTIFY to Icons.Rounded.FormatAlignJustify
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, extra.subtleBorder)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                alignItems.forEach { (alignOpt, icon) ->
                                    val isAct = selectedBlock.align == alignOpt
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isAct) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                            .clickable { onUpdateSelected(selectedBlock.copy(align = alignOpt, isModified = true)) }
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "Align ${alignOpt.name.lowercase()}",
                                            tint = if (isAct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Line Spacing pill
                    item {
                        FormatCirclePill(
                            active = openPanel == TextFormatSubPanel.SPACING,
                            onClick = { openPanel = if (openPanel == TextFormatSubPanel.SPACING) TextFormatSubPanel.NONE else TextFormatSubPanel.SPACING },
                            testTag = "format_spacing_btn"
                        ) {
                            Icon(Icons.Rounded.FormatLineSpacing, contentDescription = "Line spacing", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatCirclePill(
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary else LocalPaperPilotColors.current.subtleBorder
        ),
        modifier = Modifier.size(40.dp).testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
