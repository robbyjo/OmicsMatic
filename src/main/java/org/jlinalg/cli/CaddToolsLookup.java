/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import htsjdk.tribble.readers.TabixReader;
import org.jlinalg.genetics.CaddVariantPreparation;
import org.jlinalg.genetics.VepVcf;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Complete indexed lookups retain every overlapping candidate and every exact score hit. */
final class CaddToolsLookup {
    private CaddToolsLookup(){ }
    interface Source extends AutoCloseable {List<String> query(long row,int allele,int table,CaddVariantPreparation.Prepared variant)throws IOException;List<Map<String,Object>> bindings();void close()throws IOException;}
    static Map<String,Object> prepared(CaddVariantPreparation.Prepared v){return Map.of("chromosome",v.chromosome(),"position",v.position(),"reference",v.reference(),"alternate",v.alternate());}
    static final class Original implements Source {
        private final List<TabixReader> readers=new ArrayList<>();private final List<Map<String,Object>> bindings=new ArrayList<>();private final Map<Path,NextflowFiles.Pin> pins=new LinkedHashMap<>();private final Writer capture;private final Map<String,Object> request;private long candidates;
        Original(Path root,Map<String,Object> request)throws IOException {this.request=request;capture=Files.newBufferedWriter(root.resolve("lookup-capture.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);long remaining=CaddToolsRequest.number(request,"maximum_resource_bytes");
            try{for(Object value:list(request.get("score_tables"),"score_tables")){
                var t=object(value,"score table");Path file=CaddToolsFiles.required(Path.of(text(t,"file"))),index=CaddToolsFiles.required(Path.of(text(t,"index")));for(var pair:List.of(Map.entry(file,"sha256"),Map.entry(index,"index_sha256"))){var p=NextflowFiles.pin(pair.getKey(),remaining,()->{});remaining-=p.bytes();if(!p.sha256().equals(text(t,pair.getValue())))throw new IOException("Complete CADD resource hash differs: "+pair.getKey());pins.put(pair.getKey(),p);}
                TabixReader reader=new TabixReader(file.toString(),index.toString());readers.add(reader);var headers=new ArrayList<String>();String line;while((line=reader.readLine())!=null&&line.startsWith("#")){if(line.length()>CaddToolsRequest.number(request,"maximum_line_characters"))throw new IOException("CADD resource header line capacity exceeded");headers.add(line);if(headers.size()>10000)throw new IOException("CADD resource header count capacity exceeded");}
                if(headers.stream().noneMatch(h->h.contains("CADD "+request.get("assembly")+"-v1.6"))||!headers.contains("#Chrom\tPos\tRef\tAlt\tRawScore\tPHRED"))throw new IOException("CADD resource assembly/version or six-column source header differs; no schema/edition substitution");
                var item=new LinkedHashMap<String,Object>(t);item.put("file_bytes",pins.get(file).bytes());item.put("index_bytes",pins.get(index).bytes());item.put("headers",headers);item.put("contigs",new TreeSet<>(reader.getChromosomes()));bindings.add(item);
            }}catch(IOException|RuntimeException e){try{close();}catch(IOException suppressed){e.addSuppressed(suppressed);}throw e;}
        }
        public List<Map<String,Object>> bindings(){return List.copyOf(bindings);}
        public List<String> query(long row,int allele,int table,CaddVariantPreparation.Prepared v)throws IOException {var reader=readers.get(table);var rows=new ArrayList<String>();boolean uncovered=!reader.getChromosomes().contains(v.chromosome());if(!uncovered){int pos=Integer.parseInt(v.position());TabixReader.Iterator iterator=reader.query(v.chromosome(),pos-1,pos);if(iterator!=null){String line;while((line=iterator.next())!=null){if(++candidates>CaddToolsRequest.number(request,"maximum_hits")||line.length()>CaddToolsRequest.number(request,"maximum_line_characters"))throw new IOException("Complete CADD query candidate capacity exceeded");rows.add(line);}}}
            var event=Map.of("input_row",row,"allele_index",allele,"table_id",bindings.get(table).get("id"),"prepared",prepared(v),"uncovered_contig",uncovered,"rows",rows);String encoded=NextflowJson.json(event);if(encoded.length()>CaddToolsRequest.number(request,"maximum_line_characters"))throw new IOException("Complete saved CADD query-event line capacity exceeded");capture.write(encoded);capture.write('\n');return rows;
        }
        void verifyResources()throws IOException{for(var p:pins.entrySet())if(!p.getValue().equals(NextflowFiles.pin(p.getKey(),p.getValue().bytes(),()->{})))throw new IOException("CADD complete score resource changed during lookup: "+p.getKey());}
        public void close()throws IOException{IOException failure=null;for(var reader:readers)reader.close();try{capture.close();}catch(IOException e){failure=e;}if(failure!=null)throw failure;}
    }
    static final class Replay implements Source {
        private final List<Map<String,Object>> bindings;private final CaddToolsFiles.Lines input;private final Map<String,Object> request;private long candidates;
        Replay(Path root,Map<String,Object> request)throws IOException {this.request=request;bindings=new ArrayList<>();var saved=read(root.resolve("score-resource-bindings.json"),20000000);keys(saved,Set.of("resources","authentication"));if(!Boolean.FALSE.equals(saved.get("authentication")))throw new IOException("CADD resource binding must not claim authentication");var tables=list(request.get("score_tables"),"score_tables");var resources=list(saved.get("resources"),"resources");if(tables.size()!=resources.size())throw new IOException("Saved CADD table count differs");long bytes=0;
            for(int i=0;i<tables.size();i++){var t=object(tables.get(i),"table");var b=object(resources.get(i),"binding");keys(b,Set.of("id","file","index","sha256","index_sha256","file_bytes","index_bytes","headers","contigs"));for(String key:t.keySet())if(!same(t.get(key),b.get(key)))throw new IOException("Saved CADD resource identity differs");bytes=Math.addExact(bytes,integer(b,"file_bytes",-1,0,2000000000000L));bytes=Math.addExact(bytes,integer(b,"index_bytes",-1,0,2000000000000L));if(bytes>CaddToolsRequest.number(request,"maximum_resource_bytes"))throw new IOException("Saved complete CADD resource capacity exceeded");var headers=list(b.get("headers"),"headers");if(!headers.contains("#Chrom\tPos\tRef\tAlt\tRawScore\tPHRED")||headers.stream().noneMatch(h->h instanceof String s&&s.contains("CADD "+request.get("assembly")+"-v1.6")))throw new IOException("Saved CADD source header differs");strings(b.get("contigs"),"contigs",1,100000);bindings.add(b);}
            input=new CaddToolsFiles.Lines(root.resolve("lookup-capture.jsonl"),request);
        }
        public List<Map<String,Object>> bindings(){return List.copyOf(bindings);}
        public List<String> query(long row,int allele,int table,CaddVariantPreparation.Prepared v)throws IOException {String line=input.next();if(line==null)throw new IOException("Missing complete saved CADD query");var event=object(SimpleJson.parse(line),"query");keys(event,Set.of("input_row","allele_index","table_id","prepared","uncovered_contig","rows"));var b=bindings.get(table);boolean uncovered=!list(b.get("contigs"),"contigs").contains(v.chromosome());if(integer(event,"input_row",0,1,100000000)!=row||integer(event,"allele_index",0,1,100000000)!=allele||!text(event,"table_id").equals(b.get("id"))||!same(prepared(v),event.get("prepared"))||bool(event,"uncovered_contig",!uncovered)!=uncovered)throw new IOException("Saved CADD query/allele/resource provenance differs");var rows=new ArrayList<String>();for(Object raw:list(event.get("rows"),"rows")){if(!(raw instanceof String text)||text.indexOf('\n')>=0||text.indexOf('\r')>=0||text.length()>CaddToolsRequest.number(request,"maximum_line_characters")||++candidates>CaddToolsRequest.number(request,"maximum_hits"))throw new IOException("Saved CADD query candidate invalid/capacity exceeded");rows.add(text);}if(uncovered&&!rows.isEmpty())throw new IOException("Uncovered CADD chromosome cannot supply a score");return rows;}
        public void close()throws IOException{try{if(input.next()!=null)throw new IOException("Extra saved CADD query");}finally{input.close();}}
    }
    static Map<String,Object> render(Path root,Map<String,Object> r,Source source,boolean compare)throws IOException {
        long[] totals={0,0,0,0,0};boolean prepare=text(r,"representation").equals("original-CADD1.6-preparation");
        try(var output=new CaddToolsFiles.Output(root.resolve("results"),r,compare);var alleles=output.writer("alleles.tsv");var scores=output.writer("scores.tsv");var queries=output.writer("lookup-variants.vcf")){
            CaddToolsFiles.row(alleles,"input_row","allele_index","ID","original_CHROM","original_POS","original_REF","original_ALT","lookup_CHROM","lookup_POS","lookup_REF","lookup_ALT","prefix_trim","suffix_trim","state","score_hits","table_id");CaddToolsFiles.row(scores,"input_row","allele_index","table_id","source_hit","Chrom","Pos","Ref","Alt","RawScore","PHRED");CaddToolsFiles.row(queries,"#CHROM","POS","ID","REF","ALT");
            var summary=VepVcf.inspectOriginal(root.resolve(text(r,"input_vcf")),new VepVcf.Limits(CaddToolsRequest.number(r,"maximum_decoded_characters"),CaddToolsRequest.number(r,"maximum_variants"),CaddToolsRequest.number(r,"maximum_hits"),(int)CaddToolsRequest.number(r,"maximum_line_characters")),variant->{var fields=variant.fields();String[] alternatives=fields.get(4).split(",",-1);
                for(int a=0;a<alternatives.length;a++){if(++totals[0]>CaddToolsRequest.number(r,"maximum_hits"))throw new IOException("Complete CADD allele capacity exceeded");var v=CaddVariantPreparation.prepare(fields.get(0),fields.get(1),fields.get(3),alternatives[a],prepare);String state=v.state(),table="";int hits=0;
                    if(v.eligible()){
                        CaddToolsFiles.row(queries,v.chromosome(),v.position(),prepare?".":fields.get(2),v.reference(),v.alternate());totals[1]++;
                        for(int i=0;i<source.bindings().size();i++){int sourceHit=0;for(String line:source.query(variant.row(),a+1,i,v)){String[] hit=line.split("\t",-1);if(hit.length!=6||!hit[0].equals(v.chromosome())||!hit[1].matches("[1-9][0-9]*"))throw new IOException("CADD indexed source candidate schema/chromosome differs");if(hit[1].equals(v.position())&&hit[2].equals(v.reference())&&hit[3].equals(v.alternate())){if(++totals[4]>CaddToolsRequest.number(r,"maximum_hits"))throw new IOException("Complete CADD score-hit capacity exceeded");table=text(source.bindings().get(i),"id");CaddToolsFiles.row(scores,variant.row(),a+1,table,++sourceHit,hit[0],hit[1],hit[2],hit[3],hit[4],hit[5]);hits++;}}
                            if(hits>0)break;
                        }
                        state=hits>0?"MATCHED_SOURCE_SCORE":"NO_SCORE_DEPENDENT_PREDICTION_SKIPPED";if(hits>0)totals[2]++;else totals[3]++;
                    }
                    CaddToolsFiles.row(alleles,variant.row(),a+1,fields.get(2),fields.get(0),fields.get(1),fields.get(3),alternatives[a],v.chromosome(),v.position(),v.reference(),v.alternate(),v.prefixTrim(),v.suffixTrim(),state,hits,table);
                }
            });
            return Map.of("variants",summary.variants(),"alleles",totals[0],"eligible_alleles",totals[1],"matched_alleles",totals[2],"no_score_alleles",totals[3],"source_omitted_alleles",totals[0]-totals[1],"score_hits",totals[4],"prediction_model_executed",false,"reference_sequence_checked",false,"whole_paper_reproduced",false);
        }
    }
}
