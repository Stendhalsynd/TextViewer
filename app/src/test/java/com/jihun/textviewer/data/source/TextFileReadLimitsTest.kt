package com.jihun.textviewer.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileReadLimitsTest {
    @Test
    fun shouldReject_returnsFalseForLargeButAllowedContent() {
        assertFalse(
            "1.5M 문자는 기존 1M 제한보다 커도 파일 열기가 가능해야 합니다",
            TextFileReadLimits.shouldReject(1_500_000),
        )
    }

    @Test
    fun shouldReject_returnsTrueForExceedingConfiguredLimit() {
        assertTrue(
            "기본 한도(5,000,000자)를 넘는 파일은 계속 실패 처리되어야 합니다",
            TextFileReadLimits.shouldReject(5_500_000),
        )
    }
}
