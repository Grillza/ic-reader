package io.github.grillza.icreader.data

/** Human-readable names for the terminal and process codes in transit history records. */
object Codes {

    fun terminal(code: Int): String = when (code) {
        0x03 -> "Fare adjustment machine"
        0x04 -> "Portable terminal"
        0x05 -> "Bus terminal"
        0x07, 0x08, 0x12 -> "Ticket machine"
        0x09 -> "Charge machine"
        0x14, 0x15 -> "Ticket machine"
        0x16 -> "Ticket gate"
        0x17 -> "Simple ticket gate"
        0x18 -> "Station counter"
        0x19 -> "Green window"
        0x1A -> "Ticket gate terminal"
        0x1B -> "Mobile phone"
        0x1C -> "Transfer adjustment machine"
        0x1D -> "Transfer gate"
        0x1F -> "Simple charge machine"
        0x20 -> "Window terminal"
        0x46, 0x48 -> "VIEW ALTTE"
        0xC7 -> "Point of sale"
        0xC8 -> "Vending machine"
        else -> "Terminal %02X".format(code)
    }

    fun process(code: Int): String = when (code) {
        0x01 -> "Fare"
        0x02 -> "Charge"
        0x03 -> "Ticket purchase"
        0x04 -> "Fare adjustment"
        0x05 -> "Fare adjustment (entry)"
        0x06 -> "Exit at counter"
        0x07 -> "New card issued"
        0x08 -> "Counter deduction"
        0x0D -> "Bus (PiTaPa)"
        0x0F -> "Bus (IruCa)"
        0x11 -> "Card reissued"
        0x13 -> "Shinkansen fare"
        0x14 -> "Auto-charge (entry)"
        0x15 -> "Auto-charge (exit)"
        0x1F -> "Bus charge"
        0x23 -> "Bus/tram ticket"
        0x46 -> "Purchase"
        0x48 -> "Bonus charge"
        0x49 -> "Register charge"
        0x4A -> "Purchase cancelled"
        0x4B -> "Purchase (station)"
        0xC6 -> "Purchase (with cash)"
        0xCB -> "Purchase (station, with cash)"
        0x84 -> "Fare adjustment (with cash)"
        0x85 -> "Fare adjustment (entry, with cash)"
        else -> if (code >= 0x80) process(code and 0x7F) + " (with cash)" else "Process %02X".format(code)
    }

    fun edyType(code: Int): String = when (code) {
        0x02 -> "Charge"
        0x04 -> "Edy gift"
        0x20 -> "Payment"
        else -> "Type %02X".format(code)
    }

    fun nanacoType(code: Int): String = when (code) {
        0x47 -> "Payment"
        0x6F -> "Charge"
        0x70 -> "Charge (cash)"
        0x35 -> "Points exchange"
        0x83 -> "Transfer"
        else -> "Type %02X".format(code)
    }
}
