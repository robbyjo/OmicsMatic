/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastpJoinTest {
    @TempDir Path temporary;
    private static final FastpJoin.Limits CAPACITY=new FastpJoin.Limits(100,1_048_576,1_000_000,10000);
    private static final FastpFastq.Pairing PAIRS=FastpFastq.Pairing.TERMINAL_MATE_SUFFIX;
    private static FastpFastq.Read read(long n,String header,String sequence,String quality){return new FastpFastq.Read(n,header,sequence,quality,"+");}
    private static FastpFastq.Read good(long n,String mate){return read(n,"@same"+mate,"ACGT".repeat(10),"I".repeat(40));}
    private static FastpFastq.Read bad(long n,String mate){return read(n,"@same"+mate,"ACGT".repeat(10),"!".repeat(40));}
    private static FastpParameters.Options options(boolean dedup,boolean orphans,boolean correction){var d=FastpParameters.Options.defaults(1);return new FastpParameters.Options(1,new FastpParameters.Adapters(false,false,"",""),d.global(),d.cutting(),d.filters(),FastpParameters.PolyG.DISABLE,10,false,10,correction,new FastpParameters.Duplication(dedup,dedup,0),d.overlap(),orphans);}
    private static String input(FastpFastq.Read a,FastpFastq.Read b)throws IOException{return FastpJoin.input(a,b,FastpFastq.Encoding.PHRED33,PAIRS);}
    private static String output(String kind,FastpFastq.Read a,String failure)throws IOException{return FastpJoin.output(kind,read(a.ordinal(),a.header()+(failure.isEmpty()?"":" "+failure),a.sequence(),a.quality()),PAIRS);}
    private FastpJoin.Counts run(boolean paired,FastpParameters.Options options,FastpJoin.Limits capacity,String... rows)throws IOException {
        Path root=Files.createTempDirectory(temporary,"join-");var sorted=new ArrayList<>(List.of(rows));sorted.sort(String::compareTo);Files.writeString(root.resolve("index.tsv"),String.join("\n",sorted)+"\n");return FastpJoin.reconcile(root.resolve("index.tsv"),root.resolve("ledger.tsv"),paired,options,capacity,10000,()->{},()->{});
    }
    @Test void aPassingSingleEndOccurrenceCannotDisappearWithoutDeduplication()throws Exception {assertThrows(IOException.class,()->run(false,options(false,false,false),CAPACITY,input(good(1,""),null)));}
    @Test void twoFailingMatesAreOmittedBySourceWithoutBeingCalledDuplicates()throws Exception {
        var count=run(true,options(false,false,false),CAPACITY,input(bad(1,"/1"),bad(1,"/2")));assertEquals(2,count.unrepresentedReads());assertEquals(0,count.representedReads());assertEquals(0,count.representedSourceFilterCounts().stream().mapToLong(Long::longValue).sum());
    }
    @Test void twoPassingMatesCannotDisappearWhenNoUpstreamTrimmingOrDedupIsEnabled()throws Exception {assertThrows(IOException.class,()->run(true,options(false,false,false),CAPACITY,input(good(1,"/1"),good(1,"/2"))));}
    @Test void sourceDoesNotEmitBothFailedMatesToFailedFastq()throws Exception {
        var a=bad(1,"/1");var b=bad(1,"/2");assertThrows(IOException.class,()->run(true,options(false,false,false),CAPACITY,input(a,b),output("FAILED",a,"failed_quality_filter"),output("FAILED",b,"failed_quality_filter")));
    }
    @Test void storedOrphanRequiresTheOtherMateToFailAndUsesPairSeverity()throws Exception {
        var a=good(1,"/1");var b=bad(1,"/2");var c=run(true,options(false,true,false),CAPACITY,input(a,b),output("UNPAIRED1",a,""),output("FAILED",b,"failed_quality_filter"));assertEquals(2,c.representedSourceFilterCounts().get(20));
        assertThrows(IOException.class,()->run(true,options(false,false,false),CAPACITY,input(a,b),output("UNPAIRED1",a,""),output("FAILED",b,"failed_quality_filter")));
    }
    @Test void anOutputOccurrenceCannotBeUsedTwice()throws Exception {
        var a=good(1,"");assertThrows(IOException.class,()->run(false,options(false,false,false),CAPACITY,input(a,null),output("MAIN1",a,""),output("MAIN1",a,"")));
    }
    @Test void identicalHeaderOccurrencesRetainExplicitAssignmentAmbiguity()throws Exception {
        var a=good(1,"");var b=good(2,"");var c=run(false,options(true,false,false),CAPACITY,input(a,null),input(b,null),output("MAIN1",a,""));assertEquals(1,c.ambiguousFamilies());assertEquals(1,c.unrepresentedReads());
    }
    @Test void unchangedBaseCannotAcquireInventedQuality()throws Exception {
        var a=good(1,"");var changed=read(1,a.header(),a.sequence(),"H"+a.quality().substring(1));assertThrows(IOException.class,()->run(false,options(false,false,false),CAPACITY,input(a,null),output("MAIN1",changed,"")));
    }
    @Test void lowQualityCorrectionMustHaveAHighQualityComplementInTheMate()throws Exception {
        var a=read(1,"@same/1","A".repeat(40),"#"+"I".repeat(39));var b=read(1,"@same/2","T".repeat(40),"I".repeat(40));var invented=read(1,a.header(),"C"+"A".repeat(39),"I".repeat(40));
        assertThrows(IOException.class,()->run(true,options(false,false,true),CAPACITY,input(a,b),output("MAIN1",invented,""),output("MAIN2",b,"")));
    }
    @Test void explicitFamilyAndWorkLimitsFailInsteadOfMatchingOnlyAPrefix()throws Exception {
        var a=good(1,"");var b=good(2,"");assertThrows(IOException.class,()->run(false,options(true,false,false),new FastpJoin.Limits(2,1_048_576,1_000_000,10000),input(a,null),input(b,null),output("MAIN1",a,"")));
        assertThrows(IOException.class,()->run(false,options(false,false,false),new FastpJoin.Limits(100,1_048_576,1,10000),input(a,null),output("MAIN1",a,"")));
    }
}
