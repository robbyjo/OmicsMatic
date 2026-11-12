/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.jlinalg.metagenomics.HopsAssessmentAudit;
import org.jlinalg.metagenomics.HopsEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Independent original RData/TSV/JSON oracle; complete original outputs retained. */
final class HopsPostprocessingAuditTest {
    @TempDir Path temporary;
    private record Fixture(Path root,HopsEvidence.Inspection statistics,String export,String tsv) { }
    private Fixture fixture()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("fixture"));
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/metagenomics/hops-postprocessing/original-reference.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)) {
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    Path file=root.resolve(NextflowFiles.member(entry.getName()));Files.createDirectories(file.getParent());
                    byte[] raw=zip.readNBytes(16777217);assertTrue(raw.length<=16777216);Files.write(file,raw,StandardOpenOption.CREATE_NEW);
                }
            }
        }
        var statistics=HopsEvidence.inspect(root.resolve("tables"),List.of("Homo_sapiens"),new HopsEvidence.Limits(16777216,10000,100000,100,100));
        return new Fixture(root,statistics,Files.readString(root.resolve("scientific.json")),Files.readString(root.resolve("heatmap.tsv")));
    }
    private static Map<?,?> document(String text)throws Exception {return NextflowFiles.object(SimpleJson.parse(text));}
    private static Map<String,Map<String,List<Integer>>> json(){return Map.of("population10001.rma6",Map.of("Homo_sapiens",List.of(2)));}
    @Test void originalWorkspaceAndAllThirtySevenOriginalFilesAreRetainedAndConsistent()throws Exception {
        var f=fixture();var files=NextflowFiles.object(NextflowFiles.json(f.root().resolve("ORIGINAL_FILES.json"),16777216));assertEquals(37,files.size());
        for(Object value:files.values()) {
            var row=NextflowFiles.object(value);Path file=f.root().resolve(NextflowFiles.member(NextflowFiles.text(row.get("member"))));
            assertEquals(NextflowFiles.fingerprint(row,16777216),NextflowFiles.pin(file,16777216,()->{}));
        }
        var audit=HopsAssessmentAudit.audit(f.statistics(),document(f.export()),f.tsv(),json());
        assertEquals(14,audit.get("source_statistic_cells"));assertEquals(1,audit.get("pairs"));assertEquals(false,audit.get("biological_result_admitted"));
        assertEquals(List.of("heatmap_overview_Wevid.pdf","pdf_candidate_profiles/Homo_sapiens/stp1_population10001.rma6_Homo_sapiens_summary.pdf"),audit.get("expected_pdfs"));
        assertNotNull(HopsAssessmentAudit.audit(f.statistics(),document(f.export()),f.tsv(),null));
    }
    @Test void aSingleFiniteDoubleBitChangeIsRefused()throws Exception {
        var f=fixture();String changed=f.export().replaceFirst("40c3888000000000","40c3888000000001");assertNotEquals(f.export(),changed);
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(changed),f.tsv(),json()));
    }
    @Test void sourceNaCannotBeRelabeledAsNan()throws Exception {
        var f=fixture();String changed=f.export().replaceFirst("\"NA\"","\"NaN\"");assertNotEquals(f.export(),changed);
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(changed),f.tsv(),json()));
    }
    @Test void originalNodeAndClassMatricesCannotBeReplaced()throws Exception {
        var f=fixture();String node=f.export().replaceFirst("\"node\": \"Homo_sapiens\"","\"node\": \"Mammalia\"");
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(node),f.tsv(),json()));
        String matrix=f.export().replace("\"population10001.rma6\": \"2\"","\"population10001.rma6\": \"4\"");assertNotEquals(f.export(),matrix);
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(matrix),f.tsv(),json()));
    }
    @Test void originalJsonOrientationAndArrayCellsAreRequired()throws Exception {
        var f=fixture();var transposed=Map.of("Homo_sapiens",Map.of("population10001.rma6",List.of(2)));
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(f.export()),f.tsv(),transposed));
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(f.export()),f.tsv(),Map.of("population10001.rma6",Map.of("Homo_sapiens",List.of(2,2)))));
    }
    @Test void tsvCellsQuotingAndLineEndingsRemainOriginal()throws Exception {
        var f=fixture();for(String changed:List.of(f.tsv().replace("\t2","\t4"),f.tsv().replace("\"node\"","node"),f.tsv().replace("\n","\r\n"))) {
            assertNotEquals(f.tsv(),changed);assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(f.export()),changed,json()));
        }
    }
    @Test void differentRVersionOrUnreportedScientificColumnsAreRefused()throws Exception {
        var f=fixture();String version=f.export().replace("\"4.0.5\"","\"4.6.2\"");
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),document(version),f.tsv(),json()));
        var changed=new LinkedHashMap<Object,Object>(document(f.export()));changed.put("new_tool_credit",1);
        assertThrows(IllegalArgumentException.class,()->HopsAssessmentAudit.audit(f.statistics(),changed,f.tsv(),json()));
    }
    @Test void failureHelpIncludesSkipAndRecoveryWithoutNeedingAnUpstreamRuntime()throws Exception {
        var stdout=new java.io.ByteArrayOutputStream();var stderr=new java.io.ByteArrayOutputStream();
        int code=HopsPostprocessingCli.run(new String[]{"--help"},new java.io.PrintStream(stdout,true,StandardCharsets.UTF_8),new java.io.PrintStream(stderr,true,StandardCharsets.UTF_8));
        assertEquals(0,code);assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Required action:"));assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("skip dependent analysis"));assertEquals(0,stderr.size());
    }
}
