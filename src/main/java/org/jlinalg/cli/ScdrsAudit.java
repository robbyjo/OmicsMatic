/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.ScdrsJson.*;

/** Complete output coordinates and explicit BH families; no independent LOESS, score or graph refitting. */
final class ScdrsAudit {
    private ScdrsAudit() { }
    private static final List<String> SCORE=List.of("raw_score","norm_score","mc_pval","pval","nlog10_pval","zscore");
    private static final List<String> GROUP=List.of("n_cell","n_ctrl","assoc_mcp","assoc_mcz","hetero_mcp","hetero_mcz","n_fdr_0.05","n_fdr_0.1","n_fdr_0.2");
    private static final List<String> CORR=List.of("n_ctrl","corr_mcp","corr_mcz");
    private record Table(List<String> ids,List<String> columns,List<double[]> values) { }

    static Map<String,Object> inspect(ScdrsRequest.Request r,Path root,boolean writeDerived)throws IOException {
        Map<?,?> report=object(ScdrsJson.read(root.resolve("inspection/input-contract.json"),64000000));
        need(text(report.get("version")).equals(r.version())&&text(report.get("operation")).equals(r.operation()),"Source inspection version/operation differs");
        List<String> traits=strings(report.get("traits")),cells=strings(report.get("cells")),genes=strings(report.get("genes"));
        need(!traits.isEmpty()&&traits.size()<=r.limits().traits()&&new HashSet<>(traits).size()==traits.size(),"Trait inventory invalid");
        for(String trait:traits)need(trait.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}"),"Unsafe literal trait filename label");
        need(cells.size()<=r.limits().cells()&&genes.size()<=r.limits().genes()&&new HashSet<>(cells).size()==cells.size()&&new HashSet<>(genes).size()==genes.size(),"Expression identity capacities/uniqueness differ");
        if(!r.operation().equals("munge-gs")){
            need(cells.size()>=2&&genes.size()>=10,"No assessable retained expression identities");
            verifyIdentifiers(r,root,report,cells,genes);
        }
        List<String> calls=switch(r.operation()){
            case "munge-gs"->List.of("munge_gs");case "score"->List.of("compute_score");
            case "downstream"->List.of("perform_downstream");case "score-and-downstream"->List.of("compute_score","perform_downstream");
            default->throw new IOException("Unsupported saved scDRS operation");
        };
        need(strings(report.get("source_function_calls")).equals(calls),"Recorded source-function contract differs");
        Map<?,?> started=object(ScdrsJson.read(root.resolve("source-analysis-started.json"),1048576));
        need(text(started.get("version")).equals(r.version())&&strings(started.get("source_functions")).equals(calls),"Original source start marker differs");
        var requiredNames=new HashSet<String>();
        for(String name:traits){
            if(r.scoring()!=null){requiredNames.add(name+".score.gz");if(bool(r.scoring().get("return_control_raw"))||bool(r.scoring().get("return_control_normalized")))requiredNames.add(name+".full_score.gz");}
            if(r.downstream()!=null){
                for(String group:ScdrsRequest.names(r.downstream().get("groups")))requiredNames.add(name+".scdrs_group."+group.replace(' ','_').replace(',','_'));
                if(!ScdrsRequest.names(r.downstream().get("correlations")).isEmpty())requiredNames.add(name+".scdrs_cell_corr");
                if(bool(r.downstream().get("gene_analysis")))requiredNames.add(name+".scdrs_gene");
            }
        }
        if(r.operation().equals("munge-gs"))requiredNames.add("gene_sets.gs");
        List<String> expected=strings(report.get("expected_outputs"));var names=new HashSet<String>();
        try(var stream=Files.list(root.resolve("original"))){for(Path p:stream.toList()){need(Files.isRegularFile(p)&&!Files.isSymbolicLink(p),"Nonregular original output");names.add(p.getFileName().toString());}}
        need(names.equals(requiredNames)&&names.equals(new HashSet<>(expected))&&expected.size()==names.size(),"Missing, extra or duplicate original output files");
        Map<?,?> done=object(ScdrsJson.read(root.resolve("source-workflow-completed.json"),1048576));
        need(text(done.get("status")).equals("PASS_UNCHANGED_SCDRS_CLI_FUNCTIONS_EXECUTED")&&!bool(done.get("result_admitted"))&&text(done.get("version")).equals(r.version())&&strings(done.get("source_functions")).equals(calls),"Original source workflow has not completed");
        Map<?,?> process=object(ScdrsJson.read(root.resolve("source-process.json"),1048576));
        need(integer(process.get("exit_code"),0,0)==0&&bool(process.get("source_process_finished")),"Original source process did not exit successfully");
        integer(process.get("pid"),1,Long.MAX_VALUE);integer(process.get("elapsed_milliseconds"),0,1000L*r.limits().seconds());
        Map<?,?> command=object(ScdrsJson.read(root.resolve("source-process-command.json"),1048576));keys(command,"command","shell");need(!bool(command.get("shell")),"Original source used an unsupported shell");
        List<String> argv=strings(command.get("command"));need(argv.size()==10&&list(process.get("command")).equals(argv),"Recorded source commands differ");
        Map<?,?> profile=object(ScdrsJson.read(root.resolve("runtime-profile.json"),67108864));String producerRoot=ScdrsRuntime.posix(argv.get(9));
        for(String key:List.of("environment","source")){String runtimeRoot=text(profile.get(key));need(!producerRoot.equals(runtimeRoot)&&!producerRoot.startsWith(runtimeRoot+"/")&&!runtimeRoot.startsWith(producerRoot+"/"),"Recorded output overlaps private runtime roots");}
        need(argv.equals(List.of(text(profile.get("python")),"-s","-B",producerRoot+"/source-worker.py","--source",text(profile.get("source")),"--request",producerRoot+"/request-for-verification.json","--out",producerRoot)),"Source command does not bind saved worker/request/profile");
        Map<?,?> cleanup=object(ScdrsJson.read(root.resolve("source-process-cleanup.json"),1048576));
        need(!bool(cleanup.get("parent_still_alive"))&&list(cleanup.get("remaining_owned_pids")).isEmpty(),"Owned source processes were not confirmed stopped");
        var tables=new TreeMap<String,Object>();var derived=new TreeMap<String,Object>();
        boolean score=r.operation().equals("score")||r.operation().equals("score-and-downstream");
        if(r.operation().equals("munge-gs")){
            verifyGeneSets(root.resolve("original/gene_sets.gs"),traits,new HashSet<>(genes),(int)integer(r.geneSets().get("maximum_genes"),1,200000));
            tables.put("gene_sets.gs",map("traits",(long)traits.size(),"original_format_and_finite_weights_checked",true));
        }
        int controls=score?(int)integer(r.scoring().get("controls"),2,10000):0;
        for(String trait:traits){
            if(score){
                List<String> fullColumns=new ArrayList<>(SCORE);
                if(bool(r.scoring().get("return_control_raw")))for(int i=0;i<controls;i++)fullColumns.add("ctrl_raw_score_"+i);
                if(bool(r.scoring().get("return_control_normalized")))for(int i=0;i<controls;i++)fullColumns.add("ctrl_norm_score_"+i);
                String plain=trait+".score.gz",full=trait+".full_score.gz";
                String first=verifyScores(root.resolve("original").resolve(plain),SCORE,cells,controls);
                if(names.contains(full))need(first.equals(verifyScores(root.resolve("original").resolve(full),fullColumns,cells,controls)),"Score/full-score first six columns differ");
                tables.put(plain,map("rows",(long)cells.size(),"columns",6L,"all_values_finite",true,"literal_cell_order_retained",true));
                if(names.contains(full))tables.put(full,map("rows",(long)cells.size(),"columns",(long)fullColumns.size(),"all_values_finite",true));
            }
            if(r.downstream()!=null){
                if(!score)controls=(int)integer(report.get("downstream_controls"),2,10000);
                Map<?,?> groupValues=object(report.get("groups"));
                for(String annotation:ScdrsRequest.names(r.downstream().get("groups"))){
                    String name=trait+".scdrs_group."+annotation.replace(' ','_').replace(',','_');
                    Table table=readTable(root.resolve("original").resolve(name),GROUP,r.limits().cells());
                    var labels=new HashSet<>(strings(groupValues.get(annotation)));
                    need(labels.equals(new HashSet<>(table.ids()))&&labels.size()==table.ids().size(),"Original group identity inventory differs");
                    double[] p=new double[table.ids().size()];
                    long total=0;
                    for(int i=0;i<p.length;i++){
                        double[] v=table.values().get(i);int n=whole(v[0],1,r.limits().cells());total+=n;need(whole(v[1],2,10000)==controls,"Group control count differs");
                        mc(v[2],controls);mc(v[4],controls);p[i]=v[2];
                        int a=whole(v[6],0,n),b=whole(v[7],0,n),c=whole(v[8],0,n);need(a<=b&&b<=c,"FDR cell-count thresholds are inconsistent");
                    }
                    long expectedCells=score?cells.size():list(report.get("downstream_cell_ids")).size();
                    need(total==expectedCells,"Grouped-cell count differs from complete source cell intersection");
                    tables.put(name,map("rows",(long)p.length,"grouped_cells",total,"controls",(long)controls,"all_values_finite",true));
                    if(bool(r.downstream().get("group_bh"))){
                        double[] q=bh(p);double cutoff=number(r.downstream().get("group_fdr"));
                        String derivedName=trait+".group_bh."+annotation.replace(' ','_').replace(',','_')+".tsv";
                        StringBuilder text=new StringBuilder("group\tpval\tqval\tfdr_cutoff\tsignificant\n");
                        for(int i=0;i<p.length;i++)text.append(field(table.ids().get(i))).append('\t').append(p[i]).append('\t').append(q[i]).append('\t').append(cutoff).append('\t').append(q[i]<cutoff).append('\n');
                        Path path=root.resolve("native-derived").resolve(derivedName);
                        if(writeDerived){Files.createDirectories(path.getParent());Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
                        else need(Files.readString(path,StandardCharsets.UTF_8).equals(text.toString()),"Saved explicit-family BH output differs");
                        derived.put(derivedName,map("family","one trait and one annotation in this single dataset","tests",(long)p.length,"method","statsmodels0.13.5 fdr_bh arithmetic on literal exported P values","significance_rule","q < declared cutoff","cutoff",cutoff));
                    }
                }
                List<String> annotations=ScdrsRequest.names(r.downstream().get("correlations"));
                if(!annotations.isEmpty()){
                    String name=trait+".scdrs_cell_corr";Table table=readTable(root.resolve("original").resolve(name),CORR,100);
                    need(table.ids().equals(annotations),"Correlation annotations/order differ");
                    for(double[] v:table.values()){need(whole(v[0],2,10000)==controls,"Correlation control count differs");mc(v[1],controls);}
                    tables.put(name,map("rows",(long)table.ids().size(),"controls",(long)controls,"all_values_finite",true));
                }
                if(bool(r.downstream().get("gene_analysis"))){
                    String name=trait+".scdrs_gene";Table table=readTable(root.resolve("original").resolve(name),List.of("CORR","RANK"),r.limits().genes());
                    need(new HashSet<>(table.ids()).equals(new HashSet<>(genes))&&table.ids().size()==genes.size(),"Gene-priority identity inventory differs");
                    double last=Double.POSITIVE_INFINITY;
                    for(int i=0;i<table.values().size();i++){double[] v=table.values().get(i);need(v[0]>=-1.000001&&v[0]<=1.000001&&v[0]<=last&&v[1]==i,"Gene correlation order, rank or range differs");last=v[0];}
                    tables.put(name,map("rows",(long)table.ids().size(),"source_float32_rounding_retained",true,"all_values_finite",true));
                }
            }
        }
        var derivedNames=new HashSet<String>();if(Files.exists(root.resolve("native-derived")))try(var stream=Files.list(root.resolve("native-derived"))){for(Path p:stream.toList()){need(Files.isRegularFile(p)&&!Files.isSymbolicLink(p),"Nonregular native-derived output");derivedNames.add(p.getFileName().toString());}}
        need(derivedNames.equals(derived.keySet()),"Native-derived output inventory differs");
        return map("schema_version",1L,"version",r.version(),"operation",r.operation(),"traits",traits,"tables",tables,"native_derived",derived,
            "all_output_coordinates_checked",true,"independent_scoring_loess_covariate_or_graph_refit",false,"producer_authenticated",false,
            "warnings",report.get("warnings"));
    }
    // Statsmodels0.13.5 fdrcorrection: sorted P divided by arange(1,n+1)/n,
    // followed by reverse cumulative minimum and the explicit upper cap1.
    static double[] bh(double[] p)throws IOException {
        need(p.length>0&&p.length<=1_000_000,"Bounded nonempty BH family required");Integer[] order=new Integer[p.length];
        for(int i=0;i<p.length;i++){probability(p[i]);order[i]=i;}Arrays.sort(order,(a,b)->Double.compare(p[a],p[b]));
        double[] result=new double[p.length];double previous=Double.POSITIVE_INFINITY;
        for(int i=p.length-1;i>=0;i--){previous=Math.min(previous,p[order[i]]/((i+1.0)/p.length));result[order[i]]=Math.min(1,previous);}return result;
    }
    private static int whole(double v,int min,int max)throws IOException {need(v==Math.rint(v)&&v>=min&&v<=max,"Integer output count/rank outside bounds");return (int)v;}
    private static void probability(double p)throws IOException {need(Double.isFinite(p)&&p>=0&&p<=1,"P outside[0,1]");}
    private static void mc(double p,int controls)throws IOException {
        probability(p);need(p>0&&p>=(1.0/(controls+1))*(1-1e-6),"Monte Carlo P below plus-one lower bound");
        double count=p*(controls+1);need(Math.abs(count-Math.rint(count))<=1e-6*(controls+1),"Monte Carlo probability is off the float32 source grid");
    }
    private static List<String> strings(Object value)throws IOException {var out=new ArrayList<String>();for(Object v:list(value))out.add(text(v));return List.copyOf(out);}
    private static void verifyIdentifiers(ScdrsRequest.Request r,Path root,Map<?,?> report,List<String> cells,List<String> genes)throws IOException {
        List<?> raw=list(report.get("raw_shape")),retained=list(report.get("retained_shape"));need(raw.size()==2&&retained.size()==2,"AnnData shape evidence differs");
        int rawCells=(int)integer(raw.get(0),cells.size(),r.limits().cells()),rawGenes=(int)integer(raw.get(1),genes.size(),r.limits().genes());
        need(integer(retained.get(0),2,r.limits().cells())==cells.size()&&integer(retained.get(1),10,r.limits().genes())==genes.size(),"Retained AnnData shape differs from IDs");
        var seenCells=new HashSet<String>();var seenGenes=new HashSet<String>();var keptCells=new ArrayList<String>();var keptGenes=new ArrayList<String>();
        try(Reader reader=new Reader(root.resolve("inspection/identifier-dispositions.tsv"))){
            need(List.of("kind","identifier","source_disposition").equals(reader.row()),"Identifier-disposition header differs");
            for(List<String> row;(row=reader.row())!=null;){
                need(row.size()==3&&(row.get(0).equals("cell")||row.get(0).equals("gene")),"Invalid identifier disposition");boolean cell=row.get(0).equals("cell");
                need(!row.get(1).isEmpty()&&(cell?seenCells:seenGenes).add(row.get(1)),"Duplicate/empty original identifier disposition");
                if(row.get(2).equals("retained"))(cell?keptCells:keptGenes).add(row.get(1));
                else need(bool(r.expression().get("filter_data"))&&row.get(2).equals("removed-by-requested-upstream-filter"),"Undeclared source filtering/ID disposition");
                need(seenCells.size()<=rawCells&&seenGenes.size()<=rawGenes,"Extra original identifier dispositions");
            }
        }need(seenCells.size()==rawCells&&seenGenes.size()==rawGenes&&keptCells.equals(cells)&&keptGenes.equals(genes),"Complete original/retained identifier ledger differs");
    }
    private static String verifyScores(Path path,List<String> columns,List<String> cells,int controls)throws IOException {
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
        try(Reader reader=new Reader(path)){
            List<String> header=reader.row();need(header!=null&&header.size()==columns.size()+1&&header.subList(1,header.size()).equals(columns),"Original score column schema differs");
            for(String id:cells){
                List<String> row=reader.row();need(row!=null&&row.size()==header.size()&&row.get(0).equals(id),"Original score cell identity/order or complete dimensions differ");
                double[] v=numbers(row);mc(v[2],controls);probability(v[3]);need(v[3]>0&&v[3]>=(1.0/(cells.size()*(double)controls+1))*(1-1e-6),"Pooled empirical P outside source bounds");
                need(Math.abs(v[4]+Math.log10(v[3]))<=5e-6&&v[5]>=-10&&v[5]<=10,"Source -log10(P) or clipped Z contract differs");
                if(v[3]==1)need(v[5]==-10,"Source p=1 must retain explicit Z=-10");
                digest.update((String.join("\t",row.subList(0,7))+"\n").getBytes(StandardCharsets.UTF_8));
            }
            need(reader.row()==null,"Extra original score rows");
        }return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static Table readTable(Path path,List<String> columns,int maximum)throws IOException {
        var ids=new ArrayList<String>();var values=new ArrayList<double[]>();var seen=new HashSet<String>();
        try(Reader reader=new Reader(path)){
            List<String> header=reader.row();need(header!=null&&header.size()==columns.size()+1&&header.subList(1,header.size()).equals(columns),"Original table columns differ: "+path.getFileName());
            for(List<String> row;(row=reader.row())!=null;){need(row.size()==header.size()&&!row.get(0).isEmpty()&&seen.add(row.get(0))&&ids.size()<maximum,"Original table identity, dimensions or capacity differs");ids.add(row.get(0));values.add(numbers(row));}
        }need(!ids.isEmpty(),"Empty original result table");return new Table(List.copyOf(ids),columns,List.copyOf(values));
    }
    private static double[] numbers(List<String> row)throws IOException {
        double[] result=new double[row.size()-1];
        for(int i=0;i<result.length;i++)try{result[i]=Double.parseDouble(row.get(i+1));need(Double.isFinite(result[i]),"Nonfinite original numeric output; no result or repair");}catch(NumberFormatException e){throw new IOException("Invalid original numeric output",e);}return result;
    }
    private static void verifyGeneSets(Path path,List<String> traits,Set<String> genes,int maximum)throws IOException {
        try(Reader reader=new Reader(path)){
            need(List.of("TRAIT","GENESET").equals(reader.row()),"Original gene-set header differs");
            for(String trait:traits){
                List<String> row=reader.row();need(row!=null&&row.size()==2&&row.get(0).equals(trait),"Original gene-set trait order/dimensions differ");String[] entries=row.get(1).split(",",-1);need(entries.length>=1&&entries.length<=maximum,"Original selected gene count outside bounds");
                var seen=new HashSet<String>();for(String entry:entries){String[] pair=entry.split(":",-1);need(pair.length==2&&genes.contains(pair[0])&&seen.add(pair[0]),"Original selected gene identity/weight syntax differs");numbers(List.of(pair[0],pair[1]));}
            }need(reader.row()==null,"Extra original gene-set traits");
        }
    }
    private static String field(String s){return s.indexOf('"')>=0||s.indexOf('\t')>=0?"\""+s.replace("\"","\"\"")+"\"":s;}
    private static final class Reader implements AutoCloseable {
        private final BufferedReader input;
        Reader(Path path)throws IOException {
            InputStream raw=Files.newInputStream(path);
            try{if(path.getFileName().toString().endsWith(".gz"))raw=new GZIPInputStream(raw);input=new BufferedReader(new InputStreamReader(raw,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
            catch(IOException|RuntimeException e){raw.close();throw e;}
        }
        List<String> row()throws IOException {
            StringBuilder line=new StringBuilder();int c;
            while((c=input.read())!=-1&&c!='\n'){need(line.length()<2_000_000,"Original table line capacity exceeded");line.append((char)c);}
            if(c==-1&&line.isEmpty())return null;if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);
            var fields=new ArrayList<String>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
            for(int i=0;i<=line.length();i++){
                char ch=i==line.length()?'\t':line.charAt(i);
                if(quoted){if(ch=='"'){if(i+1<line.length()&&line.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}else field.append(ch);}
                else if(ch=='\t'){fields.add(field.toString());field.setLength(0);closed=false;}
                else if(ch=='"'&&field.isEmpty()&&!closed)quoted=true;
                else{need(!closed,"Characters after quoted TSV field");field.append(ch);}
            }need(!quoted,"Unterminated quoted TSV field");return fields;
        }
        @Override public void close()throws IOException {input.close();}
    }
}
