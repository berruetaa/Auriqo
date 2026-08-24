package com.auriqo.music.playback

/** A bounded, deterministic look-ahead plan for stream preloading. */
internal class PlaybackPreloadPlanner(
    private val maxCandidates: Int,
) {
    init {
        require(maxCandidates > 0) { "maxCandidates must be positive" }
    }

    data class Candidate(
        val mediaId: String,
        val priority: Int,
    )

    private val priorities = linkedMapOf<String, Int>()

    /** Adds a candidate or promotes an existing candidate to the higher priority. */
    @Synchronized
    fun offer(mediaId: String, priority: Int) {
        if (mediaId.isBlank()) return
        val normalizedPriority = priority.coerceAtLeast(0)
        val previousPriority = priorities[mediaId]
        if (previousPriority == null || normalizedPriority < previousPriority) {
            priorities[mediaId] = normalizedPriority
        }
    }

    @Synchronized
    fun snapshot(): List<Candidate> = priorities
        .entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(maxCandidates)
        .map { Candidate(it.key, it.value) }

    @Synchronized
    fun clear() {
        priorities.clear()
    }
}
