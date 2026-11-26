/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic consistency/tamper cases; original numerical controls are retained separately. */
class DiannOutputAuditTest {
    @TempDir Path tmp;
    static final String FILE="/raw/sample.d",RUN="sample",EXEC="/original/capture/outputs";
    static final Set<String> TEXT=Set.of("File.Name","Run","Protein.Group","Protein.Ids","Protein.Names","Genes","Modified.Sequence","Stripped.Sequence","Precursor.Id","First.Protein.Description");
    record Fixture(Path root,Path out,Path fasta,String edition,Map<?,?> profile,LinkedHashMap<String,String> row,boolean unassigned) { }
    Map<?,?> profile(String edition)throws Exception {
        try(var input=DiannOutputAudit.class.getResourceAsStream("/org/jlinalg/cli/diann-tools/report-profiles.json")){return object(object(object(SimpleJson.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8))).get("editions")).get(edition));}
    }
    Fixture fixture(String edition,boolean missingGroupGene,boolean unassigned)throws Exception {
        Path root=Files.createDirectory(tmp.resolve(UUID.randomUUID().toString())),out=Files.createDirectory(root.resolve("outputs"));
        Path fasta=Files.writeString(root.resolve("reference.fasta"),">sp|P1|ENTRY1_HUMAN Test alpha OS=Homo sapiens GN=GENE1\nMPEPTIDER\n>sp|P2|ENTRY2_HUMAN Test beta OS=Homo sapiens\nMPEPTIDER\n");
        var profile=profile(edition);var row=new LinkedHashMap<String,String>();
        for(Object raw:list(profile.get("columns"))){var col=object(raw);String name=text(col.get("name"));row.put(name,TEXT.contains(name)||"VARCHAR".equals(col.get("storage_type"))?"":"0");}
        row.put("File.Name",FILE);row.put("Run",RUN);row.put("Protein.Group",missingGroupGene?"P2":"P1");row.put("Protein.Ids",missingGroupGene?"P1;P2":"P1");row.put("Protein.Names",missingGroupGene?"ENTRY2_HUMAN":"ENTRY1_HUMAN");row.put("Genes",missingGroupGene?"":"GENE1");
        row.put("Modified.Sequence","PEPTIDER");row.put("Stripped.Sequence","PEPTIDER");row.put("Precursor.Id","PEPTIDER2");row.put("Precursor.Charge","2");row.put("Proteotypic","1");row.put("RT","2");row.put("RT.Start","1");row.put("RT.Stop","3");
        for(String name:List.of("PG.Quantity","PG.Normalised","PG.MaxLFQ","Genes.Quantity","Genes.Normalised","Genes.MaxLFQ","Genes.MaxLFQ.Unique"))if(row.containsKey(name))row.put(name,"100");
        for(String name:List.of("Precursor.Quantity","Precursor.Normalised","Precursor.Translated"))if(row.containsKey(name))row.put(name,"25");
        for(String name:List.of("Q.Value","Global.Q.Value","PG.Q.Value","Global.PG.Q.Value","GG.Q.Value","Protein.Q.Value","PEP","PG.PEP"))if(row.containsKey(name))row.put(name,"0.001");
        for(String name:List.of("Fragment.Quant.Raw","Fragment.Quant.Corrected"))if(row.containsKey(name))row.put(name,"1;2;");
        if(row.containsKey("Fragment.Correlations"))row.put("Fragment.Correlations","0.5;0.9;");
        if(row.containsKey("Translated.Quality"))row.put("Translated.Quality","");
        if(row.containsKey("First.Protein.Description"))row.put("First.Protein.Description",missingGroupGene?"Test beta":"Test alpha");
        if(missingGroupGene)for(String name:List.of("Genes.Quantity","Genes.Normalised","Genes.MaxLFQ","Genes.MaxLFQ.Unique"))if(row.containsKey(name))row.put(name,edition.equals("2.0.2")?"0":"");
        if(unassigned){row.put("Protein.Group","");row.put("Protein.Names","");row.put("Genes","");row.put("Q.Value","0.04");row.put("Global.Q.Value","1");row.put("PG.Q.Value","1");row.put("Global.PG.Q.Value","1");for(String name:List.of("PG.MaxLFQ","Genes.MaxLFQ","Genes.MaxLFQ.Unique"))row.put(name,"0");}
        var result=new Fixture(root,out,fasta,edition,profile,row,unassigned);writeMain(result);writeOther(result);return result;
    }
    static String sql(String text){return"'"+text.replace("'","''")+"'";}
    void writeMain(Fixture f)throws Exception {
        var columns=list(f.profile.get("columns"));
        if(!f.edition.equals("2.0.2")){
            var header=new ArrayList<String>();var values=new ArrayList<String>();for(Object raw:columns){String name=text(object(raw).get("name"));header.add(name);values.add(f.row.get(name));}
            Files.writeString(f.out.resolve("report.tsv"),String.join("\t",header)+"\n"+String.join("\t",values)+"\n");
        }else {
            Path path=f.out.resolve("report.parquet");Files.deleteIfExists(path);var expressions=new ArrayList<String>();
            for(Object raw:columns){var col=object(raw);String name=text(col.get("name")),type=text(col.get("storage_type"));expressions.add("CAST("+(type.equals("VARCHAR")?sql(f.row.get(name)):f.row.get(name))+" AS "+type+") AS \""+name+"\"");}
            try(var c=DriverManager.getConnection("jdbc:duckdb:");var statement=c.createStatement()){statement.execute("SET autoinstall_known_extensions=false");statement.execute("SET autoload_known_extensions=false");statement.execute("COPY (SELECT "+String.join(",",expressions)+") TO "+sql(path.toString().replace('\\','/'))+" (FORMAT PARQUET)");}
        }
    }
    void writeOther(Fixture f)throws Exception {
        for(String kind:List.of("pr","pg","gg","unique_genes")){
            var metadata=list(object(f.profile.get("matrix_metadata_columns")).get(kind));var names=new ArrayList<String>();var values=new ArrayList<String>();
            for(Object raw:metadata){String name=text(raw);names.add(name);values.add(name.equals("First.Protein.Description")?(f.row.get("Protein.Group").equals("P2")?"Test beta":"Test alpha"):f.row.get(name));}
            names.add(FILE);values.add(kind.equals("pr")?"25":"100");
            boolean empty=f.unassigned||(kind.equals("gg")||kind.equals("unique_genes"))&&f.row.get("Genes").isEmpty();
            Files.writeString(f.out.resolve("report."+kind+"_matrix.tsv"),String.join("\t",names)+"\n"+(empty?"":String.join("\t",values)+"\n"));
        }
        var names=list(f.profile.get("statistics_columns")).stream().map(Object::toString).toList();var values=new ArrayList<String>();for(String name:names)values.add(name.equals("File.Name")?FILE:name.equals("Precursors.Identified")?(f.unassigned?"0":"1"):"1");
        Files.writeString(f.out.resolve("report.stats.tsv"),String.join("\t",names)+"\n"+String.join("\t",values)+"\n");
        if(f.edition.equals("2.0.2")){
            Files.writeString(f.out.resolve("report.protein_description.tsv"),"Protein.Id\tProtein.Name\tGene\tDescription\tSequence\nP1\tENTRY1_HUMAN\tGENE1\tTest alpha\tMPEPTIDER\nP2\tENTRY2_HUMAN\t\tTest beta\tMPEPTIDER\n");
            var manifest=new StringBuilder();for(var e:object(f.profile.get("manifest_labels_to_filenames")).entrySet())manifest.append(NextflowJson.json(Map.of(e.getKey().toString(),EXEC+"/"+e.getValue()))).append('\n');Files.writeString(f.out.resolve("report.manifest.txt"),manifest);
        }
    }
    Map<String,Object> audit(Fixture f)throws Exception{return audit(f,100000,List.of(new DiannOutputAudit.Run(FILE,RUN)));}
    Map<String,Object> audit(Fixture f,long joins,List<DiannOutputAudit.Run> runs)throws Exception {
        return DiannOutputAudit.audit(f.out,f.fasta,f.edition,runs,EXEC,f.unassigned?.05:.01,
            new DiannOutputAudit.Capacity(new DiannReportRows.Limits(1000,1048576,256,10000,128,67108864),new DiannReference.Limits(100,10000,1048576,10000),joins),tmp.resolve("scratch-"+UUID.randomUUID()),()->{});
    }
    @Test void originalLegacyAndParquetSchemasBothReconcileWithoutRefitting()throws Exception {
        for(String edition:List.of("1.8","1.8.1","2.0.2")){var result=audit(fixture(edition,false,false));assertEquals(1L,result.get("main_report_rows"));assertEquals(false,result.get("original_model_refitted"));assertEquals(0L,result.get("original_unassigned_group_rows"));}
    }
    @Test void missingGeneInInferredGroupIsNotFilledFromOtherPrecursorMatches()throws Exception {
        var f=fixture("1.8.1",true,false);assertEquals(1L,audit(f).get("main_report_rows"));f.row.put("Genes","GENE1");writeMain(f);assertThrows(IOException.class,()->audit(f));
    }
    @Test void unassignedOriginalParquetPrecursorRowsRemainVisibleWithZeroGroupQuantities()throws Exception {
        var f=fixture("2.0.2",false,true);var result=audit(f);assertEquals(1L,result.get("main_report_rows"));assertEquals(1L,result.get("original_unassigned_group_rows"));
        f.row.put("PG.MaxLFQ","1");writeMain(f);assertThrows(IOException.class,()->audit(f));
    }
    @Test void invalidProbabilityNonfiniteQuantityAndPeakBoundsRejectCompleteAudit()throws Exception {
        for(var change:List.of(Map.entry("Q.Value","0.02"),Map.entry("Precursor.Quantity","NaN"),Map.entry("PG.MaxLFQ","-1"),Map.entry("RT.Start","4"))){var f=fixture("1.8.1",false,false);f.row.put(change.getKey(),change.getValue());writeMain(f);assertThrows(IOException.class,()->audit(f));}
    }
    @Test void sourceIdsPeptideAndMatrixMetadataCannotBeSilentlyRelabelled()throws Exception {
        for(var change:List.of(Map.entry("Protein.Ids","UNRESOLVED"),Map.entry("Stripped.Sequence","PEPTIDEA"),Map.entry("Protein.Names","ENTRY2_HUMAN"))){var f=fixture("1.8.1",false,false);f.row.put(change.getKey(),change.getValue());writeMain(f);assertThrows(IOException.class,()->audit(f));}
        var f=fixture("1.8.1",false,false);Path matrix=f.out.resolve("report.pg_matrix.tsv");Files.writeString(matrix,Files.readString(matrix).replace("ENTRY1_HUMAN","ENTRY2_HUMAN"));assertThrows(IOException.class,()->audit(f));
    }
    @Test void changedMatrixQuantityAndMissingCohortMemberRejectCompleteAudit()throws Exception {
        var f=fixture("1.8.1",false,false);Path matrix=f.out.resolve("report.pg_matrix.tsv");Files.writeString(matrix,Files.readString(matrix).replace("\t100\n","\t101\n"));assertThrows(IOException.class,()->audit(f));
        var cohort=fixture("1.8.1",false,false);assertThrows(IOException.class,()->audit(cohort,100000,List.of(new DiannOutputAudit.Run(FILE,RUN),new DiannOutputAudit.Run("/raw/absent.d","absent"))));
    }
    @Test void statisticsAndCapacityFailuresCannotBecomeSuccessfulPartialResults()throws Exception {
        var f=fixture("1.8.1",false,false);assertThrows(IOException.class,()->audit(f,1,List.of(new DiannOutputAudit.Run(FILE,RUN))));
        Path stats=f.out.resolve("report.stats.tsv");var lines=Files.readAllLines(stats);var cells=new ArrayList<>(Arrays.asList(lines.get(1).split("\t",-1)));cells.set(1,"2");Files.writeString(stats,lines.get(0)+"\n"+String.join("\t",cells)+"\n");assertThrows(IOException.class,()->audit(f));
    }
    @Test void mbrManifestRequiresDeclaredModeAndPreservedOriginalLibraryFiles()throws Exception {
        var f=fixture("2.0.2",false,false);Path manifest=f.out.resolve("report.manifest.txt");String base=Files.readString(manifest);
        Files.writeString(manifest,base+NextflowJson.json(Map.of("Library in .speclib format for Skyline",EXEC+"/empirical-library.parquet.skyline.speclib"))+"\n");
        assertThrows(IOException.class,()->audit(f));
        // This component checks the generated-library links and complete byte pins. It does not decode/re-fit a library.
        Files.copy(f.out.resolve("report.parquet"),f.out.resolve("empirical-library.parquet"));
        Path skyline=Files.write(f.out.resolve("empirical-library.parquet.skyline.speclib"),new byte[]{1,2,3});
        var c=new DiannOutputAudit.Capacity(new DiannReportRows.Limits(1000,1048576,256,10000,128,67108864),new DiannReference.Limits(100,10000,1048576,10000),100000);
        var result=DiannOutputAudit.audit(f.out,f.fasta,f.edition,List.of(new DiannOutputAudit.Run(FILE,RUN)),EXEC,.01,true,c,tmp.resolve("mbr-scratch"),()->{});
        assertTrue(object(result.get("original_table_pins")).containsKey("empirical-library.parquet.skyline.speclib"));
        Files.writeString(manifest,base);assertThrows(IOException.class,()->DiannOutputAudit.audit(f.out,f.fasta,f.edition,List.of(new DiannOutputAudit.Run(FILE,RUN)),EXEC,.01,true,c,tmp.resolve("mbr-missing-link"),()->{}));
        Files.delete(skyline);assertThrows(IOException.class,()->DiannOutputAudit.audit(f.out,f.fasta,f.edition,List.of(new DiannOutputAudit.Run(FILE,RUN)),EXEC,.01,true,c,tmp.resolve("mbr-missing-library"),()->{}));
    }
    @Test void proteinMatrixCanNameItsInferredGroupWithoutChangingPeptideMatches()throws Exception {
        var f=fixture("1.8.1",true,false);Path matrix=f.out.resolve("report.pg_matrix.tsv");var lines=Files.readAllLines(matrix);var header=Arrays.asList(lines.get(0).split("\t",-1));int index=header.indexOf("Protein.Ids");assertTrue(index>=0);
        var cells=new ArrayList<>(Arrays.asList(lines.get(1).split("\t",-1)));cells.set(index,"P2");Files.writeString(matrix,lines.get(0)+"\n"+String.join("\t",cells)+"\n");
        var result=audit(f);var dispositions=object(object(result.get("matrices")).get("pg"));assertEquals(1L,dispositions.get("original_inferred_group_id_annotation_rows"));
        assertEquals("P1;P2",f.row.get("Protein.Ids"));assertTrue(Files.readString(f.out.resolve("report.tsv")).contains("P1;P2"));
        cells.set(index,"P1");Files.writeString(matrix,lines.get(0)+"\n"+String.join("\t",cells)+"\n");assertThrows(IOException.class,()->audit(f));
    }
}
