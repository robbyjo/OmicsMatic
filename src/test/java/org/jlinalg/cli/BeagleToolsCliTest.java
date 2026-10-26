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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BeagleToolsCliTest {
    @TempDir Path root;
    private record Fixture(Path target,Path reference,Path map) { }
    private record Run(int code,String output,String error) { }
    private static void requireHome(){Assumptions.assumeTrue(System.getenv("JLINALG_BEAGLE_HOME")!=null,"Set the exact private Beagle runtime home for real-binary tests");}
    private static byte[] resource(String name)throws IOException{try(var input=BeagleToolsCliTest.class.getResourceAsStream("/org/jlinalg/genetics/beagle-reference/"+name)){if(input==null)throw new IOException("Missing synthetic Beagle fixture "+name);return input.readAllBytes();}}
    private static String text(String name)throws IOException{return new String(resource(name),StandardCharsets.UTF_8);}
    private Fixture fixture(String name,String target)throws Exception {Path folder=root.resolve(name);Files.createDirectories(folder);Path t=folder.resolve("target.vcf"),r=folder.resolve("reference.vcf"),map=folder.resolve("map.txt");Files.write(t,resource(target));Files.write(r,resource("reference.vcf"));Files.write(map,resource("map.txt"));return new Fixture(t,r,map);}
    private Map<String,String> options(Fixture f,String profile,String method,boolean reference,String phase,Path out){
        var o=new LinkedHashMap<String,String>();o.put("method",method);o.put("profile",profile);o.put("input",f.target().toString());o.put("map",f.map().toString());o.put("assembly","synthetic-unvalidated");o.put("out",out.toString());o.put("home",System.getenv().getOrDefault("JLINALG_BEAGLE_HOME","not-configured"));o.put("phase-policy",phase);o.put("minimum-observed-markers","1");o.put("seed","17");o.put("iterations","4");o.put("ne","1000");o.put("err","0.0001");o.put("window-unit",profile.startsWith("4.1")?"markers":"cm");o.put("window-size",profile.startsWith("4.1")?"80":"2.0");o.put("overlap-size",profile.startsWith("4.1")?"20":"0.3");o.put("threads","1");o.put("memory-mib","512");
        if(reference){o.put("reference",f.reference().toString());o.put("target-only-markers","drop-with-warning");o.put("sample-overlap","error");}
        if(!profile.startsWith("4.1")){o.put("burnin","2");o.put("phase-states","100");if(method.equals("impute"))o.put("imp-states","200");if(profile.startsWith("5.4"))o.put("em","false");}return o;
    }
    private static Run run(Map<String,String> o){var args=new ArrayList<String>();o.forEach((k,v)->{args.add("--"+k);args.add(v);});return direct(args.toArray(String[]::new));}
    private static Run direct(String... args){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=BeagleToolsCli.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));}
    private static List<String[]> records(Path path)throws IOException{return Files.readString(path).lines().filter(s->!s.startsWith("#")).map(s->s.split("\t",-1)).toList();}
    private static List<String[]> original(String name)throws IOException{try(var input=new GZIPInputStream(new java.io.ByteArrayInputStream(resource(name)))){return new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(s->!s.startsWith("#")).map(s->s.split("\t",-1)).toList();}}
    private static void success(Run r,Path out)throws Exception {assertEquals(0,r.code(),r.error()+"\n"+(Files.exists(out.resolve("WARNINGS.md"))?Files.readString(out.resolve("WARNINGS.md")):""));assertTrue(r.error().contains("WARNINGS"));assertTrue(Files.exists(out.resolve("genotypes.vcf")));assertFalse(Files.exists(out.resolve("partial")));}

    @Test void twentyUniqueActualReferenceWorkflowsPreserveCallsPosteriorsAndMarkerFamilies()throws Exception {
        requireHome();int count=0;
        for(String line:text("cases.tsv").lines().skip(1).toList()) {
            String[] c=line.split("\t");if(c[3].equals("java25"))continue;
            var f=fixture("input-"+count,c[4]);boolean reference=Boolean.parseBoolean(c[5]);String method=c[2].contains("impute")?"impute":"phase";
            String phase=c[2].equals("phased-impute")&&!c[1].startsWith("4.1")?"allow-rephase":"preserve-provided";Path out=root.resolve("run-"+count);
            success(run(options(f,c[1],method,reference,phase,out)),out);var expected=original(c[8]);var actual=records(out.resolve("genotypes.vcf"));assertEquals(Integer.parseInt(c[6]),actual.size());assertEquals(expected.size(),actual.size());
            for(int i=0;i<actual.size();i++){String[] a=actual.get(i),e=expected.get(i);assertArrayEquals(Arrays.copyOf(e,5),Arrays.copyOf(a,5));assertEquals(e[8],a[8]);assertArrayEquals(Arrays.copyOfRange(e,9,e.length),Arrays.copyOfRange(a,9,a.length));}
            assertEquals(24,Files.readAllLines(out.resolve("sample-ledger.tsv")).stream().skip(1).filter(s->s.startsWith("target\t")).count());assertEquals(actual.size(),Files.readAllLines(out.resolve("variant-results.tsv")).size()-1);assertTrue(Files.exists(out.resolve("upstream/target.calls.bin")));count++;
        }
        assertEquals(20,count);
    }

    @Test void missingRuntimeSkipsBeforeScientificParsing()throws Exception {
        var f=fixture("input","target.vcf");Path out=root.resolve("skipped");var o=options(f,"5.4-29Oct24","impute",true,"allow-rephase",out);o.put("home",root.resolve("absent").toString());o.put("input","NOT_READ");var r=run(o);
        assertEquals(3,r.code());assertTrue(r.error().contains("SKIPPED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("--allow-install"));assertFalse(Files.exists(out.resolve("upstream/target.vcf")));
    }

    @Test void changedRuntimeSkipsWithoutReplacingAnyFile()throws Exception {
        requireHome();Path home=root.resolve("changed-runtime");Files.createDirectory(home);
        try(var paths=Files.list(Path.of(System.getenv("JLINALG_BEAGLE_HOME")))){for(Path p:paths.toList())Files.copy(p,home.resolve(p.getFileName()));}
        Path jar=home.resolve("beagle.29Oct24.c8e.jar");byte[] changed=Files.readAllBytes(jar);changed[changed.length-1]^=1;Files.write(jar,changed);
        var f=fixture("input","target.vcf");Path out=root.resolve("skipped-changed");var o=options(f,"5.4-29Oct24","impute",true,"allow-rephase",out);o.put("home",home.toString());
        assertEquals(3,run(o).code());assertArrayEquals(changed,Files.readAllBytes(jar));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("runtime changed"));assertFalse(Files.exists(out.resolve("upstream/analysis.stdout")));
    }

    @Test void analysisTimeoutTerminatesTheOwnedProcessAndRetainsActionableDiagnostics()throws Exception {
        requireHome();var f=fixture("input","target.vcf");Path out=root.resolve("timed-out");var o=options(f,"5.4-29Oct24","impute",true,"allow-rephase",out);o.put("iterations","100000");o.put("timeout-seconds","1");
        var r=run(o);assertEquals(2,r.code());assertTrue(r.error().contains("FAILED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("owned process terminated"));assertFalse(Files.exists(out.resolve("genotypes.vcf")));
        Object parsed=new org.yaml.snakeyaml.Yaml().load(Files.readString(out.resolve("session.yaml")));assertInstanceOf(Map.class,parsed);Map<?,?> session=(Map<?,?>)parsed;assertEquals(Boolean.TRUE,session.get("analysis_timed_out"));long pid=((Number)session.get("analysis_owned_pid")).longValue();assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));assertTrue(session.containsKey("analysis_stdout_sha256"));assertTrue(session.containsKey("analysis_exit_code"));
    }

    @Test void incompatibleVersionOptionsAndMissingScientificPoliciesFailBeforeOutput()throws Exception {
        var f=fixture("input","target.vcf");int n=0;
        for(String[] change:List.of(new String[]{"window-unit","markers"},new String[]{"phase-policy","guess"},new String[]{"seed","not-integer"},new String[]{"ne","NaN"},new String[]{"err","1"},new String[]{"method","gl"},new String[]{"impute-default","true"})) {
            var o=options(f,"5.4-29Oct24","impute",true,"allow-rephase",root.resolve("invalid-"+n++));o.put(change[0],change[1]);assertEquals(2,run(o).code());assertFalse(Files.exists(Path.of(o.get("out"))));
        }
        var legacy=options(f,"4.1-08Jun17","impute",true,"preserve-provided",root.resolve("legacy-em"));legacy.put("em","false");assertEquals(2,run(legacy).code());
        var absent=options(f,"5.4-29Oct24","impute",true,"allow-rephase",root.resolve("missing-policy"));absent.remove("target-only-markers");assertEquals(2,run(absent).code());
    }

    @Test void preservePolicyRejectsModernPartialPhaseAndMinimumAppliesAfterExclusion()throws Exception {
        requireHome();var f=fixture("input","target-phased.vcf");Path out=root.resolve("phase-rejected");assertEquals(2,run(options(f,"5.4-29Oct24","impute",true,"preserve-provided",out)).code());
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("allow-rephase"));assertFalse(Files.exists(out.resolve("upstream/analysis.stdout")));
        var counts=options(f,"5.4-29Oct24","impute",true,"allow-rephase",root.resolve("few-retained"));counts.put("minimum-observed-markers","151");assertEquals(2,run(counts).code());assertTrue(Files.readString(Path.of(counts.get("out")).resolve("WARNINGS.md")).contains("after explicit exclusions"));
    }

    @Test void targetOnlyExclusionAndAlleleHarmonizationAreNeverInferred()throws Exception {
        requireHome();var f=fixture("input","target.vcf");var o=options(f,"5.4-29Oct24","impute",true,"allow-rephase",root.resolve("excluded-error"));o.put("target-only-markers","error");assertEquals(2,run(o).code());assertTrue(Files.readString(Path.of(o.get("out")).resolve("WARNINGS.md")).contains("would be excluded"));
        var rows=new ArrayList<>(Files.readAllLines(f.reference()));for(int i=0;i<rows.size();i++)if(!rows.get(i).startsWith("#")){String[] row=rows.get(i).split("\t");row[4]="C";rows.set(i,String.join("\t",row));break;}Files.write(f.reference(),rows);Path out=root.resolve("allele-error");assertEquals(2,run(options(f,"5.4-29Oct24","impute",true,"allow-rephase",out)).code());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("REF/ALT mismatch"));assertFalse(Files.exists(out.resolve("upstream/analysis.stdout")));
    }

    @Test void invalidPloidyReferencePhaseAndMapCoverageStopBeforeInference()throws Exception {
        requireHome();int attempt=0;
        for(String kind:List.of("ploidy","reference-phase","map")) {
            var f=fixture("input-"+kind,"target.vcf");
            if(kind.equals("map"))Files.writeString(f.map(),"1 start 0 50000\n1 end 1 50001\n");
            else{Path path=kind.equals("ploidy")?f.target():f.reference();var lines=new ArrayList<>(Files.readAllLines(path));for(int i=0;i<lines.size();i++)if(!lines.get(i).startsWith("#")){String[] row=lines.get(i).split("\t");row[9]=kind.equals("ploidy")?"0/1/1":"0/1";lines.set(i,String.join("\t",row));break;}Files.write(path,lines);}
            Path out=root.resolve("bad-"+attempt++);assertEquals(2,run(options(f,"5.4-29Oct24","impute",true,"allow-rephase",out)).code());assertFalse(Files.exists(out.resolve("upstream/analysis.stdout")));assertFalse(Files.exists(out.resolve("genotypes.vcf")));
        }
    }

    @Test void nativePosteriorChecksDoNotReplaceGTWithLargestUnphasedProbability()throws Exception {
        var f=fixture("input","target.vcf");Path stage=root.resolve("stage");Files.createDirectory(stage);var p=BeagleInputs.prepare(f.target(),f.reference(),f.map(),stage,"5.4","impute","allow-rephase","drop-with-warning","error",1,new BeagleInputs.Limits(100,1000,100000,1048576,32L*1048576,65536));
        var lines=adaptedReport("5.4-impute.vcf.gz");
        for(int i=0;i<lines.size();i++)if(lines.get(i).startsWith("1\t6000\t")){String[] row=lines.get(i).split("\t");row[7]=".";row[9]="1|1:1.02:0.51:0.51:0.24,0.50,0.26";lines.set(i,String.join("\t",row));break;}
        Path report=root.resolve("synthetic-posterior.vcf"),out=root.resolve("validated");Files.write(report,lines);Files.createDirectory(out);var result=BeagleOutputs.validate(report,p,"5.4","impute","allow-rephase",out,1048576);
        assertTrue(result.strictNonmaxGenotypes()>0);assertEquals("1|1:1.02:0.51:0.51:0.24,0.50,0.26",records(out.resolve("genotypes.vcf")).stream().filter(r->r[1].equals("6000")).findFirst().orElseThrow()[9]);
        var invalid=new ArrayList<>(lines);for(int i=0;i<invalid.size();i++)if(invalid.get(i).startsWith("1\t6000\t")){String[] row=invalid.get(i).split("\t");row[9]="1|1:1.02:0.51:0.51:1,1,1";invalid.set(i,String.join("\t",row));break;}
        Path bad=root.resolve("bad-posterior.vcf"),badOut=root.resolve("bad-output");Files.write(bad,invalid);Files.createDirectory(badOut);assertThrows(IOException.class,()->BeagleOutputs.validate(bad,p,"5.4","impute","allow-rephase",badOut,1048576));
    }
    private static ArrayList<String> adaptedReport(String name)throws IOException {
        try(var input=new GZIPInputStream(new java.io.ByteArrayInputStream(resource(name)))){var lines=new ArrayList<>(new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().toList());for(int i=0;i<lines.size();i++)if(!lines.get(i).startsWith("#")){String[] row=lines.get(i).split("\t");row[2]="V"+row[1];lines.set(i,String.join("\t",row));}return lines;}
    }

    @Test void unicodeRecipesRestoreOpaqueSampleAndVariantIdentifiers()throws Exception {
        requireHome();var f=fixture("输入_α","target.vcf");var lines=new ArrayList<>(Files.readAllLines(f.target()));
        for(int i=0;i<lines.size();i++)if(lines.get(i).startsWith("#CHROM\t")){String[] row=lines.get(i).split("\t");row[9]="家庭_a";row[10]="家庭_a_b";lines.set(i,String.join("\t",row));}else if(lines.get(i).startsWith("1\t1000\t")){String[] row=lines.get(i).split("\t");row[2]="変異_α";lines.set(i,String.join("\t",row));}Files.write(f.target(),lines,StandardCharsets.UTF_8);
        Path out=root.resolve("输出_β"),recipe=root.resolve("unicode-options.json");var o=options(f,"5.4-29Oct24","impute",true,"allow-rephase",out);var fields=new ArrayList<String>();for(var e:o.entrySet())fields.add("  \""+e.getKey()+"\": \""+e.getValue().replace("\\","\\\\").replace("\"","\\\"")+"\"");Files.writeString(recipe,"{\n"+String.join(",\n",fields)+"\n}\n",StandardCharsets.UTF_8);
        success(direct("--options-file",recipe.toString()),out);String header=Files.readString(out.resolve("genotypes.vcf")).lines().filter(s->s.startsWith("#CHROM")).findFirst().orElseThrow();assertTrue(header.contains("家庭_a\t家庭_a_b"));assertEquals("変異_α",records(out.resolve("genotypes.vcf")).get(0)[2]);
    }

    @Test void existingOutputAndRuntimeOverlapArePreserved()throws Exception {
        var f=fixture("input","target.vcf");Path out=root.resolve("existing");Files.createDirectory(out);Files.writeString(out.resolve("sentinel"),"retain");assertEquals(2,run(options(f,"5.4-29Oct24","impute",true,"allow-rephase",out)).code());assertEquals("retain",Files.readString(out.resolve("sentinel")));
        var o=options(f,"5.4-29Oct24","impute",true,"allow-rephase",root.resolve("runtime/inside"));o.put("home",root.resolve("runtime").toString());assertEquals(2,run(o).code());assertFalse(Files.exists(root.resolve("runtime")));assertEquals(0,direct("--help").code());
    }
}
