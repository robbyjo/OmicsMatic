package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkToolsCliTest {
    @TempDir(cleanup=org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path root;
    private record Run(int code,String output,String error) { }
    private Path home() { String value=System.getenv("JLINALG_PLINK_HOME");Assumptions.assumeTrue(value!=null && Files.isRegularFile(Path.of(value,".jlinalg-plink.json")),"Set JLINALG_PLINK_HOME to the permitted sealed original profile");return Path.of(value); }
    private Run run(String... args) { var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8)); }
    private Run glm(PlinkGlmInputsTest.Fixture fixture,String engine,String model,String coding,Path output,String... extra) {
        var args=new ArrayList<>(List.of("plink-tools","--task","glm","--engine",engine,"--home",home().toString(),"--bfile",fixture.prefix().toString(),"--cohort",fixture.cohort().toString(),"--model",model,"--covariates","AGE,SEX","--missing","error","--genome-build","unspecified","--out",output.toString()));
        if(!model.equals("linear"))args.addAll(List.of("--binary-coding",coding));
        // Explicit test overrides replace a key; normal CLI rejects duplicate keys.
        for(int i=0;i<extra.length;i+=2){int index=args.indexOf(extra[i]);if(index>=0)args.set(index+1,extra[i+1]);else args.addAll(List.of(extra[i],extra[i+1]));}return run(args.toArray(String[]::new));
    }
    private List<String[]> rows(Path output) throws Exception { return Files.readAllLines(output.resolve("results.tsv")).stream().skip(1).map(line->line.split("\t",-1)).toList(); }
    @Test void helpAndInvalidChoicesDoNotCreateAnalysisOutputs() throws Exception {
        assertEquals(0,run("plink-tools","--help").code());assertTrue(run("plink-tools","--help").output().contains("--binary-coding"));
        assertEquals(2,run("plink-tools","--task","raw-command","--out",root.resolve("x").toString()).code());assertFalse(Files.exists(root.resolve("x")));
        assertEquals(2,run("plink-tools","--task","glm","--engine","plink1","--model","firth","--missing","error","--genome-build","GRCh38","--out",root.resolve("firth").toString()).code());assertFalse(Files.exists(root.resolve("firth")));
        assertEquals(2,run("plink-tools","--task","glm","--raw-args","--bad").code());
    }
    @Test void missingDependencySkipsWithExactSetupAndNoScientificInputRead() throws Exception {
        Path out=root.resolve("skip");Run result=run("plink-tools","--task","glm","--engine","plink2","--home",root.resolve("absent").toString(),"--model","linear","--missing","error","--covariates","none","--genome-build","GRCh38","--bfile",root.resolve("not-read").toString(),"--cohort",root.resolve("not-read.tsv").toString(),"--out",out.toString());
        assertEquals(3,result.code(),result.error());assertTrue(result.error().contains("SKIPPED"));String warnings=Files.readString(out.resolve("WARNINGS.md"));assertTrue(warnings.contains("--allow-install"));assertTrue(warnings.contains("--allow-download"));assertTrue(warnings.contains("Required action"));assertFalse(Files.exists(out.resolve("results.tsv")));assertFalse(Files.exists(out.resolve("upstream/input.bed")));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("skipped-dependency"));
    }
    @Test void overlappingOutputCannotCreateOrContaminateRuntimeHome() throws Exception {
        Path absent=root.resolve("uninstalled-home");
        Run result=run("plink-tools","--task","glm","--engine","plink2","--home",absent.toString(),"--model","linear","--missing","error","--covariates","none","--genome-build","GRCh38","--bfile","not-read","--cohort","not-read.tsv","--out",absent.resolve("results").toString());
        assertEquals(2,result.code());assertTrue(result.error().contains("outside the sealed runtime"));assertFalse(Files.exists(absent));
    }
    @Test void actualBothLinearEnginesRetainKnownEffectsAlleleSwitchesAndProvenance() throws Exception {
        var fixture=PlinkGlmInputsTest.fixture(root.resolve("输入"),"linear","quantitative",false);
        for(String engine:List.of("plink1","plink2")) {
            Path out=root.resolve("线性-"+engine);Run result=glm(fixture,engine,"linear","quantitative",out);assertEquals(0,result.code(),result.error()+" "+(Files.exists(out.resolve("WARNINGS.md"))?Files.readString(out.resolve("WARNINGS.md")):""));
            var rows=rows(out);assertEquals(96,rows.size());assertEquals("rs10000",rows.get(0)[0]);assertEquals("C",rows.get(0)[3]);assertEquals("A",rows.get(0)[4]);assertEquals(128,Integer.parseInt(rows.get(0)[6]));assertEquals(.382292,Double.parseDouble(rows.get(0)[8]),.00006);
            if(engine.equals("plink2")){assertEquals(.211202,Double.parseDouble(rows.get(0)[10]));assertEquals(.0727059,Double.parseDouble(rows.get(0)[12]));assertEquals(40,rows.stream().filter(r->r[5].equals("false")).count());}
            else {assertEquals("",rows.get(0)[10]);assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("no SE is reconstructed"));}
            assertEquals(129,Files.readAllLines(out.resolve("sample-ledger.tsv")).size());assertTrue(Files.readString(out.resolve("manifest.yaml")).contains(FollowupSupport.hash(fixture.cohort())));assertTrue(Files.readString(out.resolve("session.yaml")).contains("eligible_samples: 128"));assertFalse(Files.exists(out.resolve("partial")));assertTrue(result.error().contains("WARNINGS"));
            assertEquals(2,glm(fixture,engine,"linear","quantitative",out).code());assertEquals(96,rows(out).size());
        }
    }
    @Test void actualBinaryCodingIsExplicitAndEquivalentAcrossBothEngines() throws Exception {
        for(String engine:List.of("plink1","plink2")) {
            List<String[]> reference=null;
            for(String coding:List.of("0/1","1/2")) {
                var fixture=PlinkGlmInputsTest.fixture(root.resolve(engine+coding.replace('/','-')),"logistic",coding,false);Path out=root.resolve("result-"+engine+coding.replace('/','-'));Run result=glm(fixture,engine,"logistic",coding,out);assertEquals(0,result.code(),result.error()+" "+Files.readString(out.resolve("WARNINGS.md")));var rows=rows(out);assertEquals(96,rows.size());
                if(reference!=null)for(int i=0;i<rows.size();i++)assertArrayEquals(reference.get(i),rows.get(i));reference=rows;
                assertTrue(rows.stream().allMatch(r->r[7].equals("false")));assertTrue(Files.readString(out.resolve("session.yaml")).contains("input_phenotype_coding: "+coding));
            }
        }
    }
    @Test void actualSeparatedFirthErrorsSuppressFiniteProvisionalNumbers() throws Exception {
        var fixture=PlinkGlmInputsTest.fixture(root.resolve("separated"),"logistic","1/2",true);
        for(String model:List.of("logistic","firth-fallback","firth")) {
            Path out=root.resolve(model);Run result=glm(fixture,"plink2",model,"1/2",out);assertEquals(0,result.code(),result.error());String[] first=rows(out).get(0);assertEquals("UNREPORTABLE_UPSTREAM_ERROR",first[14]);for(int i=8;i<=13;i++)assertEquals("",first[i]);
            if(model.equals("logistic")){assertEquals("SEPARATION,ALT1",first[15]);assertEquals("false",first[7]);}else{assertEquals("UNFINISHED",first[15]);assertEquals("true",first[7]);assertTrue(Files.readString(out.resolve("upstream/result.TRAIT.glm."+(model.equals("firth")?"firth":"logistic.hybrid"))).contains("2731.98"));}
            assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("UNREPORTABLE"));
        }
    }
    @Test void actualCompleteCaseExclusionAndNumericMinusNineKeepExactCohort() throws Exception {
        for(String engine:List.of("plink1","plink2")) {
            var fixture=PlinkGlmInputsTest.fixture(root.resolve(engine),"linear","quantitative",false);var lines=Files.readAllLines(fixture.cohort());
            for(int ordinal=1;ordinal<=3;ordinal++){String[] f=lines.get(ordinal).split("\t");if(ordinal==1){f[2]="-9";f[3]="-9";}else if(ordinal==2)f[2]="NA";else f[3]="NA";lines.set(ordinal,String.join("\t",f));}Files.write(fixture.cohort(),lines);
            Path out=root.resolve("result-"+engine);Run result=glm(fixture,engine,"linear","quantitative",out,"--missing","exclude");assertEquals(0,result.code(),result.error()+" "+Files.readString(out.resolve("WARNINGS.md")));assertTrue(rows(out).stream().allMatch(r->r[6].equals("126")));assertTrue(Files.readString(out.resolve("sample-ledger.tsv")).contains("MISSING_PHENOTYPE"));assertTrue(Files.readString(out.resolve("sample-ledger.tsv")).contains("MISSING_COVARIATE"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("2 samples were explicitly excluded"));
        }
    }
    @Test void noReportableAssociationFailsAndQuarantinesCanonicalStream() throws Exception {
        var fixture=PlinkGlmInputsTest.fixture(root.resolve("input"),"linear","quantitative",false);byte[] bed=Files.readAllBytes(Path.of(fixture.prefix()+".bed"));java.util.Arrays.fill(bed,3,bed.length,(byte)0xff);Files.write(Path.of(fixture.prefix()+".bed"),bed);
        Path out=root.resolve("failed");Run result=glm(fixture,"plink2","linear","quantitative",out);assertEquals(2,result.code(),result.error());assertFalse(Files.exists(out.resolve("results.tsv")));assertTrue(Files.exists(out.resolve("partial/results.tsv")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("No association"));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("failed"));
    }
    @Test void resourceAndRecipeValidationFailBeforeScientificResults() throws Exception {
        var fixture=PlinkGlmInputsTest.fixture(root.resolve("input"),"linear","quantitative",false);Path out=root.resolve("bounded");Run result=glm(fixture,"plink2","linear","quantitative",out,"--maximum-cells","1");assertEquals(2,result.code());assertFalse(Files.exists(out.resolve("results.tsv")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("maximum-cells"));
        Path recipe=root.resolve("recursive.json");Files.writeString(recipe,"{\"options-file\":\"another.json\"}");assertEquals(2,run("plink-tools","--options-file",recipe.toString()).code());Files.writeString(recipe,"{\"threads\":2}");assertEquals(2,run("plink-tools","--options-file",recipe.toString()).code());
    }
    @Test void invalidConfidenceChoicesFailBeforeCreatingAnAttempt() {
        for(String level:List.of("0","0.009","1","NaN","Infinity","bad")) {
            Path out=root.resolve("invalid-"+level);Run result=run("plink-tools","--task","glm","--engine","plink2","--home",root.resolve("not-read").toString(),"--bfile","not-read","--cohort","not-read","--model","linear","--covariates","none","--missing","error","--genome-build","GRCh38","--confidence-level",level,"--out",out.toString());assertEquals(2,result.code());assertFalse(Files.exists(out));
        }
    }
    @Test void actualOriginalConfidenceReportsSupplySeAndKeepRequestedLevelAndScale() throws Exception {
        for(String engine:List.of("plink1","plink2"))for(String model:List.of("linear","logistic")) {
            var fixture=PlinkGlmInputsTest.fixture(root.resolve(engine+model),model,"1/2",false);Path out=root.resolve("ci-"+engine+model);Run result=glm(fixture,engine,model,"1/2",out,"--confidence-level","0.955");assertEquals(0,result.code(),result.error());var rows=rows(out);assertEquals(96,rows.size());
            for(String[] row:rows) {assertFalse(row[10].isEmpty());assertEquals("0.955",row[17]);assertEquals(model.equals("linear")?"BETA":"ODDS_RATIO",row[18]);assertTrue(Double.isFinite(Double.parseDouble(row[19])));assertTrue(Double.parseDouble(row[19])<=Double.parseDouble(row[20]));assertEquals("REPORTED",row[21]);}
            String report=Files.readString(out.resolve(engine.equals("plink1")?"upstream/result.assoc."+model:"upstream/result.TRAIT.glm."+model));assertTrue(report.lines().findFirst().orElseThrow().contains(engine.equals("plink1")?"L95 ":"L95.5"));assertTrue(Files.readString(out.resolve("session.yaml")).contains("confidence_level_requested: 0.955"));assertFalse(Files.readString(out.resolve("WARNINGS.md")).contains("did not report standard errors"));
        }
    }
    @Test void originalSeparatedErrorsSuppressFiniteConfidenceLimits() throws Exception {
        var fixture=PlinkGlmInputsTest.fixture(root.resolve("separated-ci"),"logistic","1/2",true);
        for(String model:List.of("logistic","firth-fallback","firth")) {
            Path out=root.resolve(model);Run result=glm(fixture,"plink2",model,"1/2",out,"--confidence-level","0.955");assertEquals(0,result.code(),result.error());String[] first=rows(out).get(0);assertEquals("UNREPORTABLE_UPSTREAM_ERROR",first[14]);assertEquals("0.955",first[17]);assertEquals("ODDS_RATIO",first[18]);assertEquals("",first[19]);assertEquals("",first[20]);assertEquals("UNREPORTABLE_ESTIMATE",first[21]);assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("UNREPORTABLE"));
        }
    }
}
