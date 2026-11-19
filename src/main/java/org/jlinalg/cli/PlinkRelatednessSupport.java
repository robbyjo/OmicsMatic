/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Closed, UTF-8 capture helpers; never execute an input-provided command. */
final class PlinkRelatednessSupport {
    private PlinkRelatednessSupport() { }
    static void require(boolean condition,String message)throws IOException { if(!condition)throw new IOException(message); }
    static Map<String,Object> object(Object value,String name)throws IOException {
        require(value instanceof Map<?,?>,name+" must be an object");
        var result=new LinkedHashMap<String,Object>();
        for(var e:((Map<?,?>)value).entrySet()){require(e.getKey() instanceof String,"Object key must be text");result.put((String)e.getKey(),e.getValue());}
        return result;
    }
    static void keys(Map<String,Object> value,String... expected)throws IOException {
        require(value.keySet().equals(Set.of(expected)),"Require exactly these fields: "+Arrays.toString(expected)+"; found "+value.keySet());
    }
    static String text(Map<String,Object> value,String key)throws IOException {
        Object x=value.get(key);require(x instanceof String && !((String)x).isBlank(),key+" must be nonblank text");
        String s=(String)x;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(Character.isHighSurrogate(c)){require(i+1<s.length()&&Character.isLowSurrogate(s.charAt(i+1)),"Unpaired Unicode surrogate in "+key);i++;}
            else require(!Character.isLowSurrogate(c)&&c!=0,"Invalid Unicode/NUL in "+key);
        }
        return s;
    }
    static boolean bool(Map<String,Object> value,String key)throws IOException {require(value.get(key) instanceof Boolean,key+" must be boolean");return (Boolean)value.get(key);}
    static long integer(Map<String,Object> value,String key,long minimum,long maximum)throws IOException {
        require(value.get(key) instanceof Number,key+" must be an integer");
        try {long n=new BigDecimal(value.get(key).toString()).longValueExact();require(n>=minimum&&n<=maximum,key+" is outside "+minimum+".."+maximum);return n;}
        catch(NumberFormatException|ArithmeticException e){throw new IOException(key+" must be a finite exact integer",e);}
    }
    static double number(Map<String,Object> value,String key)throws IOException {
        require(value.get(key) instanceof Number,key+" must be a number");double n=((Number)value.get(key)).doubleValue();require(Double.isFinite(n),key+" must be finite");return n;
    }
    static Double nullableNumber(Map<String,Object> value,String key)throws IOException {return value.get(key)==null?null:number(value,key);}
    static Path resolve(Path base,String value){Path p=Path.of(value);return (p.isAbsolute()?p:base.resolve(p)).toAbsolutePath().normalize();}
    static String utf8(byte[] raw)throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    static Map<String,Object> read(Path file)throws IOException {return read(file,1_048_576);}
    static Map<String,Object> read(Path file,int maximum)throws IOException {
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular JSON file: "+file);
        byte[] raw;try(var in=Files.newInputStream(file)){raw=in.readNBytes(maximum+1);}require(raw.length<=maximum,"JSON byte capacity exceeded: "+file);
        String s=utf8(raw);int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){require(++depth<=32,"JSON nesting capacity exceeded");}else if(c=='}'||c==']')depth--;}
        return object(SimpleJson.parse(s),"JSON document");
    }
    static String json(Object value)throws IOException {var b=new StringBuilder();append(b,value,0);return b+"\n";}
    static void json(Path path,Object value)throws IOException {Files.writeString(path,json(value),StandardCharsets.UTF_8);}
    static boolean same(Object a,Object b)throws IOException {return json(a).equals(json(b));}
    private static void append(StringBuilder b,Object value,int depth)throws IOException {
        if(value==null){b.append("null");return;}
        if(value instanceof String s){quote(b,s);return;}
        if(value instanceof Boolean){b.append(value);return;}
        if(value instanceof Number n){require(!(n instanceof Double||n instanceof Float)||Double.isFinite(n.doubleValue()),"Nonfinite JSON number; retain an explicit disposition/text instead");b.append(n);return;}
        if(value instanceof Map<?,?> m){b.append('{');boolean first=true;var sorted=new TreeMap<String,Object>();for(var e:m.entrySet()){require(e.getKey() instanceof String,"JSON key must be text");sorted.put((String)e.getKey(),e.getValue());}for(var e:sorted.entrySet()){if(!first)b.append(',');b.append('\n').append("  ".repeat(depth+1));quote(b,e.getKey());b.append(": ");append(b,e.getValue(),depth+1);first=false;}if(!first)b.append('\n').append("  ".repeat(depth));b.append('}');return;}
        if(value instanceof Collection<?> xs){b.append('[');boolean first=true;for(Object x:xs){if(!first)b.append(", ");append(b,x,depth+1);first=false;}b.append(']');return;}
        throw new IOException("Unsupported JSON type: "+value.getClass());
    }
    private static void quote(StringBuilder b,String s){b.append('"');for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}}}b.append('"');}
    static final class Clock {
        private final long deadline;
        Clock(long seconds){deadline=Math.addExact(System.nanoTime(),Math.multiplyExact(seconds,1_000_000_000L));}
        void check()throws IOException {if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Owned relatedness work interrupted");if(System.nanoTime()-deadline>=0)throw new LimitExceeded("Declared relatedness wall_seconds exceeded; retain this attempt and retry NEW paths with suitable limits");}
    }
    static final class LimitExceeded extends IOException {
        private static final long serialVersionUID=1L;
        LimitExceeded(String message){super(message);}
    }
    static String sha(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static String hash(Path path,Clock clock)throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Nonregular/link input: "+path);
        try {var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){clock.check();d.update(buffer,0,n);}}return HexFormat.of().formatHex(d.digest());}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static long copy(Path source,Path target,long remaining,Clock clock)throws IOException {
        require(Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)&&Files.size(source)<=remaining,"Missing/linked input or input_bytes capacity exceeded: "+source);
        String before=hash(source,clock);long count=0;
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){clock.check();require(n<=remaining-count,"Input grew past input_bytes capacity");out.write(buffer,0,n);count+=n;}
        }
        require(before.equals(hash(source,clock))&&before.equals(hash(target,clock)),"Input changed while capturing: "+source);return count;
    }
    static Map<String,Long> footprint(Path root,long maximumFiles,long maximumBytes,Clock clock)throws IOException {
        var result=new TreeMap<String,Long>();long total=0,entries=0;
        try(var paths=Files.walk(root)){
            var iterator=paths.iterator();while(iterator.hasNext()){
                clock.check();Path p=iterator.next();require(++entries<=maximumFiles*4+32,"Capture entry capacity exceeded");
                var a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                require(!a.isSymbolicLink()&&!a.isOther(),"Linked/special capture entry: "+p);
                if(a.isRegularFile()){require(result.size()<maximumFiles,"Capture file capacity exceeded");total=Math.addExact(total,a.size());require(total<=maximumBytes,"Capture disk/output byte capacity exceeded");result.put(root.relativize(p).toString().replace('\\','/'),a.size());}
                else require(a.isDirectory(),"Unexpected capture entry: "+p);
            }
        }
        return result;
    }
    static void seal(Path root,PlinkRelatednessContracts.Limits l,Clock clock)throws IOException {
        var files=new TreeMap<String,Object>();for(var e:footprint(root,l.files(),l.diskBytes(),clock).entrySet())if(!e.getKey().equals("capture-files.json"))files.put(e.getKey(),Map.of("bytes",e.getValue(),"sha256",hash(root.resolve(e.getKey()),clock)));
        json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",files));
    }
    static void verifySeal(Path root,PlinkRelatednessContracts.Limits l,Clock clock)throws IOException {
        var seal=read(root.resolve("capture-files.json"),16_777_216);keys(seal,"schema_version","authentication","files");require(integer(seal,"schema_version",1,1)==1&&!bool(seal,"authentication"),"Capture must not claim authentication");
        var expected=object(seal.get("files"),"capture file pins");var actual=footprint(root,l.files(),l.diskBytes(),clock);actual.remove("capture-files.json");require(expected.keySet().equals(actual.keySet()),"Capture file population changed");
        for(var e:actual.entrySet()){var pin=object(expected.get(e.getKey()),"file pin");keys(pin,"bytes","sha256");require(integer(pin,"bytes",0,Long.MAX_VALUE)==e.getValue()&&text(pin,"sha256").equals(hash(root.resolve(e.getKey()),clock)),"Capture hash/size changed: "+e.getKey());}
    }
    static final class Lines implements AutoCloseable {
        private final InputStream input;private final int maximum;private final Clock clock;
        Lines(Path path,int maximum,Clock clock)throws IOException {input=new BufferedInputStream(Files.newInputStream(path));this.maximum=maximum;this.clock=clock;}
        String next()throws IOException {clock.check();var b=new ByteArrayOutputStream();int c;while((c=input.read())!=-1&&c!='\n'){require(b.size()<maximum,"Line byte capacity exceeded");b.write(c);}if(c==-1&&b.size()==0)return null;byte[] raw=b.toByteArray();if(raw.length>0&&raw[raw.length-1]=='\r')raw=Arrays.copyOf(raw,raw.length-1);return utf8(raw);}
        @Override public void close()throws IOException {input.close();}
    }
    static final class Table implements AutoCloseable {
        private final BufferedWriter writer;private final Lines reader;private final int maximum;private final Clock clock;
        Table(Path path,boolean verify,int lineBytes,Clock clock)throws IOException {this.maximum=lineBytes;this.clock=clock;writer=verify?null:Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);reader=verify?new Lines(path,lineBytes,clock):null;}
        void line(String value)throws IOException {clock.check();require(value.getBytes(StandardCharsets.UTF_8).length<=maximum,"Canonical output line_bytes exceeded");if(writer!=null){writer.write(value);writer.write('\n');}else require(value.equals(reader.next()),"Published relatedness row/header changed");}
        void end()throws IOException {if(reader!=null)require(reader.next()==null,"Extra published relatedness rows");}
        @Override public void close()throws IOException {if(writer!=null)writer.close();if(reader!=null)reader.close();}
    }
}
