/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.DiamondRequest.need;

/** Complete native input/coordinate/statistic transport checks; alignment and E-values remain upstream. */
final class DiamondAudit {
    private DiamondAudit() { }
    record Inputs(DiamondSequences.Population reference,List<DiamondSequences.Population> queries) { }
    static Inputs inputs(Path root,DiamondRequest.Request request,Runnable cancellation)throws IOException {
        var l=request.limits();DiamondSequences.Population reference=request.databaseKind().equals("protein-fasta")?DiamondSequences.read(root.resolve("inputs/reference.original"),true,l,cancellation):null;
        long records=reference==null?0:reference.records().size(),decoded=reference==null?0:reference.decodedBytes();var queries=new ArrayList<DiamondSequences.Population>();
        for(int i=0;i<request.queries().size();i++){var q=DiamondSequences.read(root.resolve("inputs/query-"+(i+1)+".original"),false,l,cancellation);queries.add(q);records=Math.addExact(records,q.records().size());decoded=Math.addExact(decoded,q.decodedBytes());}
        need(records<=l.records()&&decoded<=l.decodedBytes(),"Complete DIAMOND input record/decoded-byte capacity exceeded");return new Inputs(reference,List.copyOf(queries));
    }
    static Map<String,Object> verify(Path root,Path worker,DiamondRequest.Request r,boolean write,Runnable cancellation)throws IOException {
        Inputs input=inputs(root,r,cancellation);var queries=new LinkedHashMap<String,List<DiamondSequences.Sequence>>();long queryRecords=0;
        for(var population:input.queries())for(var sequence:population.records()){queries.computeIfAbsent(sequence.id(),k->new ArrayList<>()).add(sequence);queryRecords++;}
        Map<String,List<DiamondSequences.Sequence>> reference=input.reference()==null?Map.of():DiamondSequences.byId(input.reference());
        try(var census=new Output(root.resolve("input-records.jsonl"),write,r.limits(),cancellation)) {
            if(input.reference()!=null)population("reference",input.reference(),census);
            for(int i=0;i<input.queries().size();i++)population("query-"+(i+1),input.queries().get(i),census);census.finish();
        }
        var hitRows=new TreeMap<String,Long>();long rows=0,zeros=0,nonfinite=0,ambiguousQueryRows=0,ambiguousReferenceRows=0;
        try(var source=new DiamondSequences.Lines(worker.resolve("hits.tsv"),r.limits().lineCharacters(),cancellation);
            var annotated=new Output(root.resolve("audited-hits.tsv"),write,r.limits(),cancellation)) {
            annotated.line(String.join("\t",DiamondRequest.FIELDS)+"\tinferred_identical_columns\tinferred_gap_columns\tcoordinate_query_coverage\tcoordinate_subject_coverage\tquery_id_candidates\treference_id_candidates");
            for(String line=source.next();line!=null;line=source.next()) {
                need(++rows<=r.limits().rows(),"DIAMOND alignment-row capacity exceeded");String[] v=line.split("\t",-1);need(v.length==DiamondRequest.FIELDS.size(),"Original DIAMOND output field population differs");
                String qid=v[0],sid=v[1];need(queries.containsKey(qid),"Source query ID absent from complete input census");long length=integer(v[3],1,Integer.MAX_VALUE),mismatch=integer(v[4],0,length),opens=integer(v[5],0,length),qstart=integer(v[6],1,Integer.MAX_VALUE),qend=integer(v[7],1,Integer.MAX_VALUE),sstart=integer(v[8],1,Integer.MAX_VALUE),send=integer(v[9],1,Integer.MAX_VALUE),qlen=integer(v[12],1,Integer.MAX_VALUE),slen=integer(v[13],1,Integer.MAX_VALUE);
                need(Math.max(qstart,qend)<=qlen&&sstart<=send&&send<=slen,"Source alignment coordinates exceed declared sequence lengths");long qspan=Math.abs(qend-qstart)+1,sspan=send-sstart+1;
                if(r.search().get("mode").equals("blastx")){need(qspan%3==0,"Original standard non-frameshift BLASTX span is not complete codons");qspan/=3;}else need(qstart<=qend,"Protein query coordinates are reversed");
                long gaps=2*length-qspan-sspan,identical=qspan+sspan-length-mismatch;
                need(gaps>=0&&gaps<=length&&identical>=0&&identical<=length&&opens<=gaps&&(gaps==0)==(opens==0),"Original alignment length/mismatch/gap statistics are inconsistent");
                double percent=number(v[2]);need(Double.isFinite(percent)&&percent>=0&&percent<=100&&Math.abs(percent-identical*100.0/length)<=0.050000001,"Original printed identity differs from coordinate/statistic counts");
                double evalue=number(v[10]),bits=number(v[11]);if(!Double.isFinite(evalue)||!Double.isFinite(bits))nonfinite++;if(Double.isFinite(evalue))need(evalue>=0,"Negative original DIAMOND E-value");if(evalue==0)zeros++;
                long qc=queries.get(qid).stream().filter(q->q.length()==qlen).count();need(qc>0,"Source query length differs from all literal-ID input candidates");if(qc>1)ambiguousQueryRows++;
                long sc=-1;if(input.reference()!=null){sc=reference.getOrDefault(sid,List.of()).stream().filter(s->s.length()==slen).count();need(sc>0,"Source subject ID/length absent from complete reference FASTA");if(sc>1)ambiguousReferenceRows++;}
                hitRows.merge(qid,1L,Math::addExact);
                long sourceQspan=Math.abs(qend-qstart)+1;annotated.line(line+"\t"+identical+"\t"+gaps+"\t"+ratio(sourceQspan,qlen)+"\t"+ratio(sspan,slen)+"\t"+qc+"\t"+(sc<0?"opaque-native-database":sc));
            }
            annotated.finish();
        }
        long duplicateIds=queries.values().stream().filter(v->v.size()>1).count(),unmatched=0,ambiguous=0;
        try(var disposition=new Output(root.resolve("query-dispositions.jsonl"),write,r.limits(),cancellation)) {
            for(int i=0;i<input.queries().size();i++)for(var sequence:input.queries().get(i).records()) {
                long count=hitRows.getOrDefault(sequence.id(),0L);String state=count==0?"no-reported-source-hit":queries.get(sequence.id()).size()>1?"ambiguous-duplicate-query-id-with-source-hits":"source-hits-reported";if(count==0)unmatched++;if(state.startsWith("ambiguous"))ambiguous++;
                disposition.line(NextflowJson.json(ordered("query_file",i+1,"record_index",sequence.index(),"id",sequence.id(),"length",sequence.length(),"source_rows_with_this_id",count,"disposition",state)));
            }
            disposition.finish();
        }
        return ordered("source_version",r.version(),"query_files",input.queries().size(),"query_records",queryRecords,"reference_records",input.reference()==null?null:input.reference().records().size(),"original_alignment_rows",rows,"source_reported_zero_Evalues",zeros,"source_nonfinite_numeric_rows",nonfinite,"duplicate_query_ids",duplicateIds,"ambiguous_query_id_rows",ambiguousQueryRows,"ambiguous_reference_id_rows",ambiguousReferenceRows,"no_reported_hit_records",unmatched,"ambiguous_query_dispositions",ambiguous,"native_reference_population_checked",input.reference()!=null,"native_coordinate_identity_statistic_checks",true,"alignment_placement_and_Evalues_independently_refitted",false,"source_reported_fields_rounded",true,"study_cutoff_reinterpretation_applied",false);
    }
    private static void population(String label,DiamondSequences.Population p,Output output)throws IOException {
        for(var s:p.records())output.line(NextflowJson.json(ordered("dataset",label,"format",p.format(),"gzip",p.gzip(),"record_index",s.index(),"id",s.id(),"title",s.title(),"sequence_length",s.length())));
    }
    static LinkedHashMap<String,Object> ordered(Object... pairs){var values=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)values.put((String)pairs[i],pairs[i+1]);return values;}
    private static String ratio(long span,long length){return BigDecimal.valueOf(span).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(length),16,RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();}
    private static long integer(String value,long min,long max)throws IOException {try{need(value.matches("[0-9]+"),"Noninteger source alignment coordinate/count");long n=Long.parseLong(value);need(n>=min&&n<=max,"Source alignment coordinate/count outside bounds");return n;}catch(NumberFormatException error){throw new IOException("Source alignment integer capacity exceeded",error);}}
    private static double number(String value)throws IOException {try{return switch(value){case "nan","NaN"->Double.NaN;case "inf","Inf"->Double.POSITIVE_INFINITY;case "-inf","-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value);};}catch(NumberFormatException error){throw new IOException("Invalid original source numeric field",error);}}
    private static final class Output implements AutoCloseable {
        private final BufferedWriter writer;private final DiamondSequences.Lines reader;
        Output(Path path,boolean write,DiamondRequest.Limits limits,Runnable cancellation)throws IOException {writer=write?Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW):null;reader=write?null:new DiamondSequences.Lines(path,(int)Math.min(Integer.MAX_VALUE-8L,(long)limits.lineCharacters()*8+4096),cancellation);}
        void line(String text)throws IOException {if(writer!=null){writer.write(text);writer.write('\n');}else need(text.equals(reader.next()),"Native DIAMOND census/annotation/disposition replay differs");}
        void finish()throws IOException {if(reader!=null)need(reader.next()==null,"Extra native DIAMOND output rows");}
        @Override public void close()throws IOException {if(writer!=null)writer.close();if(reader!=null)reader.close();}
    }
}
