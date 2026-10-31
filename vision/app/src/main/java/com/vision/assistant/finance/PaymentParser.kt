package com.vision.assistant.finance

enum class Direction { PAID, RECEIVED }

data class ParsedPayment(val direction: Direction, val amountPaise: Long, val party: String, val bill: Boolean)

/**
 * Reads payment notifications (PhonePe, Paytm, Google Pay, banks). Pure text parsing, no network.
 * Deliberately ignores OTP/verification texts and never keeps account or card numbers.
 */
object PaymentParser {
    val PAYMENT_APPS = setOf(
        "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
        "com.amazon.mShop.android.shopping", "in.amazon.mShop.android.shopping", "com.mobikwik_new", "com.freecharge.android",
        "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.sbi.SBIFreedomPlus", "com.axis.mobile", "com.dreamplug.androidapp", "com.kotak.mobile",
    )
    private val amount = Regex("(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val otp = Regex("\\botp\\b|one[- ]time|verification code|do not share|never share", RegexOption.IGNORE_CASE)
    private val paidWords = Regex("\\bpaid\\b|\\bsent\\b|debited|spent|purchase|withdrawn|payment of|bill payment|\\bpaid to\\b", RegexOption.IGNORE_CASE)
    private val recvWords = Regex("received|credited|\\bcredit\\b|deposited|refund", RegexOption.IGNORE_CASE)
    private val failWords = Regex("fail|declined|unsuccessful|reversed|pending|request(ed)? (money|₹)|requests you|collect request|will be debited|due on|is due|reminder", RegexOption.IGNORE_CASE)
    private val billWords = Regex("\\bbill\\b|recharge|electricity|insurance|premium|emi\\b|broadband|dth|postpaid", RegexOption.IGNORE_CASE)
    private val party = Regex("(?:\\bto|\\bfrom|\\bat|\\bto merchant|\\btrf to|\\bvpa|\\binfo[:/]? *(?:upi/)?)\\s+([A-Za-z][A-Za-z0-9&.' @_-]{1,30}?)(?=\\s+(?:on|via|using|ref|upi|for|a/c|ac|through|is|has|was|\\.|,)|[.,;\\n]|$)", RegexOption.IGNORE_CASE)

    fun parse(title: String, text: String): ParsedPayment? {
        val all = "$title. $text"
        if (otp.containsMatchIn(all) || failWords.containsMatchIn(all)) return null
        val m = amount.find(all) ?: return null
        val paise = Math.round((m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null) * 100)
        if (paise <= 0 || paise > 100_000_000_00L) return null
        val paid = paidWords.containsMatchIn(all); val recv = recvWords.containsMatchIn(all)
        val dir = when { paid && !recv -> Direction.PAID; recv && !paid -> Direction.RECEIVED
            paid && recv -> if (all.indexOf("received", ignoreCase = true).let { r -> r >= 0 && r < (paidWords.find(all)?.range?.first ?: Int.MAX_VALUE) }) Direction.RECEIVED else Direction.PAID
            else -> return null }
        val who = party.find(all)?.groupValues?.get(1)?.trim()?.take(30).orEmpty().ifBlank { title.take(30) }
        return ParsedPayment(dir, paise, who, dir == Direction.PAID && billWords.containsMatchIn(all))
    }

    val CATEGORIES = listOf("Food", "Groceries", "Travel", "Shopping", "Bills", "Recharge", "Health", "Entertainment", "Rent", "Transfer", "Other")
    /** A guess only; the user confirms it in the evening review. */
    fun suggestCategory(party: String, bill: Boolean): String {
        val p = party.lowercase()
        return when {
            bill -> "Bills"
            Regex("swiggy|zomato|restaurant|cafe|pizza|dominos|kfc|mcdonald|food|hotel|bakery|chai").containsMatchIn(p) -> "Food"
            Regex("bigbasket|blinkit|zepto|grocer|mart|dmart|kirana|vegetable|milk").containsMatchIn(p) -> "Groceries"
            Regex("uber|ola|rapido|irctc|redbus|metro|petrol|fuel|fastag|indigo|makemytrip").containsMatchIn(p) -> "Travel"
            Regex("amazon|flipkart|myntra|meesho|ajio|store|mall").containsMatchIn(p) -> "Shopping"
            Regex("pharm|medic|apollo|hospital|clinic|lab").containsMatchIn(p) -> "Health"
            Regex("netflix|hotstar|spotify|prime|bookmyshow|youtube|game").containsMatchIn(p) -> "Entertainment"
            Regex("jio|airtel|vodafone|vi\\b|recharge").containsMatchIn(p) -> "Recharge"
            Regex("rent|landlord").containsMatchIn(p) -> "Rent"
            else -> ""
        }
    }
    fun isCandidatePackage(pkg: String) = pkg in PAYMENT_APPS
    /** Other apps are only parsed when the text itself looks like a bank debit/credit alert; chat apps never count (anyone can type "paid ₹500" in a chat). */
    fun looksLikeBankAlert(pkg: String, all: String) = pkg !in setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger") &&
        Regex("(debited|credited).*(a/c|account|upi)|(a/c|account).*(debited|credited)", RegexOption.IGNORE_CASE).containsMatchIn(all)
}

object SpendSummary {
    data class Totals(val paid: Long, val received: Long, val bills: Long, val count: Int, val top: List<Pair<String, Long>>, val categories: List<Pair<String, Long>> = emptyList())
    data class Row(val direction: Direction, val paise: Long, val party: String, val bill: Boolean, val category: String = "")
    fun byCategory(rows: List<Row>): List<Pair<String, Long>> = rows.filter { it.direction == Direction.PAID }.groupBy { it.category.ifBlank { "Unsorted" } }.map { it.key to it.value.sumOf { r -> r.paise } }.sortedByDescending { it.second }

    fun totals(rows: List<Row>): Totals {
        val paid = rows.filter { it.direction == Direction.PAID }
        val top = paid.groupBy { it.party.lowercase() }.map { (_, v) -> v.first().party to v.sumOf { it.paise } }.sortedByDescending { it.second }.take(3)
        return Totals(paid.sumOf { it.paise }, rows.filter { it.direction == Direction.RECEIVED }.sumOf { it.paise }, paid.filter { it.bill }.sumOf { it.paise }, rows.size, top, byCategory(rows).take(4))
    }
    fun rupees(p: Long) = if (p % 100 == 0L) "₹%,d".format(p / 100) else "₹%,.2f".format(p / 100.0)

    fun describe(label: String, t: Totals): String =
        if (t.count == 0) "No payments recorded $label. VISION only sees payments whose notifications arrived after you granted notification access."
        else buildString {
            append("$label: spent ${rupees(t.paid)}, received ${rupees(t.received)} (${t.count} payments)")
            if (t.bills > 0) append(", of which bills ${rupees(t.bills)}")
            if (t.categories.isNotEmpty()) append(". By category: " + t.categories.joinToString(", ") { "${it.first} ${rupees(it.second)}" })
            if (t.top.isNotEmpty()) append(". Most spent on: " + t.top.joinToString(", ") { "${it.first} ${rupees(it.second)}" })
            append(".")
        }
}
