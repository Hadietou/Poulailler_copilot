package com.hadietou.poulailler

import com.hadietou.poulailler.data.StockAdjustment
import com.hadietou.poulailler.util.StockAdjustments
import com.hadietou.poulailler.util.StockType
import org.junit.Assert.assertEquals
import org.junit.Test

class StockInventoryTest {

    private fun adj(type: StockType, theoretical: Double, counted: Double, batchId: String? = "b1") =
        StockAdjustment(type = type.name, date = 0L, theoretical = theoretical, counted = counted, reason = "test", batchId = batchId)

    @Test
    fun offset_isSumOfDifferences_forTheRequestedStockOnly() {
        val list = listOf(
            adj(StockType.FEED, 412.0, 380.0),
            adj(StockType.FEED, 380.0, 390.0),
            adj(StockType.EGGS, 100.0, 90.0)
        )
        assertEquals(-22.0, StockAdjustments.offset(list, StockType.FEED, "b1"), 0.0001)
        assertEquals(-10.0, StockAdjustments.offset(list, StockType.EGGS, "b1"), 0.0001)
    }

    @Test
    fun feedAndEggs_areFilteredByBatch_butTraysAreFarmWide() {
        val list = listOf(
            adj(StockType.FEED, 100.0, 90.0, batchId = "b1"),
            adj(StockType.FEED, 50.0, 40.0, batchId = "b2"),
            adj(StockType.TRAYS, 36.0, 37.0, batchId = null)
        )
        assertEquals(-10.0, StockAdjustments.offset(list, StockType.FEED, "b2"), 0.0001)
        assertEquals(1.0, StockAdjustments.offset(list, StockType.TRAYS, "b2"), 0.0001)
        assertEquals(0.0, StockAdjustments.offset(null, StockType.FEED, "b1"), 0.0001)
    }

    @Test
    fun formats_areReadable() {
        assertEquals("12 plateaux + 7 œufs", StockType.EGGS.format(367.0))
        assertEquals("1 plateau", StockType.EGGS.format(30.0))
        assertEquals("−32 kg", StockType.FEED.formatDifference(-32.0))
        assertEquals("+1 alvéole", StockType.TRAYS.formatDifference(1.0))
    }
}
