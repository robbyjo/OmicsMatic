/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkPcaCliTest {
    @TempDir Path root;
    private record Fixture(Path prefix,Path samples,Path panel) { }
    private record Run(int code,String output,String error) { }
    private static void requireHome(){Assumptions.assumeTrue(System.getenv("JLINALG_PLINK_HOME")!=null,"Set the verified original private runtime home for actual binary tests");}
    private static String resource(String folder,String name)throws IOException {
        try(var input=PlinkPcaCliTest.class.getResourceAsStream("/org/jlinalg/"+folder+"/"+name)){if(input==null)throw new IOException("Missing PCA reference fixture "+name);return new String(input.readAllBytes(),StandardCharsets.UTF_8);}
    }
    private Fixture fixture(String name,String population)throws Exception {
        Path folder=root.resolve(name);Files.createDirectory(folder);Path prefix=folder.resolve("known");
        for(String suffix:List.of("bed","bim","fam"))try(var input=PlinkPcaCliTest.class.getResourceAsStream("/org/jlinalg/genetics/plink-pruning/known."+suffix)){assertNotNull(input);Files.copy(input,Path.of(prefix+"."+suffix));}
        var included=new LinkedHashSet<PlinkQcInputs.Id>();for(String line:resource("genetics/plink-pruning","samples.tsv").lines().skip(1).toList()){String[] row=line.split("\t");if(row[2].equals("yes"))included.add(new PlinkQcInputs.Id(row[0],row[1]));}
        var rows=new ArrayList<String>();rows.add("fid\tiid\tfit\tfrequency_reference");
        for(String line:Files.readAllLines(Path.of(prefix+".fam"))){String[] r=line.split("\\s+");boolean fit=included.contains(new PlinkQcInputs.Id(r[0],r[1]));rows.add(r[0]+"\t"+r[1]+"\t"+(fit?"yes":"no")+"\t"+(fit&&(population.equals("all")||r[2].equals("0")&&r[3].equals("0"))?"yes":"no"));}
        Path samples=folder.resolve("samples.tsv"),panel=folder.resolve("panel.txt");Files.write(samples,rows,StandardCharsets.UTF_8);Files.writeString(panel,resource("genetics/plink-pruning","plink2-count-legacy.in"),StandardCharsets.UTF_8);return new Fixture(prefix,samples,panel);
    }
    private Map<String,String> options(Fixture f,String engine,String algorithm,String missing,Path out){
        var o=new LinkedHashMap<String,String>();o.put("task","pca");o.put("engine",engine);o.put("algorithm",algorithm);o.put("pca-missing",missing);o.put("home",System.getenv().getOrDefault("JLINALG_PLINK_HOME","not-configured"));o.put("bfile",f.prefix().toString());o.put("samples",f.samples().toString());o.put("variants",f.panel().toString());o.put("out",out.toString());o.put("genome-build","unspecified");o.put("chromosome-scope","autosomes");o.put("unresolved-alleles","error");o.put("missing-genotypes","upstream");o.put("minimum-reference-calls","2");o.put("components","5");if(missing.equals("mean"))o.put("maximum-relative-eigen-residual",".01");if(algorithm.equals("approx"))o.put("seed","17");return o;
    }
    private static Run run(Map<String,String> o){var args=new ArrayList<String>();o.forEach((k,v)->{args.add("--"+k);args.add(v);});return publicRun(args.toArray(String[]::new));}
    private static Run publicRun(String... args){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();String[] command=new String[args.length+1];command[0]="plink-tools";System.arraycopy(args,0,command,1,args.length);int code=JLinAlgCli.run(command,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));}
    private static List<String[]> rows(Path p)throws IOException{return Files.readString(p).lines().skip(1).map(s->s.split("\t",-1)).toList();}

    @Test void allTenActualOriginalExactAndSeededCasesKeepRawValuesAndAlleleConventions()throws Exception {
        requireHome();int cases=0;
        for(String line:resource("cli/plink-pca-reports","cases.tsv").lines().skip(1).toList()) {
            String[] c=line.split("\t",-1);var f=fixture("input-"+cases,c[3]);Path out=root.resolve("run-"+cases);var o=options(f,c[1],c[2],c[4],out);if(c[2].equals("approx"))o.put("seed",c[5]);var result=run(o);
            assertEquals(0,result.code(),result.error()+"\n"+(Files.exists(out.resolve("WARNINGS.md"))?Files.readString(out.resolve("WARNINGS.md")):""));assertTrue(result.error().contains("WARNINGS"));
            assertEquals(resource("cli/plink-pca-reports",c[0]+".eigenval").lines().toList(),rows(out.resolve("eigenvalues.tsv")).stream().map(r->r[1]).toList());
            var expectedVectors=resource("cli/plink-pca-reports",c[0]+".eigenvec").lines().skip(1).map(s->s.strip().split("\\s+")).toList();var actualVectors=rows(out.resolve("eigenvectors.tsv"));assertEquals(125,actualVectors.size());
            for(int i=0;i<125;i++)assertArrayEquals(expectedVectors.get(i),actualVectors.get(i));
            String suffix=c[1].equals("plink1")?"eigenvec.var":"eigenvec.allele";var original=resource("cli/plink-pca-reports",c[0]+"."+suffix).lines().skip(1).map(s->s.strip().split("\\s+")).toList();var actual=rows(out.resolve("variant-weights.tsv"));assertEquals(original.size(),actual.size());
            for(int i=0;i<actual.size();i++){String[] before=original.get(i),after=actual.get(i);assertEquals(before[1],after[0]);assertEquals(before[2],after[4]);assertEquals(before[3],after[5]);assertEquals(before[c[1].equals("plink1")?2:4],after[6]);for(int k=0;k<5;k++)assertEquals(before[k+(c[1].equals("plink1")?4:5)],after[k+7]);}
            assertEquals(128,rows(out.resolve("sample-reference.tsv")).size());assertEquals(87,rows(out.resolve("variant-reference.tsv")).size());assertFalse(Files.exists(out.resolve("partial")));
            if(c[4].equals("mean")){assertEquals(5,rows(out.resolve("eigen-quality.tsv")).size());assertTrue(rows(out.resolve("eigen-quality.tsv")).stream().allMatch(r->r[4].equals("true")));}else assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("indefinite"));cases++;
        }
        assertEquals(10,cases);
    }

    @Test void unavailableDependencySkipsBeforeReadingScientificInputs()throws Exception {
        var f=fixture("input","all");Path out=root.resolve("skipped");var o=options(f,"plink2","approx","mean",out);o.put("home",root.resolve("absent").toString());o.put("bfile","NOT_READ");
        var result=run(o);assertEquals(3,result.code());assertTrue(result.error().contains("SKIPPED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("--allow-install"));assertFalse(Files.exists(out.resolve("upstream/input.bed")));
    }

    @Test void invalidPoliciesDoNotSilentlyChangeAlgorithmSeedOrMissingness()throws Exception {
        var f=fixture("input","all");int attempt=0;
        for(String[] change:List.of(new String[]{"engine","plink1"},new String[]{"pca-missing","pairwise"},new String[]{"seed","0"},new String[]{"maximum-relative-eigen-residual","NaN"},new String[]{"maximum-relative-eigen-residual","1"},new String[]{"model","linear"})) {
            var o=options(f,"plink2","approx","mean",root.resolve("invalid-"+attempt++));o.put(change[0],change[1]);assertEquals(2,run(o).code());assertFalse(Files.exists(Path.of(o.get("out"))));
        }
        var exact=options(f,"plink2","exact","mean",root.resolve("exact-seed"));exact.put("seed","17");assertEquals(2,run(exact).code());
        var absent=options(f,"plink2","approx","mean",root.resolve("no-seed"));absent.remove("seed");assertEquals(2,run(absent).code());
    }

    @Test void approximationAccuracyFailureRetainsDiagnosticsWithoutPublishingCoordinates()throws Exception {
        requireHome();var f=fixture("input","founders");Path out=root.resolve("quality-failed");var o=options(f,"plink2","approx","mean",out);o.put("maximum-relative-eigen-residual",".00001");
        var result=run(o);assertEquals(2,result.code());assertTrue(result.error().contains("FAILED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("accuracy tolerance"));
        var diagnostics=rows(out.resolve("eigen-quality.tsv"));assertTrue(diagnostics.stream().anyMatch(r->r[4].equals("false")));assertEquals(.004215149686551196,Double.parseDouble(diagnostics.get(4)[1]),1e-8);
        for(String name:List.of("eigenvectors.tsv","eigenvalues.tsv","variant-weights.tsv"))assertFalse(Files.exists(out.resolve(name)));assertTrue(Files.exists(out.resolve("upstream/pca.eigenvec")));
    }

    @Test void qualityWorkBoundRejectsBeforeFrequencyAndPcaProcesses()throws Exception {
        requireHome();var f=fixture("input","all");Path out=root.resolve("work-bound");var o=options(f,"plink2","approx","mean",out);o.put("maximum-quality-products","1");assertEquals(2,run(o).code());
        Map<?,?> session=new org.yaml.snakeyaml.Yaml().load(Files.readString(out.resolve("session.yaml")));assertFalse(session.containsKey("frequency_command"));assertFalse(session.containsKey("pca_command"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("M*N*K"));
    }

    @Test void upstreamDefaultLowMafFailureIsPreservedWithoutOverrideOrCanonicalResults()throws Exception {
        requireHome();var f=fixture("input","all");var fam=Files.readAllLines(Path.of(f.prefix()+".fam"));var bim=Files.readAllLines(Path.of(f.prefix()+".bim"));String id=Files.readAllLines(f.panel()).get(0);int marker=0;while(!bim.get(marker).split("\\s+")[1].equals(id))marker++;
        var reference=new LinkedHashSet<PlinkQcInputs.Id>();for(var row:rows(f.samples()))if(row[3].equals("yes"))reference.add(new PlinkQcInputs.Id(row[0],row[1]));
        int heterozygote=0;while(true){String[] row=fam.get(heterozygote).split("\\s+");if(reference.contains(new PlinkQcInputs.Id(row[0],row[1])))break;heterozygote++;}
        byte[] raw=Files.readAllBytes(Path.of(f.prefix()+".bed"));int stride=(fam.size()+3)/4;
        for(int i=0;i<fam.size();i++){int offset=3+marker*stride+i/4,shift=2*(i%4);raw[offset]=(byte)((raw[offset]&~(3<<shift))|((i==heterozygote?2:3)<<shift));}Files.write(Path.of(f.prefix()+".bed"),raw);
        Path out=root.resolve("low-maf");var o=options(f,"plink2","exact","mean",out);var result=run(o);assertEquals(2,result.code());assertTrue(Files.readString(out.resolve("upstream/pca.stderr")).contains("under 0.005"));
        Map<?,?> session=new org.yaml.snakeyaml.Yaml().load(Files.readString(out.resolve("session.yaml")));assertFalse(((List<?>)session.get("pca_command")).contains("--grm-maf"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("declared QC/panel"));assertFalse(Files.exists(out.resolve("eigenvectors.tsv")));
    }

    @Test void helpDescribesApproximationAndTaskSpecificOptionsRemainStrict() {
        var help=publicRun("--task","pca","--help");assertEquals(0,help.code());assertTrue(help.output().contains("--maximum-relative-eigen-residual"));assertTrue(help.output().contains("--seed"));
        assertEquals(2,publicRun("--task","qc","--algorithm","approx").code());assertEquals(0,publicRun("--help").code());
    }
}
