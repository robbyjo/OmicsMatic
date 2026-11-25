/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class MetaxcanInputTest {
    @TempDir Path temporary;
    static MetaxcanRequest.Capacity capacity(){return new MetaxcanRequest.Capacity(10,100,100,100,1048576,1048576,1048576,1048576,1000000,60000);}
    Path text(String value)throws IOException{Path p=temporary.resolve(UUID.randomUUID()+".txt");return Files.writeString(p,value,StandardCharsets.UTF_8);}
    MetaxcanGwas.Result read(Path p,MetaxcanRequest.Columns c,boolean keep,boolean floor,List<MetaxcanGwas.Disposition> ledger)throws IOException{return MetaxcanGwas.read(p,false,c,keep,floor,capacity(),new MetaxcanTable.Budget(1048576,()->{}),ledger::add);}
    static String col(Map<?,?> c,String name)throws IOException{return c.get(name)==null?null:NextflowFiles.text(c.get(name));}
    static MetaxcanRequest.Columns columns(Map<?,?> c,String separator)throws IOException{return new MetaxcanRequest.Columns("ID","EA","OA",col(c,"zscore"),col(c,"beta"),col(c,"pvalue"),col(c,"se"),col(c,"odds_ratio"),col(c,"beta_sign"),separator);}
    @Test void agreesWithAll24UnmodifiedOriginalGwasIngestionControls()throws Exception {
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/genetics/metaxcan/gwas-ingestion.json")) {
            assertNotNull(stream);var fixture=object(SimpleJson.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));int cases=0;
            for(Object raw:list(fixture.get("cases"))) {
                var c=object(raw);String label=c.get("source")+":"+c.get("name");Path input=text(NextflowFiles.text(c.get("text")));byte[] original=Files.readAllBytes(input);var ledger=new ArrayList<MetaxcanGwas.Disposition>();
                var result=read(input,columns(object(c.get("columns")),(String)c.getOrDefault("separator",null)==null?"tab":NextflowFiles.text(c.get("separator"))),Boolean.TRUE.equals(c.get("keep")),!Boolean.FALSE.equals(c.get("floor")),ledger);
                var expected=list(c.get("expected"));assertEquals(expected.size(),result.rows().size(),label);assertEquals(result.sourceRows(),ledger.size(),label);
                for(Object e:expected) {
                    var r=object(e);var actual=result.rows().get(r.get("id"));assertNotNull(actual,label);assertEquals(r.get("ea"),actual.effectAllele(),label);assertEquals(r.get("oa"),actual.otherAllele(),label);assertEquals(((Number)r.get("z")).doubleValue(),actual.zscore(),0,label);
                    if(r.get("beta")==null)assertNull(actual.beta(),label);else assertEquals(((Number)r.get("beta")).doubleValue(),actual.beta(),2e-15,label);
                }
                assertArrayEquals(original,Files.readAllBytes(input));cases++;
            }assertEquals(24,cases);
        }
    }
    @Test void noSourceFloorIsInventedForAllZeroPValues()throws Exception {
        var c=columns(Map.of("pvalue","P","beta_sign","S"),"tab");Path p=text("ID\tEA\tOA\tP\tS\nrs1\tG\tA\t0\t+\n");
        assertTrue(assertThrows(IOException.class,()->read(p,c,false,true,new ArrayList<>())).getMessage().contains("no nonzero finite P"));assertThrows(IOException.class,()->read(p,c,false,false,new ArrayList<>()));
    }
    @Test void duplicatesUnknownSignsAndFiniteZWithMissingBetaNeverGetAdmitted()throws Exception {
        var z=columns(Map.of("zscore","Z"),"tab");assertThrows(IOException.class,()->read(text("ID\tEA\tOA\tZ\nrs1\tG\tA\t1\nrs1\tG\tA\t2\n"),z,false,true,new ArrayList<>()));
        var signed=columns(Map.of("pvalue","P","beta_sign","S"),"tab");assertThrows(IOException.class,()->read(text("ID\tEA\tOA\tP\tS\nrs1\tG\tA\t.05\t++?\n"),signed,false,true,new ArrayList<>()));
        var beta=columns(Map.of("zscore","Z","beta","B"),"tab");assertThrows(IOException.class,()->read(text("ID\tEA\tOA\tZ\tB\nrs1\tG\tA\t1\tNA\n"),beta,false,true,new ArrayList<>()));
    }
    @Test void quotedCsvAndGzipRetainLiteralCellsAndRejectMalformedOrOversizedInput()throws Exception {
        String csv="\ufeffgene,name,value\r\nG1,\"A, B\",1\r\nG2,\"a\"\"b\nnext\",2\r\n";Path p=temporary.resolve("quoted.gz");try(var gzip=new GZIPOutputStream(Files.newOutputStream(p))){gzip.write(csv.getBytes(StandardCharsets.UTF_8));}
        try(var t=new MetaxcanTable(p,true,"comma",2,new MetaxcanTable.Budget(1000,()->{}),true)){assertEquals(List.of("gene","name","value"),t.header());assertEquals(List.of("G1","A, B","1"),t.next());assertEquals(List.of("G2","a\"b\nnext","2"),t.next());assertNull(t.next());assertTrue(t.complete());}
        assertThrows(IOException.class,()->{try(var t=new MetaxcanTable(p,true,"comma",2,new MetaxcanTable.Budget(10,()->{}),true)){while(t.next()!=null){}}});
        for(String bad:List.of("A,A\n1,2\n","A,B\n\"x\"oops,1\n","A,B\n\"x,1\n","A,B\nx,1,2\n","A,B\nx\"y,1\n")){assertThrows(IOException.class,()->{try(var t=new MetaxcanTable(text(bad),false,"comma",10,new MetaxcanTable.Budget(1000,()->{}),true)){while(t.next()!=null){}}});}
    }
    @Test void completeRowsAndCancellationAreChecked()throws Exception {
        Path p=text("A\n1\n2\n");try(var t=new MetaxcanTable(p,false,"tab",1,new MetaxcanTable.Budget(100,()->{}),true)){assertEquals(List.of("1"),t.next());assertThrows(IOException.class,t::next);assertFalse(t.complete());assertThrows(IOException.class,t::next);}
        assertThrows(IllegalStateException.class,()->new MetaxcanTable(p,false,"tab",10,new MetaxcanTable.Budget(100,()->{throw new IllegalStateException("controlled cancellation");}),true));
    }
    @Test void gzipReadsAllMembersAndRefusesTrailingGarbage()throws Exception {
        Path p=temporary.resolve("concatenated.gz");for(String member:List.of("A\n1\n","2\n"))try(var gzip=new GZIPOutputStream(Files.newOutputStream(p,StandardOpenOption.CREATE,StandardOpenOption.APPEND))){gzip.write(member.getBytes(StandardCharsets.UTF_8));}
        try(var table=new MetaxcanTable(p,true,"tab",10,new MetaxcanTable.Budget(1000,()->{}),true)){assertEquals(List.of("1"),table.next());assertEquals(List.of("2"),table.next());assertNull(table.next());}
        Files.writeString(p,"invalid trailing bytes",StandardCharsets.UTF_8,StandardOpenOption.APPEND);assertThrows(IOException.class,()->{try(var table=new MetaxcanTable(p,true,"tab",10,new MetaxcanTable.Budget(1000,()->{}),true)){while(table.next()!=null){}}});
    }
    @Test void numericMissingMarkersDoNotBecomeMissingIdentifiersAndUtf8BomRequestsWork()throws Exception {
        Path p=text("ID\tEA\tOA\tZ\n.\tG\tA\t1\nrs1\tG\tA\t2\n");var ledger=new ArrayList<MetaxcanGwas.Disposition>();var result=read(p,columns(Map.of("zscore","Z"),"tab"),true,true,ledger);assertTrue(result.rows().containsKey("."));assertEquals(0,result.missingIds());
        Path request=text("\ufeff"+NextflowJson.json(request()));assertEquals("Z.score",MetaxcanRequest.read(request,false).columns().zscore());
        assertThrows(IOException.class,()->read(text("ID\tEA\tOA\tZ\n123\tG\tA\t1\n"),columns(Map.of("zscore","Z"),"tab"),true,true,new ArrayList<>()));
    }
    @Test void requestRequiresExplicitStatsPoliciesAndRetainsPunctuationInHeaders()throws Exception {
        var r=request();var s=MetaxcanRequest.parse(r,false);assertEquals("Z.score",s.columns().zscore());assertEquals("G1,annotation",s.build());
        var settings=new LinkedHashMap<String,Object>();for(var entry:object(r.get("settings")).entrySet())settings.put(NextflowFiles.text(entry.getKey()),entry.getValue());settings.remove("keep_non_rsid");r.put("settings",settings);assertThrows(IOException.class,()->MetaxcanRequest.parse(r,false));
        var unsupported=request();unsupported.put("edition","0.7.4");assertThrows(MetaxcanRequest.Unavailable.class,()->MetaxcanRequest.parse(unsupported,false));
    }
    static Map<String,Object> request() {
        var columns=new LinkedHashMap<String,Object>();columns.putAll(Map.of("snp","ID","effect_allele","EA","non_effect_allele","OA","zscore","Z.score"));for(String n:List.of("beta","pvalue","se","odds_ratio","beta_sign"))columns.put(n,null);
        var settings=new LinkedHashMap<String,Object>(Map.of("calibration",Map.of("mode","source-uncalibrated"),"keep_non_rsid",false,"pvalue_policy","source-default-divergent-floor"));settings.put("joint_cutoff",null);settings.put("regularization",null);
        var cap=new LinkedHashMap<String,Object>(Map.of("models",10,"genes",100,"variants",100,"samples",100,"input_bytes",1048576,"decoded_bytes",1048576,"output_bytes",1048576,"numeric_bytes",1048576,"multiply_adds",1000000));cap.put("wall_millis",60000);
        var process=new LinkedHashMap<String,Object>(Map.of("wall_millis",60000,"address_space_bytes",4294967296L,"cpu_seconds",60,"file_bytes",67108864,"open_files",256,"workspace_bytes",201326592,"file_count",1000,"processes",8,"threads",128,"sampled_rss_bytes",1073741824));process.put("monitor_millis",100);
        return new LinkedHashMap<>(Map.of("schema_version",1,"edition","tag-0.8.1","mode","summary","genome_build","G1,annotation","engine","original","data",Map.of("models",List.of(Map.of("name","TissueA","file","/declared/model.db")),"gwas",Map.of("file","/declared/gwas.txt","compression","none","separator","tab","columns",columns),"covariance",Map.of("file","/declared/cov.txt","compression","none")),"settings",settings,"capacity",cap,"process",process));
    }
}
