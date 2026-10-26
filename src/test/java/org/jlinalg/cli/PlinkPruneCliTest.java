package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class PlinkPruneCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path root;
    private record Fixture(Path prefix,Path samples) { }
    private record Run(int code,String output,String error) { }
    private static String resource(String name)throws Exception{try(var stream=PlinkPruneCliTest.class.getResourceAsStream("/org/jlinalg/genetics/plink-pruning/"+name)){assertNotNull(stream);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}}
    private Fixture fixture(String folder)throws Exception{Path directory=Files.createDirectory(root.resolve(folder));for(String name:List.of("known.bed","known.bim","known.fam","samples.tsv"))try(var stream=PlinkPruneCliTest.class.getResourceAsStream("/org/jlinalg/genetics/plink-pruning/"+name)){assertNotNull(stream);Files.copy(stream,directory.resolve(name));}return new Fixture(directory.resolve("known"),directory.resolve("samples.tsv"));}
    private static void requireHome(){Assumptions.assumeTrue(System.getenv("JLINALG_PLINK_HOME")!=null,"Set the verified original private runtime home for actual binary tests");}
    private Map<String,String> options(Fixture fixture,String engine,Path out){var values=new LinkedHashMap<String,String>();values.put("task","prune");values.put("engine",engine);values.put("home",System.getenv().getOrDefault("JLINALG_PLINK_HOME","not-configured"));values.put("bfile",fixture.prefix().toString());values.put("samples",fixture.samples().toString());values.put("out",out.toString());values.put("genome-build","unspecified");values.put("chromosome-scope","autosomes");values.put("unresolved-alleles","error");values.put("reference-population","founders");values.put("window-unit","variants");values.put("window-size","20");values.put("step-size","5");values.put("r2",".2");values.put("pair-order","legacy");values.put("missing-genotypes","upstream");values.put("minimum-reference-calls","3");return values;}
    private static Run run(Map<String,String> values){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=PlinkPruneCli.run(new PlinkToolsCli.Parsed(values,null),new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));}
    private static Run publicRun(String... args){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));}
    private static List<Map<String,String>> table(Path path)throws Exception{var lines=Files.readAllLines(path);String[] names=lines.get(0).split("\t",-1);var result=new ArrayList<Map<String,String>>();for(String line:lines.subList(1,lines.size())){String[] values=line.split("\t",-1);assertEquals(names.length,values.length,line);var row=new LinkedHashMap<String,String>();for(int i=0;i<names.length;i++)row.put(names[i],values[i]);result.add(row);}return result;}
    private static void success(Run run,Path out)throws Exception{assertEquals(0,run.code(),run.error()+"\n"+(Files.exists(out.resolve("WARNINGS.md"))?Files.readString(out.resolve("WARNINGS.md")):""));assertTrue(run.error().contains("WARNINGS"));assertTrue(Files.exists(out.resolve("prune.in")));assertTrue(Files.exists(out.resolve("prune.out")));assertFalse(Files.exists(out.resolve("genotypes.bed")));assertFalse(Files.exists(out.resolve("partial")));}

    @Test void allSixActualOriginalPartitionsAndExplicitOrdersArePreserved()throws Exception{
        requireHome();var input=fixture("input");String[] lines=resource("cases.tsv").lines().toArray(String[]::new);
        for(int i=1;i<lines.length;i++){String[] row=lines[i].split("\t");Path out=root.resolve(row[0]);var values=options(input,row[1],out);values.put("window-unit",row[2]);values.put("window-size",row[3]);values.put("step-size",row[4]);values.put("pair-order",row[5]);success(run(values),out);assertEquals(Set.copyOf(resource(row[8]).lines().toList()),Set.copyOf(Files.readAllLines(out.resolve("prune.in"))));assertEquals(Set.copyOf(resource(row[9]).lines().toList()),Set.copyOf(Files.readAllLines(out.resolve("prune.out"))));assertEquals(Integer.parseInt(row[6]),Files.readAllLines(out.resolve("prune.in")).size());assertEquals(Integer.parseInt(row[7]),Files.readAllLines(out.resolve("prune.out")).size());var samples=table(out.resolve("sample-reference.tsv"));assertEquals(128,samples.size());assertEquals(123,samples.stream().filter(r->r.get("used_for_reference").equals("true")).count());assertTrue(samples.stream().anyMatch(r->r.get("used_for_reference").equals("true")&&r.get("group_unused").equals("case")));assertEquals(91,table(out.resolve("variant-reference.tsv")).size());assertEquals(91,table(out.resolve("variant-decisions.tsv")).size());}
    }
    @Test void unavailableDependencySkipsBeforeScientificInputRead()throws Exception{
        var input=fixture("input");Path out=root.resolve("skip");var values=options(input,"plink2",out);values.put("home",root.resolve("missing").toString());values.put("bfile","not-read");var result=run(values);assertEquals(3,result.code());assertTrue(result.error().contains("SKIPPED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("--allow-install"));assertFalse(Files.exists(out.resolve("upstream/input.bed")));
    }
    @Test void unsupportedOrAmbiguousPoliciesFailBeforeCreatingOutput()throws Exception{
        var input=fixture("input");int index=0;for(Map<String,String> change:List.of(Map.of("r2","NaN"),Map.of("r2","0"),Map.of("r2","1"),Map.of("window-size","1"),Map.of("minimum-reference-calls","2"),Map.of("window-unit","kb","step-size","5"),Map.of("reference-population","all"),Map.of("hwe-mode","off"))){Path out=root.resolve("invalid"+(index++));var values=options(input,"plink2",out);values.putAll(change);assertEquals(2,run(values).code());assertFalse(Files.exists(out));}Path out=root.resolve("plink1-backward");var values=options(input,"plink1",out);values.put("pair-order","backward");assertEquals(2,run(values).code());assertFalse(Files.exists(out));assertTrue(PlinkPruneCli.help().contains("not a guarantee"));
    }
    @Test void missingCallsAreProhibitedOnlyByAnExplicitPolicy()throws Exception{
        requireHome();var input=fixture("input");Path out=root.resolve("missing-error");var values=options(input,"plink2",out);values.put("missing-genotypes","error");assertEquals(2,run(values).code());assertFalse(Files.exists(out.resolve("upstream/prune.stdout")));assertFalse(Files.exists(out.resolve("prune.in")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("No marker was silently removed"));assertTrue(table(out.resolve("variant-reference.tsv")).stream().anyMatch(r->r.get("status").equals("MISSING_CALLS_PROHIBITED")));
    }
    @Test void noCallsAndNonvariableDosageFailWithoutImplicitVariantRemoval()throws Exception{
        requireHome();for(int code:List.of(0x55,0xff)){var input=fixture("input"+code);Path bed=Path.of(input.prefix()+".bed");byte[] data=Files.readAllBytes(bed);java.util.Arrays.fill(data,3,35,(byte)code);Files.write(bed,data);Path out=root.resolve("failed"+code);assertEquals(2,run(options(input,"plink2",out)).code());assertFalse(Files.exists(out.resolve("upstream/prune.stdout")));assertFalse(Files.exists(out.resolve("prune.in")));var first=table(out.resolve("variant-reference.tsv")).get(0);assertEquals(code==0x55?"INSUFFICIENT_CALLS":"NONVARIABLE_DOSAGE",first.get("status"));if(code==0x55){assertEquals("",first.get("minimum_dosage"));assertEquals("",first.get("maximum_dosage"));}}
    }
    @Test void sparseFounderReferenceCannotSilentlyEnableBadLdOverride()throws Exception{
        requireHome();var input=fixture("input");var lines=Files.readAllLines(input.samples());for(int i=31;i<lines.size();i++){String[] row=lines.get(i).split("\t");row[2]="no";lines.set(i,String.join("\t",row));}Files.write(input.samples(),lines);Path out=root.resolve("small");assertEquals(2,run(options(input,"plink2",out)).code());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("no --bad-ld override"));assertFalse(Files.exists(out.resolve("upstream/prune.stdout")));
    }
    @Test void zeroOrUnsortedPositionsFailWithoutReorderingInput()throws Exception{
        requireHome();int index=0;for(String position:List.of("0","7000")){var input=fixture("input"+(index++));Path bim=Path.of(input.prefix()+".bim");var lines=Files.readAllLines(bim);String[] first=lines.get(0).split("\\s+");first[3]=position;lines.set(0,String.join(" ",first));Files.write(bim,lines);String before=Files.readString(bim);Path out=root.resolve("bad-position"+index);assertEquals(2,run(options(input,"plink2",out)).code());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("positive, sorted"));assertEquals(before,Files.readString(bim));assertFalse(Files.exists(out.resolve("upstream/prune.stdout")));}
    }
    @Test void exactUnicodeAndCompositeSampleIdentitiesDoNotChangeThePrunedFamily()throws Exception{
        requireHome();var input=fixture("输入");Path fam=Path.of(input.prefix()+".fam");var families=Files.readAllLines(fam);var samples=Files.readAllLines(input.samples());String[][] pairs={{"A_B","C"},{"A","B_C"},{"家族","成员"}};for(int i=0;i<3;i++){int ordinal=i+2;String[] f=families.get(ordinal).split("\\s+");f[0]=pairs[i][0];f[1]=pairs[i][1];families.set(ordinal,String.join(" ",f));String[] row=samples.get(ordinal+1).split("\t");row[0]=pairs[i][0];row[1]=pairs[i][1];samples.set(ordinal+1,String.join("\t",row));}Files.write(fam,families);Files.write(input.samples(),samples);Path out=root.resolve("输出");success(run(options(input,"plink2",out)),out);assertEquals(Set.copyOf(resource("plink2-count-legacy.in").lines().toList()),Set.copyOf(Files.readAllLines(out.resolve("prune.in"))));var rows=table(out.resolve("sample-reference.tsv"));for(String[] pair:pairs)assertTrue(rows.stream().anyMatch(r->r.get("fid").equals(pair[0])&&r.get("iid").equals(pair[1])&&r.get("used_for_reference").equals("true")));
    }
    @Test void publicCommandDispatchesPruningAndRejectsOtherTaskOptions()throws Exception{
        assertEquals(0,publicRun("plink-tools","--task","prune","--help").code());assertTrue(publicRun("plink-tools","--help").output().contains("--window-unit"));assertEquals(2,publicRun("plink-tools","--task","qc","--window-size","20").code());assertEquals(2,publicRun("plink-tools","--task","prune","--hwe-mode","off").code());
        requireHome();var input=fixture("public-input");Path out=root.resolve("public-prune");var args=new ArrayList<String>();args.add("plink-tools");for(var entry:options(input,"plink2",out).entrySet()){args.add("--"+entry.getKey());args.add(entry.getValue());}success(publicRun(args.toArray(String[]::new)),out);assertEquals(Set.copyOf(resource("plink2-count-legacy.in").lines().toList()),Set.copyOf(Files.readAllLines(out.resolve("prune.in"))));
    }
}
