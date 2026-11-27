/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.splicing;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RmatsResultsTest {
    static String fixture(RmatsResults.Event event,boolean second,String p,String psi,String fdr){
        var values=new ArrayList<String>();int ids=0;
        for(String key:event.header(second))values.add(switch(key){
            case "ID" -> ++ids==1?"original-a":"original-b";
            case "GeneID" -> "\"gene-1\"";case "geneSymbol" -> "symbol";case "chr" -> "1";case "strand" -> "-";
            case "IJC_SAMPLE_1","IJC_SAMPLE_2" -> "1,2";case "SJC_SAMPLE_1","SJC_SAMPLE_2" -> "3,4";
            case "IncFormLen" -> "100";case "SkipFormLen" -> "50";case "PValue" -> p;case "FDR" -> fdr;
            case "IncLevel1" -> "0.25,NA";case "IncLevel2" -> "0.25,0.50";case "IncLevelDifference" -> psi;
            default -> key.endsWith("ES")||key.contains("Start")?"0":"20";
        });return String.join("\t",event.header(second))+"\n"+String.join("\t",values)+"\n";
    }
    static RmatsResults.Table read(String value)throws IOException{return RmatsResults.read(new StringReader(value),RmatsResults.Event.SE,new RmatsResults.Limits(100,10000,2,2));}
    static RmatsResults.Threshold threshold(){return new RmatsResults.Threshold(new BigDecimal("0.05"),new BigDecimal("0.05"));}
    static RmatsResults.Decision decision(String p,String psi,String fdr)throws IOException{return threshold().classify(read(fixture(RmatsResults.Event.SE,true,p,psi,fdr)).rows().get(0));}
    @Test void preservesEveryOriginalPositionalTokenAndBothUnequalIds()throws Exception{
        String source=fixture(RmatsResults.Event.SE,true,"0.01","-0.05","0.99");var table=read(source);assertEquals(List.of(0,11),table.idPositions());assertEquals("original-a",table.rows().get(0).cells().get(0));assertEquals("original-b",table.rows().get(0).cells().get(11));assertEquals(source.split("\n")[1],table.rows().get(0).rawLine());assertEquals(1L,table.missingFields().get("IncLevel1"));assertThrows(UnsupportedOperationException.class,()->table.rows().get(0).cells().set(0,"repaired"));
    }
    @Test void importsAllFiveSchemasWithOneAndTwoIdColumns()throws Exception{
        for(var event:RmatsResults.Event.values())for(boolean second:new boolean[]{false,true}){
            var table=RmatsResults.read(new StringReader(fixture(event,second,"0.01","0.1","0.5")),event,new RmatsResults.Limits(1,10000,2,2));assertEquals(second?2:1,table.idPositions().size());assertEquals(1,table.rows().size());
        }
    }
    @Test void appliesStrictRawPAndInclusiveAbsolutePsiUsingExactDecimals()throws Exception{
        assertEquals(RmatsResults.Decision.EXCLUDED,decision("0.05","0.9","0.001"));assertEquals(RmatsResults.Decision.SELECTED,decision("0.049999999999999999999999999","0.05","0.9"));assertEquals(RmatsResults.Decision.SELECTED,decision("1e-2","-5e-2","0.9"));assertEquals(RmatsResults.Decision.EXCLUDED,decision("0.01","0.049999999999999999999999999","0.0001"));
    }
    @Test void retainsMissingMasksAndNeverUsesFdrAsRawP()throws Exception{
        assertEquals(RmatsResults.Decision.UNASSESSABLE,decision("NA","0.9","0.001"));assertEquals(RmatsResults.Decision.UNASSESSABLE,decision("0.9","NA","0.001"));assertEquals(RmatsResults.Decision.SELECTED,decision("0.01","0.9","NA"));assertEquals(RmatsResults.Decision.EXCLUDED,decision("0.9","0.9","0.001"));var table=read(fixture(RmatsResults.Event.SE,true,"NA","NA","NA"));assertEquals(4,table.missingFields().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void retainsZeroPopulationAndAcceptsCrLfWithoutChangingFields()throws Exception{
        String header=String.join("\t",RmatsResults.Event.SE.header(true));assertTrue(read(header+"\r\n").rows().isEmpty());var table=read(fixture(RmatsResults.Event.SE,true,"0.01","0.1","0.2").replace("\n","\r\n"));assertEquals(header,table.rawHeader());assertFalse(table.rows().get(0).rawLine().contains("\r"));
    }
    @Test void rejectsMalformedProbabilitiesInsteadOfRepairingThem(){
        for(String p:List.of("NaN","Inf","nan",""," 0.01","-0.1","1.01","1e1000000000"))assertThrows(IOException.class,()->read(fixture(RmatsResults.Event.SE,true,p,"0.1","0.2")),p);
    }
    @Test void rejectsRaggedRenamedDuplicateAndReorderedFields(){
        String source=fixture(RmatsResults.Event.SE,true,"0.01","0.1","0.2");assertThrows(IOException.class,()->read(source.replaceFirst("PValue","FDR")));assertThrows(IOException.class,()->read(source.replaceFirst("ID","renamed-ID")));assertThrows(IOException.class,()->read(source+source.split("\n")[1]+"\n"));assertThrows(IOException.class,()->read(source.replace("original-a\t","original-a\textra\t")));
    }
    @Test void rejectsCountsCoordinatesAndReplicateMismatch(){
        String source=fixture(RmatsResults.Event.SE,true,"0.01","0.1","0.2");assertThrows(IOException.class,()->read(source.replace("1,2","1")));assertThrows(IOException.class,()->read(source.replace("1,2","1,-2")));assertThrows(IOException.class,()->read(source.replace("1,2","1,NA")));assertThrows(IOException.class,()->read(source.replace("\t0\t20\t","\t20\t0\t")));
    }
    @Test void refusesCapacityAndInvalidThresholdDeclarations(){
        String source=fixture(RmatsResults.Event.SE,true,"0.01","0.1","0.2");assertThrows(IOException.class,()->RmatsResults.read(new StringReader(source),RmatsResults.Event.SE,new RmatsResults.Limits(0,10000,2,2)));assertThrows(IOException.class,()->RmatsResults.read(new StringReader(source),RmatsResults.Event.SE,new RmatsResults.Limits(100,10,2,2)));assertThrows(IllegalArgumentException.class,()->new RmatsResults.Threshold(new BigDecimal("1.1"),BigDecimal.ZERO));assertThrows(IllegalArgumentException.class,()->new RmatsResults.Limits(1,1,0,2));
    }
}
