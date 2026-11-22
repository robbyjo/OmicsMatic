/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.GenomicRangesWorkflowContracts.require;

/** Literal streaming intake, generated source parameters and complete file inventory. */
final class GenomicRangesWorkflowFiles {
    private GenomicRangesWorkflowFiles(){ }
    static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    @FunctionalInterface interface Row {void accept(int index,String[] values)throws IOException;}
    static Map<String,Object> map(Object value)throws IOException{return object(value,"object");}
    static long cap(Map<String,Object> request,String key)throws IOException{return ((Number)map(request.get("limits")).get(key)).longValue();}
    static byte[] bytes(Path path,long maximum)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new GenomicRangesWorkflowContracts.Unavailable("Supply exact permitted regular file: "+path);
        require(Files.size(path)<=maximum&&Files.size(path)<=Integer.MAX_VALUE-8,"Member exceeds declared byte capacity: "+path);byte[] raw=Files.readAllBytes(path);require(raw.length<=maximum,"File changed/exceeded byte capacity while reading: "+path);return raw;
    }
    static String utf8(byte[] raw)throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    static Map<String,Object> read(Path path,long maximum)throws IOException{return map(SimpleJson.parse(utf8(bytes(path,maximum))));}
    static void json(Path path,Object value)throws IOException {Files.write(path,RsemJson.bytes(value));}
    static String hash(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}}
    static Map<String,Object> pin(byte[] raw){return Map.of("bytes",raw.length,"sha256",hash(raw));}
    static Map<String,Object> pin(Path path)throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require a regular captured member: "+path);
        try(var input=Files.newInputStream(path)){var digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[65536];long total=0;int n;while((n=input.read(block))!=-1){digest.update(block,0,n);total+=n;}require(total==Files.size(path),"File length changed during fingerprint: "+path);return Map.of("bytes",total,"sha256",HexFormat.of().formatHex(digest.digest()));}
        catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    static long capture(Path source,Path target,long maximum)throws IOException {
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new GenomicRangesWorkflowContracts.Unavailable("Supply exact permitted input file: "+source);
        require(Files.size(source)<=maximum,"Input exceeds remaining declared byte capacity: "+source);Map<String,Object> before=pin(source);Files.copy(source,target);require(before.equals(pin(source))&&before.equals(pin(target)),"Input changed while capturing: "+source);return ((Number)before.get("bytes")).longValue();
    }
    static List<String> rows(Path path,long maximum,int maximumColumns,int maximumRows,Row action)throws IOException {
        return rows(path,maximum,maximumColumns,maximumRows,action,false);
    }
    static List<String> rows(Path path,long maximum,int maximumColumns,int maximumRows,Row action,boolean allowEmpty)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new GenomicRangesWorkflowContracts.Unavailable("Supply exact permitted captured table: "+path);
        require(Files.size(path)<=maximum,"Table exceeds declared member capacity: "+path);
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line=reader.readLine();require(line!=null,"Missing literal table header: "+path);String[] header=line.split("\t",-1);require(header.length>0&&header.length<=maximumColumns,"Header column capacity exceeded: "+path);
            var names=new ArrayList<String>();var seen=new HashSet<String>();for(String name:header){GenomicRangesWorkflowContracts.identifier(name);require(seen.add(name),"Duplicate literal header: "+name);names.add(name);}
            int row=0;while((line=reader.readLine())!=null){require(++row<=maximumRows,"Row capacity exceeded: "+path);String[] values=line.split("\t",-1);require(values.length==header.length,"Literal table width differs at row"+row+": "+path);action.accept(row,values);}require(allowEmpty||row>0,"Empty input table: "+path);return List.copyOf(names);
        }
    }
    static double finite(String value)throws IOException {require(DECIMAL.matcher(value).matches(),"Require finite decimal numeric cells;no trimming,coercion or repair: "+value);double result;try{result=Double.parseDouble(value);}catch(NumberFormatException error){throw new IOException("Invalid decimal numeric cell",error);}require(Double.isFinite(result),"Nonfinite numeric input;no repair performed");return result;}
    static String id(String value)throws IOException{return GenomicRangesWorkflowContracts.identifier(value);}
}
