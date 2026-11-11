/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Physical count/input profile with exact original IDs and declared source-file axes. */
final class LeafCutterWorkflowInputs {
    private LeafCutterWorkflowInputs() { }
    record Matrix(List<String> samples,List<String> introns,long[][] numerators,long[][] denominators) { }
    static List<String> lines(Path path,long maximum)throws IOException {
        var result=new ArrayList<String>();long used=0;try(var raw=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS);var buffer=new BufferedInputStream(raw)){
            buffer.mark(2);int first=buffer.read(),second=buffer.read();buffer.reset();InputStream decoded=first==31&&second==139?new GZIPInputStream(buffer):buffer;
            InputStream bounded=new FilterInputStream(decoded){long count;private void spend(int n)throws IOException{if(n>0){count=Math.addExact(count,n);require(count<=maximum,"Complete decompressed byte capacity exceeded");}}@Override public int read()throws IOException{int value=in.read();spend(value<0?0:1);return value;}@Override public int read(byte[] b,int off,int length)throws IOException{int n=in.read(b,off,length);spend(n);return n;}};
            try(var reader=new BufferedReader(new InputStreamReader(bounded,StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)))){for(String line;(line=reader.readLine())!=null;){used=Math.addExact(used,(long)line.length()+1);require(used<=maximum&&line.length()<=1048576&&result.size()<10000000,"Complete decompressed text/row capacity exceeded; no partial input");require(!line.isBlank(),"Blank source data lines are outside this valid-input profile");result.add(line);}}
        }return List.copyOf(result);
    }
    static Matrix matrix(Path path,boolean ratios,long maximum)throws IOException {
        var lines=lines(path,maximum);require(!lines.isEmpty(),"Missing complete count header");String[] header=fields(lines.get(0));int offset=ratios?1:0;require(!ratios||header[0].equals("chrom"),"Ratio header must start with original chrom field");require(header.length>offset,"No count sample columns");var samples=new ArrayList<String>();var seen=new HashSet<String>();for(int j=offset;j<header.length;j++){String value=LeafCutterWorkflowRequest.token(header[j]);require(!value.equals("chrom")&&seen.add(value),"Duplicate/ambiguous sample header; no dictionary replacement");samples.add(value);}require(samples.size()<=100000&&(long)(lines.size()-1)*samples.size()<=10000000,"Complete count matrix capacity exceeded");var introns=new ArrayList<String>();seen.clear();long[][] numerator=new long[lines.size()-1][samples.size()],denominator=ratios?new long[lines.size()-1][samples.size()]:null;
        for(int i=1;i<lines.size();i++){String[] row=fields(lines.get(i));require(row.length==samples.size()+1,"Ragged count family");String id=LeafCutterWorkflowRequest.token(row[0]);new LeafCutterPhenotypes.Row(id,List.of());require(seen.add(id),"Duplicate original intron ID");introns.add(id);for(int j=0;j<samples.size();j++){String[] pair=ratios?row[j+1].split("/",-1):new String[]{row[j+1]};require(pair.length==(ratios?2:1),"Require exact numerator/denominator tokens");long value=integerText(pair[0]);numerator[i-1][j]=value;if(ratios){long total=integerText(pair[1]);require(value<=total,"Numerator exceeds original cluster total");denominator[i-1][j]=total;}}}
        return new Matrix(List.copyOf(samples),List.copyOf(introns),numerator,denominator);
    }
    static List<LeafCutterClusters.Sample> junctions(Map<String,Object> c,Map<String,Path> captured,long maximum)throws IOException {
        var result=new ArrayList<LeafCutterClusters.Sample>();String id=text(c.get("id"));int index=0;long rows=0;for(Object raw:list(c.get("samples"))){var sample=object(raw);var inputs=new ArrayList<LeafCutterClusters.Input>();for(String line:lines(captured.get(id+"-junction-"+index++),maximum)){require(++rows<=10000000,"Complete BED12 row capacity exceeded");inputs.add(LeafCutterClusters.parseRegtools(line));}result.add(new LeafCutterClusters.Sample(text(sample.get("sample_id")),inputs));}return List.copyOf(result);
    }
    static List<LeafCutterPhenotypes.Row> phenotypeRows(Matrix matrix){var result=new ArrayList<LeafCutterPhenotypes.Row>();for(int i=0;i<matrix.introns().size();i++){var counts=new ArrayList<LeafCutterPhenotypes.Counts>();for(int j=0;j<matrix.samples().size();j++)counts.add(new LeafCutterPhenotypes.Counts(matrix.numerators()[i][j],matrix.denominators()[i][j]));result.add(new LeafCutterPhenotypes.Row(matrix.introns().get(i),counts));}return List.copyOf(result);}
    static void modelEligibility(Matrix matrix)throws IOException {var totals=new HashMap<String,long[]>();for(int i=0;i<matrix.introns().size();i++){String[] f=matrix.introns().get(i).split(":",-1);long[] sum=totals.computeIfAbsent(f[0]+":"+f[3],unused->new long[matrix.samples().size()]);for(int j=0;j<sum.length;j++){long value=matrix.numerators()[i][j];require(value<=9007199254740992L&&value<=9007199254740992L-sum[j],"Original R model count/cluster total exceeds exact-double integer eligibility. Retain original int64 counts; do not round or admit a partial analysis");sum[j]+=value;}}}
    static List<List<String>> groups(Path path,Matrix matrix,long maximum)throws IOException {
        var rows=new ArrayList<List<String>>();var seen=new HashSet<String>();var available=new HashSet<>(matrix.samples());int width=-1;for(String line:lines(path,maximum)){var row=new ArrayList<String>();for(String value:fields(line))row.add(LeafCutterWorkflowRequest.token(value));require(row.size()>=2&&(width<0||width==row.size()),"Complete groups file requires sample, group and a fixed covariate family");width=row.size();require(seen.add(row.get(0))&&available.contains(row.get(0)),"Duplicate/unknown group-file sample; no replacement or matching repair");for(String value:row)require(!Set.of("NA","NaN","Inf","-Inf").contains(value),"Original metadata missing/nonfinite tokens are outside this finite declared profile");rows.add(List.copyOf(row));}require(rows.size()>=2,"Complete group family needs at least two samples");boolean numeric=true,logical=true;for(var row:rows){numeric&=numericToken(row.get(0));logical&=Set.of("TRUE","FALSE","T","F").contains(row.get(0));}require(!numeric&&!logical,"Original read.table would coerce numeric/logical group-file sample IDs to positional indexing. Retain original IDs and supply a source-compatible string-ID export with an explicit external mapping; no automatic rename is performed");return List.copyOf(rows);
    }
    static boolean numericToken(String text){return text.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?")||text.matches("[+-]?0[xX][0-9a-fA-F]+(?:[.][0-9a-fA-F]*)?(?:[pP][+-]?[0-9]+)?");}
    static String[] fields(String line){return line.strip().split("\\s+");}
    static long integerText(String text)throws IOException {require(text.matches("[0-9]+"),"Physical decimal integer counts required; no rounding or estimate substitution");try{return Long.parseLong(text);}catch(NumberFormatException failure){throw new IOException("Original count exceeds int64 eligibility",failure);}}
}
