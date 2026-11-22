package org.jlinalg.cli;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerNamedRuntimeTest {
    @Test void packagedMultilineHealthFixturesRetainTheirActualInputHashes()throws Exception{
        assertEquals("cef30f2c85668101df741417617ea61b1f4bd27ea9f9ab16563a9b1d24ba4f00",HomerNamedRuntime.healthFixtureDigest("mm10"));
        assertEquals(64,HomerNamedRuntime.healthFixtureDigest("hg38").length());
        assertThrows(IOException.class,()->HomerNamedRuntime.healthFixtureDigest("hg19"));
    }
}
