/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MetalWorkflowCliTest {
    @TempDir Path temp;
    Map<String,String> config()throws Exception {
        var c=new TreeMap<String,String>();c.put("executable","missing-runtime");c.put("executable_sha256","0".repeat(64));c.put("notice","NOTICE.txt");c.put("release","2020-05-05");c.put("execution","allow");c.put("studies","STUDIES.tsv");c.put("scheme","STDERR");c.put("allele_mode","paired");
        for(String key:List.of("strand","frequency","overlap"))c.put(key,"OFF");c.put("heterogeneity","ON");c.put("z_cutoff","-");c.put("output_probability","ordinary");c.put("post_genomic_control","OFF");c.put("warning_policy","forbid");c.put("hypothesis","Synthetic same trait and tested allele; independently declared cohorts");c.put("max_studies","10");c.put("max_rows","1000");c.put("max_metadata_bytes","10000000");c.put("max_bytes","10000000");c.put("max_files","200");c.put("max_line_bytes","65536");c.put("wall_ms","1000");
        Files.writeString(temp.resolve("STUDIES.tsv"),"study\tfile\tgenomic_control\nfirst\tfirst.tsv\tOFF\nsecond\tsecond.tsv\tOFF\n");
        study("first.tsv","M1\tA\tC\t0.2\t-\t100\t0.1\t-\t+\nM2\tC\tG\t-0.1\t-\t100\t0.2\t-\t+\n");study("second.tsv","M1\tC\tA\t-0.3\t-\t200\t0.2\t-\t+\nM3\tG\tT\t0.1\t-\t200\t0.2\t-\t+\n");return c;
    }
    void study(String name,String rows)throws Exception {Files.writeString(temp.resolve(name),MetalWorkflowInputs.HEADER+"\n"+rows,StandardCharsets.UTF_8);}
    MetalWorkflowInputs.Population inspect(Map<String,String> c)throws Exception{return MetalWorkflowInputs.inspect(temp.resolve("REQUEST.tsv"),MetalWorkflowRequest.parse(c));}
    @Test void completeUnionAndExplicitBetweenStudyMissingness()throws Exception {
        var c=config();var p=inspect(c);assertEquals(4,p.rows());assertEquals(Set.of("M1","M2","M3"),p.markers().keySet());assertEquals(2,p.markers().get("M1").studies.cardinality());assertEquals(1,p.markers().get("M2").studies.cardinality());assertEquals(300,p.markers().get("M1").totalN);assertEquals(125,p.markers().get("M1").inverseVariance,1e-12);
    }
    @Test void strictRequestsDoNotIgnoreUnknownMissingDuplicateOrInvalidUtf8()throws Exception {
        var c=config();c.put("free_form_commands","DROP INPUT");assertThrows(IOException.class,()->MetalWorkflowRequest.parse(c));c.remove("free_form_commands");c.remove("scheme");assertThrows(IOException.class,()->MetalWorkflowRequest.parse(c));
        Path raw=temp.resolve("duplicate.tsv");Files.writeString(raw,"key\tvalue\nscheme\tSTDERR\nscheme\tSAMPLESIZE\n");assertThrows(IOException.class,()->MetalWorkflowRequest.read(raw));
        Files.write(raw,new byte[]{(byte)0xc3,(byte)0x28});assertThrows(IOException.class,()->MetalWorkflowRequest.read(raw));
    }
    @Test void repeatedStudyOrMarkerNeverCreatesImplicitDuplicateWeight()throws Exception {
        var c=config();Files.writeString(temp.resolve("STUDIES.tsv"),"study\tfile\tgenomic_control\nfirst\tfirst.tsv\tOFF\nsecond\tfirst.tsv\tOFF\n");assertThrows(IOException.class,()->inspect(c));
        Files.writeString(temp.resolve("STUDIES.tsv"),"study\tfile\tgenomic_control\nfirst\tfirst.tsv\tOFF\n");study("first.tsv","M1\tA\tC\t0.2\t-\t100\t0.1\t-\t+\nM1\tA\tC\t0.2\t-\t100\t0.1\t-\t+\n");assertThrows(IOException.class,()->inspect(c));
    }
    @Test void nonfiniteNonpositiveAndConflictingUnusedFieldsRefuseWholePopulation()throws Exception {
        var c=config();for(String row:List.of("M1\tA\tC\tNaN\t-\t100\t0.1\t-\t+\n","M1\tA\tC\t0.1\t-\t0\t0.1\t-\t+\n","M1\tA\tC\t0.1\t-\t100\t0\t-\t+\n","M1\tA\tC\t0.1\t-\t100\t1e-200\t-\t+\n","M1\tA\tC\t0.1\t.05\t100\t.1\t-\t+\n")){study("first.tsv",row);assertThrows(IOException.class,()->inspect(c));}
    }
    @Test void sampleSizeModeRefusesZeroPAndAmbiguousZeroDirections()throws Exception {
        var c=config();c.put("scheme","SAMPLESIZE");study("second.tsv","M1\tA\tC\t+\t.1\t200\t-\t-\t+\n");
        for(String value:List.of("+\t0","0\t.05","+\tNaN")){study("first.tsv","M1\tA\tC\t"+value+"\t100\t-\t-\t+\n");assertThrows(IOException.class,()->inspect(c));}
        study("first.tsv","M1\tA\tC\t0\t1\t100\t-\t-\t+\n");assertEquals(2,inspect(c).rows());
    }
    @Test void geneUnitsDoNotAcquireInventedAllelesOrSnpFrequencies()throws Exception {
        var c=config();c.put("scheme","SAMPLESIZE");c.put("allele_mode","none");study("first.tsv","ENSG1\t-\t-\t+\t.05\t100\t-\t-\t+\n");study("second.tsv","ENSG2\t-\t-\t-\t.1\t200\t-\t-\t+\n");assertEquals(2,inspect(c).markers().size());c.put("frequency","ON");assertThrows(IOException.class,()->MetalWorkflowRequest.parse(c));
    }
    @Test void incompatibleAllelesAndUnspecifiedStrandRefuseBeforeOriginalExecution()throws Exception {
        var c=config();study("second.tsv","M1\tA\tG\t.3\t-\t200\t.2\t-\t+\n");assertThrows(IOException.class,()->inspect(c));study("second.tsv","M1\tA\tC\t.3\t-\t200\t.2\t-\t-\n");assertThrows(IOException.class,()->inspect(c));c.put("strand","ON");assertEquals(3,inspect(c).rows());
    }
    @Test void capacityRefusesWholeRowsStudiesAndMetadataWithoutTruncation()throws Exception {
        var c=config();c.put("max_rows","3");assertThrows(IOException.class,()->inspect(c));c.put("max_rows","1000000000000");assertEquals(4,inspect(c).rows());c.put("max_rows","1000000000001");assertThrows(IOException.class,()->inspect(c));c.put("max_rows","1000");c.put("max_studies","1");assertThrows(IOException.class,()->inspect(c));c.put("max_studies","10");c.put("max_metadata_bytes","8192");assertThrows(IOException.class,()->inspect(c));
    }
    @Test void schemaLineLimitAndMalformedUtf8CannotBeLenientlyConsumed()throws Exception {
        var c=config();study("first.tsv","M1 A C .1 - 100 .1 - +\n");assertThrows(IOException.class,()->inspect(c));Files.write(temp.resolve("first.tsv"),new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,'x'});assertThrows(IOException.class,()->inspect(c));c.put("max_line_bytes","64");study("first.tsv","X".repeat(100)+"\tA\tC\t.1\t-\t100\t.1\t-\t+\n");assertThrows(IOException.class,()->inspect(c));
    }
    @Test void typedScriptsUseCapturedNamesAndPreservePostGcLineage()throws Exception {
        var c=config();c.put("post_genomic_control","1.2");c.put("frequency","ON");study("first.tsv","M1\tA\tC\t.2\t-\t100\t.1\t.2\t+\n");study("second.tsv","M1\tC\tA\t-.3\t-\t200\t.2\t.6\t+\n");var p=inspect(c);var request=MetalWorkflowRequest.parse(c);String primary=MetalWorkflowRuntime.script(request,p,false),post=MetalWorkflowRuntime.script(request,p,true);
        assertTrue(primary.contains("PROCESSFILE ../../inputs/0000.tsv\n"));assertTrue(primary.contains("LOGPVALUE OFF\n"));assertTrue(primary.contains("ANALYZE HETEROGENEITY\n"));assertTrue(post.contains("GENOMICCONTROL 1.2\nPROCESSFILE ../primary/result1.tbl\n"));assertFalse(post.contains("ANALYZE HETEROGENEITY"));assertTrue(post.contains("FREQLABEL Freq1\n"));
        c.put("overlap","ON");assertThrows(IOException.class,()->MetalWorkflowRequest.parse(c));
    }
    static final String OUTPUT_HEADER="MarkerName\tAllele1\tAllele2\tEffect\tStdErr\tP-value\tDirection\tHetISq\tHetChiSq\tHetDf\tHetPVal\tDeclaredN\n";
    static final String OUTPUT_ROWS="M1\ta\tc\t0.220000000000\t0.089442719100\t0.01391\t++\t0.0\t0.200\t1\t0.6547\t300\nM2\tc\tg\t-0.100000000000\t0.200000000000\t0.6171\t-?\t0.0\t0.000\t0\t1\t100\nM3\tg\tt\t0.100000000000\t0.200000000000\t0.6171\t?+\t0.0\t0.000\t0\t1\t200\n";
    Path output(String rows)throws Exception {Path stage=temp.resolve("stages/primary");Files.createDirectories(stage);Path path=stage.resolve("result1.tbl");Files.writeString(path,OUTPUT_HEADER+rows);return path;}
    @Test void admissionKeepsExplicitMissingnessAndOriginalSingleCohortConvention()throws Exception {
        var c=config();var p=inspect(c);output(OUTPUT_ROWS);var summary=new ArrayList<String>();MetalWorkflowAudit.table(temp,MetalWorkflowRequest.parse(c),p,false,summary::add);assertEquals(3,summary.size());assertTrue(summary.get(1).contains("\t0.000\t0\tordinary\t1\t0.0\t"));
    }
    @Test void sourceExitZeroCannotAdmitDroppedOrInventedContributorsOrNonfiniteResults()throws Exception {
        var c=config();var p=inspect(c);var request=MetalWorkflowRequest.parse(c);
        for(String rows:List.of(OUTPUT_ROWS.replace("\t++\t","\t+?\t"),OUTPUT_ROWS.substring(0,OUTPUT_ROWS.indexOf("M3")),OUTPUT_ROWS.replace("0.220000000000","nan"),OUTPUT_ROWS.replace("0.089442719100","0"),OUTPUT_ROWS.replace("0.01391","0"),OUTPUT_ROWS+OUTPUT_ROWS.substring(0,OUTPUT_ROWS.indexOf('\n')+1))){output(rows);assertThrows(IOException.class,()->MetalWorkflowAudit.table(temp,request,p,false,line->{}));}
    }
    @Test void largeDeclaredNTotalsUseSixSignificantDigitsAndRefuseAnExtraPrintedUnit()throws Exception {
        var c=config();study("first.tsv","M1\tA\tC\t.2\t-\t1234567.89\t.1\t-\t+\nM2\tC\tG\t-.1\t-\t100\t.2\t-\t+\n");study("second.tsv","M1\tC\tA\t-.3\t-\t456789.12\t.2\t-\t+\nM3\tG\tT\t.1\t-\t200\t.2\t-\t+\n");var p=inspect(c);var request=MetalWorkflowRequest.parse(c);
        output(OUTPUT_ROWS.replace("\t300\n","\t1.69136e+06\n"));MetalWorkflowAudit.table(temp,request,p,false,line->{});
        output(OUTPUT_ROWS.replace("\t300\n","\t1.69137e+06\n"));assertThrows(IOException.class,()->MetalWorkflowAudit.table(temp,request,p,false,line->{}));
    }
    @Test void allPackagedMethodClassesIncludingNestedVisitorsHaveFingerprints()throws Exception {
        Path location=Path.of(MetalWorkflowCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());var actual=new TreeSet<String>();
        if(Files.isDirectory(location))try(var paths=Files.list(location.resolve("org/jlinalg/cli"))){paths.filter(p->p.getFileName().toString().startsWith("MetalWorkflow")&&p.toString().endsWith(".class")).forEach(p->actual.add("org.jlinalg.cli."+p.getFileName().toString().replace(".class","")));}
        else try(var zip=new ZipFile(location.toFile())){zip.stream().map(e->e.getName()).filter(n->n.startsWith("org/jlinalg/cli/MetalWorkflow")&&n.endsWith(".class")).forEach(n->actual.add(n.substring(0,n.length()-6).replace('/','.')));}
        assertEquals(actual,MetalWorkflowCli.implementation().keySet());assertTrue(actual.contains("org.jlinalg.cli.MetalWorkflowFiles$InventoryVisitor"));
    }
    @Test void captureKeepsExactStudyListAndRefusesAStaleParsedRequest()throws Exception {
        var c=config();Path executable=temp.resolve("supplied-runtime"),notice=temp.resolve("NOTICE.txt"),request=temp.resolve("REQUEST.tsv");Files.writeString(executable,"Synthetic capture fixture; never executed");Files.writeString(notice,"Synthetic capture notice");c.put("executable",executable.toString());c.put("executable_sha256",MetalWorkflowFiles.pin(executable).sha256());c.put("notice",notice.toString());MetalWorkflowFiles.map(request,c);var parsed=MetalWorkflowRequest.parse(c);byte[] originalList=Files.readAllBytes(temp.resolve("STUDIES.tsv"));Path captured=temp.resolve("captured");Files.createDirectory(captured);var normalized=MetalWorkflowInputs.capture(request,parsed,captured);assertArrayEquals(originalList,Files.readAllBytes(captured.resolve("STUDIES-ORIGINAL.tsv")));MetalWorkflowInputs.originalDeclarations(captured,normalized,MetalWorkflowInputs.inspect(captured.resolve("REQUEST.tsv"),normalized));
        Files.writeString(request,Files.readString(request).replace("Synthetic same trait","Changed requested trait"));Path stale=temp.resolve("stale");Files.createDirectory(stale);assertThrows(IOException.class,()->MetalWorkflowInputs.capture(request,parsed,stale));assertFalse(Files.exists(stale.resolve("stages/primary/PROCESS-STARTED.tsv")));
    }
    @Test void forbiddenExecutionSkipsWithProminentActionAndNoCompletion()throws Exception {
        var c=config();c.put("execution","forbid");Path request=temp.resolve("REQUEST.tsv"),out=temp.resolve("new-output");MetalWorkflowFiles.map(request,c);var console=new ByteArrayOutputStream();int code=MetalWorkflowCli.run(new String[]{"run","--request",request.toString(),"--out",out.toString()},new PrintStream(console),new PrintStream(console));assertEquals(3,code);assertTrue(console.toString(StandardCharsets.UTF_8).contains("SKIP3_DEPENDENT_ANALYSIS_SKIPPED"));assertTrue(Files.exists(out.resolve("FAILURE.txt")));assertFalse(Files.exists(out.resolve("COMPLETED.tsv")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("No software is downloaded"));
    }
    @Test void reusedOutputRemainsUntouchedAndUnsupportedEditionIsNotSubstituted()throws Exception {
        var c=config();Path request=temp.resolve("REQUEST.tsv"),out=temp.resolve("old-output");MetalWorkflowFiles.map(request,c);Files.createDirectory(out);Files.writeString(out.resolve("preserved"),"retain exact evidence");var console=new ByteArrayOutputStream();assertEquals(2,MetalWorkflowCli.run(new String[]{"run","--request",request.toString(),"--out",out.toString()},new PrintStream(console),new PrintStream(console)));assertEquals("retain exact evidence",Files.readString(out.resolve("preserved")));try(var files=Files.list(out)){assertEquals(1,files.count());}c.put("release","2011-03-25");assertThrows(MetalWorkflowFiles.Prerequisite.class,()->MetalWorkflowRuntime.prerequisite(MetalWorkflowRequest.parse(c)));
    }
}
