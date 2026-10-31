package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaigeSingleInputsTest {
    @TempDir Path temp;
    private static final int[] Y={0,1,0,1,1,0,1,0};
    private Map<String,Object> recipe() {
        var root=new LinkedHashMap<String,Object>();root.put("version",1);root.put("operation","single");root.put("model_bundle","bundle");root.put("genotypes","cohort");root.put("genotype_coding","diploid-autosomal-plink-bed");root.put("assembly","synthetic fixture");root.put("chromosome","1");root.put("allele_order","alt-first");root.put("cohort",Map.of("extra_genotyped_samples","allow-with-ledger"));root.put("qc",Map.of("min_maf",0,"min_mac",1,"max_missing",.15,"imputation","best_guess","invalid_marker_policy","exclude-with-ledger","dosage_zero_cutoff",.2,"dosage_zero_max_mac",10));root.put("method",Map.of("firth_beta",false,"firth_p_cutoff",.05,"spa_cutoff",2,"fast_test",true,"genotype_adjustment","center-only-fast","resampling_max_mac",4,"non_estimable","error"));root.put("resources",Map.of("threads",1,"variants_per_chunk",10000,"max_samples",100,"max_variants",100,"max_input_bytes",1048576,"max_genotype_cells",1000,"max_model_cells",1000,"max_result_bytes",1048576));return root;
    }
    private static Map<String,Object> copy(Object value){var result=new LinkedHashMap<String,Object>();((Map<?,?>)value).forEach((k,v)->result.put((String)k,v));return result;}
    private Path recipeFile(Map<String,Object> recipe)throws Exception {Path file=temp.resolve("recipe.json");Files.writeString(file,RegenieRuntime.json(recipe));return file;}
    private SaigeRuntime.Context runtime(){return new SaigeRuntime.Context(temp,temp,temp.resolve("not-executed-Rscript"),"fixture-marker",Map.of(),Map.of(),Map.of());}
    private static Map<String,Object> nullRecipe(boolean quantitative) {
        var parameters=new LinkedHashMap<String,Object>();parameters.put("min_maf",.01);parameters.put("max_missing",.15);parameters.put("relatedness_cutoff",.125);parameters.put("seed",1);parameters.put("diagonal","observed");parameters.put("fit_tolerance",.02);parameters.put("max_iterations",20);parameters.put("pcg_tolerance",1e-5);parameters.put("max_pcg_iterations",500);parameters.put("trace_cv",.0025);parameters.put("trace_runs",30);parameters.put("spa_cutoff",2);parameters.put("tau_initial",List.of(0,0));parameters.put("variance_ratio",Map.of("kind","single","minimum_mac",20,"num_markers",30,"coefficient_variation",.001));var ph=new LinkedHashMap<String,Object>();ph.put("file","not-needed-for-bundle-read.tsv");ph.put("delimiter","tab");ph.put("id","sample");ph.put("response","trait");ph.put("covariates",List.of(Map.of("column","x","kind","numeric")));ph.put("missing",List.of("NA"));ph.put("missing_policy","error");if(!quantitative){ph.put("control","0");ph.put("case","1");}
        return new LinkedHashMap<>(Map.of("version",1,"operation","fit-null","genotypes","unused-cohort","genotype_coding","diploid-autosomal-plink-bed","assembly","synthetic fixture","phenotypes",ph,"model",Map.of("trait_type",quantitative?"quantitative":"binary","inverse_normalize",false,"loco",false,"relationship","full","sparse_variance_ratio",false,"covariate_offset",false),"parameters",parameters,"resources",Map.of("threads",1,"memory_chunk_gib",.1)));
    }
    private void fixture(boolean quantitative)throws Exception {
        Path bundle=Files.createDirectory(temp.resolve("bundle"));List<String> ids=java.util.stream.IntStream.range(0,8).mapToObj(i->"s"+i).toList();List<Double> outcomes=Arrays.stream(Y).mapToDouble(v->v).boxed().toList(),residuals=outcomes.stream().map(v->v-.5).toList();var design=new ArrayList<List<Number>>();for(int i=0;i<8;i++)design.add(List.of(1,i%4));var model=new LinkedHashMap<String,Object>();model.put("schema_version",1);model.put("trait_type",quantitative?"quantitative":"binary");model.put("converged",true);model.put("sample_ids",ids);model.put("outcomes",outcomes);model.put("design",design);model.put("theta",List.of(1,.2));model.put("covariate_offset",false);model.put("sparse_fit",false);model.put("loco",false);model.put("base",Map.of("fitted",Collections.nCopies(8,.5),"residuals",residuals));model.put("loco_models",List.of());Files.writeString(bundle.resolve("original-model.json"),RegenieRuntime.json(model));Files.writeString(bundle.resolve("null-model.rda"),"fixture data only;never loaded or executed by these Java tests");Files.writeString(bundle.resolve("variance-ratios.tsv"),"1 null 1\n1 null_noXadj 1\n");Files.writeString(bundle.resolve("supplied-recipe.json"),RegenieRuntime.json(nullRecipe(quantitative)));Files.writeString(bundle.resolve("phenotype-admission.json"),RegenieRuntime.json(Map.of("model_export_cells_bound",56,"design_columns",2)));Files.writeString(bundle.resolve("cohort-ledger.tsv"),"sample\tstatus\n"+String.join("\n",ids.stream().map(s->s+"\tincluded").toList())+"\n");
        var audit=new LinkedHashMap<String,Object>();audit.put("operation","fit-null");audit.put("null_model_sha256",FollowupSupport.hash(bundle.resolve("null-model.rda")));audit.put("variance_ratios_sha256",FollowupSupport.hash(bundle.resolve("variance-ratios.tsv")));audit.put("original_model_export_sha256",FollowupSupport.hash(bundle.resolve("original-model.json")));Files.writeString(bundle.resolve("native-audit.json"),RegenieRuntime.json(audit));var results=new LinkedHashMap<String,Object>(audit);results.put("status","completed-with-review-warnings");results.put("assembly_declaration","synthetic fixture");results.put("trait_type",quantitative?"quantitative":"binary");results.put("loco",false);results.put("sparse_fit",false);results.put("sparse_variance_ratio",false);for(String[] pair:List.of(new String[]{"native_audit_sha256","native-audit.json"},new String[]{"recipe_sha256","supplied-recipe.json"},new String[]{"phenotype_admission_sha256","phenotype-admission.json"},new String[]{"cohort_ledger_sha256","cohort-ledger.tsv"}))results.put(pair[0],FollowupSupport.hash(bundle.resolve(pair[1])));Files.writeString(bundle.resolve("results.json"),RegenieRuntime.json(results));Files.writeString(bundle.resolve("analysis-status.json"),"{\"status\":\"completed-with-review-warnings\"}");var session=new LinkedHashMap<String,Object>();SaigeRuntime.provenance(runtime(),session);Files.writeString(bundle.resolve("session.json"),RegenieRuntime.json(session));sealManifest(bundle);
        var fam=new StringBuilder();for(int i=0;i<10;i++)fam.append("f s").append(i).append(" 0 0 0 -9\n");Files.writeString(temp.resolve("cohort.fam"),fam);Files.writeString(temp.resolve("cohort.bim"),"1 v1 0 1 C A\n1 v2 0 2 C A\n2 v3 0 3 C A\n1 v4 0 4 C A\n");double[][] calls={{0,0,1,1,2,0,1,2,Double.NaN,2},new double[10],{0,0,1,1,2,0,1,2,0,0},new double[10]};Arrays.fill(calls[3],Double.NaN);var bed=new ByteArrayOutputStream();bed.writeBytes(new byte[]{0x6c,0x1b,1});for(double[] row:calls)for(int start=0;start<10;start+=4){int packed=0;for(int j=0;j<4&&start+j<10;j++){double value=row[start+j];int code=Double.isNaN(value)?1:value==2?0:value==1?2:3;packed|=code<<(j*2);}bed.write(packed);}Files.write(temp.resolve("cohort.bed"),bed.toByteArray());
    }
    private static void sealManifest(Path bundle)throws Exception {var pins=new TreeMap<String,String>();try(var stream=Files.list(bundle)){for(Path p:stream.filter(Files::isRegularFile).toList())if(!p.getFileName().toString().equals("manifest.yaml"))pins.put(p.getFileName().toString(),FollowupSupport.hash(p));}Files.writeString(bundle.resolve("manifest.yaml"),new org.yaml.snakeyaml.Yaml().dump(Map.of("operation","original-saige-typed-audited-operation","analysis_status","completed-with-review-warnings","output_sha256",pins)));}
    private SaigeSingleInputs.Prepared prepare(Map<String,Object> root)throws Exception {return SaigeSingleInputs.prepare(SaigeSingleInputs.read(recipeFile(root)),Files.createTempDirectory(temp,"work-"),runtime());}
    @Test void completeCohortAndVariantLedgersNameEveryExclusion()throws Exception {
        fixture(false);var prepared=prepare(recipe());assertEquals(10,prepared.sourceSamples());assertEquals(8,prepared.cohort().length);assertEquals(List.of("v1"),prepared.decisions().stream().filter(d->d.status().equals("eligible")).map(SaigeSingleInputs.Decision::id).toList());assertEquals(List.of("eligible","excluded-initial-mac","excluded-other-chromosome","excluded-all-model-calls-missing"),prepared.decisions().stream().map(SaigeSingleInputs.Decision::status).toList());prepared.verify();assertEquals(2,Files.readAllLines(prepared.prefix().getParent().getParent().resolve("genotype-sample-ledger.tsv")).stream().filter(s->s.endsWith("extra-not-analyzed")).count());for(String line:Files.readAllLines(prepared.prefix().getParent().getParent().resolve("variant-ledger.tsv")))assertEquals(13,line.split("\t",-1).length);
    }
    @Test void genotypeAndModelMutationInvalidateSnapshots()throws Exception {
        fixture(false);var prepared=prepare(recipe());Files.writeString(temp.resolve("bundle/null-model.rda"),"changed");assertThrows(IOException.class,prepared::verify);assertThrows(IOException.class,()->prepare(recipe()));
    }
    @Test void extraSamplesRequireExplicitPermission()throws Exception {
        fixture(false);var root=recipe();root.put("cohort",Map.of("extra_genotyped_samples","error"));assertThrows(IOException.class,()->prepare(root));
    }
    @Test void missingModelParticipantAndCollidingIidsFail()throws Exception {
        fixture(false);Path fam=temp.resolve("cohort.fam");Files.writeString(fam,Files.readString(fam).replace("s0 ","other "));assertThrows(IllegalArgumentException.class,()->prepare(recipe()));Files.writeString(fam,Files.readString(fam).replace("other ","s1 "));assertThrows(Exception.class,()->prepare(recipe()));
    }
    @Test void allMissingMarkerCanRequireError()throws Exception {
        fixture(false);var root=recipe();var qc=copy(root.get("qc"));qc.put("invalid_marker_policy","error");root.put("qc",qc);assertThrows(IOException.class,()->prepare(root));
    }
    @Test void emptyEligibleSetIsAnErrorWithRetainedLedger()throws Exception {
        fixture(false);var root=recipe();var qc=copy(root.get("qc"));qc.put("min_mac",100);root.put("qc",qc);assertThrows(IOException.class,()->prepare(root));
    }
    @Test void modelManifestDetectsChangedRuntimeMetadata()throws Exception {
        fixture(false);Path session=temp.resolve("bundle/session.json");Files.writeString(session,"{}");assertThrows(IOException.class,()->prepare(recipe()));
    }
    @Test void differentProfileIsRejectedEvenWithUpdatedManifest()throws Exception {
        fixture(false);Path session=temp.resolve("bundle/session.json");var value=copy(SimpleJson.parse(Files.readString(session)));value.put("runtime_lock_sha256","different");Files.writeString(session,RegenieRuntime.json(value));sealManifest(temp.resolve("bundle"));assertThrows(IOException.class,()->prepare(recipe()));
    }
    @Test void unsafeYamlManifestTagsAreNotConstructed()throws Exception {
        fixture(false);Files.writeString(temp.resolve("bundle/manifest.yaml"),"!!java.net.URL ['https://example.invalid']");assertThrows(Exception.class,()->prepare(recipe()));
    }
    @Test void quantitativeModelRejectsBinaryFirthRequest()throws Exception {
        fixture(true);var root=recipe();var method=copy(root.get("method"));method.put("firth_beta",true);root.put("method",method);assertThrows(IOException.class,()->prepare(root));
    }
    @Test void unknownRecipeOptionsAndAliasesFail()throws Exception {
        var root=recipe();root.put("script","user.R");assertThrows(IOException.class,()->SaigeSingleInputs.read(recipeFile(root)));root.remove("script");root.put("chromosome","chr1");assertThrows(IOException.class,()->SaigeSingleInputs.read(recipeFile(root)));
    }
    @Test void missingCleanupAndInconsistentFastChoicesFail()throws Exception {
        var root=recipe();var qc=copy(root.get("qc"));qc.remove("dosage_zero_cutoff");root.put("qc",qc);assertThrows(IOException.class,()->SaigeSingleInputs.read(recipeFile(root)));var other=recipe();var method=copy(other.get("method"));method.put("fast_test",false);other.put("method",method);assertThrows(IOException.class,()->SaigeSingleInputs.read(recipeFile(other)));
    }
    @Test void workLimitsApplyBeforeUpstreamExecution()throws Exception {
        fixture(false);var root=recipe();var resources=copy(root.get("resources"));resources.put("max_genotype_cells",39);root.put("resources",resources);assertThrows(IOException.class,()->prepare(root));
    }
    @Test void originalCommandUsesOnlyTypedPathsAndExplicitNumericalControls()throws Exception {
        fixture(false);var prepared=prepare(recipe());var command=SaigeSingleInputs.command(prepared,prepared.prefix().getParent().getParent());assertTrue(command.contains("--AlleleOrder=alt-first"));assertTrue(command.contains("--dosage_zerod_cutoff=0.2"));assertTrue(command.contains("--is_noadjCov=TRUE"));assertTrue(command.contains("--is_imputed_data=FALSE"));assertTrue(command.stream().anyMatch(s->s.startsWith("--idstoIncludeFile=")));assertFalse(command.stream().anyMatch(s->s.startsWith("--condition=")));
    }
    @Test void initialAndPostImputationMafAreSeparateDecisions() {
        var s=SaigeGenotypes.calculatePlink(new double[]{0,1,Double.NaN,0},null,SaigeGenotypes.AlleleOrder.ALT_FIRST,SaigeGenotypes.Missing.BEST_GUESS).summary();assertTrue(SaigeGenotypes.passesOriginalQc(s,.15,1,.25));assertFalse(SaigeGenotypes.passesFinalSingleQc(s,.15,1,.25));assertEquals(.125,s.frequency());
    }
    @Test void cleanupUsesInclusiveDosageThresholdAndExplicitMacTrigger() {
        double[] calls={0,1,Double.NaN,0};var clean=SaigeGenotypes.calculatePlink(calls,null,SaigeGenotypes.AlleleOrder.ALT_FIRST,SaigeGenotypes.Missing.MEAN,1.0/3,4.0/3).summary();var noClean=SaigeGenotypes.calculatePlink(calls,null,SaigeGenotypes.AlleleOrder.ALT_FIRST,SaigeGenotypes.Missing.MEAN,Math.nextDown(1.0/3),4.0/3).summary();assertEquals(1,clean.alleleCount());assertEquals(4.0/3,noClean.alleleCount());assertEquals(0,clean.imputedMinorDosage());
    }
    @Test void cleanupIsNotActivatedAboveItsMacBound() {
        var s=SaigeGenotypes.calculatePlink(new double[]{0,1,Double.NaN,0},null,SaigeGenotypes.AlleleOrder.ALT_FIRST,SaigeGenotypes.Missing.MEAN,1.0/3,Math.nextDown(4.0/3)).summary();assertEquals(4.0/3,s.alleleCount());
    }
    @Test void independentRationalProjectionAndCenteredVariancesAgree() {
        double[] g={0,0,1,1,2,0,1,2},x=new double[16],w=new double[8],res=new double[8];Arrays.fill(w,.25);for(int i=0;i<8;i++){x[2*i]=1;x[2*i+1]=i%4;res[i]=Y[i]-.5;}var result=SaigeSingleAudit.projection(g,7.0/16,false,x,new double[]{.5,.25},2,w,res,1);assertEquals(.5,result.centeredScore());assertEquals(.5,result.projectedScore());assertEquals(39.0/32,result.centeredVariance());assertEquals(17.0/16,result.projectedVariance());assertTrue(result.estimable());
    }
    @Test void majorAlleleCenteringRetainsOriginalReportedAfArithmetic() {
        double[] g={0,0,1,1,2,0,1,2},x=new double[16],w=new double[8],res=new double[8];Arrays.fill(w,.25);Arrays.fill(res,1);for(int i=0;i<8;i++){x[2*i]=1;x[2*i+1]=i%4;}var result=SaigeSingleAudit.projection(g,9.0/16,true,x,new double[]{.5,.25},2,w,res,1);assertEquals(2,result.centeredScore());assertEquals(-0.0,result.projectedScore());assertEquals(43.0/32,result.centeredVariance());
    }
    @Test void probabilityParsingPreservesValuesBelowDoubleRange()throws Exception {
        assertEquals(new BigDecimal("1.2E-1000"),SaigeSingleAudit.probability("1.2E-1000"));assertThrows(IOException.class,()->SaigeSingleAudit.probability("0"));assertThrows(IOException.class,()->SaigeSingleAudit.probability("NaN"));assertThrows(IOException.class,()->SaigeSingleAudit.probability("1.01"));
    }
    @Test void normalPIntervalsPropagatePrintedStatisticPrecision()throws Exception {
        SaigeSingleAudit.normalInterval(1.95996,1,"5.000000E-02","known-normal-1.96");assertThrows(IOException.class,()->SaigeSingleAudit.normalInterval(1.95996,1,"5.000000E-01","corrupt-P"));SaigeSingleAudit.normalInterval(0,1,"1.000000E+00","null-score");
    }
    @Test void effectAndSeIntervalsRejectCorruptStatistics()throws Exception {
        SaigeSingleAudit.effectIntervals(1.95996,1,1.95996,1,new BigDecimal("5.000000E-02"),true,true,"known-normal");assertThrows(IOException.class,()->SaigeSingleAudit.effectIntervals(1.95996,1,5,1,new BigDecimal("5.000000E-02"),true,true,"wrong-beta"));assertThrows(IOException.class,()->SaigeSingleAudit.effectIntervals(1.95996,1,1.95996,2,new BigDecimal("5.000000E-02"),false,true,"wrong-SE"));
    }
    @Test void unknownOutputRowsAndEmptyExitZeroCannotBeAdmitted()throws Exception {
        fixture(false);var prepared=prepare(recipe());Path work=prepared.prefix().getParent().getParent();Files.writeString(work.resolve("single.stdout"),"");Files.writeString(work.resolve("original-associations.tsv"),"");assertThrows(IOException.class,()->SaigeSingleAudit.audit(prepared,work));Files.writeString(work.resolve("original-associations.tsv"),"MarkerID\nunknown\n");assertThrows(IOException.class,()->SaigeSingleAudit.audit(prepared,work));
    }
    @Test void singleRecipeDependencySkipHasExplicitRecovery()throws Exception {
        Path recipe=recipeFile(recipe()),out=temp.resolve("out");int code=SaigeToolsCli.run(new String[]{"--recipe",recipe.toString(),"--out",out.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));assertEquals(3,code);assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));assertFalse(Files.exists(out.resolve("results.json")));
    }
    @Test void exactOriginalNumericalDiagnosticsRetainPcgFailuresAndFirthCounts()throws Exception {
        Path stdout=temp.resolve("single.stdout");String pcg="PCG in getPCG1ofSigmaAndGtilde did not converge. You may increase maxiter number.";
        Files.writeString(stdout,"Firth approx was applied to 4 markers. 3 sucessfully converged.\n"+pcg+"\n "+pcg+" \nOther PCG did not converge\n");
        var diagnostics=SaigeSingleAudit.firth(stdout);assertEquals(4,diagnostics.applied());assertEquals(3,diagnostics.converged());assertEquals(1,diagnostics.chunks());assertEquals(2,diagnostics.pcgFailures());
    }
    @Test void unavailableAndOversizedNumericalLogsCannotBeAdmitted()throws Exception {
        Path stdout=temp.resolve("missing.stdout");assertThrows(IOException.class,()->SaigeSingleAudit.firth(stdout));
        try(var channel=java.nio.channels.FileChannel.open(stdout,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){channel.position(64L*1048576);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}
        assertThrows(IOException.class,()->SaigeSingleAudit.firth(stdout));
    }
}
