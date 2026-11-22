package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class SingleCellQcCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path dir;
    private static final Path DATA=Path.of("src/test/resources/single-cell-qc");
    private String output;
    private String environment(String name){String x=System.getenv(name);assumeTrue(x!=null,"Set "+name+" for actual pinned single-cell workflow tests");return x;}
    private int run(String name,String method,String... extra)throws Exception{
        var a=new ArrayList<>(List.of("single-cell-qc","--method",method,"--out",dir.resolve(name).toString(),"--no-config"));a.addAll(List.of(extra));var b=new ByteArrayOutputStream();var p=new PrintStream(b);int code=JLinAlgCli.run(a.toArray(String[]::new),p,p);output=b.toString();for(String f:List.of("WARNINGS.md","analysis.log","worker-status.json"))if(code!=0&&Files.exists(dir.resolve(name).resolve(f)))output+=Files.readString(dir.resolve(name).resolve(f));return code;
    }
    private int model(String name,String method,String... extra)throws Exception{
        var a=new ArrayList<>(List.of("--matrix",DATA.resolve("counts.mtx").toString(),"--features",DATA.resolve("features.tsv").toString(),"--barcodes",DATA.resolve("barcodes.tsv").toString(),"--dataset","LIB1","--seed","42"));
        if(method.equals("scrublet")){a.addAll(List.of("--python",environment("JLINALG_SCRUBLET_PYTHON"),"--neighbors","10"));if(!List.of(extra).contains("--components"))a.addAll(List.of("--components","10"));}else a.addAll(List.of("--rscript",environment("JLINALG_SCT_RSCRIPT"),"--r-library",environment("JLINALG_SCT_R_LIBRARY"),"--fit-cells","200"));a.addAll(List.of(extra));return run(name,method,a.toArray(String[]::new));
    }
    private List<String[]> table(Path p)throws Exception{var lines=Files.readAllLines(p);return lines.subList(1,lines.size()).stream().map(s->s.split("\t",-1)).toList();}
    private double[][] matrix(Path path)throws Exception{
        var lines=Files.readAllLines(path);int i=1;while(lines.get(i).startsWith("%"))i++;String[] d=lines.get(i++).trim().split("\\s+");var m=new double[Integer.parseInt(d[0])][Integer.parseInt(d[1])];for(;i<lines.size();i++){String[] f=lines.get(i).trim().split("\\s+");m[Integer.parseInt(f[0])-1][Integer.parseInt(f[1])-1]=Double.parseDouble(f[2]);}return m;
    }
    @Test void nativeCountsPreserveZeroCellsAndCanonicalCoordinatesWithoutRuntime()throws Exception{
        Path f=dir.resolve("f.tsv"),b=dir.resolve("b.tsv"),m=dir.resolve("m.mtx");Files.writeString(f,"G1\tgene\tGene Expression\nA1\tantibody\tAntibody Capture\n");Files.writeString(b,"C1\nC2\n");Files.writeString(m,"%%MatrixMarket matrix coordinate integer general\n2 2 3\n1 1 3\n1 1 2\n2 1 50\n");
        assertEquals(0,run("counts","counts","--matrix",m.toString(),"--features",f.toString(),"--barcodes",b.toString(),"--sort-records","1"),output);assertTrue(Files.readString(dir.resolve("counts/cell-counts.tsv")).contains("C1\t5.0\t1"));assertTrue(Files.readString(dir.resolve("counts/WARNINGS.md")).contains("ZERO-COUNT CELLS"));assertTrue(Files.readString(dir.resolve("counts/WARNINGS.md")).contains("FEATURE TYPE SELECTION"));
    }
    @Test void nativeScoresAndResidualsHaveIndependentKnownValues()throws Exception{
        Path s=dir.resolve("scores.tsv");Files.writeString(s,"id\tsimulated_neighbors\tneighbors\tsimulation_ratio\texpected_rate\trate_sd\nlow\t0\t10\t2\t0.1\t0.02\nhigh\t10\t10\t2\t0.1\t0.02\n");assertEquals(0,run("scores","doublet-score","--input",s.toString(),"--threshold","0.2"),output);var r=table(dir.resolve("scores/results.tsv"));assertEquals(1.0/199,Double.parseDouble(r.get(0)[1]),1e-15);assertEquals("false",r.get(0)[3]);assertEquals("true",r.get(1)[3]);
        Path n=dir.resolve("residuals.tsv");Files.writeString(n,"id\tcount\tmean\ttheta\tminimum_variance\tclip_low\tclip_high\np\t4\t2\tInf\t0\t-Inf\tInf\nfloor\t4\t2\t1\t100\t-1\t1\n");assertEquals(0,run("nb","nb-residual","--input",n.toString()),output);r=table(dir.resolve("nb/results.tsv"));assertEquals(Math.sqrt(2),Double.parseDouble(r.get(0)[1]),1e-15);assertEquals(.2,Double.parseDouble(r.get(1)[1]),1e-15);
    }
    @Test void originalScrubletExactNeighborsMatchFrozenFullPipelineScores()throws Exception{
        assertEquals(0,model("exact","scrublet","--approximate","false"),output);var actual=table(dir.resolve("exact/observed.tsv"));var expected=table(DATA.resolve("scrublet-neighbor-scores.tsv"));assertEquals(252,actual.size());
        for(int i=0;i<actual.size();i++){assertEquals("C"+i,actual.get(i)[0]);assertEquals(Double.parseDouble(expected.get(i)[6]),Double.parseDouble(actual.get(i)[1]),2e-11);assertEquals(Double.parseDouble(expected.get(i)[7]),Double.parseDouble(actual.get(i)[2]),2e-11);}
        var singlets=actual.subList(0,240).stream().mapToDouble(a->Double.parseDouble(a[1])).sorted().toArray();var doublets=actual.subList(240,252).stream().mapToDouble(a->Double.parseDouble(a[1])).sorted().toArray();assertTrue(doublets[6]>singlets[120]*3);assertTrue(Files.readString(dir.resolve("exact/WARNINGS.md")).contains("invalid value encountered in sqrt"));assertEquals(504,table(dir.resolve("exact/simulated.tsv")).size());
    }
    @Test void approximateCosineAndExplicitNoCallThresholdHaveRetainedWarnings()throws Exception{
        assertEquals(0,model("approx","scrublet","--approximate","true","--metric","cosine","--threshold","1"),output);assertTrue(table(dir.resolve("approx/observed.tsv")).stream().allMatch(r->r[5].equals("false")));assertTrue(Files.readString(dir.resolve("approx/WARNINGS.md")).contains("NO DETECTABLE SIMULATED DOUBLETS"));assertTrue(Files.exists(dir.resolve("approx/manifold-observed.tsv")));
    }
    @Test void tooManyComponentsFailWithRetainedPartialsAndNoSilentReduction()throws Exception{
        assertEquals(2,model("components","scrublet","--components","200"),output);assertFalse(Files.exists(dir.resolve("components/results.tsv")));assertTrue(Files.exists(dir.resolve("components/partial/counts/matrix.mtx")));assertTrue(Files.readString(dir.resolve("components/WARNINGS.md")).contains("No automatic cell filtering or parameter reduction"));
    }
    @Test void standaloneVstV1AndV2MatchFrozenActualModelResiduals()throws Exception{
        for(String flavor:List.of("v1","v2")){
            String name="vst-"+flavor;assertEquals(0,model(name,"sct-vst","--flavor",flavor,"--fit-genes","300"),output);double[][] actual=matrix(dir.resolve(name+"/residuals.mtx"));var genes=Files.readAllLines(dir.resolve(name+"/residual-features.tsv"));var cells=Files.readAllLines(dir.resolve(name+"/barcodes.tsv"));assertEquals(600,genes.size());assertEquals(252,cells.size());
            for(var r:table(DATA.resolve("sctransform-residuals.tsv")))if(r[0].equals(flavor))assertEquals(Double.parseDouble(r[9]),actual[genes.indexOf(r[1])][cells.indexOf(r[2])],2e-8,flavor+":"+r[1]);assertTrue(Files.exists(dir.resolve(name+"/vst-model.rds")));assertTrue(Files.exists(dir.resolve(name+"/corrected-counts.mtx")));
        }
    }
    @Test void seuratV1AndV2KeepPhaseRegressionSeparateAndMatchFrozenReferences()throws Exception{
        for(String flavor:List.of("v1","v2")){
            String name="seurat-"+flavor;assertEquals(0,model(name,"sct-seurat","--flavor",flavor,"--variable-features","100","--metadata",DATA.resolve("phase-covariates.tsv").toString(),"--regress","S.Score,G2M.Score"),output);double[][] actual=matrix(dir.resolve(name+"/residuals.mtx"));var genes=Files.readAllLines(dir.resolve(name+"/residual-features.tsv"));var cells=Files.readAllLines(dir.resolve(name+"/barcodes.tsv"));assertEquals(100,genes.size());
            for(var r:table(DATA.resolve("seurat-regressed-residuals.tsv")))if(r[0].equals(flavor))assertEquals(Double.parseDouble(r[3]),actual[genes.indexOf(r[1])][cells.indexOf(r[2])],3e-7,flavor+":"+r[1]);assertTrue(Files.exists(dir.resolve(name+"/seurat-model.rds")));assertTrue(Files.exists(dir.resolve(name+"/sct-logdata.mtx")));assertTrue(Files.exists(dir.resolve(name+"/corrected-counts.mtx")));
        }
    }
    @Test void metadataMismatchAndResidualSizeLimitFailBeforeRunningR()throws Exception{
        Path bad=dir.resolve("bad.tsv");Files.writeString(bad,"cell_id\tpercent.mt\nunknown\t0\n");assertEquals(2,model("metadata","sct-seurat","--metadata",bad.toString()),output);assertFalse(Files.exists(dir.resolve("metadata/analysis.log")));assertTrue(output.contains("Metadata must match"));
        assertEquals(2,model("memory","sct-vst","--max-residual-values","1"),output);assertFalse(Files.exists(dir.resolve("memory/analysis.log")));assertTrue(output.contains("exceeding max-residual-values"));
    }
    @Test void missingRuntimeAndMissingPrivateLibrarySkipWithExactActions()throws Exception{
        assertEquals(3,run("missing","scrublet","--python",dir.resolve("absent.exe").toString(),"--dataset","L1","--matrix","missing","--features","missing","--barcodes","missing"),output);assertTrue(Files.readString(dir.resolve("missing/WARNINGS.md")).contains("install_scrublet.py --allow-install --allow-source-build"));assertFalse(Files.exists(dir.resolve("missing/results.tsv")));
        assertEquals(3,run("missing-library","sct-vst","--rscript",environment("JLINALG_SCT_RSCRIPT"),"--r-library",dir.resolve("absent-library").toString(),"--dataset","D1","--matrix","missing","--features","missing","--barcodes","missing"),output);assertTrue(Files.readString(dir.resolve("missing-library/WARNINGS.md")).contains("install_sctransform_dependencies.R"));
    }
    @Test void incompatibleOptionsFailBeforeOutputCreation()throws Exception{
        assertEquals(2,run("bad-options","sct-seurat","--rscript","missing","--r-library","missing","--dataset","D1","--matrix","missing","--features","missing","--barcodes","missing","--latent","log_umi"),output);assertFalse(Files.exists(dir.resolve("bad-options")));
    }
    @Test void exactCellLabelsAndSeuratFeatureAliasesPreventLanguageLevelRewriting()throws Exception{
        Path features=dir.resolve("features.tsv"),barcodes=dir.resolve("barcodes.tsv"),meta=dir.resolve("metadata.tsv");var fs=Files.readAllLines(DATA.resolve("features.tsv"));fs.set(0,fs.get(0).replaceFirst("G0\\t","G_0\t"));fs.set(1,fs.get(1).replaceFirst("G1\\t","G-0\t"));fs.set(2,fs.get(2).replaceFirst("G2\\t","G|0\t"));Files.write(features,fs);
        var cells=Files.readAllLines(DATA.resolve("barcodes.tsv"));cells.set(0,"0001");cells.set(1,"cell\"two");Files.write(barcodes,cells);var text=new StringBuilder("cell_id\tCOV\n");for(int i=0;i<cells.size();i++)text.append(cells.get(i)).append('\t').append(i+1).append('\n');Files.writeString(meta,text);
        var input=new ArrayList<>(List.of("--matrix",DATA.resolve("counts.mtx").toString(),"--features",features.toString(),"--barcodes",barcodes.toString(),"--dataset","L1","--seed","42"));
        var scr=new ArrayList<>(input);scr.addAll(List.of("--python",environment("JLINALG_SCRUBLET_PYTHON"),"--components","10","--neighbors","10"));assertEquals(0,run("named-scrublet","scrublet",scr.toArray(String[]::new)),output);var values=table(dir.resolve("named-scrublet/observed.tsv"));assertEquals("0001",values.get(0)[0]);assertEquals("cell\"two",values.get(1)[0]);
        input.addAll(List.of("--rscript",environment("JLINALG_SCT_RSCRIPT"),"--r-library",environment("JLINALG_SCT_R_LIBRARY"),"--fit-cells","200","--variable-features","100","--variable-only","false","--metadata",meta.toString(),"--regress","COV"));assertEquals(0,run("named-seurat","sct-seurat",input.toArray(String[]::new)),output);assertEquals(cells,Files.readAllLines(dir.resolve("named-seurat/barcodes.tsv")));var genes=Files.readAllLines(dir.resolve("named-seurat/residual-features.tsv"));assertTrue(genes.containsAll(List.of("G_0","G-0","G|0")));assertTrue(Files.readString(dir.resolve("named-seurat/WARNINGS.md")).contains("FEATURE ID MAP"));
    }
}
