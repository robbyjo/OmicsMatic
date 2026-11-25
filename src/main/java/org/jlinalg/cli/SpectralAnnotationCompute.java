/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.proteomics.FragmentAnnotation;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.SpectralAnnotationRequest.require;

/** Streaming metadata expansion; every original cell remains literal and ordered. */
final class SpectralAnnotationCompute {
    static final List<String> ADDED=List.of("FragmentCharge","FragmentType","FragmentSeriesNumber","FragmentLossType");
    private SpectralAnnotationCompute() { }
    static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}

    static Map<String,Object> run(Path root,SpectralAnnotationRequest.Spec s,boolean replay,Runnable check)throws IOException {
        var budget=new OutputBudget(s.capacity().outputBytes(),check);var charges=new TreeMap<String,Long>();long b=0,y=0,rows=0;int smallest=Integer.MAX_VALUE,largest=0;List<String> header;
        Pin tablePin,ledgerPin;long decoded;
        try(var input=new LiteralTable(root.resolve("inputs/library.tsv"),s.capacity().inputBytes(),check);
            var table=new HashSink(replay?OutputStream.nullOutputStream():Files.newOutputStream(root.resolve("outputs/library.tsv"),StandardOpenOption.CREATE_NEW),budget);
            var ledger=new HashSink(replay?OutputStream.nullOutputStream():Files.newOutputStream(root.resolve("ROWS.jsonl"),StandardOpenOption.CREATE_NEW),budget)) {
            String line=input.line();require(line!=null,"Missing spectral-library header");if(line.startsWith("\ufeff"))line=line.substring(1);
            header=cells(line);require(!header.isEmpty()&&header.size()<=1020&&new HashSet<>(header).size()==header.size(),"Require at most1020 distinct original column names");
            var reserved=new HashSet<String>();for(String name:ADDED)reserved.add(headerKey(name));
            for(String name:header)require(!name.isBlank()&&name.length()<=1024&&!reserved.contains(headerKey(name)),"Existing/ambiguous appended column or invalid original header; already annotated libraries need no expansion");
            int annotation=header.indexOf(s.column());require(annotation>=0,"Declared literal annotation column is absent");
            table.text(String.join("\t",header)+"\t"+String.join("\t",ADDED)+"\n");
            while((line=input.line())!=null){
                check.run();require(rows<s.capacity().rows(),"Complete spectral-library row capacity exceeded");rows=Math.addExact(rows,1);var row=cells(line);require(row.size()==header.size(),"Spectral-library column count differs at source row "+rows);
                String raw=row.get(annotation);FragmentAnnotation a;
                try{a=FragmentAnnotation.parse(raw);}catch(IllegalArgumentException e){throw new IOException("Unsupported annotation at source row "+rows+": "+e.getMessage(),e);}
                require(a.charge()<=s.capacity().chargeLimit(),"Declared fragment-charge capacity exceeded at source row "+rows);
                charges.merge(Integer.toString(a.charge()),1L,Math::addExact);if(a.series()=='b')b++;else y++;smallest=Math.min(smallest,a.number());largest=Math.max(largest,a.number());
                // Original fields are emitted as the original literal record. No numeric conversion occurs.
                table.text(line+"\t"+a.charge()+"\t"+a.series()+"\t"+a.number()+"\t"+a.lossType()+"\n");
                ledger.text(NextflowJson.json(map("source_row",rows,"annotation",raw,"series",String.valueOf(a.series()),"number",a.number(),"charge",a.charge(),"loss_type",a.lossType()))+"\n");
            }
            require(rows>0,"Require at least one original spectral-library record; no empty preparation admitted");decoded=input.bytes();table.flush();ledger.flush();tablePin=table.finish();ledgerPin=ledger.finish();
        }
        if(replay){require(pin(root.resolve("outputs/library.tsv"),s.capacity().outputBytes(),check).equals(tablePin),"Saved expanded library differs from full source-cell/annotation replay");require(pin(root.resolve("ROWS.jsonl"),s.capacity().outputBytes(),check).equals(ledgerPin),"Saved complete annotation row ledger differs");}
        return map("schema_version",1,"status","COMPLETE_EXPLICIT_FRAGMENT_ANNOTATION_PREPARATION","input_records",rows,"input_columns",header,"appended_columns",ADDED,"original_cells_preserved",Math.multiplyExact(rows,header.size()),"fragment_charge_rows",charges,"fragment_series_rows",map("b",b,"y",y),"minimum_fragment_number",smallest,"maximum_fragment_number",largest,"decoded_input_bytes",decoded,"expanded_library",tablePin,"annotation_ledger",ledgerPin,"normalization_or_scientific_values_modified",false,"original_runtime_executed",false,"raw_MS_analysis_admitted",false);
    }
    private static String headerKey(String s){return s.toLowerCase(Locale.ROOT).replace("_","").replace("-","").replace(" ","");}
    private static List<String> cells(String line)throws IOException {
        require(!line.isEmpty(),"Empty spectral-library record; no blank record is dropped");int columns=1;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='\t'){require(++columns<=1020,"Original spectral library exceeds1020 columns");}else require(c>=32&&c!=127,"Control character in literal spectral-library record");}
        return List.of(line.split("\t",-1));
    }
    private static final class OutputBudget {
        final long maximum;final Runnable check;long used;
        OutputBudget(long maximum,Runnable check){this.maximum=maximum;this.check=check;}
        void add(long n)throws IOException{check.run();require(n<=maximum-used,"Complete expanded table plus annotation ledger exceeds output_bytes");used+=n;}
    }
    private static final class HashSink extends OutputStream {
        final OutputStream out;final MessageDigest digest;final OutputBudget budget;long count;boolean finished;
        HashSink(OutputStream out,OutputBudget budget){this.out=new BufferedOutputStream(out,65536);this.budget=budget;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
        void text(String text)throws IOException{write(text.getBytes(StandardCharsets.UTF_8));}
        @Override public void write(int value)throws IOException{budget.add(1);out.write(value);digest.update((byte)value);count++;}
        @Override public void write(byte[] data,int offset,int length)throws IOException{require(!finished,"Output digest already finalized");budget.add(length);out.write(data,offset,length);digest.update(data,offset,length);count+=length;}
        @Override public void flush()throws IOException{out.flush();}
        Pin finish(){if(finished)throw new IllegalStateException("Digest finalized twice");finished=true;return new Pin(count,HexFormat.of().formatHex(digest.digest()));}
        @Override public void close()throws IOException{out.close();}
    }
    /** UTF-8 with explicit row/byte bounds. Quotes are literal; only CR/LF record delimiters normalize. */
    private static final class LiteralTable implements AutoCloseable {
        final PushbackReader in;final Runnable check;long readBytes;
        LiteralTable(Path file,long maximum,Runnable check)throws IOException{
            this.check=check;InputStream raw=Files.newInputStream(file);InputStream bounded=new FilterInputStream(raw){
                private void add(int n)throws IOException{check.run();if(n>0){require(n<=maximum-readBytes,"Complete spectral-library input exceeds input_bytes");readBytes+=n;}}
                @Override public int read()throws IOException{int n=in.read();if(n>=0)add(1);return n;}
                @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);add(n);return n;}
            };
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            in=new PushbackReader(new BufferedReader(new InputStreamReader(bounded,decoder),65536),1);
        }
        String line()throws IOException{
            check.run();StringBuilder result=new StringBuilder();int c;
            while((c=in.read())!=-1){if(c=='\n')return result.toString();if(c=='\r'){int next=in.read();if(next!=-1&&next!='\n')in.unread(next);return result.toString();}require(result.length()<1048576,"Spectral-library record exceeds1Mi characters");result.append((char)c);}
            return result.isEmpty()?null:result.toString();
        }
        long bytes(){return readBytes;}
        @Override public void close()throws IOException{in.close();}
    }
}
