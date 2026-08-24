package com.auriqo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPreloadPlannerTest {
    @Test
    fun duplicateCandidatesAreDeduplicatedAndHigherPriorityWins() {
        val planner = PlaybackPreloadPlanner(maxCandidates = 3)

        planner.offer("second", priority = 1)
        planner.offer("next", priority = 2)
        planner.offer("second", priority = 0)
        planner.offer("next", priority = 3)

        assertEquals(
            listOf(
                PlaybackPreloadPlanner.Candidate("second", 0),
                PlaybackPreloadPlanner.Candidate("next", 2),
            ),
            planner.snapshot(),
        )
    }

    @Test
    fun plannerIsBoundedAndOrdersCandidatesByPriority() {
        val planner = PlaybackPreloadPlanner(maxCandidates = 2)
        planner.offer("third", priority = 2)
        planner.offer("next", priority = 0)
        planner.offer("second", priority = 1)

        assertEquals(
            listOf(
                PlaybackPreloadPlanner.Candidate("next", 0),
                PlaybackPreloadPlanner.Candidate("second", 1),
            ),
            planner.snapshot(),
        )
    }
}
