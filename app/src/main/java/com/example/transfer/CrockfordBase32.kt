package com.example.transfer

object CrockfordBase32 {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { index, ch ->
            this[ch.code] = index
            this[ch.lowercaseChar().code] = index
        }
        this['O'.code] = 0; this['o'.code] = 0
        this['I'.code] = 1; this['i'.code] = 1
        this['L'.code] = 1; this['l'.code] = 1
    }

    fun encode(data: ByteArray, groupSize: Int = 0): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val idx = (buffer shr (bitsLeft - 5)) and 0x1F
                sb.append(ALPHABET[idx])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val idx = (buffer shl (5 - bitsLeft)) and 0x1F
            sb.append(ALPHABET[idx])
        }
        if (groupSize <= 0) return sb.toString()

        val grouped = StringBuilder()
        sb.forEachIndexed { index, c ->
            if (index > 0 && index % groupSize == 0) grouped.append('-')
            grouped.append(c)
        }
        return grouped.toString()
    }

    fun decode(input: String): ByteArray {
        val cleaned = input.filterNot { it == '-' || it.isWhitespace() }
        if (cleaned.isEmpty()) return ByteArray(0)

        val out = ArrayList<Byte>(cleaned.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (ch in cleaned) {
            val code = ch.code
            val value = if (code in 0..127) DECODE_TABLE[code] else -1
            if (value < 0) {
                throw IllegalArgumentException("Carácter inválido en código alfanumérico: '$ch'")
            }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}
