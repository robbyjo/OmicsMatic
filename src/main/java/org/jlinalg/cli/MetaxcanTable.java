/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Bounded literal UTF-8 table reader. No header mangling, value repair or truncation. */
final class MetaxcanTable implements AutoCloseable {
    static final class Budget {
        final long maximum;final Runnable check;long decoded;
        Budget(long maximum,Runnable check){if(maximum<1)throw new IllegalArgumentException("Invalid decoded-byte bound");this.maximum=maximum;this.check=Objects.requireNonNull(check);}
        void add(long n)throws IOException{check.run();if(n>maximum-decoded)throw new IOException("Complete MetaXcan decoded-byte capacity exceeded; no truncated table admitted");decoded+=n;}
    }
    private final PushbackReader input;
    private final String separator;
    private final Budget budget;
    private final long maximumRows;
    private final List<String> header;
    private long rows;
    private int recordChars;
    private boolean complete,failed;
    MetaxcanTable(Path file,boolean gzip,String separator,long maximumRows,Budget budget,boolean header)throws IOException {
        if(!Set.of("comma","tab","whitespace").contains(separator)||maximumRows<1)throw new IllegalArgumentException("Invalid table profile");
        this.separator=separator;this.maximumRows=maximumRows;this.budget=budget;
        InputStream raw=new FilterInputStream(Files.newInputStream(file)){
            @Override public int read()throws IOException{budget.check.run();return in.read();}
            @Override public int read(byte[] b,int off,int len)throws IOException{budget.check.run();return in.read(b,off,len);}
        };InputStream decoded=null;
        try {
            decoded=gzip?GzipCompressorInputStream.builder().setInputStream(raw).setDecompressConcatenated(true).get():raw;
            InputStream limited=new FilterInputStream(decoded){
                @Override public int read()throws IOException{int n=super.read();if(n>=0)budget.add(1);return n;}
                @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0)budget.add(n);return n;}
            };
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            input=new PushbackReader(new BufferedReader(new InputStreamReader(limited,decoder)),2);
            int first=input.read();if(first!=-1&&first!=0xfeff)input.unread(first);
            List<String> names=header?record():List.of();if(names==null)throw new IOException("Missing MetaXcan table header");
            if(header&&(new HashSet<>(names).size()!=names.size()||names.stream().anyMatch(n->n.isBlank()||n.length()>1024||n.chars().anyMatch(c->c<32||c==127))))throw new IOException("Require distinct literal bounded table headers without controls; duplicate headers are not renamed");
            this.header=List.copyOf(names);
        }catch(IOException|RuntimeException|Error e){try{if(decoded!=null)decoded.close();else raw.close();}catch(IOException suppressed){e.addSuppressed(suppressed);}throw e;}
    }
    List<String> header(){return header;}
    long rows(){return rows;}
    boolean complete(){return complete;}
    int column(String name)throws IOException{int index=header.indexOf(name);if(index<0)throw new IOException("Declared MetaXcan column absent: "+name);return index;}
    List<String> next()throws IOException {
        if(failed)throw new IOException("A failed table scan cannot be resumed");if(complete)return null;
        try {
            budget.check.run();var row=record();if(row==null){complete=true;return null;}
            if(rows>=maximumRows)throw new IOException("Complete MetaXcan table row capacity exceeded");
            if(!header.isEmpty()&&row.size()!=header.size())throw new IOException("MetaXcan table width differs from the complete literal header at data row "+(rows+1));rows++;return row;
        }catch(IOException|RuntimeException|Error e){failed=true;throw e;}
    }
    private int take()throws IOException{int c=input.read();if(c>=0){if(++recordChars>1048576)throw new IOException("MetaXcan logical table record exceeds1Mi characters");if(c==0)throw new IOException("NUL in MetaXcan table");}return c;}
    private int peek()throws IOException{int c=input.read();if(c>=0)input.unread(c);return c;}
    private boolean delimiter(int c){return separator.equals("comma")?c==',':separator.equals("tab")?c=='\t':c==' '||c=='\t';}
    private void end(int c)throws IOException{if(c=='\r'&&peek()=='\n')take();}
    private List<String> record()throws IOException {
        for(;;) {
            budget.check.run();recordChars=0;int c=peek();if(c==-1)return null;
            if(c=='\n'||c=='\r'){end(take());continue;}
            boolean whitespace=separator.equals("whitespace");
            if(whitespace){while(delimiter(peek()))take();c=peek();if(c=='\n'||c=='\r'){end(take());continue;}if(c==-1)return null;}
            var row=new ArrayList<String>();
            for(;;) {
                if(row.size()>=1024)throw new IOException("MetaXcan table exceeds1024 columns");var value=new StringBuilder();
                if(peek()=='"') {
                    take();for(;;){c=take();if(c==-1)throw new IOException("Unterminated quoted MetaXcan cell");if(c=='"'){if(peek()=='"'){take();value.append('"');}else break;}else value.append((char)c);}
                    c=peek();if(c!=-1&&c!='\n'&&c!='\r'&&!delimiter(c))throw new IOException("Unexpected text after closing MetaXcan quote");
                }else for(;;){c=peek();if(c==-1||c=='\n'||c=='\r'||delimiter(c))break;if(c=='"')throw new IOException("Bare quote in an unquoted MetaXcan cell");value.append((char)take());}
                row.add(value.toString());c=peek();
                if(c==-1)return List.copyOf(row);
                if(c=='\n'||c=='\r'){end(take());return List.copyOf(row);}
                take();if(whitespace){while(delimiter(peek()))take();c=peek();if(c==-1)return List.copyOf(row);if(c=='\n'||c=='\r'){end(take());return List.copyOf(row);}}
            }
        }
    }
    @Override public void close()throws IOException{input.close();}
}
