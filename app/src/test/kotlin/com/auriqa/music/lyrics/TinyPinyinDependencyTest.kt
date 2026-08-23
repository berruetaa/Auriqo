package com.auriqo.music.lyrics

import com.github.promeg.pinyinhelper.Pinyin
import org.junit.Assert.assertFalse
import org.junit.Test

class TinyPinyinDependencyTest {
    @Test
    fun centralArtifactProvidesThePinyinApiUsedByLyrics() {
        assertFalse(Pinyin.toPinyin('音').isBlank())
    }
}
