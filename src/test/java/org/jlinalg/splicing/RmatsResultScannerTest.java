/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.splicing;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RmatsResultScannerTest {
    private String source(){String first=RmatsResultsTest.fixture(RmatsResults.Event.SE,true,"0.049999999999999999999999999999999","-0.05","NA");String second=RmatsResultsTest.fixture(RmatsResults.Event.SE,true,"NA","0.5","0.01").split("\n")[1].replace("original-a","second-a").replace("original-b","second-b");return first+second+"\n";}
    @Test void matchesWholeTableProjectionAndActualGlobalLineNumbers()throws Exception{
        var limits=new RmatsResults.Limits(10,10000,2,2);var threshold=new RmatsResults.Threshold(new BigDecimal("0.05"),new BigDecimal("0.05"));var all=RmatsResults.read(new StringReader(source()),RmatsResults.Event.SE,limits);var streamed=new ArrayList<RmatsResultScanner.Observed>();var summary=RmatsResultScanner.scan(new StringReader(source()),RmatsResults.Event.SE,limits,threshold,streamed::add);assertEquals(List.of(2L,3L),streamed.stream().map(RmatsResultScanner.Observed::sourceLine).toList());assertEquals(all.rows().stream().map(RmatsResults.Row::rawLine).toList(),streamed.stream().map(RmatsResultScanner.Observed::rawLine).toList());assertEquals(all.missingFields(),summary.missingFields());assertEquals(List.of(RmatsResults.Decision.SELECTED,RmatsResults.Decision.UNASSESSABLE),streamed.stream().map(RmatsResultScanner.Observed::decision).toList());
    }
    @Test void detectsCrossRowIdentityCollisionsAndPropagatesSinkFailures(){
        String source=source();String duplicate=source+source.split("\n")[1]+"\n";var limits=new RmatsResults.Limits(10,10000,2,2);var threshold=new RmatsResults.Threshold(new BigDecimal("0.05"),new BigDecimal("0.05"));assertThrows(IOException.class,()->RmatsResultScanner.scan(new StringReader(duplicate),RmatsResults.Event.SE,limits,threshold,row->{}));assertThrows(IOException.class,()->RmatsResultScanner.scan(new StringReader(source),RmatsResults.Event.SE,limits,threshold,row->{throw new IOException("Output unavailable");}));
    }
    @Test void admitsHeaderOnlyButNeverTruncatesRowsAtCapacity()throws Exception{
        String header=String.join("\t",RmatsResults.Event.SE.header(true))+"\n";var threshold=new RmatsResults.Threshold(new BigDecimal("0.05"),new BigDecimal("0.05"));assertEquals(0,RmatsResultScanner.scan(new StringReader(header),RmatsResults.Event.SE,new RmatsResults.Limits(0,10000,2,2),threshold,row->{}).rows());assertThrows(IOException.class,()->RmatsResultScanner.scan(new StringReader(source()),RmatsResults.Event.SE,new RmatsResults.Limits(1,10000,2,2),threshold,row->{}));
    }
}
