package com.bugenzhao.mnga.util

/**
 * Deterministic dice rolling, a faithful port of `Utilities/DiceRoller.swift`.
 * Reproduces the NGA LCG exactly (constants and thresholds included) so the
 * same post shows the same rolls on every client and matches server rerolls.
 *
 * All seed arithmetic uses [Long]: `233279 * 9301` and `seed * faces` both
 * overflow 32-bit ints, while Swift's `Int` is 64-bit.
 */
object DiceRoller {

    private const val LC_A = 9301L
    private const val LC_C = 49297L
    private const val LC_M = 233280L

    /** Only "new" posts (beyond these ids) get distinct seeds per collapse block. */
    const val TOPIC_ID_THRESHOLD = 10_246_184L
    const val POST_ID_THRESHOLD = 200_188_932L

    /** Mutable rolling state for one post; [copy] per `[collapse]` block. */
    class Context(
        val authorId: Int,
        val topicId: Int,
        val postId: Int,
        var seedOffset: Int = 0,
        var rndSeed: Int? = null,
    ) {
        companion object {
            /** Null unless all three id strings parse as integers. */
            fun from(
                authorIdString: String?,
                topicIdString: String?,
                postIdString: String?,
            ): Context? {
                val authorId = authorIdString?.toIntOrNull() ?: return null
                val topicId = topicIdString?.toIntOrNull() ?: return null
                val postId = postIdString?.toIntOrNull() ?: return null
                return Context(authorId, topicId, postId)
            }
        }

        fun copy(withSeedOffset: Int? = null): Context =
            Context(authorId, topicId, postId, withSeedOffset ?: seedOffset, rndSeed)

        private fun ensureSeed(): Long {
            rndSeed?.takeIf { it != 0 }?.let { return it.toLong() }

            var seed = authorId.toLong() + topicId + postId
            if (topicId > TOPIC_ID_THRESHOLD || postId > POST_ID_THRESHOLD) {
                seed += seedOffset
            }
            if (seed == 0L) {
                seed = (0 until 10000).random().toLong()
            }
            rndSeed = seed.toInt()
            return seed
        }

        private fun nextSeed(): Long {
            val current = ensureSeed()
            val next = (current * LC_A + LC_C) % LC_M
            rndSeed = next.toInt()
            return next
        }

        fun nextRoll(faces: Int): Int {
            require(faces > 0) { "faces must be positive" }
            val seed = nextSeed()
            return ((seed * faces) / LC_M + 1).toInt()
        }
    }

    data class Result(
        val originalExpression: String,
        val expandedExpression: String,
        /** Null when any term was invalid/out of limit ("ERROR" upstream). */
        val total: Long?,
    )

    private val termRegex = Regex("""(\+)(\d{0,10})(?:(d)(\d{1,10}))?""", RegexOption.IGNORE_CASE)

    /** Parse and roll a `2d6+3` style expression with the shared LCG state. */
    fun roll(expression: String, context: Context): Result {
        val original = expression
        if (original.isBlank()) {
            return Result(originalExpression = original, expandedExpression = "", total = null)
        }

        val working = "+$original"
        val output = StringBuilder()
        var cursor = 0
        var sum: Long? = 0L

        for (match in termRegex.findAll(working)) {
            output.append(working, cursor, match.range.first)

            val digitsToken = match.groupValues[2]
            val hasDice = match.groupValues[3].isNotEmpty()
            val facesToken = match.groupValues[4]

            val diceCount: Int = digitsToken.toIntOrNull() ?: if (hasDice) 1 else 0

            if (!hasDice) {
                val value = diceCount
                output.append('+').append(value)
                sum = sum?.plus(value)
            } else {
                val faces = facesToken.toIntOrNull()
                if (faces == null || faces <= 0) {
                    sum = null
                    output.append("+INVALID")
                    cursor = match.range.last + 1
                    continue
                }

                if (diceCount > 10 || faces > 100_000) {
                    sum = null
                    output.append("+OUT OF LIMIT")
                } else {
                    for (i in 0 until diceCount) {
                        val rollValue = context.nextRoll(faces)
                        output.append("+d").append(facesToken).append('(').append(rollValue).append(')')
                        sum = sum?.plus(rollValue)
                    }
                }
            }

            cursor = match.range.last + 1
        }

        output.append(working, cursor, working.length)

        val expanded = if (output.isNotEmpty()) output.substring(1) else ""
        return Result(originalExpression = original, expandedExpression = expanded, total = sum)
    }
}
