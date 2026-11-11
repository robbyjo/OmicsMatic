/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.shrinkage.MashFit;
import org.jlinalg.shrinkage.MashGaussian;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class MashNativeExecutionTest {
    @TempDir static Path temporary;
    private static Path originals,references;
    private static final Runnable CHECK=()->{};
    private static final MashNativeExecution.Limits LIMITS=MashNativeExecution.Limits.defaults();
    private static Path extract(String resource,String name,int bytes,String hash)throws Exception {
        Path root=temporary.resolve(name);Files.createDirectory(root);
        try(var input=MashNativeExecutionTest.class.getResourceAsStream(resource)){
            assertNotNull(input);byte[] raw=input.readAllBytes();assertEquals(bytes,raw.length);assertEquals(hash,sha(raw));long total=0;int count=0;
            try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                assertFalse(entry.isDirectory());String file=entry.getName();assertFalse(file.contains("..")||file.contains("\\")||file.contains(":"));Path destination=root.resolve(file).normalize();assertTrue(destination.startsWith(root));byte[] value=zip.readNBytes(8_388_609);assertTrue(value.length<=8_388_608);total+=value.length;assertTrue(total<=134_217_728);assertTrue(++count<=5000);Files.createDirectories(destination.getParent());assertFalse(Files.exists(destination));Files.write(destination,value);
            }}
        }return root;
    }
    private static Path originals()throws Exception {if(originals==null)originals=extract("mashr-workflow/original-controls.zip","source-workflows",5817297,"28187ad99751081f608473ffe6a24f2a7e61081e9eb5bdddfddc6de361239d42");return originals;}
    private static Path references()throws Exception {if(references==null)references=extract("mashr-native/residual-reference.zip","residual-reference",629271,"0cbe93724f61912a02b3dea5d4e15091a3b109dffeb433ab09a3dbe71ed1bbeb");return references;}
    private static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static double[][] matrix(Path path)throws IOException {var lines=Files.readAllLines(path);double[][] result=new double[lines.size()][];for(int i=0;i<result.length;i++)result[i]=Arrays.stream(lines.get(i).split("\t")).mapToDouble(Double::parseDouble).toArray();return result;}
    private static Map<String,String> settings(Path path)throws IOException {var result=new LinkedHashMap<String,String>();var lines=Files.readAllLines(path);for(String line:lines.subList(1,lines.size())){String[] f=line.split("\t",-1);result.put(f[0],f[1]);}return result;}
    private static void near(double[][] expected,double[][] observed){assertEquals(expected.length,observed.length);for(int i=0;i<expected.length;i++){assertEquals(expected[i].length,observed[i].length);for(int j=0;j<expected[i].length;j++)assertEquals(expected[i][j],observed[i][j],1e-9+1e-8*Math.abs(expected[i][j]),"cell "+i+","+j);}}
    @TestFactory Stream<DynamicTest> allOriginalResidualMomentsAndConditionalNoiseMeans()throws Exception {
        var tests=new ArrayList<DynamicTest>();Path source=originals(),reference=references();List<String> names=Files.readAllLines(reference.resolve("cases.txt"));assertEquals(17,names.size());
        for(String name:names)tests.add(DynamicTest.dynamicTest("residual-reference-"+name,()->{
            Path root=source.resolve(name+"/outputs");var options=settings(root.resolve("settings.tsv"));double[][] effects=matrix(root.resolve("training-bhat.tsv")),errors=matrix(root.resolve("training-shat.tsv"));
            double[] pi=matrix(root.resolve("model/proportions.tsv"))[0];var covariances=new ArrayList<double[][]>();for(int i=0;i<pi.length;i++)covariances.add(matrix(root.resolve("model/component-"+i+".tsv")));
            var model=MashGaussian.prepare(covariances,pi,matrix(root.resolve("model/residual.tsv")),new MashGaussian.Options(Double.parseDouble(options.get("alpha")),Double.parseDouble(options.get("pi_threshold")),1e-12,Math.ulp(1.),1e-7,true),LIMITS.fit().gaussian(),CHECK);
            double[][] means=new double[effects.length][];for(int row=0;row<effects.length;row++)means[row]=model.evaluateWithResidual(effects[row],errors[row],CHECK).standardizedResidualMean();
            near(matrix(reference.resolve(name+"/original-standardized-residual-mean.tsv")),means);
            near(matrix(reference.resolve(name+"/original-expected-noise.tsv")),MashFit.expectedNoise(effects,errors,model,LIMITS.fit(),CHECK));
            System.out.println(name+"\toriginal-residual-values="+(effects.length*3+9));
        }));return tests.stream();
    }
    @Test void trueSingletonMatchesOriginalVectorLikelihoodOptimizerAndConditionalPrimitives()throws Exception {
        Path reference=references().resolve("singleton"),input=single("singleton-source"),output=temporary.resolve("singleton-source-output");execute(input,output);
        double[] pi=matrix(output.resolve("model/proportions.tsv"))[0]; // Export is a column, unlike original workflow rows.
        assertEquals(1,pi.length);near(matrix(reference.resolve("original-proportions.tsv")),new double[][]{Arrays.stream(matrix(output.resolve("model/proportions.tsv"))).mapToDouble(r->r[0]).toArray()});
        for(String field:List.of("mean","sd","negative","zero","lfsr","component-loglik"))near(matrix(reference.resolve("original-"+field+".tsv")),matrix(output.resolve("posterior/"+field+".tsv")));
        double[][] covariance=matrix(reference.resolve("original-covariance.tsv"));near(new double[][]{{covariance[0][0],covariance[1][0],covariance[0][1],covariance[1][1]}},matrix(output.resolve("posterior/covariance-column-major.tsv")));
        assertEquals(List.of("0001"),Files.readAllLines(output.resolve("training-ids.tsv")));assertEquals(List.of("0001"),Files.readAllLines(output.resolve("target-ids.tsv")));assertTrue(Files.readString(output.resolve("result.tsv")).contains("public_bundle_admitted\tfalse"));verifyManifest(output);
    }
    @Test void largeMeanBoundaryRetainsTheOriginalCancellationAndUsesTheAnalyticNoiseIdentity()throws Exception {
        Path source=references().resolve("large-mean-boundary");near(new double[][]{{0,0},{0,0}},matrix(source.resolve("original-expected-noise.tsv")));near(new double[][]{{0,0},{0,0},{0,0}},matrix(source.resolve("subtracted-residual-mean.tsv")));
        var model=MashGaussian.prepare(List.<double[][]>of(new double[][]{{1e200,0},{0,1e200}}),new double[]{1},new double[][]{{1,0},{0,1}},MashGaussian.Options.defaults(),LIMITS.fit().gaussian(),CHECK);
        double[][] effects={{1e100,0},{0,1e100},{1e100,1e100}},errors={{1,1},{1,1},{1,1}};
        near(new double[][]{{1,0},{0,1}},MashFit.expectedNoise(effects,errors,model,LIMITS.fit(),CHECK));
        assertEquals(1e-100,model.evaluateWithResidual(effects[0],errors[0],CHECK).standardizedResidualMean()[0],1e-113);
    }
    @TestFactory Stream<DynamicTest> completeOriginalModelsPredictThroughTheNativeFileExecutor()throws Exception {
        var tests=new ArrayList<DynamicTest>();Path source=originals();
        for(String name:Files.readAllLines(references().resolve("cases.txt")))tests.add(DynamicTest.dynamicTest("native-file-original-model-"+name,()->{
            Path original=source.resolve(name+"/outputs"),input=temporary.resolve("import-"+name),output=temporary.resolve("import-output-"+name);Files.createDirectory(input);
            List<String> conditions=Files.readAllLines(original.resolve("condition-ids.tsv")),ids=Files.readAllLines(original.resolve("target-ids.tsv"));var options=settings(original.resolve("settings.tsv"));
            wideInput(input,"effects.tsv",conditions,ids,matrix(original.resolve("target-bhat.tsv")));wideInput(input,"standard-errors.tsv",conditions,ids,matrix(original.resolve("target-shat.tsv")));
            StringBuilder populations=new StringBuilder("feature\ttrain\tstrong\ttarget\n");for(String id:ids)populations.append(id).append("\t0\t0\t1\n");write(input,"populations.tsv",populations.toString());write(input,"settings.tsv","key\tvalue\nschema_version\t2\noperation\tpredict\n");
            write(input,"model/representation.tsv","key\tvalue\nschema_version\t1\nrepresentation\tfully_expanded\n");write(input,"model/conditions.tsv",String.join("\n",conditions)+"\n");
            write(input,"model/settings.tsv","key\tvalue\nalpha\t"+options.get("alpha")+"\nposterior_threshold\t"+options.get("pi_threshold")+"\ncovariance_tolerance\t1e-12\nminimum_se\t2.220446049250313e-16\nnumeric_sensitivity\t1e-7\n");
            List<String> names=Files.readAllLines(original.resolve("model/component-names.tsv"));write(input,"model/component-names.tsv",String.join("\n",names)+"\n");
            for(int i=0;i<names.size();i++)Files.copy(original.resolve("model/component-"+i+".tsv"),input.resolve("model/component-"+(i+1)+".tsv"));Files.copy(original.resolve("model/residual.tsv"),input.resolve("model/residual.tsv"));
            StringBuilder proportions=new StringBuilder();for(double p:matrix(original.resolve("model/proportions.tsv"))[0])proportions.append(p).append('\n');write(input,"model/proportions.tsv",proportions.toString());modelManifest(input.resolve("model"));
            var result=execute(input,output);assertEquals(ids.size(),result.targetRows());assertEquals(0,result.trainingRows());assertEquals(ids,Files.readAllLines(output.resolve("target-ids.tsv")));
            for(String field:List.of("mean","sd","negative","zero","lfsr","covariance-column-major","weights","component-loglik"))near(matrix(original.resolve("posterior/"+field+".tsv")),matrix(output.resolve("posterior/"+field+".tsv")));
            double[] expected=matrix(original.resolve("posterior/loglik.tsv"))[0];double[][] likelihood=matrix(output.resolve("posterior/loglik.tsv"));assertEquals(expected.length,likelihood.length);for(int i=0;i<expected.length;i++)assertEquals(expected[i],likelihood[i][0],1e-9+1e-8*Math.abs(expected[i]));verifyManifest(output);
        }));return tests.stream();
    }
    private static void wideInput(Path input,String name,List<String> conditions,List<String> ids,double[][] values)throws IOException {StringBuilder text=new StringBuilder("feature\t"+String.join("\t",conditions)+"\n");for(int i=0;i<ids.size();i++){text.append(ids.get(i));for(double value:values[i])text.append('\t').append(value);text.append('\n');}write(input,name,text.toString());}
    private static void modelManifest(Path root)throws Exception {StringBuilder manifest=new StringBuilder();try(var files=Files.list(root)){for(Path path:files.sorted().toList())manifest.append(sha(Files.readAllBytes(path))).append("  ").append(path.getFileName()).append('\n');}write(root,"SHA256SUMS",manifest.toString());}
    private static void write(Path root,String name,String value)throws IOException {Path path=root.resolve(name);Files.createDirectories(path.getParent());Files.writeString(path,value,StandardCharsets.UTF_8);}
    private static Path single(String name)throws IOException {
        Path root=temporary.resolve(name);Files.createDirectory(root);
        write(root,"effects.tsv","feature\tA\tB\n0001\t10\t10\n");write(root,"standard-errors.tsv","feature\tA\tB\n0001\t1\t1\n");write(root,"populations.tsv","feature\ttrain\tstrong\ttarget\n0001\t1\t0\t1\n");
        write(root,"settings.tsv","key\tvalue\nschema_version\t2\noperation\tfit_predict\nalpha\t0\nposterior_threshold\t1e-10\ncovariance_tolerance\t1e-12\nminimum_se\t2.220446049250313e-16\nnumeric_sensitivity\t1e-7\nbasis\tsupplied\ngrid\t1\nnormalize\tfalse\npoint_mass\ttrue\nprior\tnullbiased\noptimizer\tnative_em\nweight_maxiter\t10000\nweight_dual_gap\t1e-10\nresidual\tfixed\nresidual_maxiter\t30\nresidual_tolerance\t1\n");
        write(root,"residual.tsv","1\t0\n0\t1\n");write(root,"basis/component-names.tsv","shared\n");write(root,"basis/component-1.tsv","100\t100\n100\t100\n");return root;
    }
    private static void change(Path input,String key,String value)throws IOException {Path path=input.resolve("settings.tsv");var lines=new ArrayList<>(Files.readAllLines(path));boolean found=false;for(int i=1;i<lines.size();i++)if(lines.get(i).startsWith(key+"\t")){lines.set(i,key+"\t"+value);found=true;}if(!found)lines.add(key+"\t"+value);Files.write(path,lines);}
    private static MashNativeExecution.Result execute(Path input,Path output)throws IOException {return MashNativeExecution.execute(input,output,LIMITS,CHECK);}
    private static void copy(Path source,Path target)throws IOException {try(var files=Files.walk(source)){for(Path path:files.toList()){Path to=target.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectories(to);else Files.copy(path,to);}}}
    private static Path predict(String name,Path model)throws IOException {Path root=temporary.resolve(name);Files.createDirectory(root);copy(model,root.resolve("model"));write(root,"settings.tsv","key\tvalue\nschema_version\t2\noperation\tpredict\n");write(root,"effects.tsv","feature\tA\tB\nnew-target\t10\t10\n");write(root,"standard-errors.tsv","feature\tA\tB\nnew-target\t1\t1\n");write(root,"populations.tsv","feature\ttrain\tstrong\ttarget\nnew-target\t0\t0\t1\n");return root;}
    private static void failed(Path output)throws IOException {assertTrue(Files.isRegularFile(output.resolve("FAILURE.txt")));assertFalse(Files.exists(output.resolve("result.tsv")));assertFalse(Files.exists(output.resolve("SHA256SUMS")));String warning=Files.readString(output.resolve("WARNINGS.md"));assertTrue(warning.contains("FAIL2"));assertTrue(warning.contains("Required action"));assertTrue(warning.contains("NEW output directory"));}
    private static void verifyManifest(Path output)throws Exception {var names=new java.util.HashSet<String>();for(String line:Files.readAllLines(output.resolve("SHA256SUMS"))){String name=line.substring(66);assertTrue(names.add(name));assertEquals(line.substring(0,64),sha(Files.readAllBytes(output.resolve(name))));}try(var files=Files.walk(output)){assertEquals(files.filter(Files::isRegularFile).count()-1,names.size());}}
    @Test void exportedExpandedModelReloadsWithoutRefittingOrDoubleScaling()throws Exception {
        Path input=single("roundtrip"),first=temporary.resolve("roundtrip-fit");change(input,"grid","0.5,2");execute(input,first);
        Path request=predict("roundtrip-predict",first.resolve("model")),second=temporary.resolve("roundtrip-prediction");var result=execute(request,second);assertEquals("IMPORTED_EXPANDED_MODEL",result.fitStatus());assertEquals(0,result.trainingRows());assertEquals(1,result.targetRows());
        for(String field:List.of("mean","sd","negative","zero","lfsr","covariance-column-major","weights","component-loglik","standardized-residual-mean","loglik"))assertEquals(Files.readString(first.resolve("posterior/"+field+".tsv")),Files.readString(second.resolve("posterior/"+field+".tsv")));
        assertEquals(Files.readString(first.resolve("model/component-3.tsv")),Files.readString(second.resolve("model/component-3.tsv")));verifyManifest(second);
    }
    @Test void targetChangesCannotTrainThePrior()throws Exception {
        Path input=single("leakage-first");write(input,"effects.tsv","feature\tA\tB\n0001\t10\t10\nonly-target\t2\t-3\n");write(input,"standard-errors.tsv","feature\tA\tB\n0001\t1\t1\nonly-target\t1\t1\n");write(input,"populations.tsv","feature\ttrain\tstrong\ttarget\n0001\t1\t0\t0\nonly-target\t0\t0\t1\n");Path first=temporary.resolve("leakage-output-first");execute(input,first);
        Path changed=temporary.resolve("leakage-second");copy(input,changed);write(changed,"effects.tsv","feature\tA\tB\n0001\t10\t10\nonly-target\t-200\t300\n");Path second=temporary.resolve("leakage-output-second");execute(changed,second);
        assertEquals(Files.readString(first.resolve("model/SHA256SUMS")),Files.readString(second.resolve("model/SHA256SUMS")));assertNotEquals(Files.readString(first.resolve("posterior/mean.tsv")),Files.readString(second.resolve("posterior/mean.tsv")));
    }
    @Test void canonicalFileExecutionUsesEveryExplicitComponent()throws Exception {
        Path input=single("canonical");Files.delete(input.resolve("basis/component-1.tsv"));Files.delete(input.resolve("basis/component-names.tsv"));change(input,"basis","canonical");change(input,"grid","10");Path output=temporary.resolve("canonical-output");var result=execute(input,output);assertEquals(8,result.components());assertEquals(8,Files.readAllLines(output.resolve("model/component-names.tsv")).size());assertEquals(1,result.trainingRows());
    }
    private static Path nullPopulation(String name,String mode)throws IOException {
        Path input=single(name);write(input,"effects.tsv","feature\tA\tB\na\t1\t0\nb\t0\t2\nc\t-1\t0\nd\t0\t-2\ntarget\t0.1\t-0.2\n");write(input,"standard-errors.tsv","feature\tA\tB\na\t1\t1\nb\t1\t1\nc\t1\t1\nd\t1\t1\ntarget\t1\t1\n");write(input,"populations.tsv","feature\ttrain\tstrong\ttarget\na\t1\t0\t0\nb\t1\t0\t0\nc\t1\t0\t0\nd\t1\t0\t0\ntarget\t0\t0\t1\n");write(input,"basis/component-1.tsv","0\t0\n0\t0\n");change(input,"point_mass","false");change(input,"prior","uniform");change(input,"residual",mode);change(input,"residual_tolerance","1e-10");return input;
    }
    @Test void simpleNullExecutionRecordsEverySelectedTrainingIdentity()throws Exception {
        Path input=nullPopulation("simple-null","simple");change(input,"null_z_threshold","3");change(input,"null_correlation","false");Files.delete(input.resolve("residual.tsv"));Path output=temporary.resolve("simple-null-output");execute(input,output);near(new double[][]{{2./3,0},{0,8./3}},matrix(output.resolve("model/residual.tsv")));assertEquals(List.of("a","b","c","d"),Files.readAllLines(output.resolve("fit/null-selected-ids.tsv")));
    }
    @Test void residualEmExecutionKeepsWeightHistoriesAndTheAcceptedModel()throws Exception {
        Path input=nullPopulation("residual-em","em_covariance"),output=temporary.resolve("residual-em-output");execute(input,output);near(new double[][]{{.5,0},{0,2}},matrix(output.resolve("model/residual.tsv")));assertTrue(Files.isRegularFile(output.resolve("fit/weights-1/history.tsv")));assertTrue(Files.isRegularFile(output.resolve("fit/proposal-1/residual.tsv")));verifyManifest(output);
    }
    @Test void exhaustedResidualFitRetainsPartialModelsAndCannotProducePosteriors()throws Exception {
        Path input=nullPopulation("residual-limit","em_covariance"),output=temporary.resolve("residual-limit-output");change(input,"residual_maxiter","1");change(input,"residual_tolerance","0");assertThrows(IllegalStateException.class,()->execute(input,output));failed(output);assertTrue(Files.readString(output.resolve("fit/final.tsv")).contains("RESIDUAL_LIMIT"));assertTrue(Files.isRegularFile(output.resolve("fit/weights-1/proportions.tsv")));assertFalse(Files.exists(output.resolve("model")));assertFalse(Files.exists(output.resolve("posterior")));
    }
    @Test void exhaustedWeightFitIsRetainedAndNeverBecomesACompletedModel()throws Exception {
        Path input=single("weight-limit"),output=temporary.resolve("weight-limit-output");write(input,"effects.tsv","feature\tA\tB\n0001\t1\t1\n");write(input,"basis/component-names.tsv","first\nsecond\n");write(input,"basis/component-1.tsv",".1\t0\n0\t.1\n");write(input,"basis/component-2.tsv",".11\t0\n0\t.11\n");change(input,"point_mass","false");change(input,"prior","uniform");change(input,"weight_maxiter","1");change(input,"weight_dual_gap","1e-12");assertThrows(IllegalStateException.class,()->execute(input,output));failed(output);assertTrue(Files.isRegularFile(output.resolve("fit/weights-0/history.tsv")));assertFalse(Files.exists(output.resolve("model")));
    }
    @TestFactory Stream<DynamicTest> invalidSettingsAndMatricesFailWithRecovery(){
        List<String> cases=List.of("old-schema","wrong-optimizer","unknown-setting","zero-se","reordered-se","extra-row","unassigned","unused-input");var tests=new ArrayList<DynamicTest>();
        for(String name:cases)tests.add(DynamicTest.dynamicTest(name,()->{Path input=single("invalid-"+name),output=temporary.resolve("invalid-"+name+"-output");switch(name){
            case "old-schema"->change(input,"schema_version","1");case "wrong-optimizer"->change(input,"optimizer","mixSQP");case "unknown-setting"->change(input,"made_up","true");
            case "zero-se"->write(input,"standard-errors.tsv","feature\tA\tB\n0001\t0\t1\n");case "reordered-se"->write(input,"standard-errors.tsv","feature\tB\tA\n0001\t1\t1\n");case "extra-row"->Files.writeString(input.resolve("effects.tsv"),"unrequested\t1\t1\n",StandardOpenOption.APPEND);
            case "unassigned"->write(input,"populations.tsv","feature\ttrain\tstrong\ttarget\n0001\t0\t0\t0\n");case "unused-input"->write(input,"ignored.tsv","1\n");default->throw new AssertionError();}
            assertThrows(IOException.class,()->execute(input,output));failed(output);
        }));return tests.stream();
    }
    @Test void alteredImportedModelFailsItsCompleteManifest()throws Exception {Path fit=temporary.resolve("tamper-fit");execute(single("tamper"),fit);Path input=predict("tamper-predict",fit.resolve("model")),output=temporary.resolve("tamper-output");write(input,"model/component-2.tsv","99\t99\n99\t99\n");assertTrue(assertThrows(IOException.class,()->execute(input,output)).getMessage().contains("integrity"));failed(output);}
    @Test void alteredConditionIdentitiesCannotReuseAnImportedModel()throws Exception {Path fit=temporary.resolve("conditions-fit");execute(single("conditions"),fit);Path input=predict("conditions-predict",fit.resolve("model")),output=temporary.resolve("conditions-output");write(input,"effects.tsv","feature\tB\tA\nnew-target\t10\t10\n");write(input,"standard-errors.tsv","feature\tB\tA\nnew-target\t1\t1\n");assertTrue(assertThrows(IOException.class,()->execute(input,output)).getMessage().contains("identities"));failed(output);}
    @Test void overlappingPathsAndExistingOutputsArePreserved()throws Exception {Path input=single("protected-input");String before=Files.readString(input.resolve("effects.tsv"));assertThrows(IOException.class,()->execute(input,input.resolve("output")));assertFalse(Files.exists(input.resolve("output")));assertEquals(before,Files.readString(input.resolve("effects.tsv")));Path output=temporary.resolve("existing-output");Files.createDirectory(output);write(output,"sentinel.txt","preserve");assertThrows(IOException.class,()->execute(input,output));assertEquals("preserve",Files.readString(output.resolve("sentinel.txt")));try(var files=Files.list(output)){assertEquals(1,files.count());}}
    @Test void outputQuotaFailureRetainsAnExplicitFailureInsteadOfPrefixSuccess()throws Exception {Path input=single("quota"),output=temporary.resolve("quota-output");assertThrows(IOException.class,()->MashNativeExecution.execute(input,output,new MashNativeExecution.Limits(100,1_048_576,1024,LIMITS.fit()),CHECK));failed(output);}
    @Test void workBudgetFailureCannotExportAUsableModel()throws Exception {Path input=single("work"),output=temporary.resolve("work-output");var fit=LIMITS.fit();var tiny=new MashFit.Limits(fit.rows(),fit.gaussian(),fit.weights(),fit.cells(),fit.ownedBytes(),1,fit.totalWeightCells());assertThrows(IOException.class,()->MashNativeExecution.execute(input,output,new MashNativeExecution.Limits(100,1_048_576,1_048_576,tiny),CHECK));failed(output);assertFalse(Files.exists(output.resolve("model")));}
    @Test void cancellationRetainsTheRecoveryMessage()throws Exception {Path input=single("cancel"),output=temporary.resolve("cancel-output");assertThrows(CancellationException.class,()->MashNativeExecution.execute(input,output,LIMITS,()->{throw new CancellationException("requested cancellation");}));failed(output);}
    @Test void commandReportsFailureAndScopeProminently()throws Exception {
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();try(var out=new PrintStream(stdout,true,StandardCharsets.UTF_8);var err=new PrintStream(stderr,true,StandardCharsets.UTF_8)){
            assertEquals(2,MashNativeExecution.run(new String[]{"--unknown"},out,err));assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Required action"));stderr.reset();Path input=single("cli"),output=temporary.resolve("cli-output");assertEquals(0,MashNativeExecution.run(new String[]{"--input",input.toString(),"--out",output.toString()},out,err));assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("NATIVE_MASH_DEVELOPMENT_COMPLETE"));assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("DEVELOPMENT ONLY"));
        }
    }
}
