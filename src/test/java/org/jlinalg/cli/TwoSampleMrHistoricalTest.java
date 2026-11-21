/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Real tagged-package captures, including edition-specific defaults and return structure. */
final class TwoSampleMrHistoricalTest {
    @TempDir Path temp;
    static Stream<String> genuineCases() {
        return Stream.of("0.4.9","0.5.6","0.5.7","0.6.8").flatMap(v->Stream.of("harmonise-1","harmonise-2","harmonise-3","mr-default","mr-deterministic","multivariable","pipeline","mr-modes","format-exposure","format-outcome","format-all-missing").map(name->v+"/"+name));
    }
    private Path fixture(String name)throws IOException {
        Path root=temp.resolve(name);if(Files.exists(root))return root;
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/cli/twosamplemr-historical.zip")) {
            assertNotNull(raw);try(var zip=new ZipInputStream(raw)) {
                int count=0,selected=0;long total=0;java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    assertTrue(++count<4000);if(!entry.getName().startsWith(name+"/"))continue;assertFalse(entry.isDirectory());
                    Path target=temp.resolve(entry.getName()).normalize();assertTrue(target.startsWith(root));byte[] bytes=zip.readAllBytes();total+=bytes.length;assertTrue(total<16000000);Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);selected++;
                }assertTrue(selected>20);
            }
        }return root;
    }
    @ParameterizedTest @MethodSource("genuineCases") void genuineTaggedCaptureVerifiesWithoutR(String name)throws IOException {TwoSampleMrHistoricalCli.verify(fixture(name));}
    @Test void earlyEditionHasOriginalDefaultsAndNoAddedTestDistribution()throws IOException {
        Path root=fixture("0.4.9/mr-default");var actual=read(root.resolve("worker/effective-parameters.json"));assertEquals("tukey",actual.get("loss.function"));assertFalse(actual.containsKey("test_dist"));assertFalse(actual.containsKey("shrinkage"));
        var request=read(root.resolve("request.json"));request.put("parameters",Map.of("test_dist","z"));assertThrows(IOException.class,()->TwoSampleMrHistoricalContracts.validate(request));
    }
    @Test void laterDefaultsRemainVersionBound()throws IOException {
        Path root=fixture("0.5.6/mr-default");var actual=read(root.resolve("worker/effective-parameters.json"));assertEquals("huber",actual.get("loss.function"));assertEquals("z",actual.get("test_dist"));assertEquals(false,actual.get("shrinkage"));
        actual.put("loss.function","tukey");json(root.resolve("worker/effective-parameters.json"),actual);TwoSampleMrHistoricalCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root));
    }
    @Test void earlyMatrixUsesPhenotypeLabelsAndSixMembers()throws IOException {
        Path root=fixture("0.4.9/multivariable");TwoSampleMrHistoricalCli.verify(root);var report=read(root.resolve("worker/report.json"));var members=object(report.get("mv_members"),"members");assertEquals(6,members.size());assertFalse(members.containsKey("expname"));
        assertTrue(Files.readString(root.resolve("worker/mv-exposure_beta.tsv")).startsWith("Exposure\tExposure2\n"));
        var lines=new ArrayList<>(Files.readAllLines(root.resolve("worker/mv-exposure_beta.tsv")));lines.set(0,"e1\te2");Files.write(root.resolve("worker/mv-exposure_beta.tsv"),lines);Files.copy(root.resolve("worker/mv-exposure_beta.tsv"),root.resolve("results/mv-exposure_beta.tsv"),StandardCopyOption.REPLACE_EXISTING);
        var tables=object(report.get("tables"),"tables");var shape=object(tables.get("mv-exposure_beta.tsv"),"matrix shape");shape.put("columns",List.of("e1","e2"));tables.put("mv-exposure_beta.tsv",shape);report.put("tables",tables);json(root.resolve("worker/report.json"),report);TwoSampleMrHistoricalCli.seal(root);assertTrue(assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root)).getMessage().contains("phenotype-label"));
    }
    @Test void originalROrderOfNonAsciiPhenotypesIsPreserved()throws IOException {
        Path root=fixture("0.4.9/multivariable-latin");TwoSampleMrHistoricalCli.verify(root);var columns=TwoSampleMrHistoricalFiles.table(root.resolve("results/mv-exposure_beta.tsv"),read(root.resolve("effective-request.json"))).columns();assertEquals(List.of("\u00e9Trait","zTrait"),columns);assertNotEquals(new ArrayList<>(new TreeSet<>(columns)),columns);
    }
    @ParameterizedTest @ValueSource(strings={"0.5.6","0.5.7","0.6.8"})
    void laterMatricesRetainTheirNameTables(String version)throws IOException {
        Path root=fixture(version+"/multivariable");TwoSampleMrHistoricalCli.verify(root);var report=read(root.resolve("worker/report.json"));assertEquals(8,object(report.get("mv_members"),"members").size());assertTrue(Files.isRegularFile(root.resolve("worker/mv-expname.tsv")));
    }
    @Test void formatKeepsOriginalNonestimableRowFlag()throws IOException {
        Path root=fixture("0.4.9/format-all-missing");TwoSampleMrHistoricalCli.verify(root);var request=read(root.resolve("effective-request.json"));var table=TwoSampleMrHistoricalFiles.table(root.resolve("results/formatted.tsv"),request);assertEquals(1,table.rows().size());assertEquals("FALSE",table.cell(table.rows().get(0),"mr_keep.exposure"));assertEquals(true,read(root.resolve("worker/report.json")).get("no_statistical_estimate_produced"));
    }
    @Test void originalFormatterDuplicateHandlingIsRetained()throws IOException {
        Path root=fixture("0.6.8/format-exposure");TwoSampleMrHistoricalCli.verify(root);var population=object(read(root.resolve("worker/report.json")).get("original_population"),"population");assertEquals(7,((Number)population.get("input_rows")).intValue());assertEquals(6,((Number)population.get("formatted_rows")).intValue());
    }
    @Test void formattingOptionsCannotBeBorrowedFromLaterRelease()throws IOException {
        Path root=fixture("0.4.9/format-exposure");var request=read(root.resolve("request.json"));var options=object(request.get("format_options"),"format options");options.put("log_pval",true);request.put("format_options",options);assertThrows(IOException.class,()->TwoSampleMrHistoricalContracts.validate(request));
    }
    @Test void formatterPopulationCannotBeFabricatedAfterResealing()throws IOException {
        Path root=fixture("0.5.7/format-exposure");var report=read(root.resolve("worker/report.json"));report.put("original_population",Map.of("input_rows",7,"formatted_rows",7));json(root.resolve("worker/report.json"),report);TwoSampleMrHistoricalCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root));
    }
    @Test void originalNativeIvWPointCannotBeChanged()throws IOException {
        Path root=fixture("0.4.9/mr-deterministic");change(root,"mr.tsv","method","Inverse variance weighted","b","0.99");TwoSampleMrHistoricalCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root));
    }
    @Test void harmonisationKeepFlagCannotBeFlipped()throws IOException {
        Path root=fixture("0.5.6/harmonise-2");var request=read(root.resolve("effective-request.json"));var table=TwoSampleMrHistoricalFiles.table(root.resolve("worker/harmonised.tsv"),request);String snp=null;for(var row:table.rows())if(table.cell(row,"mr_keep").equals("FALSE")){snp=table.cell(row,"SNP");break;}assertNotNull(snp);change(root,"harmonised.tsv","SNP",snp,"mr_keep","TRUE");TwoSampleMrHistoricalCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root));
    }
    @Test void versionAndSourceGuardCountCannotDrift()throws IOException {
        Path root=fixture("0.4.9/mr-default");var runtime=read(root.resolve("worker/runtime-profile.json"));runtime.put("source_functions_guarded",149);json(root.resolve("worker/runtime-profile.json"),runtime);TwoSampleMrHistoricalCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root));
    }
    @Test void originalDefaultReturnCannotBeDiscarded()throws IOException {
        Path root=fixture("0.5.7/mr-default");Files.delete(root.resolve("worker/objects/001-default_parameters/original-return.rds"));TwoSampleMrHistoricalCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root));
    }
    @Test void publicWarningsCannotBeHidden()throws IOException {
        Path root=fixture("0.4.9/multivariable");Files.writeString(root.resolve("WARNINGS.md"),"Everything is fine\n");TwoSampleMrHistoricalCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrHistoricalCli.verify(root));
    }
    @Test void unqualifiedEditionSkipsWithoutCreatingOutput()throws IOException {
        Path root=fixture("0.4.9/mr-default");var request=read(root.resolve("request.json"));request.put("profile","TwoSampleMR0.4.8");Path out=temp.resolve("unqualified");request.put("out",out.toString());Path path=temp.resolve("unknown.json");json(path,request);assertEquals(3,run("--request",path.toString()));assertFalse(Files.exists(out));
    }
    @Test void replayCannotMutateParentCapture()throws IOException {
        Path root=fixture("0.6.8/mr-default");String before=FollowupSupport.hash(root.resolve("capture-files.json"));Path request=temp.resolve("replay.json");json(request,Map.of("schema_version",1,"mode","REPLAY","analysis",root.toString(),"out",root.resolve("child").toString()));assertEquals(2,run("--request",request.toString()));assertFalse(Files.exists(root.resolve("child")));assertEquals(before,FollowupSupport.hash(root.resolve("capture-files.json")));TwoSampleMrHistoricalCli.verify(root);
    }
    private static int run(String... args){return TwoSampleMrHistoricalCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    private static void change(Path root,String file,String matchColumn,String match,String column,String value)throws IOException {
        Path path=root.resolve("worker/"+file);var lines=new ArrayList<>(Files.readAllLines(path));var columns=List.of(lines.get(0).split("\t",-1));boolean changed=false;
        for(int i=1;i<lines.size();i++){var row=lines.get(i).split("\t",-1);if(row[columns.indexOf(matchColumn)].equals(match)){row[columns.indexOf(column)]=value;lines.set(i,String.join("\t",row));changed=true;break;}}assertTrue(changed);Files.write(path,lines,StandardCharsets.UTF_8);Files.copy(path,root.resolve("results/"+file),StandardCopyOption.REPLACE_EXISTING);
    }
}
