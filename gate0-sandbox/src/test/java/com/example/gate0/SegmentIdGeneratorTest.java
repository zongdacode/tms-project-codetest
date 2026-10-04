package com.example.gate0;

import com.example.gate0.idgen.SegmentIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gate 0 关键项：号段模式发号在 MP 3.5.17 + Boot 4 下可用，且跨号段连续不重复。 */
@SpringBootTest
class SegmentIdGeneratorTest {

    @Autowired
    private SegmentIdGenerator generator;

    @Test
    void idsAreUniqueAndMonotonicAcrossSegments() {
        Set<Long> seen = new HashSet<>();
        long previous = -1;
        // 3000 个 = 跨 3 个号段（STEP=1000），覆盖换段逻辑
        for (int i = 0; i < 3000; i++) {
            long id = generator.nextId("tms_test");
            assertTrue(id > previous, "ID 必须严格递增：" + previous + " -> " + id);
            previous = id;
            assertTrue(seen.add(id), "ID 重复：" + id);
        }
        assertEquals(3000, seen.size());
    }
}
