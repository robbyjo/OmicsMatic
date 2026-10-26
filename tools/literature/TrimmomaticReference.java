/* Direct calls into unmodified original JARs; no JLinAlg algorithm is used for expected output. */
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public final class TrimmomaticReference {
    private TrimmomaticReference() { }
    public static void main(String[] args)throws Exception{
        Path jar=Path.of(args[0]),input=Path.of(args[1]),output=Path.of(args[2]);Map<String,String> classes=Map.ofEntries(Map.entry("LEADING","LeadingTrimmer"),Map.entry("TRAILING","TrailingTrimmer"),Map.entry("SLIDINGWINDOW","SlidingWindowTrimmer"),Map.entry("HEADCROP","HeadCropTrimmer"),Map.entry("TAILCROP","TailCropTrimmer"),Map.entry("CROP","CropTrimmer"),Map.entry("MINLEN","MinLenTrimmer"),Map.entry("MAXLEN","MaxLenTrimmer"),Map.entry("AVGQUAL","AvgQualTrimmer"),Map.entry("BASECOUNT","BaseCountTrimmer"),Map.entry("TOPHRED33","ToPhred33Trimmer"),Map.entry("TOPHRED64","ToPhred64Trimmer"),Map.entry("MAXINFO","MaximumInformationTrimmer"));
        try(var loader=new URLClassLoader(new URL[]{jar.toUri().toURL()},ClassLoader.getPlatformClassLoader());var in=Files.newBufferedReader(input);var out=Files.newBufferedWriter(output)){
            Class<?> record=loader.loadClass("org.usadellab.trimmomatic.fastq.FastqRecord");Constructor<?> constructor=record.getConstructor(String.class,String.class,String.class,String.class,int.class);var cached=new HashMap<String,Object>();String line=in.readLine();if(!"case_id\tphred\tmate\tsteps\tsequence\tquality".equals(line))throw new IOException("Unexpected case header");out.write("case_id\tretained\tsequence\tquality\tphred\tfirst\tend\n");
            while((line=in.readLine())!=null){String[] f=line.split("\t",-1);int mate=Integer.parseInt(f[2]),index=mate==2?1:0;Object array=Array.newInstance(record,mate==0?1:2);Array.set(array,index,constructor.newInstance(f[0],f[4],"",f[5],Integer.parseInt(f[1])));for(String token:f[3].split(";")){Object trimmer=cached.get(token);if(trimmer==null){String[] parts=token.split(":",2);Class<?> type=loader.loadClass("org.usadellab.trimmomatic.trim."+classes.get(parts[0]));trimmer=type.getConstructor(String.class).newInstance(parts.length>1?parts[1]:"");cached.put(token,trimmer);}array=trimmer.getClass().getMethod("processRecords",array.getClass()).invoke(trimmer,array);}
                Object result=Array.get(array,index);if(result==null)out.write(f[0]+"\tfalse\t\t\t0\t0\t0\n");else{String seq=(String)record.getMethod("getSequence").invoke(result),quality=(String)record.getMethod("getQuality").invoke(result);int offset=(Integer)record.getMethod("getPhredOffset").invoke(result),first=(Integer)record.getMethod("getHeadPos").invoke(result);out.write(f[0]+"\ttrue\t"+seq+"\t"+quality+"\t"+offset+"\t"+first+"\t"+(first+seq.length())+"\n");}
            }
        }
    }
}
