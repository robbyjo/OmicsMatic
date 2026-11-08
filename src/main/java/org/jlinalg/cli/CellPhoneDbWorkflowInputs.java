/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.jlinalg.singlecell.CellPhoneDbPreparation;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Bounded strict UTF8 CSV/TSV inputs, unchanged labels and safe database members. No header trimming or missing-value repair. */
final class CellPhoneDbWorkflowInputs {
    private CellPhoneDbWorkflowInputs() { }
    private static final Set<String> PANDAS_NA=Set.of("","#N/A","#N/A N/A","#NA","-1.#IND","-1.#QNAN","-NaN","-nan","1.#IND","1.#QNAN","<NA>","N/A","NA","NULL","NaN","None","n/a","nan","null");
    record Table(List<String> header,List<List<String>> rows) { }
    record Preflight(Map<String,Table> metadata) { }
    static final class Rows implements AutoCloseable {
        final PushbackReader input;final char separator;final long maximum;long characters;boolean ended;
        Rows(Path path,char separator,long maximum)throws IOException {
            this.separator=separator;this.maximum=maximum;InputStream original=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS);var buffered=new BufferedInputStream(original);buffered.mark(2);int first=buffered.read(),second=buffered.read();buffered.reset();InputStream decoded=first==31&&second==139?new GZIPInputStream(buffered):buffered;
            InputStream bounded=new FilterInputStream(decoded){long bytes;private void add(int n)throws IOException{if(n>0){bytes=Math.addExact(bytes,n);require(bytes<=maximum,"Complete decompressed original table byte capacity exceeded");}}@Override public int read()throws IOException{int value=in.read();add(value<0?0:1);return value;}@Override public int read(byte[] b,int offset,int length)throws IOException{int n=in.read(b,offset,length);add(n);return n;}};
            input=new PushbackReader(new BufferedReader(new InputStreamReader(bounded,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))),1);
        }
        private int read()throws IOException {int value=input.read();if(value>=0){characters++;require(characters<=maximum,"Complete original text/record capacity exceeded");if((characters&65535)==0)require(!Thread.currentThread().isInterrupted(),"Original input parsing cancelled; no partial family");}return value;}
        List<String> next()throws IOException {
            if(ended)return null;var fields=new ArrayList<String>();var value=new StringBuilder();boolean quoted=false,closed=false,started=false,any=false;
            for(;;){int raw=read();if(raw<0){ended=true;require(!quoted,"Unterminated original CSV/TSV quotation");if(!any&&fields.isEmpty()&&value.isEmpty())return null;fields.add(value.toString());return fields;}char c=(char)raw;any=true;
                if(quoted){if(c=='"'){int next=read();if(next=='"')value.append('"');else{quoted=false;closed=true;if(next>=0){input.unread(next);characters--;}}}else value.append(c);continue;}
                if(c==separator){fields.add(value.toString());value.setLength(0);started=false;closed=false;continue;}
                if(c=='\r'||c=='\n'){if(c=='\r'){int next=read();if(next>=0&&next!='\n'){input.unread(next);characters--;}}fields.add(value.toString());return fields;}
                require(!closed,"Unexpected text after a closed quoted field; no whitespace/header repair");if(c=='"'){require(!started&&value.isEmpty(),"Quote inside an unquoted field is outside the strict original-input profile");quoted=true;started=true;}else{value.append(c);started=true;}
            }
        }
        @Override public void close()throws IOException{input.close();}
    }
    static Table table(Path path,String format,long maximum,int maximumRows,int maximumColumns)throws IOException {
        try(var input=new Rows(path,format.equals("csv")?',':'\t',maximum)){var header=input.next();require(header!=null&&!header.isEmpty()&&header.size()<=maximumColumns,"Missing/oversized complete table header");var rows=new ArrayList<List<String>>();for(List<String> row;(row=input.next())!=null;){if(row.size()==1&&row.get(0).isEmpty())continue;require(row.size()==header.size()&&rows.size()<maximumRows,"Ragged table or complete row capacity exceeded");rows.add(List.copyOf(row));}return new Table(List.copyOf(header),List.copyOf(rows));}
    }
    static CellPhoneDbPreparation.Counts rawCounts(Path path,long maximum,int maximumGenes,int maximumCells,long maximumMatrixCells)throws IOException {
        try(var input=new Rows(path,'\t',maximum)){var header=input.next();require(header!=null&&header.size()>1&&header.size()-1<=maximumCells,"Missing/oversized interpreted count header");var cells=new ArrayList<String>();var unique=new HashSet<String>();for(String cell:header.subList(1,header.size())){CellPhoneDbWorkflowRequest.data(cell);require(unique.add(cell),"Duplicate interpreted original cell header");cells.add(cell);}var genes=new ArrayList<String>();var values=new ArrayList<double[]>();
            for(List<String> row;(row=input.next())!=null;){require(row.size()==header.size()&&genes.size()<maximumGenes&&(long)(genes.size()+1)*cells.size()<=maximumMatrixCells,"Ragged/oversized complete interpreted matrix; no partial rows admitted");genes.add(CellPhoneDbWorkflowRequest.data(row.get(0)));double[] next=new double[cells.size()];for(int j=0;j<next.length;j++){try{next[j]=Double.parseDouble(row.get(j+1));}catch(NumberFormatException failure){throw new IOException("Nonnumeric interpreted expression at gene row "+genes.size()+", cell "+cells.get(j),failure);}require(Double.isFinite(next[j])&&next[j]>=0,"Nonfinite/negative original expression at gene row "+genes.size()+", cell "+cells.get(j)+"; no repair");}values.add(next);}
            require(!genes.isEmpty(),"Interpreted matrix has no complete gene family");return new CellPhoneDbPreparation.Counts(genes,cells,values.toArray(double[][]::new));
        }
    }
    static Preflight preflight(CellPhoneDbWorkflowRequest.Request request,Map<String,Path> captured)throws IOException {
        var metadata=new LinkedHashMap<String,Table>();
        for(var case_:request.analyses()){String id=text(case_.get("id"));var specification=object(case_.get("metadata"));var table=table(captured.get(id+"-metadata"),text(specification.get("format")),request.inputBytes(),request.maximumCells(),100000);var names=new ArrayList<String>();for(String name:table.header())names.add(name.toLowerCase(Locale.ROOT));int cell=names.indexOf("cell"),type=names.indexOf("cell_type");require(cell>=0&&type>=0&&names.lastIndexOf("cell")==cell&&names.lastIndexOf("cell_type")==type,"Declare unambiguous cell and cell_type metadata headers; no layout guessing");var seen=new HashSet<String>();require(!table.rows().isEmpty(),"Complete metadata family is empty");for(var row:table.rows()){dataLabel(row.get(cell));dataLabel(row.get(type));require(seen.add(row.get(cell)),"Duplicate original metadata cell; no index replacement");}metadata.put(id,table);var database=object(case_.get("database"));if(database.containsKey("expected_sha256"))require(NextflowFiles.pin(captured.get(id+"-database"),request.inputBytes(),()->{}).sha256().equals(text(database.get("expected_sha256"))),"Original database differs from explicitly expected SHA256; no version fallback");database(captured.get(id+"-database"),request.inputBytes());
        }
        return new Preflight(Collections.unmodifiableMap(metadata));
    }
    static void dataLabel(String label)throws IOException {CellPhoneDbWorkflowRequest.data(label);require(!PANDAS_NA.contains(label),"Original metadata label matches a pandas2.2.3 missing-value token: "+label+". Keep original IDs and provide a separately mapped source-compatible export; no automatic rename occurs");}
    private static void database(Path path,long maximum)throws IOException {
        var names=new HashSet<String>();long bytes=0;var allowed=Set.of("gene_table.csv","protein_table.csv","complex_table.csv","complex_composition_table.csv","multidata_table.csv","interaction_table.csv","gene_synonym_to_gene_name.csv","receptor_to_transcription_factor.csv");try(var archive=new ZipFile(path.toFile(),StandardCharsets.UTF_8)){var entries=archive.entries();while(entries.hasMoreElements()){var entry=entries.nextElement();String name=entry.getName();require(!entry.isDirectory()&&allowed.contains(name)&&names.add(name),"Unknown, duplicate or non-table database ZIP member; the archive is read as data and never extracted");long size=entry.getSize();require(size>=0&&size<=maximum-bytes,"Complete expanded database byte capacity exceeded");bytes+=size;}}
        require(names.containsAll(Set.of("gene_table.csv","protein_table.csv","complex_table.csv","complex_composition_table.csv","multidata_table.csv","interaction_table.csv")),"Original database is missing a required complete table family");
    }
}
