/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySuppliedVariantRowsTest {
    @TempDir Path root;
    private Context context(String kind,String data) throws Exception {
        Files.writeString(root.resolve("results.tsv"),data);
        var definition=Map.<String,Object>of("id","external","kind",kind,"files",Map.of("results","results.tsv"),"scope",Map.of("modality","gwas","feature_id_type","variant"),"method",Map.of("name","Externally completed synthetic functional context"));
        return new Context(root,Files.createDirectory(root.resolve("import")),Map.of("id","import","type",kind,"hypothesis","H","supplied_result_id","external","datasets",List.of()),Map.of("datasets",List.of(),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("supplied_results",List.of(definition))))),Map.of(),Map.of());
    }
    private static final String HEADER="variant_id\tfeature_id\tdataset_id\tkind\tgene_id\ttranscript_id\tscorer\tontology_curie\ttrack_id\traw_score\tquantile_score\tmodel_version\tresource_version\tinput_source\tsource_operation_id\n";
    private static final String ROW="GRCh38:1:100:A:G\tv1\t\talphagenome\tENSG1\tENST1\tRNA_SEQ\tUBERON:0002107\ttrack1\t-.4\t-.6\tmodel1\trelease1\tofficial_meta_analysis\tmeta\n";
    @Test void multipleTranscriptTissueTrackAndSourceRecordsRemainDistinct() throws Exception {
        var c=context("variant_scoring",HEADER+ROW+ROW.replace("ENST1","ENST2").replace("0002107","0000178").replace("track1","track2"));var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());
        var metadata=StudyYaml.loadArtifact(c.outputDirectory().resolve("supplied-analysis.yaml"));var table=DelimitedData.readResult(c.outputDirectory().resolve(text(metadata.get("canonical_results"))));assertEquals(2,table.rows().size());
        assertTrue(table.header().containsAll(List.of("kind","track_id","input_source","source_operation_id","ontology_curie","transcript_id")));assertEquals("meta",table.rows().get(0)[table.column("source_operation_id")]);assertEquals(false,result.details().get("analysis_executed_by_jlinalg"));
    }
    @Test void identicalContextWithConflictingScoreIsRejectedInsteadOfSelectingOne() throws Exception {
        var c=context("variant_scoring",HEADER+ROW+ROW.replace("-.6",".9"));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));
    }
    @Test void impossibleSignedQuantileIsRejected() throws Exception {
        var c=context("variant_scoring",HEADER+ROW.replace("-.6","1.1"));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));
    }
    @Test void consequenceImportsCanRetainMultipleGenesAndTranscriptsPerVariant() throws Exception {
        var c=context("variant_annotation","variant_id\tkind\tgene_id\ttranscript_id\tconsequence\nGRCh38:1:100:A:G\tconsequence\tENSG1\tENST1\tmissense_variant\nGRCh38:1:100:A:G\tconsequence\tENSG2\tENST2\tintron_variant\n");var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());var metadata=StudyYaml.loadArtifact(c.outputDirectory().resolve("supplied-analysis.yaml"));assertEquals(2,DelimitedData.readResult(c.outputDirectory().resolve(text(metadata.get("canonical_results")))).rows().size());
    }
    @Test void longSourceRecordsRemainIntactWhileTheirIdentityStorageIsHashed() throws Exception {
        String first="source-a-"+"x".repeat(6000),second="source-b-"+"x".repeat(6000);
        var c=context("variant_scoring",HEADER.stripTrailing()+"\tsource_record\n"+ROW.stripTrailing()+"\t"+first+"\n"+ROW.stripTrailing()+"\t"+second+"\n");
        var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());var metadata=StudyYaml.loadArtifact(c.outputDirectory().resolve("supplied-analysis.yaml"));var table=DelimitedData.readResult(c.outputDirectory().resolve(text(metadata.get("canonical_results"))));assertEquals(2,table.rows().size());assertEquals(first,table.rows().get(0)[table.column("source_record")]);assertEquals(second,table.rows().get(1)[table.column("source_record")]);
    }
    @Test void missingSourceRecordMarkersCannotCreateFalseDistinctContexts() throws Exception {
        var c=context("variant_scoring",HEADER.stripTrailing()+"\tsource_record\n"+ROW.stripTrailing()+"\t\n"+ROW.stripTrailing()+"\tNA\n");
        assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));
    }
}
