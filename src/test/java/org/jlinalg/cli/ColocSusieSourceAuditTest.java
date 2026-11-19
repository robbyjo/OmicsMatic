/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.coloc.*;
import org.junit.jupiter.api.*;

/** Source comparison distinguishes scientific variants and inadmissible original outputs. */
class ColocSusieSourceAuditTest {
    static Path root,fixture,nativeResults;
    static final Set<String> MATCH=Set.of("ordinary-single","ordinary-multiple","partial-permuted","overlap-partial-drop","overlap-boundary-one","high-priors","unicode-order","bundled-D1-D2","bundled-D3-D4","original-fitted-D1-D2","original-fitted-D3-D4","shared-prior-above-trait");
    static final Set<String> WEIGHTED=Set.of("one-sided-weight","both-weighted","small-weights","weighted-partial");
    static final Set<String> REFUSED=Set.of("explicit-null-last","nonzero-null-baseline","null-not-last","only-null","zero-weight");
    @BeforeAll static void prepare()throws Exception {
        Path parent=Path.of(System.getProperty("jlinalg.test.artifacts","build/coloc-susie-source-tests")).toAbsolutePath();Files.createDirectories(parent);int n=1;
        while(Files.exists(parent.resolve("attempt-"+n)))n++;root=parent.resolve("attempt-"+n);Files.createDirectory(root);fixture=root.resolve("fixture");Files.createDirectory(fixture);
        byte[] raw;
        try(var in=ColocSusieSourceAuditTest.class.getResourceAsStream("/org/jlinalg/coloc/susie-original603-audit.zip")){assertNotNull(in);raw=in.readAllBytes();}
        assertEquals("80295995ab91e2625b7cafc6246c583cbdf95925451f7f7a519c1411287c1d1f",ColocAbfFiles.sha(raw));int count=0;long bytes=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                assertFalse(entry.isDirectory());Path target=fixture.resolve(entry.getName()).normalize();assertTrue(target.startsWith(fixture));byte[] content=zip.readAllBytes();bytes+=content.length;assertTrue(bytes<=64_000_000);assertTrue(content.length<=16_000_000);
                Files.createDirectories(target.getParent());Files.write(target,content,StandardOpenOption.CREATE_NEW);count++;
            }
        }
        assertEquals(266,count);nativeResults=root.resolve("native");ColocSusieSourceReferenceProbe.main(new String[]{fixture.resolve("inputs").toString(),nativeResults.toString()});
    }
    private static List<String[]> table(Path p)throws Exception {
        var rows=new ArrayList<String[]>();int n=0;for(String line:Files.readAllLines(p,StandardCharsets.UTF_8))rows.add(DelimitedData.parse(line,'\t',++n,p).toArray(String[]::new));return rows;
    }
    private static boolean close(double a,double b){return a==b||Double.isFinite(a)&&Double.isFinite(b)&&a!=0&&b!=0&&Math.abs(a-b)<=Math.max(16*Math.ulp(a),2e-12*Math.abs(a));}
    private static void compare(Path original,Path actual,boolean numericOnly)throws Exception {
        var a=table(original);var b=table(actual);assertArrayEquals(a.get(0),b.get(0));assertEquals(a.size(),b.size());
        for(int i=1;i<a.size();i++)for(int j=0;j<a.get(0).length;j++){
            String header=a.get(0)[j];if(Set.of("snp","nsnps","idx1","idx2","hit1","hit2").contains(header)){
                if(!numericOnly)assertEquals(a.get(i)[j],b.get(i)[j]);
            }else {double x=Double.parseDouble(a.get(i)[j]),y=Double.parseDouble(b.get(i)[j]);assertTrue(close(x,y),original+" "+header+" "+x+" != "+y);}
        }
    }
    private static double[] hypotheses(Path path)throws Exception {
        var rows=table(path);var h=new double[5];for(int i=0;i<5;i++)h[i]=Double.parseDouble(rows.get(1)[Arrays.asList(rows.get(0)).indexOf("PP.H"+i+".abf")]);return h;
    }
    @TestFactory Stream<DynamicTest> twentyEightOriginalPackageProfilesAreClassifiedExplicitly()throws Exception {
        var names=Files.readAllLines(fixture.resolve("inputs/cases.tsv")).subList(1,29);assertEquals(28,names.size());
        return names.stream().map(name->DynamicTest.dynamicTest(name,()->{
            Path source=fixture.resolve("original/"+name),actual=nativeResults.resolve(name);int status=Integer.parseInt(Files.readString(actual.resolve("STATUS.txt")).strip());
            if(REFUSED.contains(name)){
                assertEquals(2,status);assertFalse(Files.exists(actual.resolve("pairs.tsv")));String error=Files.readString(actual.resolve("ERROR.txt"));
                if(!name.equals("zero-weight"))assertTrue(error.contains("reserved null hypothesis column"));return;
            }
            assertEquals(0,status);String warnings=Files.readString(actual.resolve("warnings.txt"));assertTrue(warnings.contains("NATIVE_STABLE_COLOC_VARIANT"));
            if(MATCH.contains(name)){
                assertEquals("FINITE",Files.readString(source.resolve("STATUS.txt")).strip());compare(source.resolve("pairs.tsv"),actual.resolve("pairs.tsv"),false);compare(source.resolve("variants.tsv"),actual.resolve("variants.tsv"),false);
            }else if(WEIGHTED.contains(name)){
                compare(source.resolve("pairs.tsv"),actual.resolve("pairs.tsv"),false);assertTrue(warnings.contains("PRIOR_WEIGHTED_CONDITIONAL_SNP_POSTERIOR"));
                var r=table(source.resolve("variants.tsv"));var a=table(actual.resolve("variants.tsv"));assertEquals(r.size(),a.size());boolean different=false;
                for(int i=1;i<r.size();i++){assertEquals(r.get(i)[0],a.get(i)[0]);different|=!close(Double.parseDouble(r.get(i)[1]),Double.parseDouble(a.get(i)[1]));}assertTrue(different);
            }else if(name.equals("dominant-shared")){
                double[] r=hypotheses(source.resolve("pairs.tsv")),a=hypotheses(actual.resolve("pairs.tsv"));assertEquals(0,r[3]);assertTrue(a[3]>0);
                double ratio=.004*Math.exp(-50)/(1+Math.exp(-100));assertEquals(ratio,a[3]/a[4],ratio*2e-12);
            }else if(name.equals("single-variant")){
                assertTrue(Arrays.stream(hypotheses(source.resolve("pairs.tsv"))).sum()>2);assertEquals(1,Arrays.stream(hypotheses(actual.resolve("pairs.tsv"))).sum(),2e-15);assertTrue(warnings.contains("SINGLE_VARIANT_NATIVE_MODEL"));
            }else{
                assertTrue(Set.of("disjoint-shared-support","large-weights","no-common","no-common-signal-support","overlap-all-drop").contains(name));
                assertNotEquals("FINITE",Files.readString(source.resolve("STATUS.txt")).strip());
                assertTrue(warnings.contains(name.equals("large-weights")?"PRIOR_WEIGHTED_CONDITIONAL_SNP_POSTERIOR":name.equals("no-common")||name.equals("overlap-all-drop")?"NO_RETAINED_SIGNAL_PAIRS":"UNDEFINED_CONDITIONAL_SNP_POSTERIOR"));
            }
            var rows=table(actual.resolve("pairs.tsv"));
            for(int i=1;i<rows.size();i++){
                double sum=0;for(int h=0;h<5;h++){double p=Double.parseDouble(rows.get(i)[Arrays.asList(rows.get(0)).indexOf("PP.H"+h+".abf")]);assertTrue(Double.isFinite(p)&&p>=0&&p<=1);sum+=p;}assertEquals(1,sum,3e-14);
            }
        }));
    }
    @Test void completeOverlapAtOneKeepsAllSignalPairsAndOriginalOrder(){
        var a=new ColocSusieInput(List.of("a","b","c"),new double[][]{{2,-1,.5},{.2,4,1}});
        var b=new ColocSusieInput(List.of("c","a","b"),new double[][]{{-.5,1,.2},{0,2,1}});
        var result=ColocSusie.analyze(a,b,new ColocOptions(1e-4,1e-4,5e-6,1,true,null,null));assertEquals(4,result.signalPairs().size());assertEquals(0,result.skippedSignalPairs());assertEquals(List.of("a","b","c"),result.commonVariants());
    }
    @Test void largerSharedPriorIsAcceptedWithoutChangingDefault(){
        var input=new ColocSusieInput(List.of("a","b"),new double[][]{{1,2}});var options=new ColocOptions(1e-4,1e-4,.003,.5,true,null,null);
        assertEquals(.003,ColocSusie.analyze(input,input,options).options().sharedPrior());assertEquals(5e-6,ColocOptions.defaults().sharedPrior());
    }
    @Test void nullAndMalformedUnicodeAreRejectedClearlyWithoutIdentifierRepair(){
        for(var ids:List.of(Arrays.asList("a",null),List.of("a","\ud800"),List.of("a","null")))assertThrows(IllegalArgumentException.class,()->new ColocSusieInput(ids,new double[][]{{1,2}}));
        var input=new ColocSusieInput(List.of(" a ","α😀"),new double[][]{{1,2}});assertEquals(List.of(" a ","α😀"),input.variantNames());
    }
    @Test void undefinedConditionalHasExplicitMarkerAndImmutableWarnings(){
        var a=new ColocSusieInput(List.of("a","b"),new double[][]{{100,Double.NEGATIVE_INFINITY}});var b=new ColocSusieInput(List.of("a","b"),new double[][]{{Double.NEGATIVE_INFINITY,100}});
        var result=ColocSusie.analyze(a,b,new ColocOptions(1e-4,1e-4,5e-6,.5,false,null,null));assertFalse(result.isSharedVariantPosteriorDefined(0));assertTrue(result.warnings().stream().anyMatch(w->w.startsWith("UNDEFINED_CONDITIONAL_SNP_POSTERIOR")));assertArrayEquals(new double[2],result.sharedVariantPosterior(0));
        assertThrows(UnsupportedOperationException.class,()->result.warnings().clear());assertThrows(IndexOutOfBoundsException.class,()->result.isSharedVariantPosteriorDefined(1));
    }
    private static int cli(Path p,String label,String...args)throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));Files.write(p.resolve(label+".stdout"),out.toByteArray());Files.write(p.resolve(label+".stderr"),err.toByteArray());return code;
    }
    @Test void cliPrintsAndLogsNativeMethodWarningsAndRetainsBoundaryPair()throws Exception {
        Path p=root.resolve("cli-boundary");Files.createDirectory(p);
        for(int t=1;t<=2;t++)Files.writeString(p.resolve("trait"+t+".tsv"),"effect_index\tvariant_id\tlog_bayes_factor\n1\ta\t2\n1\tb\t-1\n1\tc\t0.5\n");
        Path target=p.resolve("pairs.tsv");assertEquals(0,cli(p,"run","coloc","--trait1",p.resolve("trait1.tsv").toString(),"--trait2",p.resolve("trait2.tsv").toString(),"--minimum-overlap","1","--out",target.toString()));
        assertEquals(2,Files.readAllLines(target).size());assertTrue(Files.readString(p.resolve("run.stderr")).contains("NATIVE_STABLE_COLOC_VARIANT"));assertTrue(Files.readString(Path.of(target+".log")).contains("method=native-stable-coloc-variant"));
    }
    @Test void cliDoesNotAdmitUndefinedConditionalAsZeroProbability()throws Exception {
        Path p=root.resolve("cli-undefined");Files.createDirectory(p);
        Files.writeString(p.resolve("first.tsv"),"effect_index\tvariant_id\tlog_bayes_factor\n1\ta\t100\n1\tb\t-Infinity\n");Files.writeString(p.resolve("second.tsv"),"effect_index\tvariant_id\tlog_bayes_factor\n1\ta\t-Infinity\n1\tb\t100\n");
        Path target=p.resolve("pairs.tsv");assertEquals(2,cli(p,"run","coloc","--trait1",p.resolve("first.tsv").toString(),"--trait2",p.resolve("second.tsv").toString(),"--no-trim","--out",target.toString()));assertFalse(Files.exists(target));assertFalse(Files.exists(Path.of(target+".variants.tsv")));assertTrue(Files.readString(p.resolve("run.stderr")).contains("undefined"));
    }
    @Test void colocTransportKeepsWhitespaceDistinctFromAnUnspacedIdentifier()throws Exception {
        Path p=root.resolve("cli-exact-coloc-ids");Files.createDirectory(p);
        for(int t=1;t<=2;t++)Files.writeString(p.resolve("trait"+t+".tsv"),"effect_index\tvariant_id\tlog_bayes_factor\n1\t a \t2\n1\ta\t-1\n1\tα😀\t0.5\n");
        Path target=p.resolve("pairs.tsv");assertEquals(0,cli(p,"run","coloc","--trait1",p.resolve("trait1.tsv").toString(),"--trait2",p.resolve("trait2.tsv").toString(),"--out",target.toString()));
        String variants=Files.readString(Path.of(target+".variants.tsv"));assertTrue(variants.contains("\t a \t"));assertTrue(variants.contains("\ta\t"));assertTrue(variants.contains("\tα😀\t"));
    }
    @Test void susieSummaryAndLabeledLdKeepTheSameExactIds()throws Exception {
        Path p=root.resolve("cli-exact-susie-ids");Files.createDirectory(p);Path summary=p.resolve("summary.tsv"),ld=p.resolve("ld.tsv"),target=p.resolve("susie.tsv");
        Files.writeString(summary,"SNP\tbeta\tse\n a \t1.2\t0.1\na\t0.02\t0.1\nα😀\t-0.01\t0.1\n");
        Files.writeString(ld,"variant_id\t a \ta\tα😀\n a \t1\t0\t0\na\t0\t1\t0\nα😀\t0\t0\t1\n");
        assertEquals(0,cli(p,"run","susie","--summary",summary.toString(),"--ld",ld.toString(),"--sample-size","1000","--effects","1","--backend","cpu","--out",target.toString()));
        var rows=DelimitedData.read(target).rows();assertEquals(List.of(" a ","a","α😀"),rows.stream().map(r->r[0]).toList());
    }
}
