package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class EpgSourceEditorPolicyTest {
    private val a="https://example.com/guide.xml"
    private val b="https://example.org/guide.xml.gz"
    private val c="http://example.net/epg.xml"

    @Test fun importsLegacyNewlineEntriesWithoutChangingPriority(){
        assertEquals(listOf(a,b),EpgSourceEditorPolicy.read("  $a\n$b\n$a\n  "))
    }

    @Test fun editsOnlyOneEntryAndRejectsDuplicates(){
        assertEquals(listOf(a,c),EpgSourceEditorPolicy.edit(listOf(a,b),1,c))
        assertNull(EpgSourceEditorPolicy.edit(listOf(a,b),1,a))
        assertEquals(listOf(a,b),EpgSourceEditorPolicy.edit(listOf(a,b),1,b))
    }

    @Test fun addRejectsInvalidOrDuplicateUrls(){
        assertEquals(listOf(a,b),EpgSourceEditorPolicy.add(listOf(a),b))
        assertNull(EpgSourceEditorPolicy.add(listOf(a),a))
        assertNull(EpgSourceEditorPolicy.add(listOf(a),"ftp://example.com/guide.xml"))
        assertNull(EpgSourceEditorPolicy.add(listOf(a),"https://user:pass@example.com/guide"))
        assertNull(EpgSourceEditorPolicy.add(listOf(a),"https://example.com/guide xml"))
    }

    @Test fun priorityReorderAndDeleteAreLocal(){
        assertEquals(listOf(b,a,c),EpgSourceEditorPolicy.move(listOf(a,b,c),1,-1))
        assertEquals(listOf(a,c,b),EpgSourceEditorPolicy.move(listOf(a,b,c),1,1))
        assertNull(EpgSourceEditorPolicy.move(listOf(a,b),0,-1))
        assertEquals(listOf(a,c),EpgSourceEditorPolicy.remove(listOf(a,b,c),1))
    }

    @Test fun legacyStoredSourceCountIsBoundedWithoutChangingPriority(){
        val urls=(0 until 20).map{"https://guide$it.example.org/epg.xml"}
        assertEquals(urls.take(EpgSourceEditorPolicy.MAX_MANUAL_SOURCES),
            EpgSourceEditorPolicy.read(urls.joinToString("\n")))
    }

    @Test fun sourceCountIsBounded(){
        val urls=(0 until 16).map{"https://guide$it.example.org/epg.xml"}
        assertNull(EpgSourceEditorPolicy.add(urls,a))
    }
}
