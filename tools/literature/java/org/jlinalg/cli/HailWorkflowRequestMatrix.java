/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Actual-candidate tests for typed scientific bindings, input immutability and rejected implicit operations. */
public final class HailWorkflowRequestMatrix {
    private HailWorkflowRequestMatrix() { }
    @SuppressWarnings("unchecked") private static Map<String,Object> obj(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Object> array(Object value){return (List<Object>)value;}
    private static Map<String,Object> c(Map<String,Object> root,int i){return obj(array(root.get("cases")).get(i));}
    private static Map<String,Object> step(Map<String,Object> root,int i){return obj(array(c(root,1).get("steps")).get(i));}
    private static Map<String,Object> job(Map<String,Object> root,int i){return obj(array(c(root,7).get("jobs")).get(i));}
    private static Map<String,Object> copy(String value)throws IOException{return obj(NextflowParameters.parse(value,16777216,1000000));}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void rejects(String id,String source,Path parent,Consumer<Map<String,Object>> mutation,List<String> rejected)throws IOException {
        var m=copy(source);mutation.accept(m);
        try{HailWorkflowRequest.parse(m,parent);}catch(IOException expected){rejected.add(id);return;}
        throw new AssertionError("Must reject "+id);
    }
    public static void main(String[] args)throws Exception {
        require(args.length==3,"Require typed request fixture, report and expected candidate JAR");Path candidate=Path.of(args[2]).toRealPath();
        for(Class<?> type:List.of(HailWorkflowRequest.class,HailWorkflowRequestMatrix.class))require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(candidate),"Use actual candidate classes");
        Path fixture=Path.of(args[0]).toAbsolutePath().normalize();String source=NextflowFiles.utf8(NextflowFiles.bytes(fixture,16777216));var m=copy(source);String before=NextflowJson.json(m);var r=HailWorkflowRequest.parse(m,fixture.getParent());require(before.equals(NextflowJson.json(m)),"Parsing must preserve the caller's complete request");require(r.cases().size()==10&&r.captures().size()==8&&r.executables().size()==1&&!r.needsService(),"Complete case/input/executable binding family");require(obj(r.cases().get(3).get("input")).get("path").equals(r.out().resolve("worker/filtered-qc/result.mt").toString()),"Earlier producer binding");require(obj(r.cases().get(5).get("input")).get("path").equals(r.out().resolve("worker/make-synthetic-summary/result.mt").toString()),"Summary-resource producer binding");
        require(obj(r.cases().get(9).get("input")).get("path").toString().endsWith("mixed.vcf.bgz"),"Captured input must retain its compression/format basename");
        for(var capture:r.captures())require(capture.source().isAbsolute()&&capture.id().matches("input[0-9]{4}"),"Complete source capture identity");
        var legacy=copy(source);legacy.put("edition","0.2.130");legacy.put("cases",List.of(c(legacy,6)));var lr=HailWorkflowRequest.parse(legacy,fixture.getParent());require(lr.cases().size()==1&&lr.captures().size()==2&&lr.edition().equals("0.2.130"),"Explicit legacy liftover binding");var service=copy(source);c(service,7).put("backend","SERVICE");require(HailWorkflowRequest.parse(service,fixture.getParent()).needsService(),"SERVICE must be recognized for explicit unconfigured-backend skip before execution");var rejected=new ArrayList<String>();
        rejects("unknown-top-level-field",source,fixture.getParent(),v->v.put("python_code","print(1)"),rejected);
        rejects("unreported-edition-substitution",source,fixture.getParent(),v->v.put("edition","latest"),rejected);
        rejects("case-folded-duplicate-case",source,fixture.getParent(),v->c(v,1).put("id","RAW-MIXED"),rejected);
        rejects("future-matrix-producer",source,fixture.getParent(),v->obj(c(v,3).get("input")).put("from_case","summary-resource-read"),rejected);
        rejects("silent-source-build-substitution",source,fixture.getParent(),v->c(v,6).put("source_reference","GRCh38"),rejected);
        rejects("implicit-allele-repair",source,fixture.getParent(),v->c(v,6).put("allele_policy","AUTO_REPAIR"),rejected);
        rejects("missing-unmapped-policy",source,fixture.getParent(),v->c(v,6).remove("unmapped_policy"),rejected);
        rejects("noninteger-complete-capacity",source,fixture.getParent(),v->obj(v.get("limits")).put("maximum_rows",.5),rejected);
        rejects("negative-output-capacity",source,fixture.getParent(),v->v.put("maximum_output_bytes",-1),rejected);
        rejects("empty-case-batch",source,fixture.getParent(),v->v.put("cases",List.of()),rejected);
        rejects("unknown-original-operation",source,fixture.getParent(),v->step(v,0).put("operation","guess_QC"),rejected);
        rejects("missing-explicit-keep",source,fixture.getParent(),v->step(v,1).remove("keep"),rejected);
        rejects("string-is-not-boolean",source,fixture.getParent(),v->step(v,1).put("keep","true"),rejected);
        rejects("python-expression-source",source,fixture.getParent(),v->step(v,1).put("predicate","__import__('os').system('true')"),rejected);
        rejects("unsupported-executable-expression-function",source,fixture.getParent(),v->step(v,1).put("predicate",Map.of("function","eval","args",List.of(Map.of("literal","True")))),rejected);
        rejects("ambiguous-expression-axis",source,fixture.getParent(),v->step(v,1).put("predicate",Map.of("field",Map.of("axis","auto","path",List.of("DP")))),rejected);
        rejects("untyped-missing-literal",source,fixture.getParent(),v->{var node=new LinkedHashMap<String,Object>();node.put("literal",null);step(v,1).put("predicate",node);},rejected);
        rejects("wrong-original-function-arity",source,fixture.getParent(),v->step(v,1).put("predicate",Map.of("function","sqrt","args",List.of(Map.of("literal",1),Map.of("literal",2)))),rejected);
        rejects("cyclic-batch-dependency",source,fixture.getParent(),v->job(v,0).put("depends_on",List.of("reduce")),rejected);
        rejects("duplicate-batch-output",source,fixture.getParent(),v->job(v,0).put("outputs",List.of("table","table")),rejected);
        rejects("unknown-input-resource",source,fixture.getParent(),v->array(job(v,0).get("argv")).set(1,Map.of("input","not-declared")),rejected);
        rejects("future-job-resource",source,fixture.getParent(),v->array(job(v,0).get("argv")).set(1,Map.of("job","reduce","output","table")),rejected);
        rejects("unknown-current-output",source,fixture.getParent(),v->array(job(v,0).get("argv")).set(2,Map.of("output","not-declared")),rejected);
        rejects("implicit-resource-in-literal-argument",source,fixture.getParent(),v->array(job(v,0).get("argv")).set(1,"__RESOURCE_FILE__1"),rejected);
        rejects("nonliteral-executable",source,fixture.getParent(),v->array(job(v,0).get("argv")).set(0,Map.of("input","program")),rejected);
        rejects("unknown-backend",source,fixture.getParent(),v->c(v,7).put("backend","automatic-cloud"),rejected);
        NextflowFiles.writeJson(Path.of(args[1]).toAbsolutePath().normalize(),Map.of("status","PASS_ACTUAL_CANDIDATE_TYPED_HAIL_REQUESTS_ONLY","positive_profiles",3,"rejected_cases",rejected,"request_preserved",true,"candidate",candidate.toString(),"implementation_credit",false,"whole_goal_complete",false));System.out.println("Typed request profiles:3; meaningful rejections:"+rejected.size());
    }
}
