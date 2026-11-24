/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Bounded source text tables and synchronized original data-frame columns. */
final class GenomicsemPrepTables {
    private GenomicsemPrepTables() { }
    static final class Text implements AutoCloseable {
        final BufferedReader reader;final Map<String,Object>request;final List<String>header;final boolean tabSeparated;long rows,characters;
        Text(Path file,Map<String,Object>q)throws IOException {this(file,q,false);}
        Text(Path file,Map<String,Object>q,boolean tabs)throws IOException {
            request=q;tabSeparated=tabs;InputStream in=Files.newInputStream(file);try{if(file.toString().endsWith(".gz"))in=new GZIPInputStream(in);reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));String line=nonempty();require(line!=null,"Empty source table");header=parse(line);require(!header.isEmpty()&&new HashSet<>(header).size()==header.size(),"Empty/duplicated raw header names");}catch(IOException|RuntimeException e){in.close();throw e;}
        }
        private String read()throws IOException {String line=GenomicsemData.line(reader,GenomicsemContracts.cap(request,"maximum_line_characters"));if(line!=null){characters+=line.getBytes(StandardCharsets.UTF_8).length+1L;require(characters<=GenomicsemContracts.cap(request,"maximum_uncompressed_bytes"),"Uncompressed source table capacity exceeded");}return line;}
        private String nonempty()throws IOException {for(String line;(line=read())!=null;)if(!line.isBlank()&&(tabSeparated||!line.stripLeading().startsWith("#")))return line;return null;}
        private List<String> parse(String line)throws IOException {return tabSeparated?Arrays.asList(line.split("\t",-1)):fields(line);}
        List<String> next()throws IOException {String line=nonempty();if(line==null)return null;require(++rows<=GenomicsemContracts.cap(request,"maximum_rows"),"Source row capacity exceeded");List<String> values=parse(line);require(values.size()==header.size(),"Raw table width differs; supply an explicit rectangular whitespace/tab-delimited table");return values;}
        int column(String name)throws IOException {int i=header.indexOf(name);require(i>=0,"Missing source column: "+name);return i;}
        public void close()throws IOException{reader.close();}
    }
    static List<String> fields(String line)throws IOException {
        // Original read.table uses whitespace separation, double quotes and no
        // backslash escapes. Multiline quoted records remain outside this profile.
        List<String> values=new ArrayList<>();StringBuilder word=new StringBuilder();boolean quoted=false,started=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);if(c=='"'){quoted=!quoted;started=true;continue;}
            if(!quoted&&c=='#')break;
            if(!quoted&&(c==' '||c=='\t')){if(started){values.add(word.toString());word.setLength(0);started=false;}continue;}word.append(c);started=true;
        }require(!quoted,"Unterminated/multiline quoted source field");if(started)values.add(word.toString());return values;
    }
    static boolean missing(String value){return Set.of("","NA",".").contains(value);}
    static final class Frame implements AutoCloseable {
        final List<String>names;final List<GenomicsemPrepObject.Cursor>columns=new ArrayList<>();final long length;long row;
        Frame(GenomicsemPrepObject.Tree tree,GenomicsemPrepObject.Node node)throws IOException {
            require(node.frame(tree),"Expected an original base R data frame");names=node.names(tree);require(names.size()==node.children.size()&&new HashSet<>(names).size()==names.size(),"Invalid original data-frame column names");length=node.children.isEmpty()?0:node.children.get(0).length;
            try{for(var child:node.children){require(child.kind.equals("atomic")&&child.length==length,"Nonrectangular/nonprimitive original data frame");columns.add(tree.cursor(child));}}catch(IOException e){close();throw e;}
        }
        Map<String,GenomicsemPrepObject.Cell> next()throws IOException {
            if(columns.isEmpty()){require(length==0,"Invalid zero-column frame");return null;}
            Map<String,GenomicsemPrepObject.Cell> values=new LinkedHashMap<>();for(int i=0;i<columns.size();i++){var cell=columns.get(i).next();if(cell==null){require(row==length,"Incomplete original data frame");for(int j=i+1;j<columns.size();j++)require(columns.get(j).next()==null,"Unequal original column lengths");return null;}values.put(names.get(i),cell);}require(row++<length,"Extra original data-frame row");return values;
        }
        public void close()throws IOException {IOException first=null;for(var column:columns)try{column.close();}catch(IOException e){if(first==null)first=e;else first.addSuppressed(e);}if(first!=null)throw first;}
    }
    interface Lines extends AutoCloseable {void line(String text)throws IOException;void close()throws IOException;}
    static Lines lines(Path file,boolean inspect)throws IOException {
        if(!inspect){var out=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);return new Lines(){public void line(String s)throws IOException{out.write(s);out.write('\n');}public void close()throws IOException{out.close();}};}
        var in=new BufferedInputStream(Files.newInputStream(file));return new Lines(){public void line(String s)throws IOException{for(byte b:(s+'\n').getBytes(StandardCharsets.UTF_8))require(in.read()==(b&255),"Changed derived human-readable result table");}public void close()throws IOException{try{require(in.read()==-1,"Extra derived result bytes");}finally{in.close();}}};
    }
    static String display(GenomicsemPrepObject.Cell c){return c.nonfinite()?switch(c.value()){case "Q"->"NaN";case "P"->"Inf";case "M"->"-Inf";default->throw new IllegalArgumentException("Unknown nonfinite token");}:c.value();}
    static String quote(String s){return s.codePoints().anyMatch(c->c=='\t'||c=='\n'||c=='\r'||c=='"')?'"'+s.replace("\"","\"\"")+'"':s;}
    static GenomicsemPrepObject.Cell cell(Map<String,GenomicsemPrepObject.Cell>row,String key)throws IOException {var c=row.get(key);require(c!=null,"Missing original result field: "+key);return c;}
    static double number(Map<String,GenomicsemPrepObject.Cell>row,String key)throws IOException{return cell(row,key).number();}
    static String label(Map<String,GenomicsemPrepObject.Cell>row,String key)throws IOException{return cell(row,key).string();}
}
