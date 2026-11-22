package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PafAlignmentsTest {
    private final Map<String,Long> q=Map.of("q",1000L),t=Map.of("chr1",2000L);
    @Test void reverseIndelAndSplicedCigarGeometryUseForwardCoordinates() throws Exception {
        var a=PafAlignments.parse("q\t1000\t100\t200\t-\tchr1\t2000\t400\t505\t90\t105\t60\ttp:A:P\tcg:Z:50M5D50M",q,t,true);
        assertEquals(100,a.queryStart());assertEquals("-",a.strand());assertEquals("P",a.type());
        var s=PafAlignments.parse("q\t1000\t0\t100\t+\tchr1\t2000\t100\t400\t100\t100\t60\tcg:Z:50=200N50=",q,t,true);assertEquals(400,s.targetEnd());
    }
    @Test void inconsistentLengthsCoordinatesAndCigarsFail() {
        for (String row:new String[]{"q\t999\t0\t100\t+\tchr1\t2000\t0\t100\t100\t100\t60\tcg:Z:100M","q\t1000\t0\t100\t+\tchr1\t2000\t0\t100\t100\t100\t60\tcg:Z:90M","q\t1000\t0\t100\t+\tchr1\t2000\t0\t100\t100\t100\t60\tcg:Z:100M\tcg:Z:100M","q\t1000\t0\t100\t?\tchr1\t2000\t0\t100\t100\t100\t60"}) assertThrows(IOException.class,()->PafAlignments.parse(row,q,t,true));
    }
}
