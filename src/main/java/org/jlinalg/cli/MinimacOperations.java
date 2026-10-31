/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Original Minimac operations followed by complete native admission of their outputs. */
final class MinimacOperations {
    private MinimacOperations() { }

    static Map<String,Object> run(MinimacInputs.Prepared input,MinimacRuntime.Context runtime,
            Path work,int timeout,Map<String,Object> session,List<String> warnings)throws Exception{
        var spec=input.spec();input.verify();
        var results=new LinkedHashMap<String,Object>();
        results.put("operation",spec.operation());results.put("profile",spec.profile());results.put("assembly",spec.assembly());
        results.put("reference_markers",input.reference().markers().size());
        results.put("reference_matrix_sha256",input.reference().matrixHash());
        results.put("reference_logical_copy_sha256",input.reference().logicalCopyHash());
        join(input,work);
        switch(spec.operation()){
            case "inspect-reference" -> {
                results.put("scope","Complete native reference inspection; original full MSAV export only when needed; no imputation.");
                results.put("reference_format",input.reference().format());
            }
            case "check" -> {
                results.put("scope","Complete immutable input, exact allele-key, explicit identity/ploidy and target CSI admission; no imputation.");
                results.put("target",MinimacJson.value(input.target().audit()));
            }
            case "impute" -> {
                requireRuntime(runtime);
                var args=new ArrayList<String>();Path output;
                if(spec.modern()){
                    output=work.resolve("imputed.vcf.gz");
                    args.addAll(List.of("--output",output.toString(),"--format","GT,HDS,DS,GP,SD",
                            "--threads",Integer.toString(spec.engine().threads()),"--min-r2","-1",
                            "--min-ratio-behavior","fail","--temp-prefix",work.resolve("impute-temp-").toString()));
                    // Separate fields preserve a literal colon in a contig; never translate chromosome names.
                    args.addAll(region(spec));
                    args.addAll(spec.engine().arguments());
                    args.add(input.reference().engineFile().toString());args.add(input.target().engineFile().toString());
                }else{
                    output=work.resolve("imputed.dose.vcf.gz");
                    args.addAll(List.of("--refHaps",input.reference().engineFile().toString(),
                            "--haps",input.target().engineFile().toString(),"--prefix",work.resolve("imputed").toString(),
                            "--format","GT,DS,GP","--cpus",Integer.toString(spec.engine().threads())));
                    args.addAll(region(spec));args.addAll(spec.engine().arguments());
                }
                execute(input,runtime,false,args,work,"impute",timeout,session);
                String outputHash=FollowupSupport.hash(output);
                results.put("output_audit",MinimacJson.value(MinimacOutputs.audit(output,input,work,warnings)));
                unchanged(output,outputHash,spec.limits().workspaceBytes());
                results.put("scope","Original imputation; complete output admission; explicit source-INFO-R2 post-QC only.");
                results.put("upstream_vcf",output.getFileName().toString());
                results.put("upstream_vcf_sha256",outputHash);
            }
            case "prepare-reference","convert-reference" -> {
                requireRuntime(runtime);boolean convert=spec.operation().equals("convert-reference");
                Path output;MinimacVcf.Identity outputIds=input.reference().identities();
                String semantics=spec.reference().columns();
                if(spec.modern()){
                    String flag=convert?"--update-m3vcf":"--compress-reference";
                    execute(input,runtime,false,List.of(flag,input.reference().engineFile().toString()),
                            work,"reference-operation",timeout,session);
                    output=work.resolve("result-reference.msav");
                    MinimacInputs.copy(work.resolve("reference-operation.stdout"),output);input.budget().check();
                }else{
                    if(!spec.profile().equals(MinimacRecipe.MIN3)||convert)throw new IOException("Unsupported reference operation/profile");
                    output=work.resolve("prepared-reference.m3vcf.gz");
                    var args=new ArrayList<>(List.of("--refHaps",input.reference().engineFile().toString(),
                            "--processReference","--prefix",work.resolve("prepared-reference").toString(),
                            "--cpus",Integer.toString(spec.engine().threads())));
                    args.addAll(spec.engine().arguments());
                    execute(input,runtime,false,args,work,"reference-operation",timeout,session);
                    // This mapping is only for the output of this exact owned original preparation.
                    // Arbitrary supplied column suffixes are never parsed as person identities.
                    outputIds=producerHaplotypes(input);semantics="haplotype-columns";
                }
                MinimacVcf.regular(output,spec.limits().workspaceBytes());
                String outputHash=FollowupSupport.hash(output);
                Path text=output;
                if(spec.modern()){
                    execute(input,runtime,true,List.of("export","--update-info","never","-O","vcf",output.toString()),
                            work,"inspect-result",timeout,session);
                    text=work.resolve("inspect-result.stdout");
                }
                var checked=MinimacInputs.blocks(text,output,outputIds,work,"result-reference",input.budget());
                unchanged(output,outputHash,spec.limits().workspaceBytes());
                transition(input.reference(),checked,convert,spec.idPolicy(),work,input.budget(),warnings);
                Path ledger=work.resolve("result-reference-identities.tsv");
                identities(outputIds,checked.columns(),spec.region().contig(),ledger,input.budget());
                var bundle=new LinkedHashMap<String,Object>();
                bundle.put("schema_version",1);bundle.put("status","completed-native-audited-reference");
                bundle.put("assembly",spec.assembly());bundle.put("profile",spec.profile());bundle.put("operation",spec.operation());
                bundle.put("kind",spec.modern()?"msav":"m3vcf");bundle.put("path",output.getFileName().toString());
                bundle.put("sha256",outputHash);bundle.put("identity_ledger",ledger.getFileName().toString());
                bundle.put("identity_ledger_sha256",FollowupSupport.hash(ledger));bundle.put("column_semantics",semantics);
                bundle.put("matrix_sha256",checked.matrixHash());bundle.put("logical_copy_sha256",checked.logicalCopyHash());
                bundle.put("float32_model_sha256",checked.modelHash());bundle.put("markers",checked.markers().size());
                bundle.put("physical_rows",checked.physicalRows());bundle.put("blocks",checked.blocks());
                bundle.put("shared_boundaries",checked.sharedBoundaries());
                MinimacInputs.writeJson(work.resolve("reference-bundle.json"),bundle,input.budget());results.put("reference_bundle",bundle);
                results.put("scope",convert?"Original format conversion with complete matrix, identity, block and model checks."
                        :"Original reference preparation with complete matrix and identity checks; estimated model and block partition retained.");
            }
            default -> throw new IOException("Unrecognized Minimac operation");
        }
        input.verify();if(runtime!=null)runtime.verify();input.budget().check();return results;
    }

    private static void execute(MinimacInputs.Prepared input,MinimacRuntime.Context runtime,boolean exporter,
            List<String> arguments,Path work,String label,int timeout,Map<String,Object> session)throws Exception{
        input.verify();runtime.verify();
        int exit=MinimacRuntime.execute(runtime,exporter,arguments,work,label,input.spec().engine().threads(),
                timeout,input.spec().limits().workspaceBytes(),session);
        input.verify();runtime.verify();
        if(exit!=0)throw new IOException("Original Minimac "+label+" returned "+exit+"; "+diagnostic(work.resolve(label+".stdout"))+" "+diagnostic(work.resolve(label+".stderr"))+"; inspect complete logs; no partial family is admitted");
    }
    private static void requireRuntime(MinimacRuntime.Context context)throws IOException{
        if(context==null)throw new IOException("Original Minimac operation requires its explicit admitted runtime");
    }
    private static void unchanged(Path path,String sha,long limit)throws IOException{
        MinimacVcf.regular(path,limit);
        if(!FollowupSupport.hash(path).equals(sha))throw new IOException("Original Minimac output changed during its complete native audit");
    }
    /** Bounded diagnostic preview only; binary stdout is never interpreted as scientific text. */
    static String diagnostic(Path path)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))throw new IOException("Missing or linked original process diagnostic");
        int length=(int)Math.min(65536,Files.size(path));byte[] bytes=new byte[length];
        try(var channel=FileChannel.open(path,StandardOpenOption.READ)){
            channel.position(Math.max(0,channel.size()-length));var buffer=ByteBuffer.wrap(bytes);
            while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new IOException("Original process diagnostic changed while reading");
        }
        String text=new String(bytes,StandardCharsets.UTF_8);long bad=text.chars().filter(c->c==0xfffd||Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t').count();
        if(bad>0)return path.getFileName()+": binary/nontext diagnostic retained";
        String[] lines=text.split("\\R",-1);var selected=new StringBuilder();int context=0;
        for(String line:lines){
            String lower=line.toLowerCase(java.util.Locale.ROOT);
            if(lower.contains("error")||lower.contains("warn")||lower.contains("cannot")||lower.contains("only contain"))context=3;
            if(context>0&&!line.isBlank()){
                String value=(selected.isEmpty()?"":" | ")+line.strip();
                selected.append(value,0,Math.min(value.length(),8192-selected.length()));context--;
                if(selected.length()==8192)break;
            }
        }
        if(selected.isEmpty()){String tail=text.strip();selected.append(tail,Math.max(0,tail.length()-1536),tail.length());}
        return path.getFileName()+": "+selected;
    }
    static List<String> region(MinimacRecipe.Spec spec){
        return List.of("--chr",spec.region().contig(),"--start",Integer.toString(spec.region().start()),
                "--end",Integer.toString(spec.region().end()));
    }
    private static void join(MinimacInputs.Prepared input,Path work)throws IOException{
        try(var out=input.budget().writer(work.resolve("variant-join.tsv"))){
            out.write("contig\tposition\tref\talt\treference_id\ttarget_id\ttarget_present\texpected_output_region\n");
            for(var marker:input.reference().markers().values()){
                var target=input.target()==null?null:input.target().markers().get(marker.key());
                out.write(marker.key().text()+"\t"+marker.id()+"\t"+(target==null?"":target.id())+"\t"+(target!=null)
                        +"\t"+MinimacInputs.inside(marker.key(),input.spec().region())+"\n");
            }
        }
    }
    static MinimacVcf.Identity producerHaplotypes(MinimacInputs.Prepared input)throws IOException{
        var columns=new LinkedHashMap<String,List<MinimacVcf.Copy>>();String contig=input.spec().region().contig();
        for(String column:input.reference().columns()){
            var copies=input.reference().identities().copies(contig,column);
            for(int i=0;i<copies.size();i++){
                String generated=column+"_HAP_"+(i+1);
                if(columns.putIfAbsent(generated,List.of(copies.get(i)))!=null)throw new IOException("Original preparation column-name collision");
            }
        }
        return new MinimacVcf.Identity(Map.of(contig,columns));
    }
    static void identities(MinimacVcf.Identity identities,List<String> columns,String contig,Path path,
            MinimacInputs.Budget budget)throws IOException{
        try(var out=budget.writer(path)){
            out.write("contig\tcolumn_id\tcolumn_copy\tperson_id\tperson_copy\n");
            for(String column:columns){
                var copies=identities.copies(contig,column);
                for(int i=0;i<copies.size();i++)out.write(contig+"\t"+column+"\t"+(i+1)+"\t"+copies.get(i).person()+"\t"+copies.get(i).personCopy()+"\n");
            }
        }
    }
    static void transition(MinimacInputs.Reference before,MinimacInputs.Reference after,boolean conversion,
            String idPolicy,Path work,MinimacInputs.Budget budget,List<String> warnings)throws IOException{
        if(!before.matrixHash().equals(after.matrixHash())||!before.logicalCopyHash().equals(after.logicalCopyHash())
                ||!before.markers().keySet().equals(after.markers().keySet()))
            throw new IOException("Original reference operation changed full ordered haplotype matrix, exact allele keys or declared person/copy order");
        if(conversion&&(!before.modelHash().equals(after.modelHash())||before.physicalRows()!=after.physicalRows()
                ||before.blocks()!=after.blocks()||before.sharedBoundaries()!=after.sharedBoundaries()))
            throw new IOException("Original conversion changed binary32 model parameters or physical block/overlap inventory");
        long renamed=0;
        try(var out=budget.writer(work.resolve("reference-transition.tsv"))){
            out.write("contig\tposition\tref\talt\tinput_id\tupstream_id\tid_changed\n");
            for(var marker:before.markers().values()){
                var next=after.markers().get(marker.key());boolean changed=!marker.id().equals(next.id());
                if(changed){renamed++;if(idPolicy.equals("require-reference-id"))throw new IOException("Original reference operation changed an ID under require-reference-id policy");}
                out.write(marker.key().text()+"\t"+marker.id()+"\t"+next.id()+"\t"+changed+"\n");
            }
        }
        if(renamed>0)warnings.add("REFERENCE ID CHANGES: "+renamed+" original IDs differ under explicit retain-upstream-id-with-ledger policy. Required action: review reference-transition.tsv and use exact coordinate/REF/ALT joins; IDs were not rewritten.");
        if(!conversion)warnings.add("ORIGINAL REFERENCE PREPARATION: complete ordered haplotypes and declared people/copies match the input. Original estimated error/recombination parameters and block partition are retained. Required action: assess panel suitability and parameter estimation; identity preservation does not establish an appropriate population model or historical reproduction.");
    }
}

