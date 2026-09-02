package com.github.premnirmal.ticker.network.data

import com.github.premnirmal.shared.CommonParcelable
import com.github.premnirmal.shared.CommonParcelize
import kotlinx.serialization.Serializable

@CommonParcelize
@Serializable
data class Position(
    var symbol: String = "",
    var holdings: MutableList<Holding> = ArrayList()
) : CommonParcelable {

    fun add(holding: Holding) {
        holdings.add(holding)
    }

    fun remove(holding: Holding): Boolean {
        return holdings.remove(holding)
    }

    fun averagePrice(): Float {
        return holdings.averagePrice()
    }

    fun totalShares(): Float = holdings.totalShares()

    fun totalPaidPrice(): Float = holdings.totalPaidPrice()

    /** Realized P&L from all sell-out (出仓) transactions, computed from the buy/sell ledger. */
    fun realizedGainLoss(): Float = holdings.holdingsStat().realized

    /**
     * Cumulative (累积) P&L = realized P&L from sells + unrealized P&L on the remaining net shares,
     * derived from the full buy/sell ledger so add/remove (增删) operations are accounted for.
     */
    fun cumulativeGainLoss(currentPrice: Float): Float {
        val s = holdings.holdingsStat()
        return s.realized + (currentPrice - s.avgCost) * s.netShares
    }

    /** Net cash invested = buy cost − sell proceeds. */
    fun netInvested(): Float = holdings.netInvested()
}

fun List<Holding>.totalShares(): Float =
    this.sumOf { if (it.type == HOLDING_TYPE_SELL) -it.shares.toDouble() else it.shares.toDouble() }.toFloat()

/** Total buy-in cost (sell-out proceeds are realised separately, not part of cost basis). */
fun List<Holding>.totalPaidPrice(): Float =
    this.sumOf { if (it.type == HOLDING_TYPE_SELL) 0.0 else it.totalValue().toDouble() }.toFloat()

fun List<Holding>.averagePrice(): Float =
    if (this.totalShares() == 0f) 0f else this.totalPaidPrice() / this.totalShares()

/** Net cash invested = buy cost − sell proceeds. */
fun List<Holding>.netInvested(): Float =
    this.sumOf { if (it.type == HOLDING_TYPE_SELL) -it.totalValue().toDouble() else it.totalValue().toDouble() }.toFloat()

private data class HoldingsStat(val netShares: Float, val avgCost: Float, val realized: Float)

/**
 * Walks the holdings ledger in insertion order (by [Holding.id]) using the average-cost method:
 * each buy-in updates the running average cost; each sell-out realises (sellPrice − avgCost) × shares
 * and reduces the net share count. Returns the final net shares, running average cost and realized P&L.
 */
private fun List<Holding>.holdingsStat(): HoldingsStat {
    var shares = 0f
    var avgCost = 0f
    var realized = 0f
    val sorted = this.sortedBy { it.id ?: 0L }
    for (h in sorted) {
        if (h.type == HOLDING_TYPE_SELL) {
            realized += (h.price - avgCost) * h.shares
            shares -= h.shares
        } else {
            val newShares = shares + h.shares
            avgCost = if (newShares > 0f) {
                (avgCost * shares + h.price * h.shares) / newShares
            } else {
                h.price
            }
            shares = newShares
        }
    }
    return HoldingsStat(shares, avgCost, realized)
}

fun List<Holding>.holdingsSum(): HoldingSum {
    val totalShares = this.totalShares()
    val totalPaidPrice = this.totalPaidPrice()
    val averagePrice = this.averagePrice()
    return HoldingSum(totalShares, totalPaidPrice, averagePrice)
}

@CommonParcelize
data class HoldingSum(
    val totalShares: Float,
    val totalPaidPrice: Float,
    val averagePrice: Float,
) : CommonParcelable

@CommonParcelize
@Serializable
data class Holding(
    val symbol: String,
    val shares: Float = 0.0f,
    val price: Float = 0.0f,
    var id: Long? = null,
    val type: Int = HOLDING_TYPE_BUY
) : CommonParcelable {

    fun totalValue(): Float = shares * price
}

/** Holding transaction type: 0 = buy-in (进仓), 1 = sell-out (出仓). */
const val HOLDING_TYPE_BUY = 0
const val HOLDING_TYPE_SELL = 1
