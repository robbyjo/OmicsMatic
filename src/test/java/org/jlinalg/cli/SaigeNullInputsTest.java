package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaigeNullInputsTest {
    @TempDir Path temp;
    private Map<String,Object> recipe() {
        var parameters=new LinkedHashMap<String,Object>();parameters.put("min_maf",.05);parameters.put("max_missing",.05);parameters.put("seed",1);parameters.put("diagonal","observed");parameters.put("relatedness_cutoff",.125);parameters.put("fit_tolerance",.02);parameters.put("max_iterations",20);parameters.put("pcg_tolerance",1e-5);parameters.put("max_pcg_iterations",500);parameters.put("trace_cv",.0025);parameters.put("trace_runs",30);parameters.put("spa_cutoff",2);parameters.put("tau_initial",List.of(0,0));parameters.put("variance_ratio",Map.of("kind","single","minimum_mac",20,"num_markers",30,"coefficient_variation",.001));
        return new LinkedHashMap<>(Map.of("version",1,"operation","fit-null","genotypes","cohort","genotype_coding","diploid-autosomal-plink-bed","assembly","synthetic fixture","phenotypes",Map.of("file","pheno.tsv","delimiter","tab","id","sample","response","case","control","control","case","case","covariates",List.of(Map.of("column","x+unsafe()","kind","numeric"),Map.of("column","category","kind","categorical","levels",List.of("baseline","other"))),"missing",List.of("NA"),"missing_policy","exclude-with-ledger"),"model",Map.of("trait_type","binary","inverse_normalize",false,"loco",false,"relationship","full","sparse_variance_ratio",false,"covariate_offset",true),"parameters",parameters,"resources",Map.of("threads",1,"memory_chunk_gib",.1)));
    }
    private Path write(Map<String,Object> root)throws Exception {Path path=temp.resolve("recipe.json");Files.writeString(path,RegenieRuntime.json(root));return path;}
    private void fixture()throws Exception {
        Files.write(temp.resolve("cohort.bed"),new byte[]{0x6c,0x1b,1,(byte)184,(byte)236,(byte)203,(byte)178});Files.writeString(temp.resolve("cohort.bim"),"1 v1 0 1 A G\n2 v2 0 2 C T\n");var fam=new StringBuilder();var pheno=new StringBuilder("sample\tcase\tx+unsafe()\tcategory\n");int[] y={0,1,0,1,1,0,1,0};
        for(int i=0;i<8;i++){String id="s"+i;fam.append("f ").append(id).append(" 0 0 0 -9\n");pheno.append(id).append('\t').append(y[i]==1?"case":"control").append('\t').append(i%4).append('\t').append(i%4<2?"baseline":"other").append('\n');}Files.writeString(temp.resolve("cohort.fam"),fam);Files.writeString(temp.resolve("pheno.tsv"),pheno);
    }
    private SaigeNullInputs.Prepared prepare(Map<String,Object> root)throws Exception {return SaigeNullInputs.prepare(SaigeNullInputs.read(write(root)),Files.createTempDirectory(temp,"work-"));}
    private static Map<String,Object> copyMap(Object value){var result=new LinkedHashMap<String,Object>();((Map<?,?>)value).forEach((k,v)->result.put((String)k,v));return result;}
    private Map<String,Object> quantitative(Map<String,Object> root) {
        var model=copyMap(root.get("model"));model.put("trait_type","quantitative");model.put("inverse_normalize",true);root.put("model",model);var ph=copyMap(root.get("phenotypes"));ph.remove("control");ph.remove("case");root.put("phenotypes",ph);return root;
    }
    @Test void nativeCodingUsesExplicitLabelsBaselineAndSafeAliases()throws Exception {
        fixture();var prepared=prepare(recipe());assertEquals(8,prepared.sampleIds().size());assertEquals(3,prepared.columns());assertArrayEquals(new double[]{0,1,0,1,1,0,1,0},prepared.outcomes());assertEquals(1,prepared.design()[2*3+2]);String staged=Files.readString(prepared.phenotype());assertTrue(staged.startsWith("IID\ty\tc0\tc1\n"));assertFalse(staged.contains("unsafe"));prepared.verify();
    }
    @Test void completeCaseAndUnmatchedRowsHaveExplicitLedger()throws Exception {
        fixture();String ph=Files.readString(temp.resolve("pheno.tsv")).replace("s0\tcontrol\t0", "s0\tNA\t0");Files.writeString(temp.resolve("pheno.tsv"),ph+"extra\tcase\t0\tbaseline\n");var prepared=prepare(recipe());assertEquals(7,prepared.sampleIds().size());assertEquals(1,prepared.admission().get("excluded_array_samples"));assertEquals(1L,prepared.admission().get("phenotype_rows_outside_array"));String ledger=Files.readString(prepared.phenotype().getParent().getParent().resolve("cohort-ledger.tsv"));assertTrue(ledger.contains("excluded-missing:case"));assertTrue(ledger.contains("extra\texcluded-no-array-genotype"));
    }
    @Test void errorPolicyRejectsIncompleteRows()throws Exception {
        fixture();Files.writeString(temp.resolve("pheno.tsv"),Files.readString(temp.resolve("pheno.tsv")).replace("s0\tcontrol", "s0\tNA"));var root=recipe();var ph=copyMap(root.get("phenotypes"));ph.put("missing_policy","error");root.put("phenotypes",ph);assertThrows(IOException.class,()->prepare(root));
    }
    @Test void duplicatePhenotypeIidsAndUndeclaredLabelsFail()throws Exception {
        fixture();Files.writeString(temp.resolve("pheno.tsv"),Files.readString(temp.resolve("pheno.tsv"))+"s0\tcase\t0\tbaseline\n");assertThrows(IOException.class,()->prepare(recipe()));fixture();Files.writeString(temp.resolve("pheno.tsv"),Files.readString(temp.resolve("pheno.tsv")).replace("s0\tcontrol","s0\tunknown"));assertThrows(IOException.class,()->prepare(recipe()));
    }
    @Test void unknownCategoriesAndNonfiniteCovariatesFail()throws Exception {
        fixture();Files.writeString(temp.resolve("pheno.tsv"),Files.readString(temp.resolve("pheno.tsv")).replace("s0\tcontrol\t0\tbaseline","s0\tcontrol\t0\tnew"));assertThrows(IOException.class,()->prepare(recipe()));fixture();Files.writeString(temp.resolve("pheno.tsv"),Files.readString(temp.resolve("pheno.tsv")).replace("s0\tcontrol\t0","s0\tcontrol\tNaN"));assertThrows(IOException.class,()->prepare(recipe()));
    }
    @Test void rankDeficientAndSeparatedDesignsFail()throws Exception {
        fixture();var root=recipe();var ph=copyMap(root.get("phenotypes"));ph.put("covariates",List.of(Map.of("column","x+unsafe()","kind","numeric"),Map.of("column","category","kind","numeric")));root.put("phenotypes",ph);String text=Files.readString(temp.resolve("pheno.tsv")).replace("baseline","0").replace("other","0");Files.writeString(temp.resolve("pheno.tsv"),text);assertThrows(IOException.class,()->prepare(root));fixture();text=Files.readString(temp.resolve("pheno.tsv")).replace("s0\tcontrol\t0\tbaseline","s0\tcontrol\t0\tother").replace("s5\tcontrol\t1\tbaseline","s5\tcontrol\t1\tother");Files.writeString(temp.resolve("pheno.tsv"),text);assertThrows(IOException.class,()->prepare(recipe()));
    }
    @Test void irntUsesAverageRanksAndPreservesTies() {
        double[] input={3,1,1,4};double[] actual=SaigeNullInputs.irnt(input);assertEquals(actual[1],actual[2]);assertEquals(-.6744897501960817,actual[1],2e-15);assertEquals(.31863936396437514,actual[0],2e-15);assertEquals(1.1503493803760079,actual[3],2e-15);assertArrayEquals(new double[]{3,1,1,4},input);
    }
    @Test void quantitativeAdmitsExplicitIrntWithoutCaseCounts()throws Exception {
        fixture();var lines=new ArrayList<>(Files.readAllLines(temp.resolve("pheno.tsv")));for(int i=1;i<lines.size();i++)lines.set(i,lines.get(i).replace("\tcontrol\t","\t2\t").replace("\tcase\t","\t5\t"));Files.write(temp.resolve("pheno.tsv"),lines);var prepared=prepare(quantitative(recipe()));assertNull(prepared.admission().get("cases"));assertTrue(Arrays.stream(prepared.outcomes()).allMatch(Double::isFinite));
    }
    @Test void sourceMutationInvalidatesPreparedModel()throws Exception {
        fixture();var prepared=prepare(recipe());Files.writeString(temp.resolve("pheno.tsv"),"changed");assertThrows(IOException.class,prepared::verify);
    }
    @Test void originalOverridesAndUnsupportedLocoChoicesFail()throws Exception {
        var root=recipe();var ph=copyMap(root.get("phenotypes"));ph.put("covariates",List.of());root.put("phenotypes",ph);assertThrows(IOException.class,()->SaigeNullInputs.read(write(root)));var invalid=recipe();var model=copyMap(invalid.get("model"));model.put("loco",true);model.put("relationship","sparse");invalid.put("model",model);assertThrows(IOException.class,()->SaigeNullInputs.read(write(invalid)));
    }
    @Test void fixedSingleMacAndContiguousCategoryBinsAreEnforced()throws Exception {
        var root=recipe();var p=copyMap(root.get("parameters"));p.put("variance_ratio",Map.of("kind","single","minimum_mac",10,"num_markers",30,"coefficient_variation",.001));root.put("parameters",p);assertThrows(IOException.class,()->SaigeNullInputs.read(write(root)));p.put("variance_ratio",Map.of("kind","categorical","lower_exclusive",List.of(.5,21.5),"upper_inclusive",List.of(20.5),"num_markers",30,"coefficient_variation",.001));assertThrows(IOException.class,()->SaigeNullInputs.read(write(root)));
    }
    @Test void modelExportCellBoundAppliesBeforeOriginalFit()throws Exception {
        fixture();var root=recipe();root.put("resources",Map.of("threads",1,"memory_chunk_gib",.1,"max_model_cells",63));assertThrows(IOException.class,()->prepare(root));
    }
    @Test void originalCommandKeepsDeclaredModelAndFixedControls()throws Exception {
        fixture();var prepared=prepare(recipe());var command=SaigeNullInputs.command(prepared,prepared.phenotype().getParent().getParent());assertTrue(command.contains("--covarColList=c0,c1"));assertTrue(command.contains("--useSparseGRMtoFitNULL=FALSE"));assertTrue(command.contains("--isCovariateOffset=TRUE"));assertTrue(command.contains("--minCovariateCount=-1"));assertFalse(command.stream().anyMatch(x->x.contains("unsafe")));
    }
    private Map<String,Object> model(SaigeNullInputs.Prepared prepared) {
        var y=Arrays.stream(prepared.outcomes()).boxed().toList();var x=new ArrayList<List<Double>>();double[] flat=prepared.design();for(int i=0;i<y.size();i++){var row=new ArrayList<Double>();for(int j=0;j<prepared.columns();j++)row.add(flat[i*prepared.columns()+j]);x.add(row);}var mu=y.stream().map(v->.5).toList();var res=y.stream().map(v->v-.5).toList();var result=new LinkedHashMap<String,Object>();result.put("converged",true);result.put("trait_type","binary");result.put("loco",false);result.put("sparse_fit",false);result.put("covariate_offset",true);result.put("sample_ids",prepared.sampleIds());result.put("outcomes",y);result.put("theta",List.of(1,.2));result.put("design",x);result.put("base",Map.of("fitted",mu,"residuals",res));result.put("loco_models",List.of());return result;
    }
    private Path ratios()throws Exception {Path path=temp.resolve("ratios.tsv");Files.writeString(path,".9 null 1\n.95 null_noXadj 1\n");return path;}
    @Test void nativeAuditChecksFullCohortDesignAndResidualIdentity()throws Exception {
        fixture();var prepared=prepare(recipe());var audit=SaigeNullAudit.verify(prepared,model(prepared),ratios());assertEquals(8,audit.get("cohort_samples"));assertTrue((double)audit.get("native_design_span_maximum_scaled_error")<1e-14);assertEquals(0.0,audit.get("native_outcome_maximum_absolute_error"));
    }
    @Test void changedCohortOutcomeAndModelChoicesFailAudit()throws Exception {
        fixture();var prepared=prepare(recipe());var model=model(prepared);model.put("sample_ids",List.of("wrong"));assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model,ratios()));model.put("sample_ids",prepared.sampleIds());model.put("outcomes",Collections.nCopies(8,0));assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model,ratios()));model.put("outcomes",Arrays.stream(prepared.outcomes()).boxed().toList());model.put("loco",true);assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model,ratios()));
    }
    @Test void missingVarianceRatioKindsAndNonpositiveValuesFailAudit()throws Exception {
        fixture();var prepared=prepare(recipe());Path ratio=ratios();Files.writeString(ratio,".9 null 1\n");assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model(prepared),ratio));Files.writeString(ratio,"0 null 1\n.9 null_noXadj 1\n");assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model(prepared),ratio));
    }
    @Test void rankDeficientOriginalDesignCannotHideDroppedCovariate()throws Exception {
        fixture();var prepared=prepare(recipe());var model=model(prepared);model.put("design",Collections.nCopies(8,List.of(1,0,0)));assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model,ratios()));
    }
    @Test void unconvergedAndBoundaryFittedProbabilityFailAudit()throws Exception {
        fixture();var prepared=prepare(recipe());var model=model(prepared);model.put("converged",false);assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model,ratios()));model.put("converged",true);model.put("base",Map.of("fitted",Collections.nCopies(8,0),"residuals",Arrays.stream(prepared.outcomes()).boxed().toList()));assertThrows(IOException.class,()->SaigeNullAudit.verify(prepared,model,ratios()));
    }
    private Map<String,Object> sparseRecipe()throws Exception {
        Path bundle=Files.createDirectory(temp.resolve("grm-bundle"));String matrix="%%MatrixMarket matrix coordinate real symmetric\n8 8 8\n";for(int i=1;i<=8;i++)matrix+=i+" "+i+" 1\n";Files.writeString(bundle.resolve("grm.mtx"),matrix);Files.writeString(bundle.resolve("sample-ids.txt"),String.join("\n",java.util.stream.IntStream.range(0,8).mapToObj(i->"s"+i).toList())+"\n");var audit=Map.of("assembly_declaration","synthetic fixture","grm_sha256",FollowupSupport.hash(bundle.resolve("grm.mtx")),"sample_ids_sha256",FollowupSupport.hash(bundle.resolve("sample-ids.txt")));Files.writeString(bundle.resolve("native-audit.json"),RegenieRuntime.json(audit));var results=new LinkedHashMap<String,Object>(audit);results.put("operation","sparse-grm");results.put("native_audit_sha256",FollowupSupport.hash(bundle.resolve("native-audit.json")));Files.writeString(bundle.resolve("results.json"),RegenieRuntime.json(results));Files.writeString(bundle.resolve("analysis-status.json"),"{\"status\":\"completed-with-review-warnings\"}");Files.writeString(bundle.resolve("supplied-recipe.json"),"{\"parameters\":{\"relatedness_cutoff\":0.125}}");var root=recipe();var model=copyMap(root.get("model"));model.put("relationship","sparse");model.put("sparse_variance_ratio",true);root.put("model",model);root.put("sparse_grm_bundle","grm-bundle");return root;
    }
    @Test void sparseBundleRequiresBoundHashesAssemblyAndCompleteModelIids()throws Exception {
        fixture();var root=sparseRecipe();var prepared=prepare(root);assertEquals(8,prepared.admission().get("sparse_grm_sample_universe"));prepared.verify();Files.writeString(temp.resolve("grm-bundle/sample-ids.txt"),"changed");assertThrows(IOException.class,prepared::verify);assertThrows(IOException.class,()->prepare(root));
    }
    @Test void sparseMatrixSizeIsAdmittedBeforeSnapshot()throws Exception {
        fixture();var root=sparseRecipe();Path matrix=temp.resolve("grm-bundle/grm.mtx");Files.writeString(matrix,Files.readString(matrix)+"%"+"x".repeat(2000)+"\n");root.put("resources",Map.of("threads",1,"memory_chunk_gib",.1,"max_input_bytes",1000));IOException failure=assertThrows(IOException.class,()->prepare(root));assertTrue(failure.getMessage().contains("Sparse-GRM matrix/ID input exceeds"));
    }
}
