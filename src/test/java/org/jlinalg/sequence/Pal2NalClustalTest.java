/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Pal2NalClustalTest {
    private static final MafftSequences.Limits LIMITS=MafftSequences.Limits.defaults();
    private static Pal2NalClustal.Data read(String text)throws IOException{return Pal2NalClustal.read(new StringReader(text),LIMITS,()->{});}
    @Test void completeBlocksCountersSelectionsAndLiteralCaseArePreserved()throws Exception {
        String text="CLUSTAL W\r\n\r\np1    Ma- 2\r\np2    M-A 2\r\n      # #\r\n\r\np1    KQ 4\rp2    KR 4\r       #\r";
        var data=read(text);assertEquals(List.of(new Pal2Nal.Sequence("p1","Ma-KQ"),new Pal2Nal.Sequence("p2","M-AKR")),data.rows());assertEquals("# # #",data.selection());
        assertEquals(2,data.statistics().blocks());assertEquals(10,data.statistics().cells());assertEquals(text.length(),data.statistics().decodedCharacters());
        assertThrows(UnsupportedOperationException.class,()->data.rows().clear());
    }
    @Test void absentSelectionsBecomeExplicitUnselectedPositions()throws Exception {
        var data=read("CLUSTAL W\n\np1    MA\np2    MA\n\np1    KQ\np2    KR\n");assertEquals("    ",data.selection());assertEquals(2,data.statistics().blocks());
    }
    @Test void malformedOrTruncatedBlocksNeverReturnAPrefix() {
        for(String text:List.of("","p1 MA\n","CLUSTALX\np1 MA\n","CLUSTAL W\n", "CLUSTAL W\np1    MA\np1    MA\n",
                "CLUSTAL W\np1    MA\np2    M\n","CLUSTAL W\np1    MA\np2     MA\n","CLUSTAL W\np1    MA 3\n",
                "CLUSTAL W\np1    MA\np2    MA\n\np1    KQ\n","CLUSTAL W\np1    MA\np2    MA\n\np2    KQ\np1    KR\n",
                "CLUSTAL W\np1    MA\n\np2    KQ\n", "CLUSTAL W\n   ##\n", "CLUSTAL W\np1    MA\n      ###\n",
                "CLUSTAL W\np1    MA\n      ??\n","CLUSTAL W\np1    MA\nfooter garbage here\n","CLUSTAL W\np1    MΩ\n"))assertThrows(IOException.class,()->read(text),text);
    }
    @Test void eofAndSuccessfulCloseAreMandatory() {
        var closed=new AtomicBoolean();Reader source=new FilterReader(new StringReader("CLUSTAL W\np1    MA\n")){
            @Override public void close()throws IOException{closed.set(true);throw new IOException("injected late close failure");}
        };
        assertThrows(IOException.class,()->Pal2NalClustal.read(source,LIMITS,()->{}));assertTrue(closed.get());
    }
    @Test void capacityAndCancellationAreCheckedWhileReadingLongLines() {
        var small=new MafftSequences.Limits(10,100,100,10000,5,5,100000);
        assertThrows(IOException.class,()->Pal2NalClustal.read(new StringReader("CLUSTAL W\np1 MA\n"),small,()->{}));
        var closed=new AtomicBoolean();var checks=new AtomicInteger();Reader source=new FilterReader(new StringReader("CLUSTAL W\np1    "+"A".repeat(10000))){@Override public void close()throws IOException{closed.set(true);super.close();}};
        assertThrows(CancellationException.class,()->Pal2NalClustal.read(source,LIMITS,()->{if(checks.incrementAndGet()==4)throw new CancellationException();}));assertTrue(closed.get());
    }
}
