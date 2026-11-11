/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IqtreeReportTest {
    record Case(String id,String model,int code,Map<String,byte[]> files) { }
    private static final List<Case> CASES=load();
    @TempDir Path temporary;
    private static String text(byte[] raw){return new String(raw,StandardCharsets.UTF_8);}
    private static List<Case> load() {
        try {
            var result=new ArrayList<Case>();
            for(String[] archive:List.of(new String[]{"/org/jlinalg/phylogeny/iqtree-inference/original-controls.zip","dbdb94013aaf99899ef1d1e980b367e86c951f01612618a2f0683f00b969a4d3","old"},
                    new String[]{"/org/jlinalg/cli/iqtree-report/original-controls.zip","4106a8c2dddf2e49d4c002fd49613fade5e32698ccd63c5adc9381ad5b4f1cd0","reports"},
                    new String[]{"/org/jlinalg/cli/iqtree-report/duplicate-controls.zip","ff5e0f1791cf44357732d62f74e147916d471f297be89a377a728f036d8dc6ad","duplicates"})) {
                byte[] raw;try(var input=IqtreeReportTest.class.getResourceAsStream(archive[0])){assertNotNull(input);raw=input.readNBytes(8388609);assertTrue(raw.length<=8388608);}
                assertEquals(archive[1],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
                var files=new HashMap<String,byte[]>();
                try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))) {
                    for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                        String name=entry.getName();
                        if(name.endsWith("/result.iqtree")||name.endsWith("/result.model.gz")||name.endsWith("/result.log")||name.endsWith("/stderr")||name.equals("capture/original/observed.json")||name.equals("protocols.json")) {
                            if(name.startsWith("typed-standard/")||name.contains("DNA-standard-1000/"))continue;
                            byte[] value=zip.readNBytes(16777217);assertTrue(value.length<=16777216);files.put(name,value);
                        }
                    }
                }
                List<?> protocols=(List<?>)SimpleJson.parse(text(files.get(archive[2].equals("old")?"capture/original/observed.json":"protocols.json")));
                for(Object item:protocols) {
                    Map<?,?> protocol=(Map<?,?>)item;int code=((Number)protocol.get("exit_code")).intValue();if(archive[2].equals("old")&&code!=0)continue;
                    String name=(String)protocol.get("name");String prefix=archive[2].equals("old")?"original/"+name+"/":"cases/"+protocol.get("folder")+"/"+name+"/";
                    var selected=new HashMap<String,byte[]>();for(String suffix:List.of("iqtree","model.gz","log"))if(files.containsKey(prefix+"result."+suffix))selected.put(suffix,files.get(prefix+"result."+suffix));
                    if(files.containsKey(prefix+"stderr"))selected.put("stderr",files.get(prefix+"stderr"));
                    result.add(new Case(archive[2]+":"+name,(String)protocol.get("model"),code,Map.copyOf(selected)));
                }
            }
            assertEquals(118,result.size());return List.copyOf(result);
        } catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static Stream<String> cases(){return CASES.stream().map(Case::id);}
    private Case fixture(String ending){return CASES.stream().filter(c->c.id().endsWith(ending)).findFirst().orElseThrow();}
    private Map<String,Path> write(Case fixture)throws IOException {
        var paths=new HashMap<String,Path>();for(var e:fixture.files().entrySet()){Path p=temporary.toRealPath().resolve("source."+e.getKey());Files.write(p,e.getValue());paths.put(e.getKey(),p);}return paths;
    }
    private IqtreeReport.Result read(Map<String,Path> files)throws IOException {return IqtreeReport.read(files.get("iqtree"),files.get("model.gz"),files.get("log"),IqtreeReport.Limits.defaults(),()->{});}
    @ParameterizedTest(name="Complete original model report {0}") @MethodSource("cases")
    void allOriginalReportsPreserveCompleteCandidatesParametersAndBoundaryScope(String id)throws Exception {
        Case fixture=CASES.stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();
        if(fixture.code()!=0){assertEquals(2,fixture.code());assertFalse(fixture.files().containsKey("iqtree"));assertTrue(text(fixture.files().get("stderr")).contains("Invalid use of +ASC"));return;}
        var result=read(write(fixture));assertEquals(4,result.alignmentStatistics().size());
        if(fixture.model().equals("MFP")||fixture.model().equals("TEST")) {
            assertNotNull(result.selection());assertEquals(result.selection().actuallyFittedModels(),result.selection().candidates().size());assertTrue(result.selection().announcedModels()>=result.selection().actuallyFittedModels());
        } else assertNull(result.selection());
        if(id.contains("many-identical")){assertEquals(36,result.taxa());assertEquals(12,result.ignoredIdenticalTaxa().size());}
    }
    @Test void originalSmallSampleAiccIsRetainedButClassicalValidityIsExplicit()throws Exception {
        var result=read(write(fixture("10-1.6.12-DNA-mini-JC")));
        assertEquals("80.5150",result.scores().aicc().text());assertFalse(result.scores().classicalAiccDefined());assertTrue(result.warnings().stream().anyMatch(x->x.startsWith("CLASSICAL AICc UNDEFINED")));
    }
    @Test void aMissingOrExtraCandidateCannotBeHiddenByValidOtherRows()throws Exception {
        Case fixture=fixture("1.6.12-DNA-ModelFinder");var paths=write(fixture);String report=text(fixture.files().get("iqtree"));
        String row=report.lines().filter(s->s.startsWith("K2P ")).findFirst().orElseThrow();Files.writeString(paths.get("iqtree"),report.replace(row+"\n",""));assertThrows(IOException.class,()->read(paths));
        var repeated=write(fixture);Files.writeString(repeated.get("iqtree"),report.replace(row,row+"\n"+row));assertThrows(IOException.class,()->read(repeated));
    }
    @Test void modelWeightsAndStateFrequenciesAreCheckedAgainstCompleteSourceValues()throws Exception {
        var fixture=fixture("1.6.12-DNA-ModelFinder");var paths=write(fixture);String report=text(fixture.files().get("iqtree"));
        String row=report.lines().filter(s->s.startsWith("JC ")).findFirst().orElseThrow();String[] columns=row.trim().split("\\s+");columns[4]="0.9999";
        Files.writeString(paths.get("iqtree"),report.replace(row,String.join(" ",columns)));assertThrows(IOException.class,()->read(paths));
        var frequency=fixture("1.6.12-DNA-GTR-R10");var source=write(frequency);Files.writeString(source.get("iqtree"),text(frequency.files().get("iqtree")).replaceFirst("pi\\(A\\) = [0-9.]+","pi(A) = 0.9000"));assertThrows(IOException.class,()->read(source));
    }
    @Test void modelCheckpointCrcTrailingBytesAndConcatenatedMembersAreRefused()throws Exception {
        var fixture=fixture("1.6.12-DNA-ModelFinder");byte[] original=fixture.files().get("model.gz");
        for(int mode=0;mode<3;mode++) {
            var paths=write(fixture);byte[] raw=mode==0?original.clone():Arrays.copyOf(original,mode==1?original.length+1:original.length*2);
            if(mode==0)raw[raw.length-8]^=1;if(mode==2)System.arraycopy(original,0,raw,original.length,original.length);
            Files.write(paths.get("model.gz"),raw);assertThrows(IOException.class,()->read(paths));
        }
    }
    @Test void cacheCandidateRemovalCannotBeSilentlyTreatedAsMetadata()throws Exception {
        var fixture=fixture("1.6.12-DNA-ModelFinder");var paths=write(fixture);String decoded;
        try(var input=new GZIPInputStream(new ByteArrayInputStream(fixture.files().get("model.gz")))){decoded=text(input.readAllBytes());}
        decoded=decoded.replaceFirst("(?m)^K2P:.*\\R","");var bytes=new ByteArrayOutputStream();try(var zip=new GZIPOutputStream(bytes)){zip.write(decoded.getBytes(StandardCharsets.UTF_8));}
        Files.write(paths.get("model.gz"),bytes.toByteArray());assertThrows(IOException.class,()->read(paths));
    }
    @Test void reportCountsScoresDuplicateFieldsAndCapacitiesFailBeforeAdmission()throws Exception {
        var fixture=fixture("1.6.12-DNA-JC");String original=text(fixture.files().get("iqtree"));
        for(String bad:List.of(original.replace("Number of free parameters (#branches + #model parameters): 21","Number of free parameters (#branches + #model parameters): 22"),
                original.replace("Number of constant sites: 22","Number of constant sites: 99999"),original+"\nModel of substitution: JC\n")) {
            var paths=write(fixture);Files.writeString(paths.get("iqtree"),bad);assertThrows(IOException.class,()->read(paths));
        }
        var paths=write(fixture);assertThrows(IOException.class,()->IqtreeReport.read(paths.get("iqtree"),null,paths.get("log"),new IqtreeReport.Limits(10,1000),()->{}));
    }
}
