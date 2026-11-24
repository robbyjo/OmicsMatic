/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Lossless UTF-8/ASCII-whitespace tables with bounded decompression and lines. */
final class PrsiceTables {
    private PrsiceTables() { }
    static final class Rows implements AutoCloseable {
        private final BufferedReader reader;
        private final long maximumRows,maximumLine;
        private long lineNumber;
        Rows(Path file,Map<String,Object> request,boolean product)throws IOException {
            require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular captured table: "+file);
            maximumRows=PrsiceContracts.cap(request,"maximum_rows");maximumLine=PrsiceContracts.cap(request,"maximum_line_bytes");
            long byteLimit=PrsiceContracts.cap(request,product?"maximum_output_bytes":"maximum_input_bytes");
            InputStream raw=Files.newInputStream(file);InputStream opened=null;
            try {
                var probe=new PushbackInputStream(raw,2);int first=probe.read(),second=probe.read();if(second>=0)probe.unread(second);if(first>=0)probe.unread(first);
                opened=first==0x1f&&second==0x8b?new GZIPInputStream(probe,65536):probe;
                var limited=new FilterInputStream(opened){long count;
                    private void used(long n)throws IOException{if(n>0){count=Math.addExact(count,n);require(count<=byteLimit,"Decompressed table exceeds declared byte capacity: "+file);}}
                    @Override public int read()throws IOException{int n=in.read();if(n>=0)used(1);return n;}
                    @Override public int read(byte[] data,int offset,int length)throws IOException{int n=in.read(data,offset,length);used(n);return n;}
                };
                reader=new BufferedReader(new InputStreamReader(limited,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),65536);
            } catch(IOException|RuntimeException error){if(opened!=null)opened.close();else raw.close();throw error;}
        }
        String[] next()throws IOException {
            for(;;){
                var line=new StringBuilder();boolean read=false;
                for(int c;(c=reader.read())>=0;){read=true;if(c=='\n')break;require(line.length()<maximumLine,"Table line exceeds maximum_line_bytes");line.append((char)c);}
                if(!read)return null;
                require(++lineNumber<=maximumRows,"Table exceeds maximum_rows");
                String text=line.toString();require(text.getBytes(StandardCharsets.UTF_8).length<=maximumLine,"UTF-8 line exceeds maximum_line_bytes");
                int start=0,end=text.length();while(start<end&&space(text.charAt(start)))start++;while(end>start&&space(text.charAt(end-1)))end--;
                if(start==end)continue;
                require(text.indexOf('\0')<0,"NUL byte in captured table at physical line "+lineNumber);
                return text.substring(start,end).split("[ \\t\\x0B\\f\\r]+");
            }
        }
        long lineNumber(){return lineNumber;}
        @Override public void close()throws IOException {reader.close();}
    }
    private static boolean space(char c){return c==' '||c>='\t'&&c<='\r';}
    static Map<String,Integer> header(String[] row)throws IOException {
        require(row!=null&&row.length>0,"Missing table header");var result=new LinkedHashMap<String,Integer>();
        for(int i=0;i<row.length;i++)require(result.putIfAbsent(row[i],i)==null,"Duplicate literal header field: "+row[i]);return result;
    }
    static int column(Map<String,Integer> header,Object name)throws IOException {
        String field=text(name,"column");Integer index=header.get(field);require(index!=null,"Missing exact header field "+field+"; no case/BOM/name repair is performed");return index;
    }
    static String cell(String[] row,int column,long line)throws IOException {require(column>=0&&column<row.length,"Missing field at physical line "+line);return row[column];}
    static double finite(String value,String label)throws IOException {
        try{require(value.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?"),"Require original decimal numeric syntax for "+label);double n=Double.parseDouble(value);require(Double.isFinite(n),"Nonfinite "+label);if(n==0)require(new java.math.BigDecimal(value).signum()==0,"Nonzero "+label+" underflows source double; no implicit flooring");else require(Math.abs(n)>=Double.MIN_NORMAL,"Subnormal "+label+" is rejected by the original PRSice double parser");return n;}
        catch(NumberFormatException e){throw new IOException("Invalid numeric "+label+": "+value,e);}
    }
    static long positiveInteger(String value,String label)throws IOException {
        require(value.matches("[0-9]+"),"Require literal nonnegative integer "+label+": "+value);
        try{long n=Long.parseLong(value);require(n>0,"Require positive "+label);return n;}catch(NumberFormatException e){throw new IOException("Integer outside capacity: "+label,e);}
    }
    static String asciiUpper(String value) {var out=new StringBuilder(value.length());for(int i=0;i<value.length();i++){char c=value.charAt(i);out.append(c>='a'&&c<='z'?(char)(c-'a'+'A'):c);}return out.toString();}
    static boolean missing(String value){String v=asciiUpper(value);return v.equals("NA")||v.equals("NAN");}
    static String tsv(String value)throws IOException {require(value.codePoints().noneMatch(c->c<32||c==127),"Control character cannot be represented in audit TSV");return value;}
}
