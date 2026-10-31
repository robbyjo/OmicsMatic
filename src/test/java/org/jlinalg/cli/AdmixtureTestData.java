package org.jlinalg.cli;

import java.nio.file.*;
import java.util.*;

/** Synthetic unit-test data, not a substitute for actual original-runtime references. */
final class AdmixtureTestData {
    final Path root;
    AdmixtureTestData(Path root)throws Exception {this.root=root;bed();}
    void bed()throws Exception {
        Files.write(root.resolve("cohort.bed"),new byte[]{0x6c,0x1b,1,0x4b,(byte)0xcc,0x3a});
        Files.writeString(root.resolve("cohort.bim"),"1 v1 0 1 A G\n1 v2 0 2 C T\n2 v3 0 3 G A\n");
        Files.writeString(root.resolve("cohort.fam"),"f a 0 0 1 -9\nf b 0 0 2 -9\nf c 0 0 0 -9\nf d 0 0 0 -9\n");
    }
    Map<String,Object> recipe(){
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("operation","fit");m.put("version","1.4.0");m.put("plink_prefix","cohort");m.put("assembly","synthetic coordinates");m.put("input_review",Map.of("organism","synthetic","marker_scope","explicit numeric contigs","allele_harmonization","ordered BIM labels","ld_pruning","controlled fixture; no biological LD inference","sample_eligibility","synthetic unrelated fixture","reference_eligibility","none for unsupervised fixture","ploidy_coding","diploid 0/1/2 hardcalls"));m.put("k",2);m.put("seed",11);m.put("threads",1);m.put("model_ploidy",2);m.put("genotype_coding","diploid-hardcalls");m.put("method","block");m.put("acceleration","qn3");m.put("major_stop",Map.of("kind","delta","value",0.000001));m.put("minor_stop",Map.of("kind","iterations","value",3));m.put("cv_folds",0);m.put("bootstrap_replicates",0);m.put("bootstrap_block_markers",0);m.put("lambda",0);m.put("epsilon",.1);m.put("printed_precision_policy","error-if-unbounded");m.put("limits",Map.of("samples",100,"markers",100,"genotype_cells",10000,"parameter_cells",10000,"input_bytes",1048576,"retained_bytes",1048576,"line_bytes",4096,"output_bytes",1048576));return m;
    }
    Path write(Map<String,Object> recipe)throws Exception {Path file=root.resolve("recipe.json");Files.writeString(file,RegenieRuntime.json(recipe));return file;}
    Path work(String name)throws Exception{return Files.createDirectory(root.resolve(name));}
    AdmixtureInputs.Prepared prepare(Map<String,Object> recipe,String name)throws Exception{return AdmixtureInputs.prepare(AdmixtureInputs.read(write(recipe)),work(name));}
    double originalLikeOutput(Path work)throws Exception {
        double[] a={.2,.7,.1,.8};int[][] calls={{2,1,0,-1},{0,2,0,2},{1,1,2,0}};double ll=0;
        for(int[] row:calls)for(int i=0;i<row.length;i++)if(row[i]>=0){double f=a[i]*.1+(1-a[i])*.9;ll+=row[i]*Math.log(f)+(2-row[i])*Math.log1p(-f);}
        Files.writeString(work.resolve("cohort.2.Q"),"0.200000 0.800000\n0.700000 0.300000\n0.100000 0.900000\n0.800000 0.200000\n");Files.writeString(work.resolve("cohort.2.P"),"0.100000 0.900000\n".repeat(3));
        Files.writeString(work.resolve("fit.stdout"),"ADMIXTURE Version 1.4.0\nRandom seed: 11\nSize of G: 4x3\nStarting main algorithm\n1 (QN/Block) Loglikelihood: 0 (delta): 0\nSummary: \nConverged in 1 iterations (0 sec)\nLoglikelihood: "+String.format(Locale.ROOT,"%.6f",ll)+"\nWriting output files.\n");Files.writeString(work.resolve("fit.stderr"),"");return ll;
    }
}
