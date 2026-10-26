/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jlinalg.genetics.PrsCsMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrsCsOutputsTest {
    @TempDir Path root;
    private static byte[] resource(String name)throws IOException {
        try(var stream=PrsCsOutputsTest.class.getResourceAsStream("/org/jlinalg/genetics/prscs-reference/"+name)){assertNotNull(stream);return stream.readAllBytes();}
    }
    private static List<?> cases()throws Exception {
        Object value=SimpleJson.parse(new String(resource("posterior-provenance.json"),StandardCharsets.UTF_8));assertInstanceOf(Map.class,value);Object cases=((Map<?,?>)value).get("cases");assertInstanceOf(List.class,cases);return (List<?>)cases;
    }
    private static Map<?,?> item(String label)throws Exception {
        for(Object value:cases()){Map<?,?> row=(Map<?,?>)value;if(label.equals(row.get("label")))return row;}throw new IOException("Missing original fixture case");
    }
    private PrsCsInputs.Prepared prepare(Path data,String format)throws Exception {
        Path stage=Files.createTempDirectory(root,"stage-");return PrsCsInputs.prepare(new PrsCsInputs.Request(data.resolve(format+".txt"),data.resolve("target.bim"),data.resolve("ldblk_1kg_eur"),"1kg",List.of(1,22),format,20000,"exclude-with-warning","allow-upstream-with-warning","allow-complement-with-warning","require-match",PrsCsMath.PValuePolicy.ORIGINAL_FLOOR_WITH_WARNING),new PrsCsInputs.Limits(100,1000,1048576,1048576,4096,10000,100,100,1048576,10000),stage);
    }
    private Path original(Map<?,?> item,Path out,String name)throws Exception {
        Path directory=out.resolve(name);Files.createDirectory(directory);Map<?,?> files=(Map<?,?>)item.get("files_sha256");
        for(var e:files.entrySet()) {byte[] raw=resource("original-outputs/"+item.get("label")+"/"+e.getKey());String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));assertEquals(e.getValue(),hash);Files.write(directory.resolve(e.getKey().toString()),raw);}
        return directory;
    }
    private static Path effect(Path directory)throws IOException {try(var files=Files.list(directory)){return files.filter(p->p.getFileName().toString().contains("_eff_")&&p.getFileName().toString().endsWith("chr1.txt")).findFirst().orElseThrow();}}

    @Test void everyPublishedSyntheticFixtureMemberMatchesItsOwnFrozenChecksum()throws Exception {
        int count=0;for(String line:new String(resource("SHA256SUMS"),StandardCharsets.UTF_8).lines().toList()) {
            String[] row=line.split("  ",2);assertEquals(2,row.length);assertEquals(row[0],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(row[1]))),row[1]);count++;
        }
        assertEquals(219,count);
    }

    @Test void allFiftySixOriginalOutputsPreserveEveryPrintedPosteriorAndCorrectVariantFamily()throws Exception {
        Path data=PrsCsInputsTest.fixture(root);var prepared=new LinkedHashMap<String,PrsCsInputs.Prepared>();for(String f:List.of("beta-se","or-se","beta-p","or-p"))prepared.put(f,prepare(data,f));
        long scalars=0,psi=0;assertEquals(56,cases().size());
        for(Object value:cases()) {
            Map<?,?> item=(Map<?,?>)value;Path out=Files.createTempDirectory(root,"validated-");boolean draws=item.get("output").equals("draws"),withPsi=!item.get("profile").equals("1.0.0");Path upstream=original(item,out,"original");
            var result=PrsCsOutputs.validate(prepared.get(item.get("format")),draws?null:upstream,draws?upstream:null,withPsi,4,Boolean.TRUE.equals(item.get("standardized"))?"standardized":"per-allele",!draws,out,1048576);
            assertEquals(20,result.variants());assertEquals(draws?80:20,result.posteriorScalars());assertEquals(withPsi?20:0,result.psiFields());assertEquals(0,result.meanDrawChecks());scalars+=result.posteriorScalars();psi+=result.psiFields();
            var source=Files.readAllLines(effect(upstream));var canonical=Files.readAllLines(out.resolve(draws?"posterior-draws.tsv":"posterior-means.tsv"));
            for(int i=0;i<source.size();i++){String[] a=source.get(i).split("\t"),b=canonical.get(i+1).split("\t");assertArrayEquals(java.util.Arrays.copyOfRange(a,5,a.length),java.util.Arrays.copyOfRange(b,7,b.length));}
            PrsCsOutputs.verify(result,out);
        }
        assertEquals(2080,scalars);assertEquals(960,psi);
    }

    @Test void sixteenSameSeedMeanAndDrawPairsPassIndependentPrintingIntervalsAndScoreConversion()throws Exception {
        Path data=PrsCsInputsTest.fixture(root);var prepared=new LinkedHashMap<String,PrsCsInputs.Prepared>();for(String f:List.of("beta-se","or-se","beta-p","or-p"))prepared.put(f,prepare(data,f));int count=0;
        for(Object value:cases()) {
            Map<?,?> item=(Map<?,?>)value;if(!item.get("profile").equals("source-fbc9ec0")||!item.get("output").equals("mean"))continue;
            Path out=Files.createTempDirectory(root,"paired-");Path mean=original(item,out,"mean"),draw=original(item(item.get("label").toString().replace("-mean","-draws")),out,"draws");boolean standardized=Boolean.TRUE.equals(item.get("standardized"));
            var input=prepared.get(item.get("format"));var result=PrsCsOutputs.validate(input,mean,draw,true,4,standardized?"standardized":"per-allele",true,out,1048576);
            assertEquals(100,result.posteriorScalars());assertEquals(20,result.meanDrawChecks());var weights=Files.readAllLines(out.resolve("score-weights.tsv"));var means=Files.readAllLines(out.resolve("posterior-means.tsv"));
            assertEquals("variant\tea\toa\tweight\tea_frequency",weights.get(0));assertEquals(21,weights.size());
            for(int i=1;i<weights.size();i++){String[] w=weights.get(i).split("\t"),m=means.get(i).split("\t");var v=input.byId().get(w[0]);double expected=standardized?PrsCsMath.perAllele(Double.parseDouble(m[7]),v.frequency()):Double.parseDouble(m[7]);assertEquals(expected,Double.parseDouble(w[3]));assertEquals(v.frequency(),Double.parseDouble(w[4]));}
            count++;
        }
        assertEquals(16,count);
    }

    @Test void wrongIdentityExtraRowsNonfiniteValuesWidthAndUnrequestedOutputsAreRejected()throws Exception {
        Path data=PrsCsInputsTest.fixture(root);var prepared=prepare(data,"beta-se");var fixture=item("source-fbc9ec0-beta-se-fixed-allele-mean");
        for(String mutation:List.of("identity","allele","position","nonfinite","width","extra-row","extra-file")) {
            Path out=Files.createTempDirectory(root,"bad-");Path mean=original(fixture,out,"mean"),file=effect(mean);var lines=Files.readAllLines(file);String[] row=lines.get(0).split("\t");
            switch(mutation) {
                case "identity"->row[1]="UNKNOWN";case "allele"->row[3]="T";case "position"->row[2]="999";case "nonfinite"->row[5]="NaN";
                case "width"->row=java.util.Arrays.copyOf(row,5);case "extra-row"->lines.add(lines.get(0));case "extra-file"->Files.writeString(mean.resolve("result_pst_unknown.txt"),"unexpected\n");default->throw new AssertionError();
            }
            lines.set(0,String.join("\t",row));Files.write(file,lines);
            assertThrows(IOException.class,()->PrsCsOutputs.validate(prepared,mean,null,true,4,"per-allele",false,out,1048576),mutation);
        }
    }

    @Test void sameSeedDrawMeanDisagreementIsNotHiddenByRoundingTolerance()throws Exception {
        Path data=PrsCsInputsTest.fixture(root);var input=prepare(data,"beta-se");Path out=root.resolve("out");Files.createDirectory(out);
        Path mean=original(item("source-fbc9ec0-beta-se-fixed-allele-mean"),out,"mean"),draw=original(item("source-fbc9ec0-beta-se-fixed-allele-draws"),out,"draw");Path file=effect(draw);var lines=Files.readAllLines(file);String[] row=lines.get(0).split("\t");row[5]=String.format(Locale.ROOT,"%.6e",Double.parseDouble(row[5])+.1);lines.set(0,String.join("\t",row));Files.write(file,lines);
        assertThrows(IOException.class,()->PrsCsOutputs.validate(input,mean,draw,true,4,"per-allele",false,out,1048576));
    }

    @Test void boundedCanonicalWritesAndChangedOriginalsAreExplicitFailures()throws Exception {
        Path data=PrsCsInputsTest.fixture(root);var input=prepare(data,"beta-se");Path out=root.resolve("out");Files.createDirectory(out);Path mean=original(item("1.1.0-beta-se-fixed-std-mean"),out,"mean");
        assertThrows(IOException.class,()->PrsCsOutputs.validate(input,mean,null,true,4,"standardized",false,out,5));
        var result=PrsCsOutputs.validate(input,mean,null,true,4,"standardized",false,out,1048576);Files.writeString(effect(mean),"changed\n");assertThrows(IOException.class,()->PrsCsOutputs.verify(result,out));
    }

    @Test void onlyTheKnownExactDeprecationWarningAndItsSourceLineAreAdmitted()throws Exception {
        Path stderr=root.resolve("stderr");String known="X:/sealed/sources/source-fbc9ec0/gigrnd.py:38: DeprecationWarning: Conversion of an array with ndim > 0 to a scalar is deprecated, and will error in future. Ensure you extract a single element from your array before performing this operation. (Deprecated NumPy 1.25.)\n  p = float(p); a = float(a); b = float(b)\n";
        Files.writeString(stderr,known);assertEquals(1,PrsCsOutputs.warnings(stderr,4096,false).numpyArrayToScalar());assertThrows(IOException.class,()->PrsCsOutputs.warnings(stderr,4096,true));
        for(String bad:List.of("RuntimeWarning: invalid value\n",known+"unexpected diagnostic\n",known+"  RuntimeWarning: invalid value\n",known.replace("NumPy 1.25","NumPy 9.99"))) {Files.writeString(stderr,bad);assertThrows(IOException.class,()->PrsCsOutputs.warnings(stderr,4096,false));}
        for(String function:List.of("array","diag","dot","ix_","log","ones","outer","sign","sqrt","zeros")) {
            String replacement=(java.util.Set.of("sqrt","log").contains(function)?"numpy.lib.scimath.":"numpy.")+function;
            String legacy="X:/sealed/sources/1.1.0/parse_genet.py:86: DeprecationWarning: scipy."+function+" is deprecated and will be removed in SciPy 2.0.0, use "+replacement+" instead\n  n = original_call()\n";
            Files.writeString(stderr,legacy);assertEquals(1,PrsCsOutputs.warnings(stderr,4096,true).scipyAliases());assertThrows(IOException.class,()->PrsCsOutputs.warnings(stderr,4096,false));
            Files.writeString(stderr,legacy.replace("SciPy 2.0.0","SciPy 9.9.9"));assertThrows(IOException.class,()->PrsCsOutputs.warnings(stderr,4096,true));
        }
        Files.writeString(stderr,"");assertEquals(0,PrsCsOutputs.warnings(stderr,4096,false).numpyArrayToScalar());
    }
}
