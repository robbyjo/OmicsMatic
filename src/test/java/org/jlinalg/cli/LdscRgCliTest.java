/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LdscRgCliTest {
    @TempDir Path temp;
    private static List<String[]> rows(String name)throws IOException {
        try(var in=LdscRgCliTest.class.getResourceAsStream("/org/jlinalg/xwas/ldsc-rg/"+name)){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).map(s->s.split("\t",-1)).toList();}
    }
    private static Object intercept(String x){return x.equals("None")?"free":Double.valueOf(x);}
    private Path prepare(String id)throws IOException {
        String[] model=rows("models.tsv").stream().filter(row->row[0].equals(id)).findFirst().orElseThrow();
        var input=new StringBuilder("variant\tz1\tz2\tld_score\tweight_ld_score\tn1\tn2\n");for(String[] row:rows("inputs.tsv"))if(row[0].equals(id))input.append("rs").append(row[1]).append('\t').append(String.join("\t",Arrays.copyOfRange(row,2,row.length))).append('\n');Files.writeString(temp.resolve("input.tsv"),input);
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("input","input.tsv");m.put("out","result");m.put("reference_variants",Double.valueOf(model[1]));m.put("n_blocks",Integer.valueOf(model[2]));m.put("trait1","Trait A");m.put("trait2","Trait B");m.put("heritability_intercepts",List.of(intercept(model[3]),intercept(model[4])));m.put("covariance_intercept",intercept(model[5]));m.put("two_step",model[6].equals("None")?null:Double.valueOf(model[6]));m.put("out_of_range_policy","retain-raw");m.put("data_provenance","Synthetic original-core reference "+id);m.put("row_order_declaration","Fixed synthetic complete common row order");m.put("allele_alignment_declaration","Both signed Z columns already share the same declared synthetic effect allele");Path p=temp.resolve("request.json");Files.writeString(p,NextflowJson.json(m));return p;
    }
    private int run(Path p,ByteArrayOutputStream error){return LdscToolsCli.run(new String[]{"rg","--request",p.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error));}
    @Test void completeFreeInterceptFitIsAdmittedWithOriginalInputRetained()throws Exception {
        Path request=prepare("R000");assertEquals(0,run(request,new ByteArrayOutputStream()));assertTrue(Files.isRegularFile(temp.resolve("result/COMPLETED.json")));assertTrue(Files.readString(temp.resolve("result/analysis.json")).contains("\"correlation_admitted\":true"));assertArrayEquals(Files.readAllBytes(temp.resolve("input.tsv")),Files.readAllBytes(temp.resolve("result/source-input.tsv")));
    }
    @Test void nonpositiveHeritabilityRetainsComponentFitsButRefusesCorrelation()throws Exception {
        var error=new ByteArrayOutputStream();assertEquals(2,run(prepare("R006"),error));assertTrue(error.toString().contains("CORRELATION UNREPORTABLE: NONPOSITIVE_HERITABILITY"));assertTrue(Files.isRegularFile(temp.resolve("result/genetic-covariance.tsv")));assertTrue(Files.readString(temp.resolve("result/correlation.tsv")).contains("NA\tNA"));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
    @Test void nonfiniteDeleteUncertaintyIsNanRatherThanNaOrZero()throws Exception {
        assertEquals(2,run(prepare("R007"),new ByteArrayOutputStream()));assertTrue(Files.readString(temp.resolve("result/correlation.tsv")).contains("NaN\tNaN"));assertTrue(Files.readString(temp.resolve("result/model.json")).contains("NONFINITE_DELETE_UNCERTAINTY"));assertTrue(Files.readString(temp.resolve("result/analysis.json")).contains("\"result_admitted\":false"));
    }
    @Test void bothNegativeDeleteHeritabilitiesUseTheOriginalPositiveProduct()throws Exception {
        assertEquals(0,run(prepare("R008"),new ByteArrayOutputStream()));assertTrue(Files.readString(temp.resolve("result/WARNINGS.md")).contains("two negatives yield a positive product"));assertTrue(Files.isRegularFile(temp.resolve("result/COMPLETED.json")));
    }
    @Test void twoStepRetainsDistinctTraitAndJointMappedBoundaries()throws Exception {
        assertEquals(0,run(prepare("R004"),new ByteArrayOutputStream()));assertTrue(Files.readString(temp.resolve("result/WARNINGS.md")).contains("pairs deletes by ordinal"));assertTrue(Files.readString(temp.resolve("result/block-boundaries.tsv")).startsWith("boundary\th1_full\th1_step1\th2_full\th2_step1\tgcov_full\tgcov_step1"));
    }
    @Test void duplicateCommonIdsAreNotJoinedOrRemoved()throws Exception {
        Path request=prepare("R000");var lines=Files.readAllLines(temp.resolve("input.tsv"));Files.writeString(temp.resolve("input.tsv"),Files.readString(temp.resolve("input.tsv"))+lines.get(1)+"\n");var error=new ByteArrayOutputStream();assertEquals(2,run(request,error));assertTrue(error.toString().contains("Duplicate common SNP ID"));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
    @Test void routedHelpRequiresAlreadyAlignedSignedZ() {
        var out=new ByteArrayOutputStream();assertEquals(0,LdscToolsCli.run(new String[]{"rg","--help"},new PrintStream(out),System.err));assertTrue(out.toString().contains("same effect allele"));
        out.reset();assertEquals(0,LdscToolsCli.run(new String[]{"--help"},new PrintStream(out),System.err));assertTrue(out.toString().contains("ldsc-tools rg --request"));
    }
}
