/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;
import static org.jlinalg.cli.CiceroWorkflowInputs.C_ORDER;
import org.jlinalg.cli.CiceroWorkflowInputs.Population;
import org.jlinalg.singlecell.CiceroCoaccessibility;

/** Independent source-domain axis, nearest-neighbor, aggregation, covariance/penalty and connection audits.
 * Original FNN selection/R RNG, distance calibration and graphical-lasso estimation remain upstream. */
final class CiceroWorkflowAudit {
    private CiceroWorkflowAudit() { }
    static final class Meter {
        private long left,used;
        Meter(long maximum){left=maximum;}
        void spend(long n){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Native audit interrupted; no partial result admitted");if(n<0||n>left)throw new IllegalArgumentException("Native audit exceeds maximum_audit_operations; no partial result admitted");left-=n;used+=n;}
        long used(){return used;}
    }
    record Sparse(List<String> rows,List<String> columns,Map<Long,Double> entries){double get(int i,int j){return entries.getOrDefault((long)i*columns.size()+j,0.0);}}
    record Pair(String first,String second) { }
    static final Comparator<Pair> PAIRS=Comparator.comparing(Pair::first,C_ORDER).thenComparing(Pair::second,C_ORDER);
    record Constructed(Sparse counts,Map<String,Integer> inputPeaks,Map<String,Integer> inputCells,KdTree nearest){ }

    static Constructed constructed(Path worker,Population p,CiceroWorkflowRequest.Request r,Meter meter)throws IOException {
        var expectedPeaks=new ArrayList<>(p.peaks());expectedPeaks.sort(Comparator.comparing(CiceroWorkflowInputs.Peak::sourceChromosome,C_ORDER).thenComparingLong(CiceroWorkflowInputs.Peak::start).thenComparing(CiceroWorkflowInputs.Peak::id,C_ORDER));var expectedCells=new ArrayList<>(p.cells());expectedCells.sort(C_ORDER);
        var ledger=read(worker.resolve("input-ledger.json"),r);require(strings(ledger.get("input_peak_ids"),r.maximumPeaks()).equals(p.peaks().stream().map(CiceroWorkflowInputs.Peak::id).toList())&&strings(ledger.get("input_cell_ids"),r.maximumCells()).equals(p.cells()),"Original declared input ledger differs");
        Sparse counts=sparse(worker.resolve("constructed-counts.json"),r);require(counts.rows().equals(expectedPeaks.stream().map(CiceroWorkflowInputs.Peak::id).toList())&&counts.columns().equals(expectedCells),"Original factor/C chromosome/start axis order differs; no ID repair allowed");
        require(strings(ledger.get("source_peak_ids"),r.maximumPeaks()).equals(counts.rows())&&strings(ledger.get("source_cell_ids"),r.maximumCells()).equals(counts.columns()),"Original axis ledger and counts differ");require(Boolean.FALSE.equals(ledger.get("coordinate_conversion")),"Unexpected endpoint conversion");
        var ip=new HashMap<String,Integer>();for(int i=0;i<p.peaks().size();i++)ip.put(p.peaks().get(i).id(),i);var ic=index(p.cells());var sp=index(counts.rows());var sc=index(counts.columns());long positive=0;
        for(var entry:p.counts().entrySet()){
            meter.spend(1);int i=(int)(entry.getKey()/p.cells().size()),j=(int)(entry.getKey()%p.cells().size());double value=p.count(i,j,r.binarize());if(value!=0)positive++;
            require(counts.get(sp.get(p.peaks().get(i).id()),sc.get(p.cells().get(j)))==value,"Original constructed count differs from declared input/binarization");
        }
        require(counts.entries().values().stream().filter(x->x!=0).count()==positive,"Unexpected original constructed nonzero count");
        var observedRows=new boolean[p.peaks().size()];for(var e:p.counts().entrySet())if(e.getValue()>0)observedRows[(int)(e.getKey()/p.cells().size())]=true;var zeroIds=new ArrayList<String>();for(int i=0;i<observedRows.length;i++)if(!observedRows[i])zeroIds.add(p.peaks().get(i).id());require(strings(ledger.get("zero_peak_dimension_anchors"),r.maximumPeaks()).equals(zeroIds),"Zero-valued dimension-anchor ledger differs from declared matrix");

        var peaks=table(worker.resolve("constructed-peaks.json"),r);require(tokens(peaks,"site_name").equals(counts.rows()),"Original feature metadata identity differs");var chr=tokens(peaks,"chr");var starts=tokens(peaks,"bp1");var ends=tokens(peaks,"bp2");
        for(int i=0;i<counts.rows().size();i++){var peak=p.peaks().get(ip.get(counts.rows().get(i)));require(chr.get(i).equals(peak.sourceChromosome())&&number(starts.get(i))==peak.start()&&number(ends.get(i))==peak.end(),"Original peak label parser differs from declared raw coordinates");}
        double[][] embedding=new double[counts.columns().size()][];for(int j=0;j<embedding.length;j++)embedding[j]=p.embedding()[ic.get(counts.columns().get(j))];
        return new Constructed(counts,ip,ic,new KdTree(embedding,meter));
    }
    static Map<String,Object> analysis(Path target,Map<String,Object> a,Population p,Constructed c,CiceroWorkflowRequest.Request r,Meter meter)throws IOException {
        long startOperations=meter.used();var status=read(target.resolve("source-status.json"),r);require(text(status.get("status")).equals("original-returned"),"Original analysis did not return");var population=object(status.get("population"));require(Boolean.TRUE.equals(population.get("save_reload_identical")),"Original save/reload failed");
        Sparse aggregate=sparse(target.resolve("aggregate-counts.json"),r);require(aggregate.rows().equals(c.counts().rows())&&aggregate.columns().size()>=2,"Original aggregate peak axes/population differ");var aggregates=index(aggregate.columns());var sourceCells=index(c.counts().columns());int k=(int)integer(a.get("k"),2,p.cells().size());
        var map=table(target.resolve("aggregation-map.json"),r);var mapCells=tokens(map,"cell");var mapAggregates=tokens(map,"agg_cell");var membership=new ArrayList<List<Integer>>();for(int i=0;i<aggregate.columns().size();i++)membership.add(new ArrayList<>());
        int previousAggregate=-1,previousCell=-1;
        for(int i=0;i<mapCells.size();i++){
            meter.spend(1);Integer group=aggregates.get(mapAggregates.get(i)),cell=sourceCells.get(mapCells.get(i));require(group!=null&&cell!=null,"Unknown original aggregation ID");require(group>previousAggregate||(group==previousAggregate&&cell>previousCell),"Original aggregation map order or duplicate membership differs");membership.get(group).add(cell);previousAggregate=group;previousCell=cell;
        }
        var cellToAggregates=new ArrayList<List<Integer>>();for(int i=0;i<p.cells().size();i++)cellToAggregates.add(new ArrayList<>());
        for(int g=0;g<membership.size();g++){
            String name=aggregate.columns().get(g);require(name.matches("agg_[1-9][0-9]*"),"Unexpected original aggregate label");int chosen=(int)CiceroWorkflowInputs.coordinate(name.substring(4),1,p.cells().size())-1;var members=membership.get(g);require(members.size()==k&&members.contains(chosen),"Original bin must contain exactly k distinct cells including its source seed cell");c.nearest().audit(chosen,members,meter);for(int cell:members)cellToAggregates.get(cell).add(g);
        }
        var raw=new HashMap<Long,Double>();int nAgg=aggregate.columns().size();
        for(var e:c.counts().entries().entrySet()){
            if(e.getValue()==0)continue;int peak=(int)(e.getKey()/p.cells().size()),cell=(int)(e.getKey()%p.cells().size());
            for(int group:cellToAggregates.get(cell)){meter.spend(1);long key=(long)peak*nAgg+group;raw.merge(key,e.getValue(),Double::sum);require(raw.size()<=r.intermediateEntries(),"Native aggregate exceeds maximum_intermediate_entries");}
        }
        double[] totals=new double[nAgg];for(var e:raw.entrySet())totals[(int)(e.getKey()%nAgg)]+=e.getValue();double[] logs=new double[nAgg];for(int i=0;i<nAgg;i++){require(totals[i]>0&&Double.isFinite(totals[i]),"Native aggregate has invalid total");logs[i]=Math.log(totals[i]);}double geometric=Math.exp(mean(logs));boolean normalize=bool(a.get("size_factor_normalize"));
        for(var e:raw.entrySet()){meter.spend(1);int i=(int)(e.getKey()/nAgg),j=(int)(e.getKey()%nAgg);double expected=normalize?e.getValue()/(totals[j]/geometric):e.getValue();near(aggregate.get(i,j),expected,Math.abs(expected),"Original aggregate sum/size-factor normalization differs");}
        require(aggregate.entries().values().stream().filter(x->x!=0).count()==raw.size(),"Unexpected original aggregate nonzero");
        var metadata=table(target.resolve("aggregate-metadata.json"),r);require(tokens(metadata,"agg_cell").equals(aggregate.columns()),"Original aggregate metadata order differs");
        for(String statistic:strings(a.get("summary_stats"),128)){
            int column=r.numericMetadata().indexOf(statistic);var observed=tokens(metadata,"mean_"+statistic);
            for(int g=0;g<nAgg;g++){double[] values=new double[k];double scale=0;for(int i=0;i<k;i++){meter.spend(1);values[i]=p.metadata()[c.inputCells().get(c.counts().columns().get(membership.get(g).get(i)))][column];scale=Math.max(scale,Math.abs(values[i]));}near(number(observed.get(g)),mean(values),scale,"Original aggregate summary statistic differs");}
        }
        var models=read(target.resolve("models.json"),r);double parameter=finite(models.get("distance_parameter"),0,1e12);var distance=object(a.get("distance_parameter"));
        if(text(distance.get("mode")).equals("fixed"))require(parameter==finite(distance.get("value"),0,1e12),"Original fixed parameter differs");
        else {var d=read(target.resolve("distance-parameters.json"),r);var values=list(d.get("parameters"));require(!values.isEmpty()&&values.size()<=integer(distance.get("sample_num"),1,1000000),"Original distance-sample population differs");double[] v=new double[values.size()];for(int i=0;i<v.length;i++)v[i]=finite(values.get(i),0,1e12);near(parameter,mean(v),Math.abs(parameter),"Original mean distance parameter differs");if(bool(distance.get("require_full_sample")))require(v.length==integer(distance.get("sample_num"),1,1000000),"Original distance samples incomplete");}
        var settings=object(a.get("model"));long window=integer(settings.get("window"),2,1000000000);int maxElements=(int)integer(settings.get("max_elements"),2,100000);double power=finite(settings.get("power"),Double.MIN_NORMAL,Math.nextDown(1.0));var windows=list(models.get("windows"));var genome=new ArrayList<>(p.genome());genome.sort(Comparator.comparing(CiceroWorkflowInputs.Genome::chromosome,C_ORDER));
        var contributions=new TreeMap<Pair,List<Double>>(PAIRS);var secondLevels=new LinkedHashMap<String,Integer>();int at=0,modeled=0,skipped=0;long matrixEntries=0;double maxPrecisionAsymmetry=0;
        for(var chromosome:genome)for(long start=1,step=0;start<=chromosome.length();step++,start=1+step*window/2){
            require(at<windows.size(),"Missing original genomic window");long end=start+window-1;var m=object(windows.get(at++));require(text(m.get("id")).equals(chromosome.chromosome()+"_"+start+"_"+end)&&text(m.get("chromosome")).equals(chromosome.chromosome())&&integer(m.get("start"),1,2147483647)==start&&integer(m.get("end"),1,2147483647)==end,"Original generated window identity differs");var expected=new ArrayList<String>();
            for(String id:aggregate.rows()){meter.spend(1);var peak=p.peaks().get(c.inputPeaks().get(id));if(peak.sourceChromosome().equals(chromosome.sourceChromosome())&&CiceroCoaccessibility.inWindow(peak.start(),peak.end(),start,end))expected.add(id);}
            require(strings(m.get("peak_ids"),r.maximumPeaks()).equals(expected),"Original strict endpoint window membership differs");boolean shouldSkip=expected.size()<=1||expected.size()>maxElements;
            if(shouldSkip){skipped++;require(text(m.get("status")).equals("source-skipped")&&text(m.get("reason")).equals(expected.size()<=1?"Zero or one element in range":"Too many elements in range"),"Original skipped-window disposition differs");continue;}
            var matrices=read(member(target,text(m.get("matrix_file"))),r);require(text(m.get("matrix_file")).equals(String.format(Locale.ROOT,"window-%06d.json",at)),"Original model matrix file order differs");require(text(m.get("status")).equals("original-model")&&integer(matrices.get("errflag"),0,0)==0,"Original graphical-lasso error or unexpected window omission");for(String id:expected)secondLevels.putIfAbsent(id,secondLevels.size());modeled++;int n=expected.size();matrixEntries+=4L*n*n;require(matrixEntries<=r.modelEntries(),"Native model entries exceed capacity");double[][] covariance=dense(matrices.get("covariance"),expected),penalty=dense(matrices.get("penalty"),expected),w=dense(matrices.get("w"),expected),wi=dense(matrices.get("wi"),expected);var sourcePeaks=index(aggregate.rows());double[][] values=new double[n][nAgg];double[] means=new double[n];double[] variances=new double[n];
            for(int i=0;i<n;i++){for(int j=0;j<nAgg;j++){meter.spend(1);values[i][j]=aggregate.get(sourcePeaks.get(expected.get(i)),j);}means[i]=mean(values[i]);variances[i]=covariance(values[i],values[i],means[i],means[i],meter);require(w[i][i]>0&&wi[i][i]>0,"Original model has nonpositive covariance/precision diagonal");}
            for(int i=0;i<n;i++)for(int j=0;j<n;j++){
                meter.spend(1);double cov=covariance(values[i],values[j],means[i],means[j],meter)+(i==j?1e-4:0);near(covariance[i][j],cov,Math.sqrt(variances[i]*variances[j])+(i==j?1e-4:0),"Original covariance input differs");var aPeak=p.peaks().get(c.inputPeaks().get(expected.get(i)));var bPeak=p.peaks().get(c.inputPeaks().get(expected.get(j)));double dist=Math.abs((aPeak.start()+aPeak.end())/2.0-(bPeak.start()+bPeak.end())/2.0);near(penalty[i][j],CiceroCoaccessibility.penalty(dist,parameter,power),Math.abs(penalty[i][j]),"Original distance penalty differs");near(w[i][j],w[j][i],Math.abs(w[i][j]),"Original covariance fit is asymmetric");maxPrecisionAsymmetry=Math.max(maxPrecisionAsymmetry,Math.abs(wi[i][j]-wi[j][i]));
                if(i!=j){double value=CiceroCoaccessibility.correlation(w[i][j],w[i][i],w[j][j]);require(Double.isFinite(value)&&Math.abs(value)<=1+1e-10,"Original model correlation outside audited domain");contributions.computeIfAbsent(new Pair(expected.get(i),expected.get(j)),ignored->new ArrayList<>()).add(value);}
            }
        }
        require(at==windows.size()&&modeled>0,"Original window population differs or has no models");if(text(a.get("skipped_windows_policy")).equals("require-all-modeled"))require(skipped==0,"Declared policy rejects skipped windows");
        var connections=table(target.resolve("connections.json"),r);List<String> first=tokens(connections,"Peak1"),second=tokens(connections,"Peak2"),scores=tokens(connections,"coaccess"),kinds=kinds(connections,"coaccess");require(first.size()==contributions.size(),"Original connection population differs from complete modeled pairs");int row=0,unresolved=0;
        require(strings(column(connections,"Peak2").get("levels"),r.maximumPeaks()).equals(new ArrayList<>(secondLevels.keySet())),"Original Peak2 factor levels differ from complete model-column union");
        require(text(column(connections,"Peak1").get("type")).equals("character")&&strings(column(connections,"Peak2").get("class"),16).contains("factor"),"Original connection ID column types differ");
        var orderedContributions=new ArrayList<>(contributions.entrySet());orderedContributions.sort(Comparator.<Map.Entry<Pair,List<Double>>,String>comparing(e->e.getKey().first(),C_ORDER).thenComparingInt(e->secondLevels.get(e.getKey().second())));
        for(var entry:orderedContributions){
            meter.spend(entry.getValue().size());require(first.get(row).equals(entry.getKey().first())&&second.get(row).equals(entry.getKey().second()),"Original connection identity/C ordering differs");var expected=CiceroCoaccessibility.reconcile(entry.getValue().stream().mapToDouble(Double::doubleValue).toArray());
            if(expected.kind()==CiceroCoaccessibility.Kind.NA_MIXED_SIGNS){require(kinds.get(row).equals("NA")&&scores.get(row).equals("NA"),"Original unresolved mixed signs were repaired or differ");unresolved++;}
            else {require(expected.kind()==CiceroCoaccessibility.Kind.VALUE&&kinds.get(row).equals("value"),"Unexpected nonfinite original connection");near(number(scores.get(row)),expected.value(),entry.getValue().stream().mapToDouble(Math::abs).max().orElse(0),"Original signed reconciliation differs");}row++;
        }
        if(text(a.get("unresolved_connections_policy")).equals("fail-on-NA"))require(unresolved==0,"Declared policy rejects unresolved original NA");
        require(integer(population.get("connections"),0,Integer.MAX_VALUE)==row&&integer(population.get("unresolved_NA"),0,Integer.MAX_VALUE)==unresolved&&integer(population.get("aggregates"),2,r.maximumCells())==nAgg,"Original summary and complete outputs differ");
        var report=new LinkedHashMap<String,Object>();report.put("id",a.get("id"));report.put("original_peaks",aggregate.rows().size());report.put("original_aggregates",nAgg);report.put("original_models",modeled);report.put("source_skipped_windows",skipped);report.put("original_connection_rows",row);report.put("unresolved_source_NA_retained",unresolved);report.put("native_audit_operations",meter.used()-startOperations);report.put("original_precision_max_asymmetry",maxPrecisionAsymmetry);report.put("precision_symmetrized",false);report.put("native_axes_aggregation_knn_covariance_penalty_reconciliation_checked",true);report.put("original_solver_and_RNG_refitted_or_authenticated",false);NextflowFiles.writeJson(target.resolve("java-audit.json"),report);return report;
    }
    static Map<String,Object> read(Path path,CiceroWorkflowRequest.Request r)throws IOException{return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(path,r.jsonBytes())),r.jsonBytes(),r.jsonNodes()));}
    static Sparse sparse(Path path,CiceroWorkflowRequest.Request r)throws IOException {
        var m=read(path,r);var rows=strings(m.get("row_ids"),r.maximumPeaks());var columns=strings(m.get("column_ids"),r.maximumCells());require(new HashSet<>(rows).size()==rows.size()&&new HashSet<>(columns).size()==columns.size(),"Duplicate original matrix axes");var entries=new HashMap<Long,Double>();
        if(m.containsKey("entry_parts")){
            var parts=strings(m.get("entry_parts"),r.outputFiles());require(!parts.isEmpty()&&new HashSet<>(parts).size()==parts.size(),"Invalid/duplicate sparse chunks");
            for(String part:parts)addEntries(entries,list(read(member(path.getParent(),part),r).get("entries")),rows.size(),columns.size(),r);
            require(integer(m.get("entry_count"),0,r.intermediateEntries())==entries.size(),"Sparse chunk entry population differs");
        }else addEntries(entries,list(m.get("entries")),rows.size(),columns.size(),r);
        return new Sparse(rows,columns,entries);
    }
    private static void addEntries(Map<Long,Double> entries,List<?> values,int rows,int columns,CiceroWorkflowRequest.Request r)throws IOException {
        for(Object raw:values){var e=object(raw);int i=(int)integer(e.get("row"),1,rows)-1,j=(int)integer(e.get("column"),1,columns)-1;double value=number(text(e.get("value")));require(value>=0&&entries.size()<r.intermediateEntries()&&entries.putIfAbsent((long)i*columns+j,value)==null,"Invalid/duplicate/capacity original sparse entry");}
    }
    private static Path member(Path parent,String name)throws IOException {NextflowFiles.member(name);require(name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,255}"),"Unsupported nonlocal transport member");Path path=parent.resolve(name);require(java.nio.file.Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS),"Missing/nonregular transport member");return path;}
    static Map<String,Object> table(Path path,CiceroWorkflowRequest.Request r)throws IOException{return table(path,r,true);}
    private static Map<String,Object> table(Path path,CiceroWorkflowRequest.Request r,boolean allowParts)throws IOException {
        var m=read(path,r);int maximum=Math.max(Math.max(r.maximumPeaks(),r.maximumCells()),r.connectionRows());int rows=(int)integer(m.get("rows"),0,maximum);
        if(m.containsKey("parts")){
            require(allowParts,"Nested table chunks unsupported");var parts=strings(m.get("parts"),r.outputFiles());require(!parts.isEmpty()&&new HashSet<>(parts).size()==parts.size(),"Invalid/duplicate table chunks");var columns=new ArrayList<Map<String,Object>>();long count=0;
            for(String part:parts){
                var chunk=table(member(path.getParent(),part),r,false);int n=(int)integer(chunk.get("rows"),1,r.chunkEntries());count+=n;require(count<=rows,"Excess table chunk rows");var current=list(chunk.get("columns"));
                if(columns.isEmpty())for(Object raw:current){var c=object(raw);var copy=new LinkedHashMap<String,Object>(c);copy.put("tokens",new ArrayList<String>());copy.put("kinds",new ArrayList<String>());columns.add(copy);}
                require(columns.size()==current.size(),"Table chunk column population differs");
                for(int i=0;i<columns.size();i++){
                    var a=columns.get(i);var b=object(current.get(i));for(String key:List.of("name","class","type","levels"))require(Objects.equals(a.get(key),b.get(key)),"Table chunk column metadata differs");
                    append(a.get("tokens"),literal(b.get("tokens")));append(a.get("kinds"),strings(b.get("kinds"),maximum));
                }
            }
            require(count==rows,"Incomplete table chunks");return Map.of("rows",m.get("rows"),"columns",columns,"source_parts",parts);
        }
        var names=new HashSet<String>();for(Object raw:list(m.get("columns"))){var c=object(raw);require(names.add(label(c.get("name"))),"Duplicate original table column");var values=literal(c.get("tokens"));var kinds=strings(c.get("kinds"),maximum);require(values.size()==rows&&kinds.size()==rows,"Ragged original atomic table");for(int i=0;i<rows;i++){String kind=kinds.get(i);require(Set.of("value","NA","NaN","positive-infinity","negative-infinity").contains(kind),"Unknown original value kind");if(!kind.equals("value"))require(values.get(i).equals(Map.of("NA","NA","NaN","NaN","positive-infinity","Inf","negative-infinity","-Inf").get(kind)),"Original value kind/token disagree");}}return m;
    }
    private static void append(Object target,List<String> values)throws IOException {if(!(target instanceof List<?>))throw new IOException("Invalid merged table list");@SuppressWarnings("unchecked") List<String> typed=(List<String>)target;typed.addAll(values);}
    private static List<String> literal(Object raw)throws IOException {var result=new ArrayList<String>();for(Object value:list(raw))result.add(text(value));return result;}
    private static Map<String,Object> column(Map<String,Object> table,String name)throws IOException {for(Object raw:list(table.get("columns"))){var c=object(raw);if(text(c.get("name")).equals(name))return c;}throw new IOException("Missing original table column: "+name);}
    static List<String> tokens(Map<String,Object> table,String name)throws IOException{return literal(column(table,name).get("tokens"));}
    private static List<String> kinds(Map<String,Object> table,String name)throws IOException{return strings(column(table,name).get("kinds"),1000000000);}
    private static Map<String,Integer> index(List<String> ids){var result=new HashMap<String,Integer>();for(int i=0;i<ids.size();i++)result.put(ids.get(i),i);return result;}
    private static double number(String token)throws IOException {try{double x=Double.parseDouble(token);require(Double.isFinite(x),"Original numeric value is nonfinite");return x;}catch(NumberFormatException e){throw new IOException("Original numeric token invalid",e);}}
    private static double[][] dense(Object raw,List<String> ids)throws IOException {var m=object(raw);require(strings(m.get("row_ids"),1000000).equals(ids)&&strings(m.get("column_ids"),1000000).equals(ids),"Original model matrix axes differ");var rows=list(m.get("values"));require(rows.size()==ids.size(),"Original model row population differs");double[][] result=new double[ids.size()][ids.size()];for(int i=0;i<result.length;i++){var row=list(rows.get(i));require(row.size()==ids.size(),"Ragged original model matrix");for(int j=0;j<result.length;j++)result[i][j]=number(text(row.get(j)));}return result;}
    private static void near(double value,double expected,double scale,String cause)throws IOException {require(Double.isFinite(value)&&Double.isFinite(expected)&&Math.abs(value-expected)<=5e-11*Math.max(Math.max(Math.abs(expected),Math.abs(scale)),1e-300),cause+": original="+value+", native="+expected);}
    private static double mean(double[] x){double sum=0,c=0;for(double v:x){double y=v-c,t=sum+y;c=(t-sum)-y;sum=t;}double m=sum/x.length;sum=0;c=0;for(double v:x){double y=(v-m)-c,t=sum+y;c=(t-sum)-y;sum=t;}return m+sum/x.length;}
    private static double covariance(double[] x,double[] y,double mx,double my,Meter meter){meter.spend(x.length);double sum=0,c=0;for(int i=0;i<x.length;i++){double z=(x[i]-mx)*(y[i]-my)-c,t=sum+z;c=(t-sum)-z;sum=t;}return sum/(x.length-1);}

    /** Exact radius exclusion audit with tied FNN neighbors admitted; balanced tree avoids an all-cell scan per bin. */
    static final class KdTree {
        private final double[][] points;private final int[] left,right,axis;private final int root;
        KdTree(double[][] points,Meter meter){this.points=points;left=new int[points.length];right=new int[points.length];axis=new int[points.length];Arrays.fill(left,-1);Arrays.fill(right,-1);Integer[] order=new Integer[points.length];for(int i=0;i<order.length;i++)order[i]=i;root=build(order,0,order.length,0,meter);}
        private int build(Integer[] order,int from,int to,int depth,Meter meter){if(from==to)return -1;int dimension=depth%points[0].length;Arrays.sort(order,from,to,(a,b)->{meter.spend(1);int c=Double.compare(points[a][dimension],points[b][dimension]);return c==0?Integer.compare(a,b):c;});int middle=(from+to)>>>1,node=order[middle];axis[node]=dimension;left[node]=build(order,from,middle,depth+1,meter);right[node]=build(order,middle+1,to,depth+1,meter);return node;}
        void audit(int chosen,List<Integer> members,Meter meter)throws IOException {var included=new HashSet<>(members);double radius=0;for(int point:members)radius=Math.max(radius,distance(chosen,point,meter));if(radius==0)return;double threshold=radius-radius*1e-12;require(exclude(root,chosen,included,threshold,meter),"Original FNN bin omits a strictly closer cell outside tie tolerance");}
        private boolean exclude(int node,int chosen,Set<Integer> included,double threshold,Meter meter){if(node<0)return true;double distance=distance(node,chosen,meter);if(distance<threshold&&!included.contains(node))return false;double delta=points[chosen][axis[node]]-points[node][axis[node]];int near=delta<=0?left[node]:right[node],far=delta<=0?right[node]:left[node];return exclude(near,chosen,included,threshold,meter)&&(delta*delta>=threshold||exclude(far,chosen,included,threshold,meter));}
        private double distance(int a,int b,Meter meter){meter.spend(points[a].length);double sum=0;for(int i=0;i<points[a].length;i++){double d=points[a][i]-points[b][i];sum+=d*d;}return sum;}
    }
}
