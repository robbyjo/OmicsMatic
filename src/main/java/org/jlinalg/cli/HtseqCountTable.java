/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Literal source count-table audit; never normalizes, rounds or repairs identifiers. */
final class HtseqCountTable {
    private HtseqCountTable() { }
    record Field(String name,String value) { }
    record Table(List<Field> genes,List<Field> summary) {
        Table {genes=List.copyOf(genes);summary=List.copyOf(summary);}
        String genesTsv(){return tsv("feature_id",genes);}
        String summaryTsv(){return tsv("category",summary);}
        private static String tsv(String first,List<Field> rows){var out=new StringBuilder(first+"\tcount\n");for(var row:rows)out.append(row.name).append('\t').append(row.value).append('\n');return out.toString();}
    }
    static Table read(Path path,String version,String nonunique,long byteLimit,int rowLimit)throws IOException{
        var before=HtseqFileContract.pin(path,byteLimit);var rows=new ArrayList<Field>();
        var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try(var reader=new BufferedReader(new InputStreamReader(Files.newInputStream(path),decoder))){
            for(String line;(line=line(reader))!=null;){
                HtseqFileContract.CHECK.run();if(rows.size()>=rowLimit)throw new IOException("HTSeq table row capacity exceeded");
                int tab=line.indexOf('\t');if(tab<1||line.indexOf('\t',tab+1)>=0)throw new IOException("HTSeq source table requires exactly2 fields and a nonempty identity");
                String name=line.substring(0,tab),value=line.substring(tab+1);
                if(name.codePoints().anyMatch(c->c<32||c==127)||!value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))throw new IOException("Invalid literal HTSeq identity/count field");
                try{var number=new BigDecimal(value);if(number.signum()<0||!Double.isFinite(number.doubleValue()))throw new IOException("Nonfinite/negative HTSeq count");}
                catch(NumberFormatException bad){throw new IOException("Invalid complete HTSeq count",bad);}
                rows.add(new Field(name,value));
            }
        }
        if(!before.equals(HtseqFileContract.pin(path,byteLimit))||rows.size()<5)throw new IOException("HTSeq source table changed or lacks five summary categories");
        int n=rows.size()-5;var genes=rows.subList(0,n);var summary=rows.subList(n,rows.size());
        var names=List.of("no_feature","ambiguous","too_low_aQual","not_aligned","alignment_not_unique");
        for(int i=0;i<5;i++){
            String expected=(version.equals("0.5.3")?"":"__")+names.get(i);
            if(!summary.get(i).name.equals(expected))throw new IOException("HTSeq summary name/order differs from exact edition: "+expected);
            integral(summary.get(i));
        }
        var seen=new HashSet<String>();String previous=null;
        for(var field:genes){
            if(!seen.add(field.name)||previous!=null&&compare(previous,field.name)>=0)throw new IOException("HTSeq feature identities are duplicate or not source-codepoint sorted");
            previous=field.name;if(!nonunique.equals("fraction"))integral(field);
        }
        return new Table(genes,summary);
    }
    private static void integral(Field row)throws IOException{try{new BigDecimal(row.value).toBigIntegerExact();}catch(ArithmeticException bad){throw new IOException("Nonintegral HTSeq category/nonfraction count: "+row.name,bad);}}
    private static int compare(String a,String b){
        int i=0,j=0;while(i<a.length()&&j<b.length()){int x=a.codePointAt(i),y=b.codePointAt(j);if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}return Integer.compare(a.length()-i,b.length()-j);
    }
    private static String line(BufferedReader reader)throws IOException{
        var out=new StringBuilder();boolean any=false;
        for(int c;(c=reader.read())!=-1;){
            any=true;if(c=='\n')return out.toString();
            if(c=='\r'){int next=reader.read();if(next!='\n')throw new IOException("HTSeq source table has a bare carriage return");return out.toString();}
            if(out.length()>=65536)throw new IOException("HTSeq source table line exceeds65536characters");out.append((char)c);
        }return any?out.toString():null;
    }
}
