/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.plot.WesAnderson037;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Standalone versioned palette generation and complete saved-result replay. */
public final class WesAnderson037PaletteCli {
    private WesAnderson037PaletteCli() { }
    private static final String RESOURCE="/org/jlinalg/plot/wesanderson037/";
    private static final String ACTION="Required action: choose an explicit palette and a NEW output directory. "
        +"Native colors require Java 17 or later and no R installation. Qualification covers Java 21/25. "
        +"SKIP3 skips work when required files or permissions are unavailable; supply permitted prerequisites or retain the skip. "
        +"FAIL2 admits no result; correct the named request, provenance or complete-output cause and retry NEW paths. "
        +"VERIFY recomputes the complete saved Java result; it does not independently refit R, authenticate a bundle, "
        +"port R graphical devices, recover author inputs or infer paper palette/category assignments.";
    private static final Set<String> RESULT_FILES=Set.of("request.tsv","colors.tsv","attributes.tsv","source-profile.json",
        "implementation-SHA256SUMS","runtime.txt","analysis-status.json","WARNINGS.md");

    public static void main(String[] args) { System.exit(run(args)); }

    public static int run(String[] args) {
        Path out=null;boolean owned=false;
        try {
            if(args.length==0||args[0].equals("--help")||args[0].equals("help")) {
                System.out.println("palettes --out NEW_DIR\ngenerate --name NAME [--n NUMBER] [--type discrete|continuous] "
                    +"[--maximum-colors N] --out NEW_DIR\nverify --bundle SAVED_DIR --out NEW_DIR\n"+ACTION);
                return 0;
            }
            String operation=args[0];
            Set<String> allowed=switch(operation) {
                case "palettes"->Set.of("--out");
                case "generate"->Set.of("--out","--name","--n","--type","--maximum-colors");
                case "verify"->Set.of("--out","--bundle");
                default->throw new IllegalArgumentException("Unknown command: "+operation);
            };
            Map<String,String> options=new LinkedHashMap<>();
            for(int i=1;i<args.length;i+=2) {
                if(!allowed.contains(args[i])||i+1==args.length||options.putIfAbsent(args[i],args[i+1])!=null)
                    throw new IllegalArgumentException("Unknown, repeated or incomplete option: "+args[i]);
            }
            out=Path.of(required(options,"--out")).toAbsolutePath().normalize();
            if(operation.equals("verify")) {
                Path bundle=Path.of(required(options,"--bundle")).toAbsolutePath().normalize();
                if(out.startsWith(bundle)||bundle.startsWith(out))throw new IllegalArgumentException("Verification output overlaps protected saved input");
            }
            if(Files.exists(out,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("NEW output required; existing path retained: "+out);
            Files.createDirectories(out.getParent());
            if(!out.getParent().toRealPath().equals(out.getParent()))throw new IllegalArgumentException("Canonical output path required");
            Files.createDirectory(out);owned=true;
            if(operation.equals("verify"))verify(Path.of(required(options,"--bundle")),out);
            else {
                LinkedHashMap<String,String> request=new LinkedHashMap<>();request.put("schema","wesanderson037/native-v1");request.put("operation",operation);
                if(operation.equals("generate")) {
                    request.put("name",required(options,"--name"));
                    if("MISSING".equals(options.get("--type")))throw new IllegalArgumentException("Invalid type: MISSING");
                    Double n=options.containsKey("--n")?Double.valueOf(options.get("--n")):null;
                    request.put("number",n==null?"MISSING":Double.toHexString(n));request.put("type",options.getOrDefault("--type","MISSING"));
                    int maximum=Integer.parseInt(options.getOrDefault("--maximum-colors","1000000"));
                    if(maximum<1||maximum>1_000_000)throw new IllegalArgumentException("CLI complete-color capacity must be 1..1000000; the Java API exposes its own explicit capacity");
                    request.put("maximum_colors",Integer.toString(maximum));
                }
                byte[] profile=resource("source-profile.json"),implementation=resource("implementation-SHA256SUMS");
                Generated generated=generate(request);
                write(out,"request.tsv",requestBytes(request));write(out,"colors.tsv",generated.colors());write(out,"attributes.tsv",generated.attributes());
                write(out,"source-profile.json",profile);write(out,"implementation-SHA256SUMS",implementation);
                write(out,"runtime.txt",runtime());write(out,"analysis-status.json",status(operation,generated));
                write(out,"WARNINGS.md","# Warnings and required actions\n\n"+ACTION+"\n");manifest(out);
                System.out.println("PASS_NATIVE_WESANDERSON037_COMPLETE_RESULT colors="+generated.count());
            }
            return 0;
        } catch(Exception e) {
            int code=e instanceof IOException?3:2;String state=code==3?"SKIP3_NATIVE_PALETTE_PREREQUISITE_UNAVAILABLE":"FAIL2_NATIVE_PALETTE_RESULT_NOT_ADMITTED";
            if(owned) {
                try {
                    String failure="{\"status\":"+quote(state)+",\"result_admitted\":false,\"cause\":"+quote(e.toString())+",\"required_action\":"+quote(ACTION)+"}\n";
                    Files.writeString(out.resolve("analysis-status.json"),failure,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
                    write(out,"FAILURE.json",failure);
                    write(out,"WARNINGS_FAILURE.md","# "+state+"\n\n**No result is admitted.**\n\n"+e+"\n\n"+ACTION+"\n");
                } catch(IOException suppressed) { System.err.println("Unable to save failure receipt: "+suppressed); }
            }
            System.err.println(state+"\n"+e+"\n"+ACTION);return code;
        }
    }

    private record Generated(String colors,String attributes,int count) { }

    private static Generated generate(Map<String,String> request) {
        StringBuilder colors=new StringBuilder();
        if(request.get("operation").equals("palettes")) {
            requireKeys(request,Set.of("schema","operation"));colors.append("name\tindex\tcolor\n");int count=0;
            for(var entry:WesAnderson037.palettes().entrySet()) {
                int index=0;for(String color:entry.getValue()){colors.append(entry.getKey()).append('\t').append(++index).append('\t').append(color).append('\n');count++;}
            }
            return new Generated(colors.toString(),"palette_count\t"+WesAnderson037.palettes().size()+"\n",count);
        }
        requireKeys(request,Set.of("schema","operation","name","number","type","maximum_colors"));
        if(!request.get("operation").equals("generate"))throw new IllegalArgumentException("Unsupported saved operation");
        Double number=request.get("number").equals("MISSING")?null:Double.valueOf(request.get("number"));
        String type=request.get("type").equals("MISSING")?null:request.get("type");int maximum=Integer.parseInt(request.get("maximum_colors"));
        if(maximum<1||maximum>1_000_000)throw new IllegalArgumentException("Saved complete-color capacity is outside the CLI contract");
        WesAnderson037.Palette palette=WesAnderson037.palette(request.get("name"),number,type,maximum);
        colors.append("index\tcolor\n");int index=0;for(String color:palette.colors())colors.append(++index).append('\t').append(color).append('\n');
        return new Generated(colors.toString(),"name\t"+palette.name()+"\nclass\t"+palette.sourceClass()+"\nlength\t"+palette.colors().size()+"\n",palette.colors().size());
    }

    private static void requireKeys(Map<String,String> request,Set<String> keys) {
        if(!request.keySet().equals(keys)||!"wesanderson037/native-v1".equals(request.get("schema")))throw new IllegalArgumentException("Saved request schema or fields differ");
    }

    private static void verify(Path requested,Path out) throws IOException {
        Path bundle=requested.toAbsolutePath().normalize();
        if(Files.isSymbolicLink(bundle)||!Files.isDirectory(bundle))throw new IllegalArgumentException("A complete saved result directory is required");
        if(out.startsWith(bundle)||bundle.startsWith(out))throw new IllegalArgumentException("Verification output overlaps protected saved input");
        Map<String,String> hashes=new LinkedHashMap<>();
        for(String line:Files.readAllLines(bundle.resolve("SHA256SUMS"),StandardCharsets.UTF_8)) {
            if(line.length()<67||!line.substring(0,64).matches("[0-9a-f]{64}")||!line.substring(64,66).equals("  "))throw new IllegalArgumentException("Malformed complete-file manifest");
            String name=line.substring(66);
            if(!RESULT_FILES.contains(name)||hashes.putIfAbsent(name,line.substring(0,64))!=null)throw new IllegalArgumentException("Unexpected or repeated manifest member");
        }
        if(!hashes.keySet().equals(RESULT_FILES))throw new IllegalArgumentException("Incomplete result manifest");
        List<Path> entries;
        try(var list=Files.list(bundle)){entries=list.toList();}
        if(entries.size()!=RESULT_FILES.size()+1)throw new IllegalArgumentException("Extra or missing saved result entries");
        for(Path path:entries) {
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||(!path.getFileName().toString().equals("SHA256SUMS")&&!hashes.containsKey(path.getFileName().toString())))throw new IllegalArgumentException("Unexpected file, directory or link in saved result");
        }
        for(var row:hashes.entrySet())if(!hash(bundle.resolve(row.getKey())).equals(row.getValue()))throw new IllegalArgumentException("Saved complete-file bytes differ: "+row.getKey());
        for(String name:List.of("source-profile.json","implementation-SHA256SUMS"))if(!Arrays.equals(Files.readAllBytes(bundle.resolve(name)),resource(name)))throw new IllegalArgumentException("Saved implementation/source provenance differs: "+name);
        LinkedHashMap<String,String> request=new LinkedHashMap<>();
        for(String line:Files.readAllLines(bundle.resolve("request.tsv"),StandardCharsets.UTF_8)) {
            int at=line.indexOf('\t');
            if(at<1||request.putIfAbsent(line.substring(0,at),line.substring(at+1))!=null)throw new IllegalArgumentException("Invalid or repeated saved request field");
        }
        Generated generated=generate(request);
        for(var expected:Map.of("request.tsv",requestBytes(request),"colors.tsv",generated.colors(),"attributes.tsv",generated.attributes(),"analysis-status.json",status(request.get("operation"),generated),"WARNINGS.md","# Warnings and required actions\n\n"+ACTION+"\n").entrySet())
            if(!Arrays.equals(Files.readAllBytes(bundle.resolve(expected.getKey())),expected.getValue().getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Complete computational replay differs: "+expected.getKey());
        write(out,"verification-status.json","{\"status\":\"PASS_COMPLETE_NATIVE_WESANDERSON037_REPLAY\",\"result_admitted\":true,\"colors_compared\":"+generated.count()+",\"independent_R_refit\":false,\"authenticity_established\":false,\"paper_reproduction\":false}\n");
        write(out,"input-SHA256SUMS",Files.readAllBytes(bundle.resolve("SHA256SUMS")));write(out,"WARNINGS.md","# Warnings and required actions\n\n"+ACTION+"\n");
        write(out,"runtime.txt",runtime());manifest(out);System.out.println("PASS_COMPLETE_NATIVE_WESANDERSON037_REPLAY colors="+generated.count());
    }

    private static String status(String operation,Generated generated) {
        return "{\"status\":\"PASS_NATIVE_WESANDERSON037_COMPLETE_RESULT\",\"operation\":"+quote(operation)+",\"result_admitted\":true,\"complete_colors\":"+generated.count()+",\"R_runtime_required\":false,\"original_R_graphics_ported\":false,\"paper_palette_inferred\":false,\"whole_goal_complete\":false}\n";
    }
    private static String requestBytes(Map<String,String> request) {
        StringBuilder text=new StringBuilder();for(var entry:request.entrySet()) {
            if(entry.getValue().indexOf('\n')>=0||entry.getValue().indexOf('\r')>=0||entry.getValue().indexOf('\t')>=0)throw new IllegalArgumentException("Saved request values must be single TSV fields");
            text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        }return text.toString();
    }
    private static String required(Map<String,String> options,String key) {
        String value=options.get(key);if(value==null||value.isEmpty())throw new IllegalArgumentException("Required option absent: "+key);return value;
    }
    private static byte[] resource(String name) throws IOException {
        try(var input=WesAnderson037PaletteCli.class.getResourceAsStream(RESOURCE+name)) {
            if(input==null)throw new IOException("Packaged provenance unavailable: "+name);return input.readAllBytes();
        }
    }
    private static String runtime() {
        return "java.version\t"+System.getProperty("java.version")+"\njava.vendor\t"+System.getProperty("java.vendor")+"\nos.name\t"+System.getProperty("os.name")+"\nos.arch\t"+System.getProperty("os.arch")+"\n";
    }
    private static void manifest(Path out) throws IOException {
        List<Path> paths;try(var list=Files.list(out)){paths=new ArrayList<>(list.toList());}paths.sort((a,b)->a.getFileName().toString().compareTo(b.getFileName().toString()));
        StringBuilder sums=new StringBuilder();for(Path path:paths)sums.append(hash(path)).append("  ").append(path.getFileName()).append('\n');write(out,"SHA256SUMS",sums.toString());
    }
    private static String hash(Path path) throws IOException {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static void write(Path out,String name,String text) throws IOException { write(out,name,text.getBytes(StandardCharsets.UTF_8)); }
    private static void write(Path out,String name,byte[] bytes) throws IOException { Files.write(out.resolve(name),bytes,StandardOpenOption.CREATE_NEW); }
    private static String quote(String text) {
        StringBuilder value=new StringBuilder("\"");for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);switch(c){case '\\'->value.append("\\\\");case '"'->value.append("\\\"");case '\n'->value.append("\\n");case '\r'->value.append("\\r");case '\t'->value.append("\\t");default->{if(c<32)value.append(String.format("\\u%04x",(int)c));else value.append(c);}}
        }return value.append('"').toString();
    }
}
