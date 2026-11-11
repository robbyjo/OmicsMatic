/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.GseapyStatistics;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Compare complete original stats-level outcomes, including version differences and undefined states. */
public final class GseapyStatisticsMatrix {
    private GseapyStatisticsMatrix(){ }
    private static long numeric,identities,nans,zeros;
    private static double number(Object x)throws Exception{return Double.longBitsToDouble(Long.parseUnsignedLong(text(x),16));}
    private static List<String> strings(Object x)throws Exception{var out=new ArrayList<String>();for(Object item:list(x))out.add(text(item));return out;}
    private static double[] numbers(Object x)throws Exception{var raw=list(x);double[] out=new double[raw.size()];for(int i=0;i<out.length;i++)out[i]=number(raw.get(i));return out;}
    private static int count(Object x)throws Exception{return Math.toIntExact(integer(x,0,Integer.MAX_VALUE));}
    private static GseapyStatistics.Edition edition(Object x)throws Exception{return GseapyStatistics.Edition.valueOf("V"+text(x).replace('.','_'));}
    private static void same(double actual,double expected,double relative,String label)throws Exception{
        if(Double.isNaN(expected)){require(Double.isNaN(actual),"Undefined source value repaired: "+label);nans++;}
        else if(!Double.isFinite(expected))require(actual==expected,"Nonfinite source sign changed: "+label);
        else if(relative==0||expected==0){require(Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits(expected),"Exact source state changed: "+label+" "+actual+" != "+expected);if(expected==0)zeros++;}
        else require(Double.isFinite(actual)&&Math.abs(actual-expected)<=relative*Math.abs(expected)+Double.MIN_VALUE*4,"Source value outside declared bound: "+label+" "+actual+" != "+expected);
        numeric++;
    }
    private static void reject(Runnable action)throws Exception{boolean failed=false;try{action.run();}catch(IllegalArgumentException|NullPointerException expected){failed=true;}require(failed,"Invalid native input was admitted");}
    private static void combined(GseapyStatistics.Row actual,Map<String,Object> expected)throws Exception{
        double wanted=number(expected.get("combined"));
        if(!Double.isFinite(wanted)||wanted==0){same(actual.combinedScore(),wanted,0,"combined score kind");return;}
        double originalP=number(expected.get("p"));
        // The independently checked tail can differ by rounding. -log(p) is
        // ill-conditioned near1; propagate that measured tail difference,
        // plus a bounded allowance for the original NumPy versus Java log.
        double tailContribution=Math.abs(Math.log(actual.pValue())-Math.log(originalP))*Math.abs(actual.oddsRatio());
        double bound=tailContribution+16*Math.ulp(wanted);
        require(Double.isFinite(actual.combinedScore())&&Math.abs(actual.combinedScore()-wanted)<=bound,"Combined score outside measured-tail/log rounding envelope: "+actual.combinedScore()+" != "+wanted);numeric++;
    }
    public static void main(String[] args)throws Exception{
        require(args.length==2,"Require original cases and NEW result JSON");Path out=Path.of(args[1]);require(!Files.exists(out),"Existing native result protected");var source=CellPhoneDbWorkflowRuntime.read(Path.of(args[0]),134217728);long ora=0,tails=0,corrections=0;
        for(Object item:list(source.get("cases"))){
            var c=object(item);var sets=new LinkedHashMap<String,List<String>>();for(var entry:object(c.get("sets")).entrySet())sets.put(entry.getKey(),strings(entry.getValue()));
            var background=c.get("background") instanceof List<?>?new GseapyStatistics.Universe(Set.copyOf(strings(c.get("background")))):new GseapyStatistics.Population(count(c.get("background")));
            var rows=GseapyStatistics.overRepresentation(edition(c.get("edition")),strings(c.get("query")),sets,background,finite(c.get("alpha"),0,1));var expected=list(c.get("rows"));require(rows.size()==expected.size(),"Original zero-overlap/correction family differs");
            for(int i=0;i<rows.size();i++){
                var a=rows.get(i);var e=object(expected.get(i));require(a.term().equals(text(e.get("term")))&&a.querySize()==count(e.get("query_size"))&&a.overlap()==count(e.get("overlap"))&&a.termSize()==count(e.get("term_size"))&&a.hits().equals(strings(e.get("hits"))),"Literal term/gene/size state changed: "+c.get("id"));identities+=5+a.hits().size();
                same(a.pValue(),number(e.get("p")),5e-12,"ORA tail");same(a.adjustedPValue(),number(e.get("q")),5e-12,"ORA adjusted");same(a.oddsRatio(),number(e.get("odds")),0,"edition odds");same(a.rejected(),number(e.get("reject")),0,"original reject");
                require(a.combinedScoreAvailable()==(e.get("combined")!=null),"Combined score invented for1.0.4");if(a.combinedScoreAvailable())combined(a,e);else require(Double.isNaN(a.combinedScore()),"Absent combined score was fabricated");
                if(Double.isNaN(a.pValue())||a.pValue()==0||a.oddsRatio()<0)require(!a.warnings().isEmpty(),"Source diagnostic lacks a required action");
            }
            ora++;
        }
        for(Object item:list(source.get("tails"))){var row=object(item);var values=list(row.get("counts"));int bg=count(values.get(0)),k=count(values.get(1)),m=count(values.get(2)),x=count(values.get(3));same(GseapyStatistics.upperTail(bg,k,m,x),number(row.get("p")),5e-12,"complete tail "+values);for(var e:object(row.get("odds")).entrySet())same(GseapyStatistics.oddsRatio(edition(e.getKey()),bg,k,m,x),number(e.getValue()),0,"count-grid odds");tails++;}
        for(Object item:list(source.get("corrections"))){var row=object(item);var method=text(row.get("method")).equals("bonferroni")?GseapyStatistics.Correction.BONFERRONI:GseapyStatistics.Correction.BENJAMINI_HOCHBERG;double[] p=numbers(row.get("p")),expected=numbers(row.get("q")),mask=numbers(row.get("reject"));var result=GseapyStatistics.correct(p,finite(row.get("alpha"),0,1),method);double[] q=result.adjusted(),reject=result.rejected();require(q.length==p.length&&reject.length==p.length,"Incomplete correction family");for(int i=0;i<p.length;i++){same(q[i],expected[i],1e-15,"correction");same(reject[i],mask[i],0,"rejection mask");}if(p.length>0){double original=result.adjusted()[0];p[0]=17;q[0]=18;require(Double.doubleToLongBits(original)==Double.doubleToLongBits(result.adjusted()[0]),"Correction arrays were not copied");}corrections++;}
        List<Runnable> invalid=List.of(()->new GseapyStatistics.Population(-1),()->GseapyStatistics.upperTail(-1,1,1,1),()->GseapyStatistics.upperTail(10,-1,1,0),()->GseapyStatistics.upperTail(10,1,1,2),()->GseapyStatistics.correct(new double[]{-0.1},0.05,GseapyStatistics.Correction.BONFERRONI),()->GseapyStatistics.correct(new double[]{1.1},0.05,GseapyStatistics.Correction.BONFERRONI),()->GseapyStatistics.correct(new double[]{Double.POSITIVE_INFINITY},0.05,GseapyStatistics.Correction.BONFERRONI),()->GseapyStatistics.correct(new double[]{0.1},Double.NaN,GseapyStatistics.Correction.BONFERRONI),()->GseapyStatistics.correct(new double[]{0.1},-0.1,GseapyStatistics.Correction.BONFERRONI),()->GseapyStatistics.correct(new double[]{0.1},1.1,GseapyStatistics.Correction.BONFERRONI),()->GseapyStatistics.correct(new double[]{0.1},0.05,null),()->GseapyStatistics.overRepresentation(null,List.of("a"),Map.of("x",List.of("a")),new GseapyStatistics.Population(10),0.05),()->GseapyStatistics.overRepresentation(GseapyStatistics.Edition.V1_1_8,Arrays.asList("a",null),Map.of(),new GseapyStatistics.Population(10),0.05));
        for(Runnable action:invalid)reject(action);
        var mutable=new ArrayList<>(List.of("a","b"));var universe=new GseapyStatistics.Universe(Set.copyOf(mutable));var result=GseapyStatistics.overRepresentation(GseapyStatistics.Edition.V1_1_8,mutable,Map.of("x",mutable),universe,0.05);mutable.clear();require(result.size()==1&&result.get(0).hits().equals(List.of("a","b")),"Caller mutation changed results");boolean immutable=false;try{result.get(0).hits().clear();}catch(UnsupportedOperationException expected){immutable=true;}require(immutable,"Hit-list getter is mutable");
        var report=new LinkedHashMap<String,Object>();report.put("status","passed-complete-four-edition-native-GSEApy-stats-component-only");report.put("ora_cases",ora);report.put("tail_cases",tails);report.put("correction_cases",corrections);report.put("numeric_states",numeric);report.put("identity_states",identities);report.put("nan_states",nans);report.put("exact_zero_states",zeros);report.put("invalid_inputs_rejected",invalid.size());report.put("high_level_adapter_admitted",false);report.put("implementation_credit",false);report.put("whole_goal_complete",false);NextflowFiles.writeJson(out,report);System.out.println(NextflowJson.json(report));
    }
}
