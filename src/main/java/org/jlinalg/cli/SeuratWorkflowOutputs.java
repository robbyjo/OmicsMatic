/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SeuratDifferentialInputs.*;

/** Restores public identities and audits native arithmetic around original models. */
final class SeuratWorkflowOutputs {
    private SeuratWorkflowOutputs() { }
    private record Table(List<String> columns,List<String[]> rows) { }
    private record Dense(List<String> ids,List<String> columns,double[][] values) { }
    static List<String> publish(Path root,Map<String,Object> r,SeuratWorkflowInputs.Prepared prepared,boolean compare)throws IOException {
        Path worker=root.resolve("worker");var counts=prepared.counts();int nf=counts.features().size(),nc=counts.cells().size();
        var report=read(worker.resolve("worker-report.json"),64000000);
        if(!"completed-with-warnings".equals(report.get("status"))||!SeuratWorkflowContracts.PROFILE.equals(report.get("profile"))||!Boolean.FALSE.equals(report.get("installed_DLL_source_authentication"))||!Boolean.FALSE.equals(report.get("independent_native_model_refit")))throw new IOException("Original workflow incomplete or scientific scope differs");
        if(integer(report,"n_input_features",0,nf,nf)!=nf||integer(report,"n_cells",0,nc,nc)!=nc)throw new IOException("Original dimensions differ");
        var profile=read(root.resolve("source/reference/SOURCE-PROFILE.json"));var guards=object(report.get("source_guards"),"source guards");
        if(!guards.keySet().equals(new HashSet<>(list(profile.get("functions"),"functions")))||guards.values().stream().anyMatch(v->!Boolean.TRUE.equals(v)))throw new IOException("Incomplete original function guards");
        var expectedVersions=new LinkedHashMap<>(object(profile.get("required_packages"),"required packages"));
        if(r.get("normalization").equals("SCT-v2"))expectedVersions.putAll(object(profile.get("sct_packages"),"SCT packages"));
        if(Boolean.TRUE.equals(object(r.get("umap"),"umap").get("enabled")))expectedVersions.putAll(object(profile.get("umap_packages"),"UMAP packages"));
        if(Boolean.TRUE.equals(object(r.get("tsne"),"tsne").get("enabled")))expectedVersions.putAll(object(profile.get("tsne_packages"),"t-SNE packages"));
        var clustering=object(r.get("clustering"),"clustering");if(((Number)clustering.get("algorithm")).intValue()==4&&clustering.get("leiden_backend").equals("leidenbase"))expectedVersions.put("leidenbase","0.1.36");
        if(!same(expectedVersions,report.get("versions")))throw new IOException("Recorded original package editions differ");
        Set<String> stages=new HashSet<>(List.of("construct","normalization","pca","neighbors","clustering","export"));
        if(r.get("normalization").equals("LogNormalize"))stages.addAll(List.of("variable_features","scaling"));
        for(String stage:List.of("umap","tsne"))if(Boolean.TRUE.equals(object(r.get(stage),stage).get("enabled")))stages.add(stage);
        var actualStages=object(report.get("completed_stages"),"completed stages");if(!stages.equals(actualStages.keySet())||actualStages.values().stream().anyMatch(v->!Boolean.TRUE.equals(v)))throw new IOException("Original stage population incomplete");
        var warnings=new ArrayList<String>();for(Object raw:list(report.get("warnings"),"source warnings")){var w=object(raw,"source warning");warnings.add("ORIGINAL_SEURAT_WARNING ["+text(w,"stage")+"]: "+text(w,"message"));}
        Dense pca=dense(worker.resolve("pca.tsv"),nc,1000),loadings=dense(worker.resolve("pca-loadings.tsv"),nf,1000),input=dense(worker.resolve("pca-input.tsv"),nc,nf);
        requireCells(pca.ids,nc);requireCells(input.ids,nc);if(!input.columns.equals(loadings.ids)||!pca.columns.equals(loadings.columns))throw new IOException("PCA feature/component identities differ");
        for(String code:loadings.ids)decode(code,'f',counts.features());int pc=pca.columns.size(),pf=loadings.ids.size();
        var settings=object(r.get("pca"),"pca");boolean exact=settings.get("solver").equals("exact"),weighted=Boolean.TRUE.equals(settings.get("weight_by_variance"));
        int expectedPc=Math.min(((Number)settings.get("components")).intValue(),Math.min(pf-(exact?0:1),nc-(exact?0:1)));if(pc!=expectedPc)throw new IOException("PCA components differ from original rank clipping");
        Table spectrum=table(worker.resolve("pca-sdev.tsv"),Math.min(nf,nc));if(!spectrum.columns.equals(List.of("index","sd"))||spectrum.rows.size()!=(exact?Math.min(pf,nc):pc))throw new IOException("Original PCA spectrum was truncated/changed");
        double[] sd=new double[spectrum.rows.size()];for(int j=0;j<sd.length;j++){if(!spectrum.rows.get(j)[0].equals(Integer.toString(j+1)))throw new IOException("PCA spectrum indices differ");sd[j]=numeric(spectrum.rows.get(j)[1]);if(sd[j]<0||(j>0&&sd[j]>sd[j-1]+1e-10*Math.max(1,sd[j-1])))throw new IOException("PCA SDs must be nonnegative and ordered");}
        String solver=text(report,"pca_actual_solver");var gram=object(report.get("gram_observation"),"Gram observation");
        String expectedSolver=exact?"prcomp":settings.get("solver").equals("irlba")?"irlba":Boolean.TRUE.equals(gram.get("returned"))?"EigenGramPCA":Boolean.TRUE.equals(gram.get("attempted"))?"irlba-after-source-Gram-fallback":"irlba";
        if(!solver.equals(expectedSolver)||exact&&Boolean.TRUE.equals(gram.get("attempted"))||settings.get("solver").equals("irlba")&&Boolean.TRUE.equals(gram.get("attempted")))throw new IOException("PCA solver observation contradicts typed source branch");
        double tolerance=solver.startsWith("irlba")?Math.max(5e-5,settings.containsKey("tolerance")?((Number)settings.get("tolerance")).doubleValue()*50:5e-4):2e-6;
        double maxProjection=0;double[] means=new double[pf];if(exact)for(int f=0;f<pf;f++){for(int c=0;c<nc;c++)means[f]+=input.values[c][f];means[f]/=nc;}
        for(int j=0;j<pc;j++) {
            check();double singular=sd[j]*Math.sqrt(nc-1),embeddingNorm=0;
            for(int c=0;c<nc;c++){
                double projection=0;for(int f=0;f<pf;f++)projection+=(input.values[c][f]-means[f])*loadings.values[f][j];
                double score=pca.values[c][j]*(weighted?1:singular);double rel=Math.abs(projection-score)/Math.max(1,Math.max(Math.abs(projection),Math.abs(score)));maxProjection=Math.max(maxProjection,rel);
                near(projection,score,tolerance,"PCA projection differs from captured scaled input/loadings");embeddingNorm+=pca.values[c][j]*pca.values[c][j];
            }
            near(embeddingNorm,weighted?singular*singular:1,tolerance*4,"PCA score norm differs from full SD spectrum");
            for(int other=0;other<=j;other++){double dot=0;for(int f=0;f<pf;f++)dot+=loadings.values[f][j]*loadings.values[f][other];near(dot,j==other?1:0,2e-6,"PCA loadings not orthonormal");}
        }
        Table nn=table(worker.resolve("nn.tsv"),((Number)r.get("maximum_graph_edges")).intValue());if(!nn.columns.equals(List.of("row","column","weight")))throw new IOException("Invalid NN header");
        var neighbors=object(r.get("neighbors"),"neighbors");int k=((Number)neighbors.get("k")).intValue();int[][] ranked=new int[nc][k];int[] filled=new int[nc];long previous=-1;
        for(String[] row:nn.rows){int i=index(row[0],'c',nc),j=index(row[1],'c',nc);long key=(long)i*nc+j;if(key<=previous||numeric(row[2])!=1||filled[i]>=k)throw new IOException("NN edges must be unique sorted binary entries with k neighbors");ranked[i][filled[i]++]=j;previous=key;}
        for(int count:filled)if(count!=k)throw new IOException("Original NN graph has incomplete neighbor rows");
        var nativeSnn=SeuratSnn.compute(ranked,((Number)neighbors.get("prune")).doubleValue(),new SeuratSnn.Limits(((Number)r.get("maximum_work")).longValue(),((Number)r.get("maximum_graph_edges")).intValue()),()->Thread.currentThread().isInterrupted());
        Table snn=table(worker.resolve("snn.tsv"),((Number)r.get("maximum_graph_edges")).intValue());if(!snn.columns.equals(nn.columns)||snn.rows.size()!=nativeSnn.size())throw new IOException("SNN population differs from native overlap/pruning");
        for(int i=0;i<nativeSnn.size();i++){var e=nativeSnn.get(i);String[] actual=snn.rows.get(i);if(index(actual[0],'c',nc)!=e.row()||index(actual[1],'c',nc)!=e.column())throw new IOException("SNN edge identities/order differ");near(numeric(actual[2]),e.weight(),2e-15,"SNN Jaccard differs");}
        var data=layer(worker,"data",r);var scale=layer(worker,"scale",r);requireCells(data.cells(),nc);requireCells(scale.cells(),nc);
        Set<String> dataFeatures=new HashSet<>(data.features()),scaleFeatures=new HashSet<>(scale.features());for(String code:data.features())decode(code,'f',counts.features());for(String code:scale.features())decode(code,'f',counts.features());
        Set<String> pcaFeatures=new HashSet<>(loadings.ids);if(!scaleFeatures.containsAll(pcaFeatures))throw new IOException("PCA feature not in scaled data");
        // PCA must receive exactly the captured scale values for these features.
        Map<String,Integer> inputIndex=new HashMap<>();for(int f=0;f<pf;f++)inputIndex.put(loadings.ids.get(f),f);
        int[] sp=scale.pointers(),sr=scale.rows();double[] sx=scale.values();long inputFields=0;
        for(int cell=0;cell<nc;cell++){
            Map<Integer,Double> values=new HashMap<>();for(int at=sp[cell];at<sp[cell+1];at++){Integer f=inputIndex.get(scale.features().get(sr[at]));if(f!=null)values.put(f,sx[at]);}
            for(int f=0;f<pf;f++){near(input.values[cell][f],values.getOrDefault(f,0.0),2e-14,"PCA input differs from scale layer");inputFields++;}
        }
        boolean nativeNormalization=false,nativeScaling=false;
        if(r.get("normalization").equals("LogNormalize")){
            var limits=new SeuratPreprocessing.Limits(nf,nc,counts.values().length,((Number)r.get("maximum_dense_entries")).longValue(),((Number)r.get("maximum_work")).longValue());
            var original=new SeuratPreprocessing.Matrix(counts.features(),counts.cells(),counts.pointers(),counts.rows(),counts.values());
            var normalized=SeuratPreprocessing.logNormalize(original,((Number)r.get("scale_factor")).doubleValue(),limits,()->Thread.currentThread().isInterrupted());
            if(!normalized.usable())throw new IOException("Native normalization audit refused undefined arithmetic");var norm=normalized.matrix();
            if(data.features().size()!=nf)throw new IOException("LogNormalize dropped full feature population");for(int f=0;f<nf;f++)if(!data.features().get(f).equals("f"+(f+1)))throw new IOException("LogNormalize feature order differs");
            compareSparse(norm.columnPointers(),norm.rowIndices(),norm.values(),data.pointers(),data.rows(),data.values(),nc,2e-12);nativeNormalization=true;
            if(list(r.get("regressors"),"regressors").isEmpty()){
                List<String> selected=scale.features().stream().map(code->counts.features().get(Integer.parseInt(code.substring(1))-1)).toList();
                var scaled=SeuratPreprocessing.scaleData(norm,selected,new SeuratPreprocessing.ScaleOptions(Boolean.TRUE.equals(r.get("scale_center")),Boolean.TRUE.equals(r.get("scale_scale")),((Number)r.get("scale_max")).doubleValue()),limits,()->Thread.currentThread().isInterrupted());
                if(!scaled.usable())throw new IOException("Native scaling audit refused undefined arithmetic");var expected=scaled.matrix();
                for(int cell=0;cell<nc;cell++){int at=sp[cell];for(int f=0;f<scale.features().size();f++){double actual=at<sp[cell+1]&&sr[at]==f?sx[at++]:0;near(actual,expected.value(f,cell),2e-10,"ScaleData differs from native sparse calculation");}}
                nativeScaling=true;
            }
        }
        Table disposition=table(worker.resolve("feature-disposition.tsv"),nf);if(!disposition.columns.equals(List.of("feature","in_active_assay","variable","scaled","pca"))||disposition.rows.size()!=nf)throw new IOException("Feature disposition must cover every input feature");int variables=0;
        for(int f=0;f<nf;f++){String[] row=disposition.rows.get(f);String code="f"+(f+1);if(!row[0].equals(code))throw new IOException("Feature disposition order differs");for(int j=1;j<5;j++)if(!Set.of("TRUE","FALSE").contains(row[j]))throw new IOException("Invalid feature disposition flag");if(Boolean.parseBoolean(row[1])!=dataFeatures.contains(code)||Boolean.parseBoolean(row[3])!=scaleFeatures.contains(code)||Boolean.parseBoolean(row[4])!=pcaFeatures.contains(code))throw new IOException("Feature disposition disagrees with actual layers");if(Boolean.parseBoolean(row[2]))variables++;}
        if(integer(report,"active_features",0,0,nf)!=dataFeatures.size()||integer(report,"scaled_features",0,0,nf)!=scaleFeatures.size()||integer(report,"variable_features",0,0,nf)!=variables||integer(report,"pca_features",0,0,nf)!=pf||integer(report,"pca_components",0,0,1000)!=pc||integer(report,"pca_spectrum_length",0,0,Math.min(nf,nc))!=sd.length)throw new IOException("Original summary disagrees with exported populations");
        Table clusters=table(worker.resolve("clusters.tsv"),nc),mapping=table(worker.resolve("cluster-resolution-map.tsv"),64);var resolutions=list(clustering.get("resolutions"),"resolutions");
        if(clusters.rows.size()!=nc||clusters.columns.size()!=resolutions.size()+1||!clusters.columns.get(0).equals("cell")||!mapping.columns.equals(List.of("source_column","resolution"))||mapping.rows.size()!=resolutions.size()||!same(clusters.columns.subList(1,clusters.columns.size()),report.get("cluster_columns")))throw new IOException("Cluster/resolution population differs");
        for(int i=0;i<resolutions.size();i++){String[] map=mapping.rows.get(i);if(!map[0].equals(clusters.columns.get(i+1)))throw new IOException("Cluster column mapping differs");near(numeric(map[1]),((Number)resolutions.get(i)).doubleValue(),1e-15,"Cluster resolution differs");}
        for(int cell=0;cell<nc;cell++){String[] row=clusters.rows.get(cell);if(!row[0].equals("c"+(cell+1)))throw new IOException("Cluster cell identities/order differ");for(int j=1;j<row.length;j++)identity(row[j]);}
        try(var out=new Lines(root.resolve("feature-disposition.tsv"),compare)){out.line("feature\tin_active_assay\tvariable\tscaled\tpca");for(String[] row:disposition.rows){String[] mapped=row.clone();mapped[0]=decode(row[0],'f',counts.features());out.line(String.join("\t",mapped));}}
        try(var out=new Lines(root.resolve("clusters.tsv"),compare)){out.line(String.join("\t",clusters.columns));for(String[] row:clusters.rows){String[] mapped=row.clone();mapped[0]=decode(row[0],'c',counts.cells());out.line(String.join("\t",mapped));}}
        publishDense(root.resolve("pca.tsv"),pca,counts.cells(),'c',compare);publishDense(root.resolve("pca-loadings.tsv"),loadings,counts.features(),'f',compare);
        for(String method:List.of("umap","tsne"))if(Boolean.TRUE.equals(object(r.get(method),method).get("enabled"))){Dense embedding=dense(worker.resolve(method+".tsv"),nc,2);requireCells(embedding.ids,nc);if(embedding.columns.size()!=2)throw new IOException("Embedding must be two dimensional");publishDense(root.resolve(method+".tsv"),embedding,counts.cells(),'c',compare);}else if(Files.exists(worker.resolve(method+".tsv"))||Files.exists(root.resolve(method+".tsv")))throw new IOException("Disabled embedding has undeclared output");
        var diagnostics=new LinkedHashMap<String,Object>();diagnostics.put("scope","Captured original models, native LogNormalize/no-regression scaling when applicable, PCA projections/norms/orthogonality and native SNN Jaccard. No independent VST/regression/SCT/clustering/UMAP/t-SNE refit or host authentication.");diagnostics.put("input_features",nf);diagnostics.put("cells",nc);diagnostics.put("active_features",dataFeatures.size());diagnostics.put("scaled_features",scaleFeatures.size());diagnostics.put("variable_features",variables);diagnostics.put("pca_features",pf);diagnostics.put("pca_components",pc);diagnostics.put("pca_spectrum_length",sd.length);diagnostics.put("pca_actual_solver",solver);diagnostics.put("pca_max_projection_relative_error",maxProjection);diagnostics.put("pca_projection_tolerance",tolerance);diagnostics.put("pca_input_fields_checked",inputFields);diagnostics.put("nn_edges",nn.rows.size());diagnostics.put("native_snn_edges_checked",nativeSnn.size());diagnostics.put("native_normalization_checked",nativeNormalization);diagnostics.put("native_scaling_checked",nativeScaling);diagnostics.put("source_warnings",warnings);diagnostics.put("installed_DLL_source_authentication",false);diagnostics.put("independent_native_model_refit",false);
        if(compare){if(!same(diagnostics,read(root.resolve("diagnostics.json"),64000000)))throw new IOException("Native audit diagnostics differ");}else json(root.resolve("diagnostics.json"),diagnostics);
        return List.copyOf(warnings);
    }
    private static void compareSparse(int[] ap,int[] ar,double[] ax,int[] bp,int[] br,double[] bx,int cells,double tolerance)throws IOException {for(int c=0;c<cells;c++){int a=ap[c],b=bp[c];while(a<ap[c+1]||b<bp[c+1]){int i=a<ap[c+1]?ar[a]:Integer.MAX_VALUE,j=b<bp[c+1]?br[b]:Integer.MAX_VALUE;double x=i<=j?ax[a]:0,y=j<=i?bx[b]:0;near(x,y,tolerance,"Normalized layer differs from native calculation");if(i<=j)a++;if(j<=i)b++;}}}
    private static SignacPeakLinks.Sparse layer(Path worker,String name,Map<String,Object> r)throws IOException {return SignacLinkIO.readExpression(worker.resolve(name+".mtx"),worker.resolve(name+"-features.txt"),worker.resolve("cells.txt"),new SignacLinkIO.Limits(((Number)r.get("maximum_features")).intValue(),((Number)r.get("maximum_cells")).intValue(),(int)Math.min(20000000,((Number)r.get("maximum_dense_entries")).longValue()),((Number)r.get("maximum_output_bytes")).longValue(),65536,1),()->Thread.currentThread().isInterrupted());}
    private static void publishDense(Path path,Dense matrix,List<String> publicIds,char prefix,boolean compare)throws IOException {try(var out=new Lines(path,compare)){out.line("id\t"+String.join("\t",matrix.columns));for(int row=0;row<matrix.ids.size();row++){StringBuilder line=new StringBuilder(decode(matrix.ids.get(row),prefix,publicIds));for(double x:matrix.values[row])line.append('\t').append(Double.toString(x));out.line(line.toString());}}}
    private static Dense dense(Path path,int maxRows,int maxColumns)throws IOException {Table t=table(path,maxRows);if(t.columns.size()<2||t.columns.size()>maxColumns+1||!t.columns.get(0).equals("id"))throw new IOException("Invalid dense output dimensions/header");List<String> ids=new ArrayList<>();Set<String> unique=new HashSet<>();double[][] values=new double[t.rows.size()][t.columns.size()-1];for(int i=0;i<t.rows.size();i++){String[] row=t.rows.get(i);if(!unique.add(row[0]))throw new IOException("Repeated dense row identity");ids.add(row[0]);for(int j=1;j<row.length;j++)values[i][j-1]=numeric(row[j]);}return new Dense(List.copyOf(ids),t.columns.subList(1,t.columns.size()),values);}
    private static Table table(Path path,int maxRows)throws IOException {try(var in=reader(path)){List<String> header=List.of(fields(line(in)));if(new HashSet<>(header).size()!=header.size())throw new IOException("Repeated output header");List<String[]> rows=new ArrayList<>();String raw;while((raw=line(in))!=null){check();if(rows.size()>=maxRows)throw new IOException("Output table row capacity exceeded");String[] row=fields(raw);if(row.length!=header.size())throw new IOException("Output table width differs");rows.add(row);}return new Table(header,List.copyOf(rows));}}
    private static void requireCells(List<String> ids,int cells)throws IOException {if(ids.size()!=cells)throw new IOException("Incomplete cell population");for(int i=0;i<cells;i++)if(!ids.get(i).equals("c"+(i+1)))throw new IOException("Cell identity/order differs");}
    private static int index(String code,char prefix,int population)throws IOException {if(!code.matches(prefix+"[1-9][0-9]*"))throw new IOException("Invalid transport identity");try{int i=Integer.parseInt(code.substring(1))-1;if(i<0||i>=population)throw new NumberFormatException();return i;}catch(NumberFormatException e){throw new IOException("Transport identity out of range",e);}}
    private static String decode(String code,char prefix,List<String> ids)throws IOException {return ids.get(index(code,prefix,ids.size()));}
    private static void near(double actual,double expected,double tolerance,String cause)throws IOException {if(!Double.isFinite(actual)||!Double.isFinite(expected)||Math.abs(actual-expected)>tolerance*Math.max(1,Math.max(Math.abs(actual),Math.abs(expected))))throw new IOException(cause+": actual="+actual+" expected="+expected);}
}
