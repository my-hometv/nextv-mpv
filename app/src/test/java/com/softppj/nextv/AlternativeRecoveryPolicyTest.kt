package com.softppj.nextv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class AlternativeRecoveryPolicyTest {
    @Test fun circularSelectionSkipsFailedSourcesAndWraps() {
        val urls=listOf("a","b","c","d")
        assertEquals(3,AlternativeRecoveryPolicy.nextCircularUsable(urls,0,setOf("b","c")){it})
        assertEquals(0,AlternativeRecoveryPolicy.nextCircularUsable(urls,3,emptySet()){it})
        assertEquals(2,AlternativeRecoveryPolicy.nextCircularUsable(urls,0,setOf("b")){it})
    }
    @Test fun circularSelectionStopsWhenEveryOtherSourceFailed() {
        val urls=listOf("a","b","c")
        assertNull(AlternativeRecoveryPolicy.nextCircularUsable(urls,0,setOf("b","c")){it})
        assertNull(AlternativeRecoveryPolicy.nextCircularUsable(urls,0,setOf("a","b","c")){it})
        assertNull(AlternativeRecoveryPolicy.nextCircularUsable(listOf("a"),0,emptySet()){it})
        assertNull(AlternativeRecoveryPolicy.nextCircularUsable(urls,-1,emptySet()){it})
    }
    @Test fun circularSelectionIgnoresMalformedAndWhitespaceSources() {
        assertEquals(3,AlternativeRecoveryPolicy.nextCircularUsable(
            listOf("current","broken","  "," healthy "),0,emptySet()
        ){if(it=="broken")error("bad URL") else it})
        assertNull(AlternativeRecoveryPolicy.nextCircularUsable(
            listOf("current"," bad "),0,setOf("bad")
        ){it})
    }

    @Test fun canonicalAttemptKeySkipsPaddedDuplicateSources() {
        val tried = setOf(AlternativeRecoveryPolicy.attemptKey("stream", "Media3"))
        assertEquals("next" to "Media3", AlternativeRecoveryPolicy.nextEngineFirst(
            listOf(" stream ", "stream", "next"), listOf("Media3"),
            { it }, { _, _ -> true }, tried))
    }

    @Test fun untriedUrlSelectionRespectsTrimmedAttemptedKeys() {
        assertEquals("next", AlternativeRecoveryPolicy.nextUntried(
            listOf(" stream ", "next"), setOf("stream")) { it })
    }

    @Test fun skipsFailedSourceAndSelectsNextInPlaylistOrder() {
        assertEquals(2, AlternativeRecoveryPolicy.nextUntried(listOf(1,2,3),setOf("a")) {
            mapOf(1 to "a",2 to "b",3 to "c").getValue(it)
        })
    }
    @Test fun neverLoopsWhenAllSourcesHaveFailed() {
        assertNull(AlternativeRecoveryPolicy.nextUntried(listOf("a","b"),setOf("a","b")) { it })
    }
    @Test fun duplicateUrlsDoNotCauseRepeatedRetries() {
        assertEquals(3, AlternativeRecoveryPolicy.nextUntried(listOf(1,2,3),setOf("a")) {
            if(it==3)"b" else "a"
        })
    }
    @Test fun newCycleCanRetryAfterConfirmedPlayback() {
        assertEquals("a",AlternativeRecoveryPolicy.nextUntried(listOf("a","b"),emptySet()) { it })
    }

    @Test fun engineFirstChecksEveryUrlBeforeRotatingEngine() {
        val urls=listOf("u1","u2","u3")
        val engines=listOf("Media3","LibVLC","MPV")
        val tried=mutableSetOf<String>()
        val actual=mutableListOf<Pair<String,String>>()
        repeat(9) {
            val next=AlternativeRecoveryPolicy.nextEngineFirst(
                urls,engines,{it},{_,_->true},tried
            )!!
            actual.add(next)
            tried.add(next.first+"|"+next.second)
        }
        assertEquals(listOf(
            "u1" to "Media3","u2" to "Media3","u3" to "Media3",
            "u1" to "LibVLC","u2" to "LibVLC","u3" to "LibVLC",
            "u1" to "MPV","u2" to "MPV","u3" to "MPV"
        ),actual)
        assertNull(AlternativeRecoveryPolicy.nextEngineFirst(urls,engines,{it},{_,_->true},tried))
    }

    @Test fun engineFirstRespectsUnsupportedCombinations() {
        assertEquals("u2" to "Media3",AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("u1","u2"),listOf("Media3","LibVLC"),{it},
            {url,engine->url!="u1" || engine=="LibVLC"},emptySet()
        ))
    }

    @Test fun engineFirstDoesNotRepeatDuplicateUrlEnginePairs() {
        val tried=mutableSetOf<String>()
        val urls=listOf("u1","u1","u2")
        val results=mutableListOf<Pair<String,String>>()
        repeat(4) {
            val result=AlternativeRecoveryPolicy.nextEngineFirst(
                urls,listOf("Media3","LibVLC"),{it},{_,_->true},tried
            )!!
            results.add(result)
            tried.add(result.first+"|"+result.second)
        }
        assertEquals(listOf(
            "u1" to "Media3","u2" to "Media3",
            "u1" to "LibVLC","u2" to "LibVLC"
        ),results)
        assertNull(AlternativeRecoveryPolicy.nextEngineFirst(
            urls,listOf("Media3","LibVLC"),{it},{_,_->true},tried
        ))
    }

    @Test fun disabledEngineIsNeverAttempted() {
        assertEquals("u1" to "LibVLC",AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("u1","u2"),listOf("Media3","LibVLC"),{it},
            {_,engine->engine=="LibVLC"},emptySet()
        ))
    }
    @Test fun engineFirstSkipsMalformedSourcesAndBrokenCapabilityChecks() {
        val result = AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("bad", "unsupported", "good"),
            listOf("Media3", "LibVLC"),
            { if (it == "bad") error("bad source") else it },
            { url, engine -> if (url == "unsupported" && engine == "Media3") error("missing backend") else true },
            emptySet()
        )
        assertEquals("good" to "Media3", result)
    }

    @Test fun nextUntriedSkipsBlankAndThrowingSources() {
        assertEquals("valid", AlternativeRecoveryPolicy.nextUntried(
            listOf("broken", "", " ", "valid"), emptySet()
        ) { if (it == "broken") error("bad URL") else it })
    }

    @Test fun engineFirstIgnoresBlankAndDuplicateEngines() {
        val urls = listOf("u1", "u2")
        val engines = listOf("", " ", "Media3", "", "Media3", "LibVLC")
        val tried = mutableSetOf<String>()
        val visited = mutableListOf<Pair<String, String>>()
        while (true) {
            val next = AlternativeRecoveryPolicy.nextEngineFirst(
                urls, engines, { it }, { _, _ -> true }, tried
            ) ?: break
            visited += next
            tried += next.first + "|" + next.second
        }
        assertEquals(listOf(
            "u1" to "Media3", "u2" to "Media3",
            "u1" to "LibVLC", "u2" to "LibVLC"
        ), visited)
        assertNull(AlternativeRecoveryPolicy.nextEngineFirst(
            urls, listOf("", " "), { it }, { _, _ -> true }, emptySet()
        ))
    }

    @Test fun engineFirstDoesNotDispatchBlankSources() {
        assertEquals("valid" to "Media3", AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("", "  ", "valid"), listOf("Media3"), { it }, { _, _ -> true }, emptySet()
        ))
    }

    @Test fun whitespaceOnlyEngineIsNeverSelected() {
        assertNull(AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("url"), listOf(" ", "\t", "\n"), { it }, { _, _ -> true }, emptySet()
        ))
    }

    @Test fun engineFirstNormalizesPaddedNamesAndDeduplicatesAfterTrimming() {
        val tried = mutableSetOf<String>()
        val visited = mutableListOf<Pair<String, String>>()
        while (true) {
            val attempt = AlternativeRecoveryPolicy.nextEngineFirst(
                listOf("u1", "u2"),
                listOf(" Media3 ", "Media3", " LibVLC ", "LibVLC"),
                { it }, { _, _ -> true }, tried
            ) ?: break
            visited += attempt
            tried += AlternativeRecoveryPolicy.attemptKey(attempt.first, attempt.second)
        }
        assertEquals(listOf(
            "u1" to "Media3", "u2" to "Media3",
            "u1" to "LibVLC", "u2" to "LibVLC"
        ), visited)
    }

    @Test fun canonicalAttemptKeysPreventUrlEngineDelimiterCollision() {
        val tried = setOf(AlternativeRecoveryPolicy.attemptKey("a|b", "c"))
        val result = AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("a", "a|b"), listOf("b|c", "c"),
            { it }, { _, _ -> true }, tried
        )
        assertEquals("a" to "b|c", result)
        assertNull(AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("a|b"), listOf("c"), { it }, { _, _ -> true }, tried
        ))
    }

    @Test fun legacyAttemptKeysRemainRecognizedDuringMigration() {
        assertEquals("second" to "Media3", AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("first", "second"), listOf("Media3"),
            { it }, { _, _ -> true }, setOf("first|Media3")
        ))
    }

    @Test fun malformedUrlAndCapabilityExceptionsDoNotBlockOtherEngines() {
        val attempted = mutableSetOf<String>()
        val seen = mutableListOf<Pair<String, String>>()
        while (true) {
            val next = AlternativeRecoveryPolicy.nextEngineFirst(
                listOf("broken", "blocked", "healthy"),
                listOf(" Media3 ", " LibVLC "),
                { if (it == "broken") error("invalid source") else it },
                { url, engine -> if (url == "blocked" && engine == "Media3") error("missing backend") else true },
                attempted
            ) ?: break
            seen += next
            attempted += AlternativeRecoveryPolicy.attemptKey(next.first, next.second)
        }
        assertEquals(listOf(
            "healthy" to "Media3",
            "blocked" to "LibVLC",
            "healthy" to "LibVLC"
        ), seen)
    }

    @Test fun lengthPrefixedAttemptKeysAvoidDelimiterCollisions() {
        val first = AlternativeRecoveryPolicy.attemptKey("a|b", "c")
        val second = AlternativeRecoveryPolicy.attemptKey("a", "b|c")
        assertFalse(first == second)
        assertEquals(first, AlternativeRecoveryPolicy.attemptKey("a|b", "c"))
    }


    @Test fun actualRecoveryPolicySkipsFailedPairsAndPreservesEngineMajorOrder() {
        val urls = listOf("u1", "u2", "u3")
        val engines = listOf("Media3", "LibVLC")
        val attempted = mutableSetOf<String>()
        val observed = mutableListOf<Pair<String, String>>()
        while (true) {
            val next = AlternativeRecoveryPolicy.nextEngineFirst(
                urls, engines, { it }, { _, _ -> true }, attempted
            ) ?: break
            observed += next
            attempted += AlternativeRecoveryPolicy.attemptKey(next.first, next.second)
        }
        assertEquals(listOf(
            "u1" to "Media3", "u2" to "Media3", "u3" to "Media3",
            "u1" to "LibVLC", "u2" to "LibVLC", "u3" to "LibVLC"
        ), observed)
    }

    @Test fun actualRecoveryPolicyFallsBackWhenMedia3Unsupported() {
        val next = AlternativeRecoveryPolicy.nextEngineFirst(
            listOf("u1", "u2"), listOf("Media3", "LibVLC"),
            { it }, { _, engine -> engine == "LibVLC" }, emptySet()
        )
        assertEquals("u1" to "LibVLC", next)
    }
    @Test fun queuedAlternativeSwitchRejectsSupersededPlaybackSnapshots() {
        val valid = listOf(true, true, true, true, true, true)
        fun allowed(flags: List<Boolean>) = AlternativeRecoveryPolicy.mayApplyQueuedSwitch(
            flags[0], flags[1], flags[2], flags[3], flags[4], flags[5]
        )
        assertTrue(allowed(valid))
        for (changed in valid.indices) {
            val superseded = valid.toMutableList()
            superseded[changed] = false
            assertFalse("stale snapshot dimension $changed must reject playback", allowed(superseded))
        }
    }
}
