/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.GctaWorkflowFiles.*;

/** Original model exports and declared estimator checks; no invented internal model state. */
final class GctaMtcojoAudit {
    private GctaMtcojoAudit() { }
    static final String WARNING="**MTCOJO SOURCE SCOPE:** GWAS effects remain on their supplied scale. In this exact 1.94.1 source, sample/population prevalence columns are parsed and stored but are not used in downstream calculations; no liability conversion is claimed. Retain both declarations and the original outputs. Qualify the actual reported edition for a prevalence-dependent paper analysis. Original LDSC accuracy warnings remain material. Source matrices use six significant digits, and GSMR bxy/SE log entries use six fixed decimals; these are available exports, not recovered full-precision internal state. Genetic correlations/intercepts are not clamped, projected or replaced. Missing/failed GSMR estimates are not silently replaced by an rg-based estimate.\n\n";
    static void outputs(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report) throws Exception {
        var data=input.mtcojo();if(data==null)throw new IOException("Missing prepared mtCOJO inputs");
        Path logFile=root.resolve("worker/result.log");if(Files.size(logFile)>number(r,"maximum_log_bytes"))throw new IOException("maximum_log_bytes exceeded by mtCOJO log");
        String raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(logFile))).toString();
        if(!raw.contains("The LD score regression analyses completed.")||!raw.contains("mtCOJO analysis completed."))throw new IOException("Original mtCOJO/LDSC did not establish completion");
        var traits=list(r.get("traits"),"traits");int n=traits.size();var lines=raw.lines().toList();
        var intercept=matrix(lines,"Intercept:",n);var genetic=matrix(lines,"rg:",n);
        for(int i=0;i<n;i++) {
            if(genetic.get(i).get(i)<=0)throw new IOException("Original mtCOJO lacks a positive finite source h2 estimate");
            for(int j=0;j<i;j++)if(intercept.get(i).get(j).doubleValue()!=intercept.get(j).get(i).doubleValue()||genetic.get(i).get(j).doubleValue()!=genetic.get(j).get(i).doubleValue())throw new IOException("Asymmetric original mtCOJO parameter export");
        }
        var causal=new ArrayList<Map<String,Object>>();String mode=text(r,"causal_effect_mode");
        if(mode.equals("GSMR")) {
            if(raw.contains("bxy is estimated from rg."))throw new IOException("Original GSMR failed and entered its rg fallback path; no complete declared GSMR result. Retain all partial/source outputs");
            int cursor=0;
            for(int i=1;i<n;i++) {
                String name=text(object(traits.get(i),"trait"),"name"),start="GSMR analysis for covariate #"+i+" ("+name+") ...",end="GSMR analysis for covariate #"+i+" ("+name+") completed.";
                int first=raw.indexOf(start,cursor),last=first<0?-1:raw.indexOf(end,first);if(first<0||last<0)throw new IOException("Missing completed original GSMR model for literal covariate "+name);
                String block=raw.substring(first,last);cursor=last+end.length();
                var count=Pattern.compile("(?m)^(\\d+) index SNPs are retained after HEIDI-outlier analysis\\.").matcher(block);
                if(!count.find())throw new IOException("Original GSMR did not export its retained instrument count");
                long instruments=Long.parseLong(count.group(1));if(instruments<Math.max(1,number(r,"minimum_instruments")))throw new IOException("Original GSMR retained too few instruments for the declared model");
                var coefficients=Pattern.compile("(?m)^bxy ([^ \\r\\n]+) ([^ \\r\\n]+)\\s*$").matcher(block);
                if(!coefficients.find())throw new IOException("Original GSMR causal-effect/SE export missing");
                double b=finite(coefficients.group(1),"GSMR causal effect"),se=finite(coefficients.group(2),"GSMR causal SE");if(se<0)throw new IOException("Negative original GSMR causal SE");
                causal.add(Map.of("trait",name,"exported_bxy",b,"exported_SE",se,"retained_instruments",instruments,"precision","Original to_string six fixed decimals; printed zero does not recover an internal exact zero"));
            }
        }else {
            if(raw.contains("GSMR analysis for covariate #"))throw new IOException("Supplied-causal-effect mode unexpectedly invoked GSMR; no estimator substitution admitted");
            var supplied=object(r.get("causal_effects"),"causal effects");for(int i=1;i<n;i++){String name=text(object(traits.get(i),"trait"),"name");causal.add(Map.of("trait",name,"supplied_bxy",supplied.get(name),"precision","Exact captured JSON number and generated original bxy file; no inferred SE"));}
        }
        Path table=root.resolve("worker/result.mtcojo.cma");var seen=new LinkedHashSet<String>();boolean[] first={true};long[] flips={0};
        rows(table,r,number(r,"maximum_result_rows")+1,f->{
            if(first[0]){if(!Arrays.equals(f,new String[]{"SNP","A1","A2","freq","b","se","p","N","bC","bC_se","bC_pval"}))throw new IOException("Original mtCOJO conditional schema differs");first[0]=false;return;}
            width(f,11,"mtCOJO conditional output");var target=data.summaries().get(0).get(f[0]);
            if(target==null||!data.common().contains(f[0])||!seen.add(f[0])||!GctaMtcojoFiles.pair(f[1],f[2],target.get(1),target.get(2)))throw new IOException("Original mtCOJO output marker/allele population differs");
            for(int i=3;i<f.length;i++)finite(f[i],"mtCOJO result");double frequency=Double.parseDouble(f[3]);
            if(frequency<0||frequency>1||Double.parseDouble(f[5])<=0||Double.parseDouble(f[7])<=0||Double.parseDouble(f[9])<=0)throw new IOException("Undefined original mtCOJO frequency, SE or sample size");
            for(int i:new int[]{6,10})if(Double.parseDouble(f[i])<0||Double.parseDouble(f[i])>1)throw new IOException("Original mtCOJO P outside 0..1");
            boolean swap=!f[1].equals(target.get(1));if(swap)flips[0]++;
            for(int i=3;i<=7;i++) {
                double expected=Double.parseDouble(target.get(i));if(swap&&i==3)expected=1-expected;if(swap&&i==4)expected=-expected;
                double observed=Double.parseDouble(f[i]),bound=Math.abs(expected)*5.00001e-6+16*Math.ulp(expected);
                if(Math.abs(observed-expected)>bound)throw new IOException("Original mtCOJO marginal field differs from its supplied target beyond source six-significant-digit rounding");
            }
        });
        if(first[0]||seen.isEmpty())throw new IOException("No complete original mtCOJO conditional table");
        String expectedCount="There are "+seen.size()+" SNPs in common between the target trait and all the covariate trait(s).";
        if(!raw.contains(expectedCount))throw new IOException("Original mtCOJO table population differs from its declared completed model");
        var exports=new TreeMap<String,Object>();try(var paths=Files.list(root.resolve("worker"))){for(Path p:paths.filter(Files::isRegularFile).toList())if(p.getFileName().toString().startsWith("result."))exports.put(p.getFileName().toString(),Map.of("bytes",Files.size(p),"sha256",FollowupSupport.hash(p)));}
        report.put("trait_order",data.intake().get("trait_order"));report.put("effect_scale","as_supplied; source prevalence columns are retained metadata, not a claimed liability conversion");
        report.put("causal_effect_mode",mode);report.put("causal_effects",causal);report.put("source_LDSC_intercepts",intercept);report.put("source_h2_diagonal_rg_off_diagonal",genetic);
        report.put("source_parameter_precision","Original matrix blocks use six significant digits; per-trait and GSMR bxy/SE summaries use six fixed decimals. Do not invent unexported internal precision");
        report.put("source_warnings",lines.stream().filter(s->s.startsWith("Warning:")).toList());report.put("conditional_rows",seen.size());report.put("target_allele_swaps",flips[0]);
        report.put("input_common_markers_not_returned",data.common().stream().filter(id->!seen.contains(id)).toList());report.put("original_exports",exports);
        report.put("filter_scope","Original frequency/monomorphic/allele/availability filters and all available diagnostics are retained. Missing output rows are reported, not imputed or assigned invented exclusion causes");
    }
    private static List<List<Double>> matrix(List<String> lines,String label,int n) throws IOException {
        int start=lines.indexOf(label);if(start<0||start+n>=lines.size())throw new IOException("Missing complete original mtCOJO matrix: "+label);
        var result=new ArrayList<List<Double>>();
        for(int i=0;i<n;i++){String[] f=lines.get(start+i+1).strip().split("[ \\t]+");width(f,n,"mtCOJO matrix");var row=new ArrayList<Double>();for(String value:f)row.add(finite(value,"mtCOJO matrix"));result.add(List.copyOf(row));}
        return List.copyOf(result);
    }
}
