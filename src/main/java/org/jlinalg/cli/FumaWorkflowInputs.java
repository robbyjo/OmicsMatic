/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.FumaGeneSetEnrichment;
import static org.jlinalg.cli.FollowupSupport.*;

/** Audited supplied-resource bridges between existing typed FUMA stages. */
final class FumaWorkflowInputs {
    private FumaWorkflowInputs() { }
    record Limits(int bytes,int rows) { }
    record Data(List<String> header,List<String[]> rows) {
        void require(String... names)throws IOException{for(String n:names)if(!header.contains(n))throw new IOException("Missing workflow TSV column: "+n);}
        String value(String[] row,String name)throws IOException{int i=header.indexOf(name);if(i<0)throw new IOException("Missing workflow TSV column: "+name);return row[i];}
    }
    static void record(Map<Path,String> hashes,Path path,String digest)throws IOException{
        path=path.toAbsolutePath().normalize();String old=hashes.putIfAbsent(path,digest);if(old!=null&&!old.equals(digest))throw new IOException("Workflow input or completed-stage output changed: "+path);
    }
    static Data read(Path path,Map<Path,String> hashes,Limits limits)throws Exception{
        path=path.toAbsolutePath().normalize();byte[] bytes=AdmixtureInputs.raw(path,limits.bytes());record(hashes,path,AdmixtureInputs.digest(bytes));String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();var lines=text.lines().toList();if(lines.isEmpty()||lines.size()-1>limits.rows())throw new IOException("Empty or oversized workflow table: "+path);var header=Arrays.asList(lines.get(0).split("\t",-1));if(header.stream().anyMatch(String::isBlank)||new HashSet<>(header).size()!=header.size())throw new IOException("Empty/duplicate workflow TSV headers: "+path);var rows=new ArrayList<String[]>();for(int i=1;i<lines.size();i++){String line=lines.get(i);var fields=line.split("\t",-1);if(line.length()>1048576||line.indexOf('\0')>=0||fields.length!=header.size())throw new IOException("Malformed workflow TSV row "+(i+1)+": "+path);rows.add(fields);}return new Data(header,rows);
    }
    static final class NoEligibleInput extends IOException {
        private static final long serialVersionUID=1L;
        NoEligibleInput(String message){super(message);}
    }
    static void prepareVariants(Path loci,Map<String,String> resources,Map<String,String> mapping,Path out,Map<Path,String> hashes,Limits limits)throws Exception{
        for(String key:resources.keySet())if(!Set.of("annotations","candidate_score_source","candidate_score_file","extra_rows").contains(key))throw new IOException("Unknown variant_resources field: "+key);
        String policy=required(resources,"extra_rows");if(!Set.of("error","exclude-with-audit").contains(policy))throw new IOException("variant_resources.extra_rows must be error or exclude-with-audit");String scoreSource=required(resources,"candidate_score_source");if(!Set.of("annotations","file").contains(scoreSource)||scoreSource.equals("annotations")&&resources.containsKey("candidate_score_file"))throw new IOException("Declare annotations or file candidate_score_source;candidate_score_file belongs only to file mode");
        var candidates=read(loci.resolve("candidates.tsv"),hashes,limits);candidates.require("uid","rsID","chr","pos","gwasP","locus");if(candidates.rows().isEmpty())throw new NoEligibleInput("Locus selection produced no candidates;mapping and derived gene-set analyses are skipped. Review the declared locus eligibility/empty policy;no candidates are fabricated.");
        var byUid=new LinkedHashMap<String,String[]>();for(var row:candidates.rows())if(byUid.putIfAbsent(candidates.value(row,"uid"),row)!=null)throw new IOException("Duplicate completed-locus candidate UID");
        var annotations=read(Path.of(required(resources,"annotations")),hashes,limits);annotations.require("uid","chr","pos","CADD","RDB");var ledger=new ArrayList<String[]>();var annIndex=index(annotations,byUid.keySet(),"annotations",policy,ledger);var selectedAnnotations=new ArrayList<String[]>();
        Data scores=scoreSource.equals("file")?read(Path.of(required(resources,"candidate_score_file")),hashes,limits):annotations;if(scoreSource.equals("file"))scores.require("uid","chr","pos","CADD","RDB");var scoreIndex=scoreSource.equals("file")?index(scores,byUid.keySet(),"candidate-scores",policy,ledger):annIndex;
        var independent=read(loci.resolve("independent.tsv"),hashes,limits);independent.require("uid","rsID");var independentIds=new LinkedHashMap<String,String>();for(var row:independent.rows())if(independentIds.putIfAbsent(independent.value(row,"uid"),independent.value(row,"rsID"))!=null)throw new IOException("Duplicate completed-locus independent UID");
        var ld=read(loci.resolve("ld-memberships.tsv"),hashes,limits);ld.require("independent_uid","candidate_uid");var links=new LinkedHashMap<String,LinkedHashSet<String>>();for(var row:ld.rows()){String uid=ld.value(row,"candidate_uid"),lead=independentIds.get(ld.value(row,"independent_uid"));if(!byUid.containsKey(uid)||lead==null)throw new IOException("Completed-locus LD membership references an unavailable UID");links.computeIfAbsent(uid,k->new LinkedHashSet<>()).add(lead);}
        var prepared=new ArrayList<String[]>();for(var entry:byUid.entrySet()){
            String uid=entry.getKey();String[] c=entry.getValue(),a=annIndex.get(uid),s=scoreIndex.get(uid);if(a==null||s==null)throw new IOException("Selected candidate lacks supplied annotation/score row: "+uid+";supply exact canonical UID coverage,not an inferred annotation");verifyLocation(candidates,c,annotations,a);verifyLocation(candidates,c,scores,s);selectedAnnotations.add(a);var linked=links.get(uid);if(linked==null||linked.isEmpty())throw new IOException("Selected candidate lacks completed-locus independent-SNP membership: "+uid);
            prepared.add(new String[]{uid,candidates.value(c,"rsID"),candidates.value(c,"chr"),candidates.value(c,"pos"),candidates.value(c,"gwasP"),scores.value(s,"CADD"),scores.value(s,"RDB"),candidates.value(c,"locus"),String.join(";",linked)});
        }
        table(out.resolve("candidates.tsv"),List.of("uid","rsID","chr","pos","gwasP","CADD","RDB","locus","independent_rsIDs"),prepared);table(out.resolve("annotations.tsv"),annotations.header(),selectedAnnotations);mapping.put("candidates",out.resolve("candidates.tsv").toString());mapping.put("annotations",out.resolve("annotations.tsv").toString());
        for(String field:List.of("consequences","eqtls"))if(mapping.containsKey(field)){
            var data=read(Path.of(mapping.get(field)),hashes,limits);data.require("uid");var selected=new ArrayList<String[]>();int i=1;for(var row:data.rows()){i++;String uid=data.value(row,"uid");boolean retain=byUid.containsKey(uid);ledger.add(new String[]{field,""+i,uid,retain?"retained":"excluded-outside-locus-candidates"});if(retain)selected.add(row);else if(policy.equals("error"))throw new IOException("Extra "+field+" row outside selected candidates at line "+i+";choose exclude-with-audit only after reviewing the scope");}Path path=out.resolve(field+".tsv");table(path,data.header(),selected);mapping.put(field,path.toString());
        }
        table(out.resolve("resource-disposition.tsv"),List.of("role","source_line","uid","status"),ledger);
    }
    private static Map<String,String[]> index(Data data,Set<String> targets,String role,String policy,List<String[]> ledger)throws Exception{
        var result=new LinkedHashMap<String,String[]>();int i=1;for(var row:data.rows()){i++;String uid=data.value(row,"uid");if(!org.jlinalg.genetics.GeneWindowMapping.token(uid)||result.putIfAbsent(uid,row)!=null)throw new IOException("Invalid/duplicate "+role+" UID at line "+i);boolean keep=targets.contains(uid);ledger.add(new String[]{role,""+i,uid,keep?"selected":"excluded-outside-locus-candidates"});if(!keep&&policy.equals("error"))throw new IOException("Extra "+role+" UID outside selected candidates at line "+i+";review explicit extra_rows policy");}return result;
    }
    private static void verifyLocation(Data candidates,String[] c,Data resource,String[] r)throws Exception{String chr=resource.value(r,"chr");int chromosome=chr.equals("X")?23:Integer.parseInt(chr);if(chromosome!=Integer.parseInt(candidates.value(c,"chr"))||Integer.parseInt(resource.value(r,"pos"))!=Integer.parseInt(candidates.value(c,"pos")))throw new IOException("Annotation/score chromosome or position differs for selected UID "+candidates.value(c,"uid")+";do not mix assemblies or silently liftover");}
    static Path prepareQuery(Path mapping,Map<String,String> geneSet,Path out,Map<Path,String> hashes,Limits limits)throws Exception{
        var data=read(mapping.resolve("genes.tsv"),hashes,limits);data.require("gene","positional_variants","eqtl_variants");var query=new LinkedHashSet<String>();var audit=new ArrayList<String[]>();for(var row:data.rows()){String gene=data.value(row,"gene");boolean supported=Integer.parseInt(data.value(row,"positional_variants"))>0||Integer.parseInt(data.value(row,"eqtl_variants"))>0||data.header().contains("chromatin_variants")&&Integer.parseInt(data.value(row,"chromatin_variants"))>0;audit.add(new String[]{gene,supported?"actual-links-query":"excluded-declaration-only"});if(supported)query.add(gene);}
        table(out.resolve("query-disposition.tsv"),List.of("gene","status"),audit);Path file=out.resolve("query.txt");java.nio.file.Files.writeString(file,String.join("\n",query)+(query.isEmpty()?"":"\n"));if(query.size()<2)throw new NoEligibleInput("Fewer than two actually supported mapped gene IDs;derived GENE2FUNC is skipped. Review declared scientific eligibility/resource coverage;no genes are added automatically.");
        // Reuse the actual background/biotype/MHC/namespace preparation,including its global symbol priority.
        var annotation=read(Path.of(required(geneSet,"annotations")),hashes,limits);annotation.require("ensembl_gene_id","entrezID","external_gene_name","gene_biotype","chromosome_name","start_position","end_position");var geneRows=new ArrayList<FumaGeneSetEnrichment.GeneRow>();for(var row:annotation.rows())geneRows.add(new FumaGeneSetEnrichment.GeneRow(annotation.value(row,"ensembl_gene_id"),annotation.value(row,"entrezID"),annotation.value(row,"external_gene_name"),annotation.value(row,"gene_biotype"),annotation.value(row,"chromosome_name"),Long.parseLong(annotation.value(row,"start_position")),Long.parseLong(annotation.value(row,"end_position"))));
        if(geneSet.containsKey("background")==geneSet.containsKey("biotypes"))throw new IOException("Derived GENE2FUNC needs exactly one background or biotypes specification");List<String> background=null;Set<String> biotypes=null;
        if(geneSet.containsKey("background")){Path path=Path.of(geneSet.get("background")).toAbsolutePath().normalize();byte[] bytes=AdmixtureInputs.raw(path,limits.bytes());record(hashes,path,AdmixtureInputs.digest(bytes));background=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().lines().map(String::strip).filter(s->!s.isEmpty()).toList();if(background.size()>limits.rows())throw new IOException("Background ID row budget exceeded");}
        else {String[] tokens=required(geneSet,"biotypes").split(":",-1);biotypes=new HashSet<>();for(String token:tokens)if(token.isBlank()||!biotypes.add(token))throw new IOException("Invalid/duplicate biotype selection");}
        String exclusion=required(geneSet,"exclude-mhc");if(!Set.of("true","false").contains(exclusion))throw new IOException("exclude-mhc must be explicit true or false");var prepared=FumaGeneSetEnrichment.inspect(geneRows,new ArrayList<>(query),background,biotypes,Boolean.parseBoolean(exclusion));
        table(out.resolve("query-preflight-id-mapping.tsv"),List.of("input","upper_input","global_namespace","mapped_entrez"),prepared.queryMapping().stream().map(m->new String[]{m.input(),m.upperInput(),m.namespace(),String.join(":",m.entrez())}).toList());
        if(!prepared.queryNamespace().equals("ensembl"))throw new IOException("Derived mapping IDs require Ensembl interpretation;actual GENE2FUNC global namespace would be "+prepared.queryNamespace()+" after background/biotype/MHC preparation. Reconcile explicit reference namespaces;no automatic translation is performed.");
        if(prepared.selected().size()<2)throw new NoEligibleInput("Fewer than two mapped IDs remain in the explicit GENE2FUNC background after filtering. Review query-preflight-id-mapping.tsv and background/resource choices;no analysis ran and no genes are fabricated.");
        return file;
    }
}
