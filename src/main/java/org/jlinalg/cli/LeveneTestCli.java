/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.stats.LeveneTest;

/** Explicit standalone native prepared-vector Levene test; no original R execution. */
public final class LeveneTestCli {
    private LeveneTestCli() { }
    private static final String SCOPE="Independent native Java one-way mean/median-centered Levene test. This does not evaluate general R formulas, car linear models, custom center functions, grouped tidy evaluation, other rstatix tests or author pipelines. Labels and row offsets remain literal; inputs are prepared vectors. No original R runtime is required.";
    private static final String ACTION="Required action: SKIP3 skips dependent work when permitted input/Java prerequisites are missing or unreadable. Supply them only where allowed. FAIL2 admits no complete result; preserve the exact request, partial case records, warnings and status, correct the named cause and retry NEW paths. Choose explicit OMIT to treat NaN/null group as missing; infinity is always rejected. Degenerate or numerically unresolved deviation variance admits no result. No failed case may be removed to call a partial batch complete. This command does not install software or award catalog/whole-paper credit.";

    public static void main(String[] args) {System.exit(run(args));}
    static int run(String[] args) {
        Path out=null;boolean owned=false;var rows=new ArrayList<Map<String,Object>>();
        try {
            if(args.length==1 && args[0].equals("--help")) {System.out.println("LeveneTestCli --request UTF8_JSON --out NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length!=4 || !args[0].equals("--request") || !args[2].equals("--out")) throw new IOException("Use --request JSON --out NEW_DIRECTORY");
            Path request=Path.of(args[1]).toAbsolutePath().normalize();out=Path.of(args[3]).toAbsolutePath().normalize();
            if(out.startsWith(request) || request.startsWith(out)) throw new IOException("Output overlaps protected input");
            if(!Files.isRegularFile(request,LinkOption.NOFOLLOW_LINKS)) throw new MissingPrerequisite("Regular permitted request unavailable");
            if(Files.size(request)>67108864) throw new IOException("Request exceeds64MiB; no input prefix admitted");
            byte[] input=Files.readAllBytes(request);
            String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();
            Object parsed=SimpleJson.parse(json);
            Map<String,Object> document=object(parsed);fields(document,Set.of("schema_version","cases"));
            if(!(document.get("schema_version") instanceof Number version) || version.doubleValue()!=1) throw new IOException("Require schema_version1");
            Object population=document.get("cases");if(!(population instanceof List<?> cases) || cases.isEmpty() || cases.size()>10000) throw new IOException("Declare1..10000 complete cases");
            Files.createDirectory(out);owned=true;Files.write(out.resolve("request.json"),input);write(out.resolve("WARNINGS.md"),SCOPE+"\n\n"+ACTION+"\n");
            var ids=new java.util.HashSet<String>();boolean failed=false;long observations=0;
            for(Object source:cases) {
                Map<String,Object> c=object(source);fields(c,Set.of("id","values","groups","center","missing_policy"));
                Object id=c.get("id");if(!(id instanceof String name) || name.isEmpty() || !ids.add(name)) throw new IOException("Nonempty unique literal case ids required");
                var row=new LinkedHashMap<String,Object>();row.put("id",id);
                try {
                    if(!(c.get("values") instanceof List<?> values) || !(c.get("groups") instanceof List<?> groups) || values.size()!=groups.size()) throw new IOException("Matching value/group arrays required");
                    observations+=values.size();if(observations>2000000) throw new IOException("Complete population exceeds2million rows; no prefix admitted");
                    double[] x=new double[values.size()];String[] g=new String[groups.size()];
                    for(int i=0;i<x.length;i++) {x[i]=number(values.get(i));Object group=groups.get(i);if(group!=null && !(group instanceof String)) throw new IOException("Literal string or null groups required");g[i]=(String)group;}
                    LeveneTest.Center center=LeveneTest.Center.valueOf(text(c.get("center")));
                    LeveneTest.MissingPolicy missing=LeveneTest.MissingPolicy.valueOf(text(c.get("missing_policy")));
                    var result=LeveneTest.test(x,g,center,missing);
                    row.put("status","COMPUTED_NATIVE_LEVENE");row.put("statistic",result.statistic());row.put("p",result.pValue());
                    row.put("df1",result.numeratorDegrees());row.put("df2",result.denominatorDegrees());row.put("center",result.center().name());
                    row.put("levels",result.levels());row.put("group_sizes",result.groupSizes());row.put("centers",result.centers());row.put("kept_rows",result.keptRows());row.put("omitted_rows",result.omittedRows());
                    row.put("warning_review_required",!result.omittedRows().isEmpty());
                } catch(IOException|IllegalArgumentException|ArithmeticException|NullPointerException invalid) {
                    failed=true;row.put("status","FAIL2_NATIVE_LEVENE_CASE_UNADMITTED");row.put("error_class",invalid.getClass().getName());row.put("cause",String.valueOf(invalid.getMessage()));
                }
                rows.add(row);
            }
            if(!java.util.Arrays.equals(input,Files.readAllBytes(request))) throw new IOException("Protected input changed during analysis");
            write(out.resolve("cases.json"),NextflowJson.json(rows)+"\n");
            write(out.resolve("WARNINGS.md"),SCOPE+"\n\n"+ACTION+"\n\n"+(failed?"FAIL2: One or more cases failed; successful entries remain retained but the complete batch is UNADMITTED.":"Review all per-case omitted_rows and warning_review_required fields. Explicit missing-row omissions do not establish biological validity.")+"\n");
            var manifest=new LinkedHashMap<String,Object>();
            for(String n:List.of("request.json","cases.json","WARNINGS.md")) manifest.put(n,pin(out.resolve(n)));
            write(out.resolve("MANIFEST.json"),NextflowJson.json(Map.of("files",manifest,"excluded",List.of("MANIFEST.json","analysis-status.json")))+"\n");
            String status=failed?"FAIL2_NATIVE_LEVENE_BATCH_UNADMITTED":"PASS_COMPLETE_NATIVE_LEVENE_BATCH";
            write(out.resolve("analysis-status.json"),NextflowJson.json(Map.of("status",status,"exit_code",failed?2:0,"result_admitted",!failed,"cases",rows.size(),"native_Java",true,"original_R_analysis_executed",false,"whole_paper_reproduced",false))+"\n");
            System.out.println(status+"\n"+ACTION);return failed?2:0;
        } catch(Exception invalid) {
            int code=invalid instanceof MissingPrerequisite || invalid instanceof java.nio.file.FileSystemException && !(invalid instanceof java.nio.file.FileAlreadyExistsException)?3:2;
            String status=code==3?"SKIP3_LEVENE_PREREQUISITE_UNAVAILABLE":"FAIL2_NATIVE_LEVENE_UNADMITTED";
            if(owned) try {write(out.resolve("partial-cases.json"),NextflowJson.json(rows)+"\n");write(out.resolve("WARNINGS_FAILURE.md"),status+"\n"+invalid+"\n"+ACTION+"\n");write(out.resolve("analysis-status.json"),NextflowJson.json(Map.of("status",status,"exit_code",code,"result_admitted",false,"cause",invalid.toString()))+"\n");} catch(IOException recording) {System.err.println("Failure recording also failed: "+recording);}
            System.err.println(status+" "+invalid+"\n"+ACTION);return code;
        }
    }
    private static void write(Path file,String value)throws IOException {Files.writeString(file,value,StandardCharsets.UTF_8);}
    private static Map<String,Object> object(Object value)throws IOException {
        if(!(value instanceof Map<?,?> map)) throw new IOException("JSON object required");var result=new LinkedHashMap<String,Object>();
        for(var e:map.entrySet()) {if(!(e.getKey() instanceof String key)) throw new IOException("String key required");result.put(key,e.getValue());}return result;
    }
    private static void fields(Map<String,Object> m,Set<String> required)throws IOException {if(!m.keySet().equals(required)) throw new IOException("Require exactly fields: "+required);}
    private static String text(Object value)throws IOException {if(value instanceof String s)return s;throw new IOException("Literal text required");}
    private static double number(Object value)throws IOException {
        if(value instanceof Number n)return n.doubleValue();if(value instanceof String s)return switch(s) {case "NaN" -> Double.NaN;case "+Inf" -> Double.POSITIVE_INFINITY;case "-Inf" -> Double.NEGATIVE_INFINITY;default -> throw new IOException("Only explicit NaN/+Inf/-Inf numeric tokens accepted");};
        throw new IOException("Numeric value or explicit NaN/+Inf/-Inf token required");
    }
    private static Map<String,Object> pin(Path path)throws IOException,NoSuchAlgorithmException {byte[] raw=Files.readAllBytes(path);return Map.of("bytes",raw.length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));}
    private static final class MissingPrerequisite extends IOException {
        private static final long serialVersionUID=1L;
        MissingPrerequisite(String message) {super(message);}
    }
}
