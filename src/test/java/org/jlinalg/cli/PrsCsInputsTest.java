/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.PrsCsMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrsCsInputsTest {
    @TempDir Path root;
    private static final PrsCsInputs.Limits LIMITS=new PrsCsInputs.Limits(100,1000,1048576,1048576,4096,10000,100,100,1048576,10000);
    private Path fixture()throws Exception {
        return fixture(root);
    }
    static Path fixture(Path parent)throws Exception {
        Path data=parent.resolve("inputs");Files.createDirectory(data);
        for(String name:List.of("target.bim","beta-se.txt","or-se.txt","beta-p.txt","or-p.txt","ldblk_1kg_eur/snpinfo_1kg_hm3","ldblk_1kg_eur/ldblk_1kg_chr1.hdf5","ldblk_1kg_eur/ldblk_1kg_chr22.hdf5")) {
            Path target=data.resolve(name);Files.createDirectories(target.getParent());
            try(var input=PrsCsInputsTest.class.getResourceAsStream("/org/jlinalg/genetics/prscs-reference/"+name)){assertNotNull(input);Files.copy(input,target);}
        }
        return data;
    }
    private static PrsCsInputs.Request request(Path data,String format,String unmatched,String pal,String strand,String positions,PrsCsMath.PValuePolicy floor) {
        return new PrsCsInputs.Request(data.resolve(format+".txt"),data.resolve("target.bim"),data.resolve("ldblk_1kg_eur"),"1kg",List.of(1,22),format,20000,unmatched,pal,strand,positions,floor);
    }
    private PrsCsInputs.Prepared prepare(Path data,String format)throws Exception {
        Path out=Files.createTempDirectory(root,"stage-");return PrsCsInputs.prepare(request(data,format,"exclude-with-warning","allow-upstream-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ORIGINAL_FLOOR_WITH_WARNING),LIMITS,out);
    }
    private static void replace(Path file,String old,String value)throws IOException {Files.writeString(file,Files.readString(file).replace(old,value));}

    @Test void fourDeclaredFormatsRetainTheOriginalTwentySnpFamilyAndReferenceOrientation()throws Exception {
        Path data=fixture();
        for(String format:List.of("beta-se","or-se","beta-p","or-p")) {
            var result=prepare(data,format);assertEquals(20,result.byId().size());assertEquals(11,result.model().get(1).size());assertEquals(9,result.model().get(22).size());
            assertEquals(25L,result.counts().get("target_rows"));assertEquals(26L,result.counts().get("summary_rows"));assertEquals(5L,result.counts().get("unmatched_target_exclusions"));assertEquals(8L,result.counts().get("palindromic_admitted"));
            assertFalse(result.byId().containsKey("TARGET_ONLY"));assertFalse(result.byId().containsKey("SUMMARY_ONLY"));assertFalse(result.byId().containsKey("rs1_7"));
            var v=result.byId().get("rs1_1");assertEquals('G',v.effectAllele());assertEquals('A',v.otherAllele());assertEquals(.915,v.frequency(),1e-15);assertEquals(-1,v.referenceSign());assertEquals(1,v.summarySign());
            if(format.endsWith("-se"))assertEquals(-.007/.01/Math.sqrt(20000),v.marginal(),3e-15);
            assertFalse(result.warnings().isEmpty());assertEquals(5,result.sourceHashes().size());assertEquals(7,result.stagedHashes().size());
        }
    }

    @Test void gzipInputsUnicodePathsAndReorderedBimSummaryPreserveNativeModelIdentity()throws Exception {
        Path data=fixture();var expected=prepare(data,"beta-se");
        for(String name:List.of("target.bim","beta-se.txt")) {
            Path file=data.resolve(name);var lines=new ArrayList<>(Files.readAllLines(file));String header=name.endsWith("txt")?lines.remove(0):null;Collections.reverse(lines);if(header!=null)lines.add(0,header);
            try(var gzip=new GZIPOutputStream(Files.newOutputStream(file))){gzip.write((String.join("\n",lines)+"\n").getBytes(StandardCharsets.UTF_8));}
        }
        Path moved=root.resolve("métadonnées Ω");Files.move(data,moved);var actual=prepare(moved,"beta-se");
        assertEquals(expected.byId().keySet(),actual.byId().keySet());
        for(String id:expected.byId().keySet()){var e=expected.byId().get(id);var a=actual.byId().get(id);assertEquals(e.marginal(),a.marginal());assertEquals(e.frequency(),a.frequency());assertEquals(e.effectAllele(),a.effectAllele());}
    }

    @Test void explicitPalindromeExclusionIsDistinctFromUnmatchedExclusion()throws Exception {
        Path data=fixture(),out=root.resolve("stage");Files.createDirectory(out);
        var result=PrsCsInputs.prepare(request(data,"beta-se","exclude-with-warning","exclude-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ERROR),LIMITS,out);
        assertEquals(12,result.byId().size());assertEquals(8L,result.counts().get("explicit_palindromic_exclusions"));assertEquals(5L,result.counts().get("unmatched_target_exclusions"));
        assertEquals(25,Files.readAllLines(out.resolve("variant-ledger.tsv")).size()-1);
    }

    @Test void unresolvedStrandAndUnmatchedRowsRequireExplicitDecisions()throws Exception {
        Path data=fixture();
        for(var r:List.of(request(data,"beta-se","error","allow-upstream-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ERROR),request(data,"beta-se","exclude-with-warning","error","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ERROR))) {
            Path out=Files.createTempDirectory(root,"rejected-");assertThrows(IOException.class,()->PrsCsInputs.prepare(r,LIMITS,out));
        }
        Path out=Files.createTempDirectory(root,"exact-");var exact=PrsCsInputs.prepare(request(data,"beta-se","exclude-with-warning","allow-upstream-with-warning","exact","require-match",PrsCsMath.PValuePolicy.ERROR),LIMITS,out);
        assertTrue(exact.byId().size()<20);assertTrue(exact.byId().values().stream().noneMatch(PrsCsInputs.Variant::requiredComplement));
        assertTrue(exact.byId().values().stream().anyMatch(PrsCsInputs.Variant::palindrome));
    }

    @Test void coordinateMismatchFailsOrRetainsReferenceCoordinatesWithAnActionableWarning()throws Exception {
        Path data=fixture();replace(data.resolve("target.bim"),"1 rs1_1 0 11000 ","1 rs1_1 0 11001 ");
        assertThrows(IOException.class,()->prepare(data,"beta-se"));Path out=Files.createTempDirectory(root,"admitted-");
        var result=PrsCsInputs.prepare(request(data,"beta-se","exclude-with-warning","allow-upstream-with-warning","allow-complement-with-warning","reference-with-warning",PrsCsMath.PValuePolicy.ERROR),LIMITS,out);
        assertEquals(11000,result.byId().get("rs1_1").position());assertEquals(1L,result.counts().get("position_mismatches_admitted"));assertTrue(result.warnings().stream().anyMatch(s->s.contains("no liftover")));
    }

    @Test void selectedDuplicateSummaryOrBimIdsFailBeforeAnAmbiguousModelCanRun()throws Exception {
        Path data=fixture();Path sst=data.resolve("beta-se.txt");Files.writeString(sst,Files.readString(sst)+Files.readAllLines(sst).get(1)+"\n");
        assertThrows(IOException.class,()->prepare(data,"beta-se"));
        Files.writeString(sst,Files.readString(sst).substring(0,Files.readString(sst).lastIndexOf("rs1_1")));
        Path bim=data.resolve("target.bim");Files.writeString(bim,Files.readString(bim)+Files.readAllLines(bim).get(0)+"\n");assertThrows(IOException.class,()->prepare(data,"beta-se"));
    }

    @Test void invalidRetainedValuesAndSilentPvalueFlooringAreRejected()throws Exception {
        Path data=fixture(),out=Files.createTempDirectory(root,"p-error-");
        assertThrows(IOException.class,()->PrsCsInputs.prepare(request(data,"beta-p","exclude-with-warning","allow-upstream-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ERROR),LIMITS,out));
        Path sst=data.resolve("beta-se.txt");String original=Files.readString(sst),line=Files.readAllLines(sst).get(1);String[] row=line.split(" ");
        for(String bad:List.of("NaN","0","-0.1","Infinity","0x1p-2")) {
            row[4]=bad;Files.writeString(sst,original.replace(line,String.join(" ",row)));assertThrows(IOException.class,()->prepare(data,"beta-se"));
        }
    }

    @Test void independentRowStorageInputAndReportCeilingsFailBeforeUpstreamExecution()throws Exception {
        Path data=fixture();
        for(var limits:List.of(new PrsCsInputs.Limits(2,1000,1048576,1048576,4096,10000,100,100,1048576,10000),new PrsCsInputs.Limits(100,2,1048576,1048576,4096,10000,100,100,1048576,10000),new PrsCsInputs.Limits(100,1000,100,1048576,4096,10000,100,100,1048576,10000),new PrsCsInputs.Limits(100,1000,1048576,65536,4096,10000,100,100,1048576,10000),new PrsCsInputs.Limits(100,1000,1048576,1048576,4096,10000,100,100,50,10000))) {
            Path out=Files.createTempDirectory(root,"limited-");assertThrows(IOException.class,()->PrsCsInputs.prepare(request(data,"beta-se","exclude-with-warning","allow-upstream-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ERROR),limits,out));
        }
    }

    @Test void originalAndStagedHashesDetectPostPreparationChanges()throws Exception {
        Path data=fixture(),out=Files.createTempDirectory(root,"stage-");var result=PrsCsInputs.prepare(request(data,"beta-se","exclude-with-warning","allow-upstream-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ERROR),LIMITS,out);
        PrsCsInputs.verify(result,out);Path staged=out.resolve("summary.txt");String original=Files.readString(staged);Files.writeString(staged,original+"\n");assertThrows(IOException.class,()->PrsCsInputs.verify(result,out));Files.writeString(staged,original);
        Files.writeString(data.resolve("target.bim"),"changed\n");assertThrows(IOException.class,()->PrsCsInputs.verify(result,out));
    }
}
