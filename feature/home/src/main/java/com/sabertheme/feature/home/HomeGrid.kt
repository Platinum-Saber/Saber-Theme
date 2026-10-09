package com.sabertheme.feature.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.ui.CELL_HEIGHT
import com.sabertheme.core.ui.CELL_WIDTH
import com.sabertheme.core.ui.TILE_SIZE
import kotlin.math.roundToInt

internal val WIDGET_GAP = 12.dp

/**
 * Grid geometry for a page of [width] px. App cells are [CELL_WIDTH] wide and
 * spread edge to edge; widgets align with the outer edges of the icon tiles
 * and are separated by [WIDGET_GAP] both ways. All values in page-local px.
 */
internal class HomeGrid(private val width: Float, density: Density) {
    private val cell = with(density) { CELL_WIDTH.toPx() }
    val rowHeight = with(density) { CELL_HEIGHT.toPx() }
    private val inset = with(density) { ((CELL_WIDTH - TILE_SIZE) / 2).toPx() }
    private val tile = with(density) { TILE_SIZE.toPx() }
    private val gap = with(density) { WIDGET_GAP.toPx() }
    private val top = with(density) { 2.dp.toPx() }
    private val columnStep = (width - cell) / (HomeLayout.COLUMNS - 1)
    private val unit = (width - 2 * inset - gap * (HomeLayout.COLUMNS - 1)) / HomeLayout.COLUMNS

    fun cellX(col: Int) = col * columnStep

    fun cellY(row: Int) = row * rowHeight

    /** Where an app cell's icon column starts (tile + label). */
    fun cellOrigin(col: Int, row: Int) = Offset(cellX(col), cellY(row))

    /** The glass tile inside an app cell. */
    fun tileRect(col: Int, row: Int) = Rect(Offset(cellX(col) + inset, cellY(row) + top), Size(tile, tile))

    fun widgetRect(col: Int, row: Int, spanX: Int, spanY: Int): Rect {
        val left = inset + col * (unit + gap)
        val y = row * rowHeight + top
        return Rect(left, y, left + spanX * unit + (spanX - 1) * gap, y + spanY * rowHeight - gap)
    }

    fun widgetRect(c: PlacedCell) = widgetRect(c.col, c.row, c.spanX, c.spanY)

    /** Cell under [p] for a 1x1 item, or null when [p] is off the grid vertically. */
    fun cellAt(p: Offset): Pair<Int, Int>? {
        val row = (p.y / rowHeight).toInt()
        if (p.y < 0 || row >= HomeLayout.ROWS) return null
        val col = ((p.x - cell / 2) / columnStep).roundToInt().coerceIn(0, HomeLayout.COLUMNS - 1)
        return col to row
    }

    /** Nearest top-left cell for a [spanX] x [spanY] item whose top-left is at [p], kept on the grid. */
    fun spanAt(p: Offset, spanX: Int, spanY: Int): Pair<Int, Int> {
        val col = ((p.x - inset) / (unit + gap)).roundToInt().coerceIn(0, HomeLayout.COLUMNS - spanX)
        val row = ((p.y - top) / rowHeight).roundToInt().coerceIn(0, HomeLayout.ROWS - spanY)
        return col to row
    }

    /** How far [p] is from the centre of the tile at ([col], [row]), as a share of the tile size. */
    fun tileDistance(p: Offset, col: Int, row: Int): Float = (p - tileRect(col, row).center).getDistance() / tile
}
