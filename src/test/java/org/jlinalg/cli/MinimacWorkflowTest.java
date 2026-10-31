package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinimacWorkflowTest {
    @TempDir Path directory;
    private static final String M3="##fileformat=M3VCF\n##version=1.2\n##compression=block\n##n_blocks=1\n##n_haps=2\n##n_markers=2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tR_h1\tR_h2\n"
            +"20\t10-20\t<BLOCK:0-1>\t.\t.\t.\t.\tB1;VARIANTS=2;REPS=2\t.\t0\t1\n"
            +"20\t10\tv1\tA\tG\t.\t.\tB1.M1;Err=0.01;Recom=0.1\t01\n"
            +"20\t20\tv2\tC\tT\t.\t.\tB1.M2;Err=0.02;Recom=0\t10\n";
    private static final String ID_HEADER="contig\tcolumn_id\tcolumn_copy\tperson_id\tperson_copy\n";
    private static final String OUTPUT="##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tT\n"
            +"20\t10\tv1\tA\tG\t.\tPASS\tAF=0.50000;MAF=0.50000;R2=1.00000\tGT:DS:GP\t0|1:1.000:0.000,1.000,0.000\n"
            +"20\t20\tv2\tC\tT\t.\tPASS\tAF=0.25000;MAF=0.25000;R2=0.00000\tGT:DS:GP\t0|0:0.500:0.562,0.375,0.062\n";
    private Map<String,Object> recipe(String operation)throws IOException{
        for(var file:Map.of("reference.m3",M3,"reference-ids.tsv",ID_HEADER+"20\tR_h1\t1\tREF\t1\n20\tR_h2\t1\tREF\t2\n",
                "target.vcf","##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tT\n20\t10\ttyped\tA\tG\t.\t.\t.\tGT\t0|1\n",
                "target-ids.tsv",ID_HEADER+"20\tT\t1\tTARGET\t1\n20\tT\t2\tTARGET\t2\n").entrySet())
            if(!Files.exists(directory.resolve(file.getKey())))Files.writeString(directory.resolve(file.getKey()),file.getValue());
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1L);m.put("resource_root",".");m.put("operation",operation);m.put("profile",MinimacRecipe.MIN3);m.put("assembly","explicit-fixture-build");
        m.put("reference",Map.of("path","reference.m3","identity_ledger","reference-ids.tsv","kind","m3vcf","staging","lossless-gzip","column_semantics","haplotype-columns"));
        m.put("region",Map.of("contig","20","start",10L,"end",20L,"scope","other","review","Synthetic region only"));
        var review=new LinkedHashMap<String,Object>();for(String field:List.of("organism","reference_panel","reference_sample_eligibility","target_sample_eligibility","assembly_region_ploidy","phase","allele_harmonization","variant_normalization"))review.put(field,"Explicit synthetic fixture review "+field);
        m.put("input_review",review);m.put("boundary_policy","REQUIRE_SHARED_KEEP_LATER");m.put("phase_policy","require-pipe");m.put("id_policy","retain-upstream-id-with-ledger");
        m.put("engine",Map.of("threads",1L,"parameter_policy","upstream-defaults","overrides",Map.of()));
        m.put("limits",Map.of("samples",100L,"variants",100L,"genotype_cells",1000L,"input_bytes",1048576L,"decoded_characters",1048576L,"line_characters",8192L,"identity_rows",1000L,"retained_bytes",10485760L,"workspace_bytes",10485760L));
        if(operation.equals("impute")||operation.equals("check")){
            m.put("target",Map.of("path","target.vcf","identity_ledger","target-ids.tsv","kind","phased-vcf","staging","gt-only-lf-bgzf","column_semantics","person-samples"));
            m.put("target_missing_policy","exact-phased");m.put("target_only_markers","error");m.put("sample_overlap","error");
            m.put("observed_call_changes","retain-with-ledger");m.put("provided_phase_changes","retain-with-ledger");
            if(operation.equals("impute"))m.put("post_imputation_qc",Map.of("kind","none"));
        }
        return m;
    }
    private Path save(Map<String,Object> m)throws IOException{
        Path p=Files.createTempFile(directory,"recipe-",".json");Files.writeString(p,MinimacJson.json(m));return p;
    }
    private int command(Path recipe,Path output,String...extra)throws Exception{
        var args=new java.util.ArrayList<>(List.of("--recipe",recipe.toString(),"--out",output.toString()));args.addAll(List.of(extra));
        try(var stdout=new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8);var stderr=new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8)){
            return MinimacToolsCli.run(args.toArray(String[]::new),stdout,stderr);
        }
    }
    private MinimacInputs.Prepared prepared(Map<String,Object> recipe)throws Exception{
        Path work=Files.createTempDirectory(directory,"work-");
        return MinimacInputs.prepare(MinimacRecipe.read(save(recipe)),work,null,10,new LinkedHashMap<>());
    }
    private MinimacOutputs.Counts audit(MinimacInputs.Prepared p,String text)throws Exception{
        Path output=p.budget().root.resolve("original.vcf");Files.writeString(output,text);
        return MinimacOutputs.audit(output,p,p.budget().root,new java.util.ArrayList<>());
    }
    @Test void nativeInspectionPublishesCompleteReferenceAndProminentScope()throws Exception{
        Path output=directory.resolve("result");assertEquals(0,command(save(recipe("inspect-reference")),output));
        assertTrue(Files.readString(output.resolve("analysis-status.json")).contains("completed-inspect-reference"));
        assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("REFERENCE INSPECTION ONLY"));
        assertEquals(3,Files.readAllLines(output.resolve("reference-selected.tsv")).size());assertFalse(Files.exists(output.resolve("genotypes.vcf")));
    }
    @Test void absentOriginalRuntimeSkipsWithRecoveryAndNoAdmittedResults()throws Exception{
        Path output=directory.resolve("skip");assertEquals(3,command(save(recipe("impute")),output));
        assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("ANALYSIS SKIPPED"));
        assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("Required action"));
        assertFalse(Files.exists(output.resolve("results.json")));assertFalse(Files.exists(output.resolve("genotypes.vcf")));
    }
    @Test void forbiddenOrMissingHomeIsAReportedSkip()throws Exception{
        Path output=directory.resolve("skip");assertEquals(3,command(save(recipe("impute")),output,"--home",directory.resolve("missing-home").toString()));
        assertTrue(Files.readString(output.resolve("session.json")).contains("error"));
    }
    @Test void malformedIdentityQuarantinesAllProvisionalNativeTables()throws Exception{
        var m=recipe("inspect-reference");Files.writeString(directory.resolve("reference-ids.tsv"),ID_HEADER+"20\tR_h1\t1\tREF\t1\n");
        Path output=directory.resolve("failed");assertEquals(2,command(save(m),output));
        assertTrue(Files.exists(output.resolve("partial/reference-selected.tsv")));assertFalse(Files.exists(output.resolve("reference-selected.tsv")));
        assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("ANALYSIS FAILED"));
    }
    @Test void existingOutputIsNeverReplaced()throws Exception{
        Path recipe=save(recipe("inspect-reference")),output=directory.resolve("existing");Files.createDirectory(output);Files.writeString(output.resolve("sentinel"),"keep");
        assertEquals(2,command(recipe,output));assertEquals("keep",Files.readString(output.resolve("sentinel")));
    }
    @Test void immutableOriginalAndStageHashesAreRechecked()throws Exception{
        var p=prepared(recipe("impute"));p.verify();Files.writeString(directory.resolve("target.vcf"),"changed");assertThrows(IOException.class,p::verify);
    }
    @Test void explicitLfGzipStagingPreservesFieldsFromCrlfOriginal()throws Exception{
        var m=recipe("inspect-reference");Files.writeString(directory.resolve("reference.m3"),M3.replace("\n","\r\n"));
        assertThrows(IOException.class,()->prepared(m));
        m.put("reference",Map.of("path","reference.m3","identity_ledger","reference-ids.tsv","kind","m3vcf","staging","lf-gzip","column_semantics","haplotype-columns"));
        var p=prepared(m);assertEquals(2,p.reference().markers().size());assertTrue(Files.readString(directory.resolve("reference.m3")).contains("\r\n"));p.verify();
    }
    @Test void referenceAndTargetLogicalIdentityOverlapRequiresAnExplicitPolicy()throws Exception{
        var m=recipe("impute");Files.writeString(directory.resolve("target-ids.tsv"),ID_HEADER+"20\tT\t1\tREF\t1\n20\tT\t2\tREF\t2\n");
        assertThrows(IOException.class,()->prepared(m));m.put("sample_overlap","allow-with-warning");assertTrue(prepared(m).warnings().stream().anyMatch(s->s.contains("IDENTITY OVERLAP")));
    }
    @Test void targetOnlyAlleleKeysAreRejectedOrExplicitlyLedgered()throws Exception{
        var m=recipe("impute");Files.writeString(directory.resolve("target.vcf"),Files.readString(directory.resolve("target.vcf"))+"20\t20\textra\tC\tG\t.\t.\t.\tGT\t0|1\n");
        assertThrows(IOException.class,()->prepared(m));m.put("target_only_markers","exclude-with-ledger");
        var p=prepared(m);assertEquals(1,p.target().excluded());assertTrue(Files.exists(p.budget().root.resolve("target.vcf.gz.csi")));
    }
    @Test void completeOriginalFamilyPreservesRawNumericStringsAndObservedCalls()throws Exception{
        var p=prepared(recipe("impute"));var result=audit(p,OUTPUT);assertEquals(2,result.variants());assertEquals(1,result.observedGenotypes());assertEquals(1,result.missingOrPartialGenotypes());
        assertEquals(0,result.knownCopyConflicts());assertEquals(OUTPUT,Files.readString(p.budget().root.resolve("genotypes.vcf")));
    }
    @Test void omittedDuplicateOrAlteredOriginalAlleleKeysNeverFormCompleteResults()throws Exception{
        for(String bad:List.of(OUTPUT.substring(0,OUTPUT.lastIndexOf("20\t20")),OUTPUT+OUTPUT.substring(OUTPUT.indexOf("20\t20")),OUTPUT.replace("v2\tC\tT","v2\tC\tG"))){
            assertThrows(IOException.class,()->audit(prepared(recipe("impute")),bad));
        }
    }
    @Test void badPosteriorOrChangedOutputSampleIsRejected()throws Exception{
        for(String bad:List.of(OUTPUT.replace("FORMAT\tT","FORMAT\tOTHER"),OUTPUT.replace("1.000:0.000,1.000,0.000","0.100:0.000,1.000,0.000")))
            assertThrows(IOException.class,()->audit(prepared(recipe("impute")),bad));
    }
    @Test void suppliedCallAndOrderedPhasePoliciesIndependentlyRejectOriginalChanges()throws Exception{
        var genotype=recipe("impute");genotype.put("observed_call_changes","error");
        assertThrows(IOException.class,()->audit(prepared(genotype),OUTPUT.replace("0|1:1.000:0.000,1.000,0.000","0|0:0.000:1.000,0.000,0.000").replace("AF=0.50000;MAF=0.50000;R2=1.00000","AF=0.00000;MAF=0.00000;R2=0.00000")));
        var phase=recipe("impute");phase.put("provided_phase_changes","error");
        assertThrows(IOException.class,()->audit(prepared(phase),OUTPUT.replace("0|1:1.000","1|0:1.000")));
        var permissive=audit(prepared(recipe("impute")),OUTPUT.replace("0|1:1.000","1|0:1.000"));
        assertEquals(1,permissive.knownCopyConflicts());assertEquals(1,permissive.sameGenotypePhaseSwaps());assertEquals(0,permissive.unorderedObservedChanges());
    }
    @Test void explicitSourceQcCanDropRowsOnlyAfterCompleteFamilyAudit()throws Exception{
        var m=recipe("impute");m.put("post_imputation_qc",Map.of("kind","source-INFO-R2","operator","gt","minimum","0.8","missing","error"));
        var p=prepared(m);var result=audit(p,OUTPUT);assertEquals(1,result.retainedVariants());assertEquals(1,result.excludedVariants());
        assertEquals(3,Files.readAllLines(p.budget().root.resolve("genotypes.vcf")).size());assertEquals(3,Files.readAllLines(p.budget().root.resolve("variant-results.tsv")).size());
    }
    @Test void missingOrSlightlyAboveOneSourceR2RetainsItsExplicitSemantics()throws Exception{
        var result=audit(prepared(recipe("impute")),OUTPUT.replace("R2=1.00000","R2=1.00001"));assertEquals(1,result.sourceR2AboveOne());
        var missing=audit(prepared(recipe("impute")),OUTPUT.replace("R2=1.00000","R2=nan"));assertEquals(1,missing.missingSourceR2());
        var strict=recipe("impute");strict.put("post_imputation_qc",Map.of("kind","source-INFO-R2","operator","ge","minimum","0.3","missing","error"));
        assertThrows(IOException.class,()->audit(prepared(strict),OUTPUT.replace("R2=1.00000","R2=nan")));
    }
    @Test void unhonoredCompressionCapabilityIsASkipWithConcreteRecovery()throws Exception{
        var m=recipe("inspect-reference");m.put("operation","prepare-reference");m.put("profile",MinimacRecipe.CURRENT);
        m.put("reference",Map.of("path","target.vcf","identity_ledger","target-ids.tsv","kind","phased-vcf","staging","gt-only-lf-bgzf","column_semantics","person-samples"));
        m.put("engine",Map.of("threads",1L,"parameter_policy","overrides-with-pinned-defaults","overrides",Map.of("maximum_block_markers",16L)));
        Path output=directory.resolve("skip");assertEquals(3,command(save(m),output));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("120 variants"));
    }
    @Test void memoryAndOutputLineBoundsHaveIndependentAdmissionFailures()throws Exception{
        var m=recipe("inspect-reference");var limits=new LinkedHashMap<String,Object>();((Map<?,?>)m.get("limits")).forEach((k,v)->limits.put(k.toString(),v));limits.put("retained_bytes",1024L);m.put("limits",limits);
        assertThrows(IOException.class,()->prepared(m));
        var n=recipe("impute");var more=new LinkedHashMap<String,Object>();((Map<?,?>)n.get("limits")).forEach((k,v)->more.put(k.toString(),v));more.put("output_line_characters",64L);n.put("limits",more);
        assertThrows(IOException.class,()->audit(prepared(n),OUTPUT));
    }
    @Test void utf8OptionsFileCarriesUnicodePathsAndRejectsUnknownOrRecursiveOptions()throws Exception{
        Path recipe=save(recipe("inspect-reference")),options=directory.resolve("options.json"),output=directory.resolve("資料 results");
        Files.writeString(options,MinimacJson.json(Map.of("recipe",recipe.toString(),"out",output.toString())));
        assertEquals(0,MinimacToolsCli.run(new String[]{"--options-file",options.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
        Files.writeString(options,"{\"options-file\":\"recursive\"}");assertEquals(2,MinimacToolsCli.run(new String[]{"--options-file",options.toString()},System.out,System.err));
    }
    @Test void originalFailurePreviewPromotesTheCauseAndKeepsBinaryDataOutOfWarnings()throws Exception{
        Path log=directory.resolve("original.stdout");Files.writeString(log,"status\nERROR !!!\nGWAS contains chr20:fixture\nVCF File can only contain chromosomes 1-22, X, Y\n");
        String preview=MinimacOperations.diagnostic(log);assertTrue(preview.contains("chr20:fixture"));assertTrue(preview.contains("only contain chromosomes"));
        Files.write(log,new byte[]{0,1,2,3});assertTrue(MinimacOperations.diagnostic(log).contains("binary/nontext"));
    }
    @Test void originalDiagnosticPreviewIsBoundedEvenForVeryLongLogs()throws Exception{
        Path log=directory.resolve("original.stderr");Files.writeString(log,("WARNING "+"x".repeat(4096)+"\n").repeat(100));
        String preview=MinimacOperations.diagnostic(log);assertTrue(preview.length()<8300);assertTrue(preview.contains("WARNING"));
    }
}

