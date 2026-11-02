/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class FastpOutputAuditTest {
    @TempDir static Path temporary;
    static Path controls;
    @BeforeAll static void stage()throws IOException {controls=temporary.resolve("controls.zip");try(var in=FastpOutputAuditTest.class.getResourceAsStream("fastp-output/original-output-controls.zip")){assertNotNull(in);Files.copy(in,controls);}}
    static FastpOutputAudit.Limits limits(){return new FastpOutputAudit.Limits(new FastpMetrics.Limits(10000,2097152),new FastpSort.Limits(8_388_608,100000,100000,128),new FastpJoin.Limits(4096,8_388_608,100_000_000,10_000_000),4_194_304);}
    static Path extract(ZipFile z,String name)throws Exception {
        Path root=Files.createTempDirectory(temporary,"case-").toRealPath();String prefix=name+"/result/";
        for(var entry:z.stream().filter(e->e.getName().startsWith(prefix)).toList()){
            String member=FastpFiles.member(entry.getName().substring(prefix.length()));Path p=root.resolve(member);Files.createDirectories(p.getParent());try(var in=z.getInputStream(entry)){Files.copy(in,p);}
        }return root;
    }
    static FastpOutputAudit.Data data(Path root)throws Exception {
        Map<?,?> request=FastpRuntime.object(FastpFiles.json(root.resolve("request.json"),1048576));List<?> captures=FastpMetrics.array(FastpFiles.json(root.resolve("input-captures.json"),1048576));Path first=root.resolve(FastpRuntime.text(FastpRuntime.object(captures.get(0)).get("snapshot"))),second=captures.size()==2?root.resolve(FastpRuntime.text(FastpRuntime.object(captures.get(1)).get("snapshot"))):null;
        return new FastpOutputAudit.Data(root,FastpRuntime.text(request.get("profile")).substring("linux-x86_64-fastp-".length()),first,second,FastpFastq.Encoding.valueOf(FastpRuntime.text(request.get("encoding"))),FastpFastq.Alphabet.valueOf(FastpRuntime.text(request.get("alphabet"))),FastpFastq.Plus.valueOf(FastpRuntime.text(request.get("plus_policy"))),FastpFastq.Pairing.valueOf(FastpRuntime.text(request.get("pairing_policy"))),FastpChoices.options(request.get("options")),FastpChoices.fastq(FastpRuntime.object(request.get("limits")).get("fastq")));
    }
    @TestFactory Stream<DynamicTest> all57OriginalControlsHaveCompleteReadAndReportAudit()throws Exception {
        List<?> rows;try(var z=new ZipFile(controls.toFile())){try(var in=z.getInputStream(z.getEntry("CASES.json"))){rows=FastpMetrics.array(FastpRuntime.object(SimpleJson.parse(FastpFiles.utf8(in.readAllBytes()))).get("cases"));}}
        assertEquals(57,rows.size());return rows.stream().map(value->{try{String name=FastpRuntime.text(FastpRuntime.object(value).get("path"));return DynamicTest.dynamicTest(name,()->{
            try(var z=new ZipFile(controls.toFile())){
                Path root=extract(z,name),audit=root.resolve("audit");var result=FastpOutputAudit.verify(data(root),audit,limits(),()->{},()->{});assertEquals("PASSED_COMPLETE_POPULATION_AND_SOURCE_REPORT_AUDIT",result.status());assertEquals(result.conservation().inputReads(),result.conservation().representedReads()+result.conservation().unrepresentedReads());assertTrue(result.independentlyRecomputedReportFields()>2000);assertFalse(Files.exists(root.resolve("COMPLETED.json")));
                if(name.endsWith("pe-store-orphans"))assertTrue(Files.readString(audit.resolve("read-dispositions.tsv")).contains("UNPAIRED"));
                if(name.endsWith("se-dedup")||name.endsWith("pe-dedup")||name.equals("p054-reported-options"))assertTrue(result.conservation().unrepresentedReads()>0);
                if(name.endsWith("pe-overlap-correction")||name.equals("p054-reported-options"))assertTrue(result.conservation().visibleCorrectedBases()>0);
            }
        });}catch(IOException invalid){throw new IllegalArgumentException(invalid);}});
    }
    @Test void missingWholeOutputFileCannotBecomeACompletedPrefix()throws Exception {
        try(var z=new ZipFile(controls.toFile())){Path root=extract(z,"0.23.2/pe-store-orphans");Files.delete(root.resolve("original/unpaired1.fastq"));assertThrows(IOException.class,()->FastpOutputAudit.verify(data(root),root.resolve("audit"),limits(),()->{},()->{}));}
    }
    @Test void alteredSourceProfileAndUnknownTypedOptionAreRejected()throws Exception {
        try(var z=new ZipFile(controls.toFile())){Path root=extract(z,"0.23.2/se-default");Path request=root.resolve("request.json");String text=Files.readString(request).replace("linux-x86_64-fastp-0.23.2","linux-x86_64-fastp-0.23");Files.writeString(request,text);assertThrows(IOException.class,()->FastpOutputAudit.verify(data(root),root.resolve("audit"),limits(),()->{},()->{}));}
        assertThrows(IOException.class,()->FastpChoices.options(Map.of("arbitrary-command","ignored")));
    }
    @Test void failedRawStageCannotBePromotedByOtherwiseValidOutputs()throws Exception {
        try(var z=new ZipFile(controls.toFile())){Path root=extract(z,"0.23.2/se-default");Files.writeString(root.resolve("FAILURE.json"),"{}\n");assertThrows(IOException.class,()->FastpOutputAudit.verify(data(root),root.resolve("audit"),limits(),()->{},()->{}));}
    }
    @Test void outputMatePermutationFailsDespiteUnchangedReadMultisetsAndStatistics()throws Exception {
        try(var z=new ZipFile(controls.toFile())){
            Path root=extract(z,"0.23.2/pe-overlap-correction"),mate=root.resolve("original/read2.fastq");var lines=new java.util.ArrayList<>(Files.readAllLines(mate));assertTrue(lines.size()>=8);for(int i=0;i<4;i++){String value=lines.get(i);lines.set(i,lines.get(i+4));lines.set(i+4,value);}Files.writeString(mate,String.join("\n",lines)+"\n");
            assertThrows(IOException.class,()->FastpOutputAudit.verify(data(root),root.resolve("audit"),limits(),()->{},()->{}));
        }
    }
    @Test void sameCensusReportTamperingCannotPassRecomputedCycleOrKmerAudit()throws Exception {
        for(String field:List.of("cycle","kmer"))try(var z=new ZipFile(controls.toFile())){
            Path root=extract(z,"0.23.2/se-default"),report=root.resolve("original/fastp.json");var all=mutable(FastpFiles.json(report,4194304));var detail=mutable(all.get("read1_before_filtering"));all.put("read1_before_filtering",detail);
            if(field.equals("cycle")){var curves=mutable(detail.get("quality_curves"));detail.put("quality_curves",curves);var mean=new java.util.ArrayList<Object>((List<?>)curves.get("mean"));mean.set(0,((Number)mean.get(0)).doubleValue()+1);curves.put("mean",mean);}
            else {var kmers=mutable(detail.get("kmer_count"));detail.put("kmer_count",kmers);String key=kmers.keySet().iterator().next();kmers.put(key,((Number)kmers.get(key)).longValue()+1);}
            Files.writeString(report,FastpJson.json(all)+"\n");assertThrows(IOException.class,()->FastpOutputAudit.verify(data(root),root.resolve("audit"),limits(),()->{},()->{}));
        }
    }
    private static java.util.Map<String,Object> mutable(Object value)throws IOException {var result=new java.util.LinkedHashMap<String,Object>();for(var e:FastpRuntime.object(value).entrySet())result.put(FastpRuntime.text(e.getKey()),e.getValue());return result;}
}
