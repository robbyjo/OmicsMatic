/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Bounded sparse intake and explicit ASCII transport; original identities remain captured. */
final class SeuratDifferentialInputs {
    private SeuratDifferentialInputs() { }
    record Layer(List<String> features,List<String> cells,int[] pointers,int[] rows,double[] values) {
        SeuratMarkerSummary.Matrix summaryMatrix(){return new SeuratMarkerSummary.Matrix(features,cells,pointers,rows,values);}
    }
    record Group(String code,String label,int[] numerator,int[] denominator) { }
    record Prepared(Layer counts,Layer data,Layer scale,List<Group> groups,List<Integer> selected,List<String> warnings,Map<String,Object> parameters) {
        Layer foldLayer(String slot){return slot.equals("counts")?counts:slot.equals("scale.data")?scale:data;}
    }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        Path input=root.resolve("inputs"),worker=root.resolve("worker");
        int maxF=((Number)r.get("maximum_features")).intValue(),maxC=((Number)r.get("maximum_cells")).intValue(),maxE=((Number)r.get("maximum_entries")).intValue();long bytes=((Number)r.get("maximum_input_bytes")).longValue();
        var limits=new SignacMatrixIO.Limits(maxF,maxC,maxE,bytes,16384);
        var countsInput=SignacMatrixIO.readCounts(input.resolve("counts"),input.resolve("features"),input.resolve("cells"),limits,()->Thread.currentThread().isInterrupted());var counts=countsInput.counts();
        Layer countLayer=new Layer(counts.features(),counts.cells(),counts.columnPointers(),counts.rowIndices(),counts.values());Layer data;List<String> warnings=new ArrayList<>();
        if(countsInput.duplicateCoordinates()>0)warnings.add("DUPLICATE_COUNT_COORDINATES: "+countsInput.duplicateCoordinates()+" duplicate integer coordinates were combined by exact sums; original input remains captured.");
        if(r.get("data_source").equals("native_tfidf")) {
            var method=SignacNormalization.Method.values()[((Number)r.get("tfidf_method")).intValue()-1];
            var norm=SignacNormalization.runTfidf(counts,method,((Number)r.get("tfidf_scale")).doubleValue(),null,()->Thread.currentThread().isInterrupted());var m=norm.matrix();
            for(var warning:norm.warnings())warnings.add(warning.code()+": "+warning.message()+" Required action: "+warning.requiredAction());
            data=new Layer(m.features(),m.cells(),m.columnPointers(),m.rowIndices(),m.values());finite(data);
        }else if(r.get("data_source").equals("provided"))data=real(input.resolve("data"),input,maxF,maxC,maxE,bytes);
        else data=countLayer;
        Layer scale=r.containsKey("scale_data")?real(input.resolve("scale_data"),input,maxF,maxC,maxE,bytes):null;
        long dense=((long)counts.features().size())*counts.cells().size();if((scale!=null||Boolean.TRUE.equals(r.get("densify")))&&dense>((Number)r.get("maximum_dense_entries")).longValue())throw new IOException("Requested dense/scale-data population exceeds maximum_dense_entries");
        var levels=strings(r.get("group_levels"),"group_levels",2,512);var covariates=list(r.get("covariates"),"covariates");int cells=counts.cells().size();int[] group=new int[cells];String[][] metadata=new String[cells][covariates.size()];var extra=new ArrayList<String>();
        try(var in=reader(input.resolve("metadata"))) {
            String[] header=fields(line(in));if(header.length<2||!header[0].equals("cell"))throw new IOException("Metadata first column must be cell");var columns=new HashMap<String,Integer>();
            for(int i=1;i<header.length;i++){identity(header[i]);if(header[i].equals("cell")||columns.put(header[i],i)!=null)throw new IOException("Duplicate metadata column");}
            String groupName=text(r,"group_column");if(!columns.containsKey(groupName))throw new IOException("Missing declared group column");var used=new HashSet<String>();used.add(groupName);
            for(Object item:covariates){var c=object(item,"covariate");String name=text(c,"name");if(!columns.containsKey(name))throw new IOException("Missing covariate: "+name);used.add(name);}
            for(int i=1;i<header.length;i++)if(!used.contains(header[i]))extra.add(header[i]);
            for(int cell=0;cell<cells;cell++) {
                check();String[] f=fields(line(in));if(f.length!=header.length||!f[0].equals(counts.cells().get(cell)))throw new IOException("Metadata identities/order must match count columns exactly");
                int index=levels.indexOf(f[columns.get(groupName)]);if(index<0)throw new IOException("Undeclared exact group level");group[cell]=index;
                for(int j=0;j<covariates.size();j++) {
                    var c=object(covariates.get(j),"covariate");String raw=f[columns.get(c.get("name"))];
                    if(c.get("kind").equals("factor")){int level=list(c.get("levels"),"levels").indexOf(raw);if(level<0)throw new IOException("Undeclared factor level; missing covariates are not dropped/imputed");metadata[cell][j]="l"+(level+1);}
                    else {double x=numeric(raw);metadata[cell][j]=Double.toString(x);}
                }
            }
            if(line(in)!=null)throw new IOException("Extra metadata rows");
        }
        for(int i=0;i<levels.size();i++){final int index=i;if(Arrays.stream(group).noneMatch(x->x==index))throw new IOException("Declared group level has no cells; declare observed groups explicitly");}
        List<Group> groups=new ArrayList<>();
        if(r.get("mode").equals("PAIR")) {
            var c=object(r.get("contrast"),"contrast");var a=list(c.get("numerator"),"numerator");List<?> b=c.containsKey("denominator")?list(c.get("denominator"),"denominator"):levels.stream().filter(x->!a.contains(x)).toList();
            groups.add(new Group("pair","pairwise",selectedCells(group,levels,a),selectedCells(group,levels,b)));
        }else for(int i=0;i<levels.size();i++){final int index=i;groups.add(new Group("g"+(i+1),levels.get(i),java.util.stream.IntStream.range(0,cells).filter(c->group[c]==index).toArray(),java.util.stream.IntStream.range(0,cells).filter(c->group[c]!=index).toArray()));}
        long estimate=(long)groups.size()*(cells+countLayer.features.size()+data.values.length+(scale==null?0:scale.values.length)+countLayer.values.length);
        if(estimate>((Number)r.get("maximum_work")).longValue())throw new IOException("Complete contrast-summary work exceeds maximum_work");
        List<Integer> selected=new ArrayList<>();
        if(r.containsKey("test_features")) {
            var by=new HashMap<String,Integer>();for(int i=0;i<countLayer.features.size();i++)by.put(countLayer.features.get(i),i);var unique=new HashSet<Integer>();
            try(var in=reader(input.resolve("test_features"))){String id;while((id=line(in))!=null){identity(id);Integer index=by.get(id);if(index==null||!unique.add(index))throw new IOException("test_features must contain unique exact known feature IDs");selected.add(index);}}
            if(selected.isEmpty())throw new IOException("Empty explicit feature selection");
        }else for(int i=0;i<countLayer.features.size();i++)selected.add(i);
        warnings.add("INDEXED_TRANSPORT: exact feature/cell/group/covariate identities remain captured and are restored in published outputs. Only explicit ASCII f#/c#/g#/v#/l# transport codes cross R/Seurat name handling; this is not silent identifier repair.");
        warnings.add("SOURCE_ASSAY_DISPATCH: test_slot chooses the tested layer; fc_slot and declared normalization choose fold summaries. The guarded FindMarkers.Assay dispatch does not forward slot to the matrix method, so its feature-filter slot remains data even when testing counts/scale.data. No undocumented correction is substituted.");
        warnings.add("BIOLOGICAL_DESIGN: cells and covariates are supplied declarations. There is no pseudobulking, donor inference, imputation or automatic biological-replicate correction.");
        warnings.add("OPTIONAL_BACKEND_PROFILE: original Seurat may recommend installing Presto. This admitted base profile intentionally requires Presto and limma to be absent; they are not missing requirements for this request. Do not add optional packages to frozen/shared libraries or change backends without a separately validated explicit profile.");
        if(!extra.isEmpty())warnings.add("UNMODELED_METADATA: retained columns not used in this request: "+String.join(", ",extra));
        Map<String,Object> parameters=new LinkedHashMap<>();for(String key:List.of("profile","mode","data_source","test","test_slot","fc_slot","fc_normalization","pseudocount","log_base","min_pct","min_diff_pct","logfc_threshold","only_positive","max_cells_per_group","min_cells_group","seed","densify","save_source_objects","maximum_result_rows"))parameters.put(key,r.get(key));
        if(r.containsKey("return_threshold"))parameters.put("return_threshold",r.get("return_threshold"));
        parameters.put("features",countLayer.features.size());parameters.put("cells",cells);parameters.put("group_levels",java.util.stream.IntStream.range(0,levels.size()).mapToObj(i->"g"+(i+1)).toList());parameters.put("selected_features",selected.stream().map(i->"f"+(i+1)).toList());parameters.put("matrix_filter_slot","data");parameters.put("has_scale_data",scale!=null);
        List<Map<String,Object>> vars=new ArrayList<>();for(int i=0;i<covariates.size();i++){var c=object(covariates.get(i),"covariate");var v=new LinkedHashMap<String,Object>();v.put("name","v"+(i+1));v.put("kind",c.get("kind"));if(c.containsKey("levels"))v.put("levels",java.util.stream.IntStream.range(0,list(c.get("levels"),"levels").size()).mapToObj(j->"l"+(j+1)).toList());vars.add(v);}parameters.put("covariates",vars);
        List<Map<String,Object>> selectors=new ArrayList<>();
        for(var g:groups) {
            List<?> numerator,denominator;
            if(r.get("mode").equals("PAIR")){var c=object(r.get("contrast"),"contrast");numerator=list(c.get("numerator"),"numerator");denominator=c.containsKey("denominator")?list(c.get("denominator"),"denominator"):levels.stream().filter(x->!numerator.contains(x)).toList();}
            else {numerator=List.of(g.label());denominator=levels.stream().filter(x->!x.equals(g.label())).toList();}
            selectors.add(Map.of("code",g.code(),"numerator_groups",numerator.stream().map(x->"g"+(levels.indexOf(x)+1)).toList(),"denominator_groups",denominator.stream().map(x->"g"+(levels.indexOf(x)+1)).toList()));
        }
        parameters.put("groups",selectors);
        var summary=new LinkedHashMap<String,Object>();summary.put("features",countLayer.features);summary.put("cells",countLayer.cells);summary.put("group_levels",levels);summary.put("group_indices_zero_based",Arrays.stream(group).boxed().toList());summary.put("selected_indices_zero_based",selected);summary.put("covariates",covariates);summary.put("unmodeled_metadata",extra);summary.put("warnings",warnings);
        if(!compare)Files.createDirectories(worker);
        writeLayer(worker.resolve("counts.mtx"),countLayer,compare);writeLayer(worker.resolve("data.mtx"),data,compare);if(scale!=null)writeLayer(worker.resolve("scale-data.mtx"),scale,compare);
        try(var out=new Lines(worker.resolve("metadata.tsv"),compare)){StringBuilder header=new StringBuilder("cell_index\tgroup_index");for(int j=1;j<=covariates.size();j++)header.append("\tv").append(j);out.line(header.toString());for(int c=0;c<cells;c++){StringBuilder row=new StringBuilder("c"+(c+1)+"\tg"+(group[c]+1));for(String value:metadata[c])row.append('\t').append(value);out.line(row.toString());}}
        if(compare){if(!same(parameters,read(worker.resolve("parameters.json"),64_000_000))||!same(summary,read(root.resolve("prepared-inputs.json"),64_000_000)))throw new IOException("Prepared parameters/identity mapping differ from exact captured inputs");}
        else {byte[] a=RsemJson.bytes(parameters),b=RsemJson.bytes(summary);if(a.length>64_000_000||b.length>64_000_000)throw new IOException("Prepared protocol/identity map exceeds64MB; no upstream analysis launched");Files.write(worker.resolve("parameters.json"),a,StandardOpenOption.CREATE_NEW);Files.write(root.resolve("prepared-inputs.json"),b,StandardOpenOption.CREATE_NEW);}
        return new Prepared(countLayer,data,scale,List.copyOf(groups),List.copyOf(selected),List.copyOf(warnings),parameters);
    }
    private static Layer real(Path path,Path input,int f,int c,int e,long bytes)throws IOException {
        // Reject nonstandard Java-only number tokens before the shared sparse reader.
        try(var in=reader(path)){line(in);String row;boolean dimensions=true;while((row=line(in))!=null){check();if(row.startsWith("%"))continue;if(dimensions){dimensions=false;continue;}String[] values=row.strip().split("\\s+");if(values.length!=3)throw new IOException("Malformed prepared real matrix coordinate");numeric(values[2]);}}
        var m=SignacLinkIO.readExpression(path,input.resolve("features"),input.resolve("cells"),new SignacLinkIO.Limits(f,c,e,bytes,16384,1),()->Thread.currentThread().isInterrupted());return new Layer(m.features(),m.cells(),m.pointers(),m.rows(),m.values());
    }
    private static void finite(Layer l)throws IOException {for(double x:l.values)if(!Double.isFinite(x))throw new IOException("Native normalization contains nonfinite entries; preserve warnings and skip a finite-layer marker analysis rather than clipping");}
    private static int[] selectedCells(int[] group,List<String> levels,List<?> selected){return java.util.stream.IntStream.range(0,group.length).filter(i->selected.contains(levels.get(group[i]))).toArray();}
    static void writeLayer(Path p,Layer l,boolean compare)throws IOException {try(var out=new Lines(p,compare)){out.line("%%MatrixMarket matrix coordinate real general");out.line(l.features.size()+" "+l.cells.size()+" "+l.values.length);for(int c=0;c<l.cells.size();c++)for(int k=l.pointers[c];k<l.pointers[c+1];k++){check();out.line((l.rows[k]+1)+" "+(c+1)+" "+Double.toString(l.values[k]));}}}
    static BufferedReader reader(Path p)throws IOException {return new BufferedReader(new InputStreamReader(Files.newInputStream(p),StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)));}
    static String line(BufferedReader r)throws IOException {var b=new StringBuilder();int c;while((c=r.read())>=0&&c!='\n'){if(b.length()>=65536)throw new IOException("TSV line capacity exceeded");b.append((char)c);}if(c<0&&b.isEmpty())return null;if(!b.isEmpty()&&b.charAt(b.length()-1)=='\r')b.setLength(b.length()-1);return b.toString();}
    static String[] fields(String s)throws IOException {if(s==null)throw new IOException("Missing TSV row");return s.split("\t",-1);}
    static double numeric(String s)throws IOException {try{if(!s.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new NumberFormatException();double x=Double.parseDouble(s);if(!Double.isFinite(x))throw new NumberFormatException();return x;}catch(NumberFormatException e){throw new IOException("Require finite numeric metadata, no implicit missingness or repair",e);}}
    static void check(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Marker preparation interrupted");}
    static final class Lines implements AutoCloseable {
        private final BufferedReader input;private final BufferedWriter output;
        Lines(Path p,boolean compare)throws IOException {input=compare?reader(p):null;output=compare?null:Files.newBufferedWriter(p,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
        void line(String text)throws IOException {check();if(input!=null){if(!text.equals(SeuratDifferentialInputs.line(input)))throw new IOException("Prepared/published transport differs from captured inputs");}else {output.write(text);output.write('\n');}}
        public void close()throws IOException {if(input!=null)try{if(SeuratDifferentialInputs.line(input)!=null)throw new IOException("Extra transport rows");}finally{input.close();}else output.close();}
    }
}
