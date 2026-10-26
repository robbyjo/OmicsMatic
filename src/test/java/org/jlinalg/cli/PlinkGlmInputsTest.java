package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkGlmInputsTest {
    @TempDir Path root;
    record Fixture(Path prefix,Path cohort,Map<?,?> design) { }
    static List<?> list(Map<?,?> map,String name) { return (List<?>)map.get(name); }
    static double number(List<?> list,int i) { return ((Number)list.get(i)).doubleValue(); }
    static String resource(String name) throws Exception {
        try (var raw=PlinkGlmInputsTest.class.getResourceAsStream("/org/jlinalg/genetics/plink-associations/"+name)) {
            assertNotNull(raw);try (var zipped=new GZIPInputStream(raw)) { return new String(zipped.readAllBytes(),StandardCharsets.UTF_8); }
        }
    }
    static Fixture fixture(Path home,String model,String coding,boolean separated) throws Exception {
        Files.createDirectory(home);Path prefix=home.resolve("known"),cohort=home.resolve("cohort.tsv");
        Map<?,?> design=(Map<?,?>)SimpleJson.parse(resource("input-design.json.gz"));var ids=list(design,"samples");var genotypes=list(design,"a1_genotypes");
        var fam=new StringBuilder();var table=new StringBuilder("fid\tiid\tphenotype\tAGE\tSEX\n");
        for (int i=0;i<ids.size();i++) {
            List<?> pair=(List<?>)ids.get(i);String key=pair.get(0)+"\t"+pair.get(1);fam.append(key).append("\t0\t0\t").append(i%2+1).append("\t2\n");
            double value=model.equals("linear")?number(list(design,"quantitative"),i):separated?(number((List<?>)genotypes.get(0),i)>0?2:1):number(list(design,"binary12"),i);
            if (!model.equals("linear") && coding.equals("0/1")) value--;
            table.append(key).append('\t').append(value).append('\t').append(number(list(design,"age"),i)).append('\t').append(number(list(design,"sex"),i)).append('\n');
        }
        Files.writeString(Path.of(prefix+".fam"),fam);Files.writeString(cohort,table);
        String[] variants=resource("variants.tsv.gz").split("\n");var bim=new StringBuilder();var bed=new ByteArrayOutputStream();bed.writeBytes(new byte[]{0x6c,0x1b,1});
        for (int v=0;v<genotypes.size();v++) {
            String[] meta=variants[v+1].strip().split("\t");bim.append("1 ").append(meta[0]).append(" 0 ").append((v+1)*1000).append(' ').append(meta[1]).append(' ').append(meta[2]).append('\n');
            List<?> dosages=(List<?>)genotypes.get(v);
            for (int start=0;start<ids.size();start+=4) { int packed=0;for (int j=0;j<4 && start+j<ids.size();j++) { int g=((Number)dosages.get(start+j)).intValue();int code=g<0?1:g==2?0:g==1?2:3;packed|=code<<(2*j); }bed.write(packed); }
        }
        Files.writeString(Path.of(prefix+".bim"),bim);Files.write(Path.of(prefix+".bed"),bed.toByteArray());return new Fixture(prefix,cohort,design);
    }
    private PlinkGlmInputs.Bounds bounds() { return new PlinkGlmInputs.Bounds(1000,1000,1000000,10485760,10485760,16384,10485760,10485760); }
    private PlinkGlmInputs.Prepared prepare(Fixture input,String model,String coding,String missing) throws Exception {
        return PlinkGlmInputs.prepare(input.prefix(),input.cohort(),Files.createTempDirectory(root,"staged"),model,coding,missing,List.of("AGE","SEX"),bounds());
    }
    @Test void exactPairIdentityAndCompleteInputHashesSurviveSurrogates() throws Exception {
        var input=fixture(root.resolve("原始"),"linear","quantitative",false);var result=prepare(input,"linear","quantitative","error");
        assertEquals(128,result.samples());assertEquals(128,result.eligible());assertEquals(96,result.variants().size());
        assertEquals("rs10000",result.variants().get("V1").original());assertEquals(128,result.variants().get("V1").observed());
        assertEquals(FollowupSupport.hash(Path.of(input.prefix()+".bed")),result.inputHashes().get("bed"));assertEquals(6,result.stagedHashes().size());
        assertTrue(result.variants().values().stream().allMatch(v->v.staged().matches("V[0-9]+")));
    }
    @Test void exclusionsAreExplicitAndMissingTokenIsExact() throws Exception {
        var input=fixture(root.resolve("input"),"linear","quantitative",false);var lines=Files.readAllLines(input.cohort());
        lines.set(1,lines.get(1).replaceFirst("\\t[^\\t]+\\t[^\\t]+\\t[^\\t]+$","\tNA\t0.2\t0"));
        String[] row=lines.get(2).split("\t");row[3]="NA";lines.set(2,String.join("\t",row));Files.write(input.cohort(),lines);
        assertTrue(assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error")).getMessage().contains("prohibits exclusion"));
        var result=prepare(input,"linear","quantitative","exclude");assertEquals(126,result.eligible());assertEquals(2,result.excluded());assertEquals(126,result.variants().get("V1").observed());
        row[3]="nan";lines.set(2,String.join("\t",row));Files.write(input.cohort(),lines);assertThrows(Exception.class,()->prepare(input,"linear","quantitative","exclude"));
    }
    @Test void absentDuplicateAndUnknownCohortPairsFail() throws Exception {
        var input=fixture(root.resolve("input"),"linear","quantitative",false);var lines=Files.readAllLines(input.cohort());
        for (int mode=0;mode<3;mode++) { var changed=new ArrayList<>(lines);if (mode==0)changed.remove(1);else if(mode==1)changed.set(2,changed.get(1));else changed.set(1,changed.get(1).replace("F000","UNKNOWN"));Files.write(input.cohort(),changed);assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error")); }
    }
    @Test void ambiguousLinearAndIncorrectBinaryCodingFail() throws Exception {
        var input=fixture(root.resolve("input"),"logistic","1/2",false);
        assertTrue(assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error")).getMessage().contains("legacy binary"));
        assertThrows(Exception.class,()->prepare(input,"logistic","0/1","error"));
        var lines=Files.readAllLines(input.cohort());for(int i=1;i<lines.size();i++){String[] f=lines.get(i).split("\t");f[2]="1";lines.set(i,String.join("\t",f));}Files.write(input.cohort(),lines);
        assertTrue(assertThrows(Exception.class,()->prepare(input,"logistic","1/2","error")).getMessage().contains("both cases"));
    }
    @Test void minusNineIsNumericAndUnusedMissingSentinelAvoidsObservedCollision() throws Exception {
        var input=fixture(root.resolve("input"),"linear","quantitative",false);var lines=Files.readAllLines(input.cohort());String[] row=lines.get(1).split("\t");row[2]="-2147483647";row[3]="-9";lines.set(1,String.join("\t",row));Files.write(input.cohort(),lines);
        var result=prepare(input,"linear","quantitative","error");assertEquals(Integer.MIN_VALUE+2,result.missingSentinel());assertEquals(128,result.eligible());
    }
    @Test void nonfiniteUnderflowMalformedUtf8AndOversizedLinesFail() throws Exception {
        var input=fixture(root.resolve("input"),"linear","quantitative",false);var lines=Files.readAllLines(input.cohort());String original=lines.get(1);
        for (String value:List.of("Infinity","NaN","1e999","-1e-999",""," 1.2")) {String[] row=original.split("\t");row[2]=value;lines.set(1,String.join("\t",row));Files.write(input.cohort(),lines);assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error"));}
        Files.write(input.cohort(),new byte[]{(byte)0xc3,0x28});assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error"));
        Files.writeString(input.cohort(),"x".repeat(16385));assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error"));
    }
    @Test void cohortHeadersCovariateNamesAndInputMutationAreChecked() throws Exception {
        var input=fixture(root.resolve("input"),"linear","quantitative",false);var prepared=prepare(input,"linear","quantitative","error");
        Files.writeString(input.cohort(),Files.readString(input.cohort()).replace("phenotype\tAGE\tSEX","phenotype\tSEX\tAGE"));assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error"));
        assertThrows(Exception.class,()->PlinkGlmInputs.verify(prepared.inputs(),prepared.inputHashes()));
        for(String names:List.of("AGE,AGE","fid","AGE,","bad-name","phenotype"))assertThrows(IllegalArgumentException.class,()->PlinkGlmInputs.covariates(names));assertEquals(List.of(),PlinkGlmInputs.covariates("none"));
    }
    @Test void badVariantIdentityChromosomeAllelesAndBedDimensionsFail() throws Exception {
        var input=fixture(root.resolve("input"),"linear","quantitative",false);Path bim=Path.of(input.prefix()+".bim");String original=Files.readString(bim);
        for(String text:List.of(original.replaceFirst("rs10001","rs10000"),original.replaceFirst("1 rs10000","X rs10000"),original.replaceFirst("C A","0 A"),original.replaceFirst("C A","N A"))) { Files.writeString(bim,text);assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error")); }
        Files.writeString(bim,original);Files.write(Path.of(input.prefix()+".bed"),new byte[]{0x6c,0x1b,1});assertThrows(Exception.class,()->prepare(input,"linear","quantitative","error"));
    }
    @Test void familyParentsSexAndCompositeIdentifierCollisionsRemainDistinct() throws Exception {
        var input=fixture(root.resolve("input"),"linear","quantitative",false);Path fam=Path.of(input.prefix()+".fam");var famLines=Files.readAllLines(fam);var cohortLines=Files.readAllLines(input.cohort());
        String[][] pairs={{"A_B","C"},{"A","B_C"},{"家族","成员"}};
        for(int i=0;i<3;i++){String[] f=famLines.get(i).split("\\s+");f[0]=pairs[i][0];f[1]=pairs[i][1];if(i==2){f[2]="未采样父亲";f[4]="0";}famLines.set(i,String.join(" ",f));String[] row=cohortLines.get(i+1).split("\t");row[0]=pairs[i][0];row[1]=pairs[i][1];cohortLines.set(i+1,String.join("\t",row));}
        Files.write(fam,famLines);Files.write(input.cohort(),cohortLines);Path staged=Files.createDirectory(root.resolve("staged"));var prepared=PlinkGlmInputs.prepare(input.prefix(),input.cohort(),staged,"linear","quantitative","error",List.of("AGE","SEX"),bounds());
        assertEquals(128,prepared.eligible());String ledger=Files.readString(staged.resolve("sample-ledger.tsv"));assertTrue(ledger.contains("A_B\tC"));assertTrue(ledger.contains("A\tB_C"));assertTrue(ledger.contains("家族\t成员"));assertTrue(ledger.contains("false\t0\tELIGIBLE"));
        String[] third=Files.readAllLines(staged.resolve("input.fam")).get(2).split(" ");assertNotEquals("0",third[2]);assertEquals("0",third[4]);
    }
}
