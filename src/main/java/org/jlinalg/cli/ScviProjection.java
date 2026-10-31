/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.ScviGeneProjection;

/** Applies declared reference/query gene policies, with full post-projection library admission. */
final class ScviProjection {
    private ScviProjection() { }
    record Limits(long workspaceBytes,int members,long retainedBytes,ScviMatrix.Limits derived) {
        Limits{Objects.requireNonNull(derived);if(workspaceBytes<1||members<1||retainedBytes<1)throw new IllegalArgumentException("Positive complete scVI projection capacities required");}
    }
    record Prepared(Path directory,ScviMatrix.Prepared reference,ScviMatrix.Prepared query,
                    ScviGeneProjection.Plan plan,List<ScviMatrix.Prepared> parents,
                    List<ScviText.Snapshot> extraSources,Limits limits,Map<String,String> fileHashes) {
        Prepared{parents=List.copyOf(parents);extraSources=List.copyOf(extraSources);fileHashes=Map.copyOf(fileHashes);}
        void verify(Runnable check)throws IOException{
            for(var parent:parents)parent.verify(check);for(var source:extraSources)source.verify(limits.workspaceBytes(),check);
            if(reference!=null)reference.verify(check);query.verify(check);var external=external(parents);var shape=shape(limits);
            if(!ScviMatrix.inventory(directory,shape,check).equals(fileHashes))throw new IOException("scVI gene projection bytes or complete member population changed");
            if(treeBytes(directory)+external[0]>limits.workspaceBytes()||fileHashes.size()+external[1]>limits.members())throw new IOException("Complete scVI projection capacity changed");
        }
    }
    static Prepared pair(ScviMatrix.Prepared reference,ScviMatrix.Prepared query,ScviGeneProjection.Policy policy,Limits limits,Path out,Runnable check)throws IOException{
        if(reference.request().kind()!=query.request().kind())throw new IOException("Reference/query model matrix kinds differ; no count/expression conversion is inferred");
        List<ScviMatrix.Prepared> parents=List.of(reference,query);validate(parents,out,limits,reference.genes().size(),check);
        var plan=plan(reference.genes(),query.genes(),policy);var work=workspace(out,parents,limits,check);work.directory("protected");work.directory("bridge");
        var referenceMap=plan(plan.outputGenes(),reference.genes(),ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA);
        long base=baseRetained(parents,plan.outputGenes());
        var ref=apply(reference,referenceMap,"reference",work,limits,check,base);
        var target=apply(query,plan,"query",work,limits,check,base+ref.summary().reservedRetainedBytes());
        return finish(work,ref,target,plan,parents,List.of(),limits,"PREFIT_PAIR",check);
    }
    static Prepared query(ScviText.Source referenceGenes,ScviMatrix.Prepared query,ScviGeneProjection.Policy policy,Limits limits,Path out,Runnable check)throws IOException{
        if(policy==ScviGeneProjection.Policy.SHARED_BEFORE_TRAINING)throw new IOException("A fitted reference cannot be shrunk to shared query genes; select shared genes before reference fitting");
        List<ScviMatrix.Prepared> parents=List.of(query);validate(parents,out,limits,limits.derived().genes(),check);
        var work=workspace(out,parents,limits,check);work.directory("protected");work.directory("bridge");var genes=work.copy(referenceGenes,"protected/reference-genes.input",limits.derived().inputBytes());
        List<String> order=geneIds(genes,parents,limits,check);var plan=plan(order,query.genes(),policy);
        var target=apply(query,plan,"query",work,limits,check,baseRetained(parents,order));
        return finish(work,null,target,plan,parents,List.of(genes),limits,"FIXED_REFERENCE_QUERY",check);
    }
    private static ScviGeneProjection.Plan plan(List<String> ref,List<String> query,ScviGeneProjection.Policy policy)throws IOException{try{return ScviGeneProjection.plan(ref,query,policy);}catch(IllegalArgumentException bad){throw new IOException("Invalid explicit scVI gene policy: "+bad.getMessage(),bad);}}
    private static void validate(List<ScviMatrix.Prepared> parents,Path output,Limits limits,int referenceGenes,Runnable check)throws IOException{
        long retained=1_048_576L+512L*referenceGenes;
        Path out=output.toAbsolutePath().normalize();
        for(var parent:parents){parent.verify(check);if(out.startsWith(parent.directory())||parent.directory().startsWith(out))throw new IOException("Projection needs a NEW directory separate from admitted parent matrices");retained=Math.addExact(retained,Math.addExact(parent.summary().reservedRetainedBytes(),512L*parent.genes().size()+64L*parent.cells().size()));}
        if(retained>limits.retainedBytes())throw new IOException("Complete gene-plan/parent retained capacity exceeded");
    }
    private static long baseRetained(List<ScviMatrix.Prepared> parents,List<String> referenceGenes){long n=1_048_576L+512L*referenceGenes.size();for(String id:referenceGenes)n=Math.addExact(n,4L*id.length());for(var p:parents)n=Math.addExact(n,p.summary().reservedRetainedBytes()+512L*p.genes().size()+64L*p.cells().size());return n;}
    private static ScviWorkspace workspace(Path out,List<ScviMatrix.Prepared> parents,Limits limits,Runnable check)throws IOException{var external=external(parents);return new ScviWorkspace(out,limits.workspaceBytes(),limits.members(),external[0],Math.toIntExact(external[1]),check);}
    private static long[] external(List<ScviMatrix.Prepared> parents)throws IOException{long bytes=0,members=0;var seen=new HashSet<Path>();for(var p:parents)if(seen.add(p.directory())){members+=p.fileHashes().size();bytes=Math.addExact(bytes,treeBytes(p.directory()));}return new long[]{bytes,members};}
    private static long treeBytes(Path root)throws IOException{long bytes=0;try(var paths=Files.walk(root)){for(var it=paths.iterator();it.hasNext();){Path p=it.next();if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))bytes=Math.addExact(bytes,Files.size(p));}}return bytes;}
    private static List<String> geneIds(ScviText.Snapshot source,List<ScviMatrix.Prepared> parents,Limits limits,Runnable check)throws IOException{
        var ids=new ArrayList<String>();var seen=new HashSet<String>();var budget=new ScviText.DecodedBudget(limits.derived().decodedBytes());
        long retained=baseRetained(parents,List.of());
        try(var in=new ScviText.Lines(new ScviText.Source(source.copy(),source.encoding()),budget,limits.derived().lineCharacters(),check)){String line;while((line=in.next())!=null){if(line.isEmpty()||line.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||!seen.add(line))throw new IOException("Reference gene IDs require unique nonempty exact identifiers");if(ids.size()>=limits.derived().genes())throw new IOException("Reference gene population exceeds declared projection capacity");retained=Math.addExact(retained,512L+4L*line.length());if(retained>limits.retainedBytes())throw new IOException("Complete reference gene identity census exceeds retained capacity");ids.add(line);}}
        if(ids.isEmpty())throw new IOException("Reference gene population is empty");source.verify(limits.derived().inputBytes(),check);return List.copyOf(ids);
    }
    private static ScviMatrix.Prepared apply(ScviMatrix.Prepared source,ScviGeneProjection.Plan plan,String role,ScviWorkspace work,Limits limits,Runnable check,long retainedOutsideChild)throws IOException{
        if(plan.outputGenes().size()>limits.derived().genes())throw new IOException("Projected gene population exceeds declared derived capacity");
        Path canonical=source.directory().resolve("matrix.mtx");var snapshot=work.copy(new ScviText.Source(canonical,ScviText.Encoding.PLAIN_UTF8),"protected/"+role+"-matrix.mtx",limits.workspaceBytes());
        if(!snapshot.sha256().equals(source.fileHashes().get("matrix.mtx")))throw new IOException("Projection source differs from admitted canonical matrix");
        String body="bridge/"+role+"-body.tmp",matrix="bridge/"+role+".mtx",genes="bridge/"+role+"-genes.tsv",cells="bridge/"+role+"-cells.tsv";
        int[] target=plan.outputColumnForQuery();long[] kept={0},declared={0};double[] before=new double[source.cells().size()],retained=new double[before.length],dropped=new double[before.length];
        var decoded=new ScviText.DecodedBudget(limits.workspaceBytes());
        try(var in=new ScviText.Lines(new ScviText.Source(snapshot.copy(),ScviText.Encoding.PLAIN_UTF8),decoded,limits.derived().lineCharacters(),check);var out=work.writer(body)){
            String header=in.next();if(header==null||!header.matches("%%MatrixMarket matrix coordinate (integer|real) general"))throw new IOException("Admitted projection matrix header differs");
            String line;do{line=in.next();}while(line!=null&&(line.isBlank()||line.startsWith("%")));
            if(line==null)throw new IOException("Missing admitted projection matrix dimensions");String[] dimensions=line.trim().split("\\s+");
            if(dimensions.length!=3||Integer.parseInt(dimensions[0])!=source.genes().size()||Integer.parseInt(dimensions[1])!=source.cells().size()||Long.parseLong(dimensions[2])!=source.summary().canonicalEntries())throw new IOException("Admitted projection matrix population changed");
            int lastCell=-1,lastGene=-1;
            while((line=in.next())!=null){check.run();if(line.isBlank()||line.startsWith("%"))continue;String[] f=line.trim().split("\\s+");if(f.length!=3)throw new IOException("Invalid admitted projection matrix record");int gene=Integer.parseInt(f[0])-1,cell=Integer.parseInt(f[1])-1;double value=Double.parseDouble(f[2]);
                if(gene<0||gene>=target.length||cell<0||cell>=before.length||!Double.isFinite(value)||value==0||cell<lastCell||cell==lastCell&&gene<=lastGene)throw new IOException("Projection source canonical order/value/population changed");lastCell=cell;lastGene=gene;
                if(++declared[0]>source.summary().canonicalEntries())throw new IOException("Extra admitted projection matrix record");before[cell]+=value;int mapped=target[gene];
                if(mapped>=0){retained[cell]+=value;out.write((mapped+1)+" "+(cell+1)+" "+f[2]+"\n");kept[0]++;}else dropped[cell]+=value;
            }
        }catch(IllegalArgumentException bad){throw new IOException("Invalid admitted matrix during scVI gene projection",bad);}
        if(declared[0]!=source.summary().canonicalEntries())throw new IOException("Incomplete admitted projection matrix population");snapshot.verify(limits.workspaceBytes(),check);
        try(var out=work.output(matrix);var in=Files.newInputStream(work.contained(body))){out.write(("%%MatrixMarket matrix coordinate "+(source.request().kind()==ScviMatrix.Kind.RAW_COUNTS?"integer":"real")+" general\n% Explicit gene projection; all cells retained\n"+plan.outputGenes().size()+" "+source.cells().size()+" "+kept[0]+"\n").getBytes(StandardCharsets.US_ASCII));byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0){check.run();out.write(buffer,0,n);}}
        work.delete(body);try(var out=work.writer(genes)){for(String id:plan.outputGenes())out.write(id+"\n");}try(var out=work.writer(cells)){for(String id:source.cells())out.write(id+"\n");}
        try(var out=work.writer(role+"-gene-map.tsv")){out.write("output_index_0based\tgene_id\tsource_index_0based\tstatus\n");int[] columns=plan.queryColumnForOutput();for(int i=0;i<columns.length;i++)out.write(i+"\t"+plan.outputGenes().get(i)+"\t"+columns[i]+"\t"+(columns[i]<0?"PADDED_ZERO":"RETAINED")+"\n");}
        try(var out=work.writer(role+"-removed-genes.tsv")){out.write("source_index_0based\tgene_id\n");for(int i=0;i<target.length;i++)if(target[i]<0)out.write(i+"\t"+source.genes().get(i)+"\n");}
        try(var out=work.writer(role+"-cell-impact.tsv")){out.write("cell_id\tbefore_float64_source_order_sum\tretained_float64_source_order_sum\tdropped_float64_source_order_sum\n");for(int i=0;i<before.length;i++)out.write(source.cells().get(i)+"\t"+before[i]+"\t"+retained[i]+"\t"+dropped[i]+"\n");}
        work.refresh();var d=limits.derived();long remainingRetained=limits.retainedBytes()-retainedOutsideChild;
        var childLimits=new ScviMatrix.Limits(d.inputBytes(),d.decodedBytes(),Math.min(d.workspaceBytes(),work.remainingBytes()),Math.min(d.retainedBytes(),remainingRetained),Math.min(d.files(),work.remainingMembers()),d.genes(),d.cells(),d.entries(),d.sortRecords(),d.lineCharacters());
        var request=new ScviMatrix.Request(new ScviText.Source(work.contained(matrix),ScviText.Encoding.PLAIN_UTF8),new ScviText.Source(work.contained(genes),ScviText.Encoding.PLAIN_UTF8),new ScviText.Source(work.contained(cells),ScviText.Encoding.PLAIN_UTF8),source.request().kind(),ScviMatrix.Precision.EXACT_FLOAT32,ScviMatrix.Duplicates.REJECT,childLimits);
        // Re-admission sorts mapped columns, retains every cell, and rejects any now-empty raw library.
        ScviMatrix.Prepared result=ScviMatrix.prepare(request,work.contained(role),check);work.refresh();
        if(!result.cells().equals(source.cells())||!result.genes().equals(plan.outputGenes()))throw new IOException("Projected scVI IDs/order changed");source.verify(check);return result;
    }
    private static ScviMatrix.Limits shape(Limits l){var d=l.derived();return new ScviMatrix.Limits(d.inputBytes(),d.decodedBytes(),l.workspaceBytes(),l.retainedBytes(),l.members(),d.genes(),d.cells(),d.entries(),d.sortRecords(),d.lineCharacters());}
    private static Prepared finish(ScviWorkspace work,ScviMatrix.Prepared ref,ScviMatrix.Prepared query,ScviGeneProjection.Plan plan,List<ScviMatrix.Prepared> parents,List<ScviText.Snapshot> extra,Limits limits,String operation,Runnable check)throws IOException{
        for(var parent:parents)parent.verify(check);for(var source:extra)source.verify(limits.workspaceBytes(),check);long retained=baseRetained(parents,plan.outputGenes())+query.summary().reservedRetainedBytes()+(ref==null?0:ref.summary().reservedRetainedBytes());if(retained>limits.retainedBytes())throw new IOException("Complete projected parent/child retained capacity exceeded");
        var record=new LinkedHashMap<String,Object>();record.put("component_status","PREPARED_GENE_PROJECTION_ONLY");record.put("model_analysis_completed",false);record.put("operation",operation);record.put("policy",plan.policy().name());record.put("output_genes",plan.outputGenes().size());record.put("query_cells",query.cells().size());record.put("missing_reference_genes",plan.missingReferenceGenes().size());record.put("extra_query_genes",plan.extraQueryGenes().size());record.put("reference_gene_list_does_not_admit_model_weights",true);record.put("parents",parents.stream().map(p->Map.of("directory",p.directory().toString(),"file_hashes",p.fileHashes())).toList());record.put("extra_sources",extra.stream().map(s->Map.of("original",s.original().toString(),"copy",work.root.relativize(s.copy()).toString(),"sha256",s.sha256(),"encoding",s.encoding().name())).toList());
        work.text("projection.json",RegenieRuntime.json(record)+"\n");var result=new Prepared(work.root,ref,query,plan,parents,extra,limits,work.inventory(shape(limits)));result.verify(check);return result;
    }
}
