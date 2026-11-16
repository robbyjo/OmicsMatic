/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.genetics.LdCorrelation;

/** Strict, captured portable CSC inputs and source-specific model settings. */
final class LdPred2WorkflowFiles {
    static final String PROFILE="ldpred2-prepared-ld-workflow-v1";
    static final List<String> INPUTS=List.of("summary","ld_variants","ld_pointers","ld_rows","ld_values");
    static final Set<String> REQUIRED=Set.of("engine","method","summary","ld_variants","ld_pointers","ld_rows","ld_values","genome_build","ld_population");
    static final Map<String,String> DEFAULTS=Map.ofEntries(
        Map.entry("max_variants","2000000"),Map.entry("max_entries","200000000"),Map.entry("max_models","2048"),Map.entry("max_input_bytes","68719476736"),
        Map.entry("max_work_bytes","137438953472"),Map.entry("max_files","20000"),Map.entry("wall_ms","21600000"),Map.entry("java_heap_mb","1024"),Map.entry("r_address_space_mb","8192"));
    static final class Prerequisite extends IOException {
        private static final long serialVersionUID=1L;
        Prerequisite(String message){super(message);}
    }
    private static final class Unique extends Properties {
        private static final long serialVersionUID=1L;
        @Override public synchronized Object put(Object key,Object value){if(containsKey(key))throw new IllegalArgumentException("Duplicate request key: "+key);return super.put(key,value);}
    }
    record Config(Map<String,String> values) {
        Config { values=Map.copyOf(values); }
        String get(String key){return values.get(key);}
        int integer(String key){return Integer.parseInt(get(key));}
        long number(String key){return Long.parseLong(get(key));}
        double real(String key){return Double.parseDouble(get(key));}
        boolean bool(String key){return Boolean.parseBoolean(get(key));}
        boolean original(){return get("engine").equals("original");}
    }
    record Variant(String id,String effectAllele,String otherAllele) {}
    record Model(String id,double h2,double p,boolean sparse) {}
    record Data(List<Variant> variants,double[] beta,double[] se,double[] n,LdCorrelation correlation,List<Model> models) {}
    private LdPred2WorkflowFiles() {}

    static Config config(Properties properties) {
        Map<String,String> v=new LinkedHashMap<>();for(String key:properties.stringPropertyNames())v.put(key,properties.getProperty(key));
        if(!v.keySet().containsAll(REQUIRED))throw new IllegalArgumentException("Required fields: "+new TreeSet<>(REQUIRED));
        if(!Set.of("native","original").contains(v.get("engine"))||!Set.of("inf","grid","auto").contains(v.get("method")))throw new IllegalArgumentException("Use engine=native|original and method=inf|grid|auto");
        if(v.get("engine").equals("native")&&v.get("method").equals("auto"))throw new IllegalArgumentException("LDpred2-auto requires engine=original and a qualified original runtime; no method substitution");
        Set<String> allowed=new TreeSet<>(REQUIRED);allowed.addAll(DEFAULTS.keySet());
        switch(v.get("method")) {
            case "inf"->{allowed.add("heritability");require(v,"heritability");if(v.get("engine").equals("native")){allowed.addAll(Set.of("relative_tolerance","solver_iterations"));v.putIfAbsent("relative_tolerance","1e-10");v.putIfAbsent("solver_iterations","10000");}}
            case "grid"->{allowed.addAll(Set.of("models","seed","burn_in","iterations"));require(v,"models","seed");v.putIfAbsent("burn_in","50");v.putIfAbsent("iterations","100");}
            case "auto"->{allowed.addAll(Set.of("models","heritability","seed","burn_in","iterations","sparse","report_step","allow_jump_sign","shrink_corr","use_mle","p_min","p_max","alpha_min","alpha_max"));require(v,"models","heritability","seed");v.putIfAbsent("burn_in","500");v.putIfAbsent("iterations","200");v.putIfAbsent("sparse","false");v.putIfAbsent("allow_jump_sign","true");v.putIfAbsent("shrink_corr","1");v.putIfAbsent("use_mle","true");v.putIfAbsent("alpha_min","-1.5");v.putIfAbsent("alpha_max","0.5");}
            default->throw new AssertionError();
        }
        if(v.get("engine").equals("original")){allowed.add("runtime");require(v,"runtime");}
        if(!allowed.containsAll(v.keySet())){Set<String> unknown=new TreeSet<>(v.keySet());unknown.removeAll(allowed);throw new IllegalArgumentException("Unknown or unused fields: "+unknown);}
        DEFAULTS.forEach(v::putIfAbsent);
        for(var row:v.entrySet())if(row.getValue()==null||row.getValue().isBlank()||row.getValue().indexOf('\n')>=0||row.getValue().indexOf('\r')>=0||row.getValue().indexOf('\t')>=0)throw new IllegalArgumentException("Blank/control character in "+row.getKey());
        for(String key:List.of("max_variants","max_entries","max_models","max_files","java_heap_mb","r_address_space_mb"))positiveInt(v,key);
        for(String key:List.of("max_input_bytes","max_work_bytes","wall_ms"))if(Long.parseLong(v.get(key))<1)throw new IllegalArgumentException("Positive "+key+" required");
        if(Integer.parseInt(v.get("max_variants"))==Integer.MAX_VALUE)throw new IllegalArgumentException("max_variants must leave space for a CSC end pointer");
        if(v.containsKey("heritability"))positive(v,"heritability");
        if(v.containsKey("seed")&&Integer.parseInt(v.get("seed"))==Integer.MIN_VALUE)throw new IllegalArgumentException("R's missing integer is not a seed");
        if(v.containsKey("iterations")){positiveInt(v,"iterations");int burn=Integer.parseInt(v.get("burn_in"));if(burn<0||(long)burn+Integer.parseInt(v.get("iterations"))>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid burn-in plus iteration count");}
        if(v.containsKey("relative_tolerance")){positiveInt(v,"solver_iterations");positive(v,"relative_tolerance");if(Double.parseDouble(v.get("relative_tolerance"))>=1)throw new IllegalArgumentException("relative_tolerance must be below one");}
        if(v.get("method").equals("auto")) {
            v.putIfAbsent("report_step",Long.toString((long)Integer.parseInt(v.get("iterations"))+1));positiveInt(v,"report_step");
            for(String key:List.of("sparse","allow_jump_sign","use_mle"))if(!Set.of("true","false").contains(v.get(key)))throw new IllegalArgumentException("Boolean "+key+" must be true or false");
            positive(v,"shrink_corr");if(Double.parseDouble(v.get("shrink_corr"))>1)throw new IllegalArgumentException("shrink_corr must be <=1");
            double low=finite(v,"alpha_min"),high=finite(v,"alpha_max");if(low>high)throw new IllegalArgumentException("alpha_min must be <= alpha_max");
            if(v.containsKey("p_min")!=v.containsKey("p_max"))throw new IllegalArgumentException("Supply both p_min and p_max");
            if(v.containsKey("p_min")){positive(v,"p_min");positive(v,"p_max");if(Double.parseDouble(v.get("p_min"))>Double.parseDouble(v.get("p_max"))||Double.parseDouble(v.get("p_max"))>1)throw new IllegalArgumentException("p bounds must be ordered in (0,1]");}
        }
        return new Config(v);
    }
    private static void require(Map<String,String> v,String... keys){for(String key:keys)if(!v.containsKey(key))throw new IllegalArgumentException("Required field: "+key);}
    private static void positiveInt(Map<String,String> v,String key){if(Integer.parseInt(v.get(key))<1)throw new IllegalArgumentException("Positive integer "+key+" required");}
    private static double finite(Map<String,String> v,String key){double x=Double.parseDouble(v.get(key));if(!Double.isFinite(x))throw new IllegalArgumentException("Finite "+key+" required");return x;}
    private static void positive(Map<String,String> v,String key){if(finite(v,key)<=0)throw new IllegalArgumentException("Positive "+key+" required");}
    static Properties load(Path file)throws IOException{if(Files.size(file)>1048576)throw new IOException("Request exceeds 1 MiB");Unique p=new Unique();p.load(new StringReader(Files.readString(file,StandardCharsets.UTF_8)));return p;}
    static void properties(Path file,Map<String,String> values)throws IOException{Properties p=new Properties();p.putAll(values);StringWriter writer=new StringWriter();p.store(writer,"Explicit LDpred2 workflow settings");write(file,writer.toString());}
    static void write(Path file,String text)throws IOException{Files.writeString(file,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static String sha(Path file)throws IOException {
        try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] b=new byte[1048576];for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static void copy(Path source,Path target,long limit)throws IOException {
        if(!Files.isRegularFile(source)||Files.size(source)>limit)throw new IOException("Missing/oversized input: "+source);
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            byte[] bytes=new byte[1048576];long total=0;for(int n;(n=in.read(bytes))>=0;){total=Math.addExact(total,n);if(total>limit)throw new IOException("Input exceeded capacity during capture");out.write(bytes,0,n);}
        }
        if(!sha(source).equals(sha(target)))throw new IOException("Source changed during capture: "+source);
    }
    static Path resolve(Path base,String value){Path p=Path.of(value);return (p.isAbsolute()?p:base.resolve(p)).toAbsolutePath().normalize();}
    static String[] fields(String line,int columns)throws IOException{String[] x=line.split("\t",-1);if(x.length!=columns)throw new IOException("Expected "+columns+" TSV columns");return x;}
    static double numeric(String text)throws IOException{try{double x=Double.parseDouble(text);if(!Double.isFinite(x))throw new NumberFormatException();return x;}catch(NumberFormatException e){throw new IOException("Finite numeric field required: "+text,e);}}
    static String modelId(String id)throws IOException{if(!id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")||id.toUpperCase(java.util.Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new IOException("Model ID must be a portable unique name: "+id);return id;}
    static List<Model> models(Path root,Config c)throws IOException {
        if(c.get("method").equals("inf"))return List.of(new Model("inf",c.real("heritability"),1,false));
        List<Model> models=new ArrayList<>();Set<String> ids=new java.util.HashSet<>();boolean auto=c.get("method").equals("auto");
        try(BufferedReader in=Files.newBufferedReader(root.resolve("inputs/models.tsv"),StandardCharsets.UTF_8)) {
            String expected=auto?"model\tp_init":"model\th2\tp\tsparse";if(!expected.equals(in.readLine()))throw new IOException("Exact models header required: "+expected);
            for(String line;(line=in.readLine())!=null;) {
                if(models.size()>=c.integer("max_models"))throw new IOException("Model count exceeds max_models; declare adequate capacity, do not discard models");
                String[] x=fields(line,auto?2:4);String id=modelId(x[0]);if(!ids.add(id))throw new IOException("Duplicate model ID: "+id);
                double h2=auto?c.real("heritability"):numeric(x[1]),p=numeric(x[auto?1:2]);boolean sparse=auto?c.bool("sparse"):Boolean.parseBoolean(x[3]);
                if(h2<=0||p<=0||p>1||(!auto&&!Set.of("true","false").contains(x[3])))throw new IOException("Invalid h2, p or sparse model field");
                models.add(new Model(id,h2,p,sparse));
            }
        }
        if(models.isEmpty())throw new IOException("At least one model required");return List.copyOf(models);
    }
    static Data data(Path root,Config c)throws IOException {
        long pointerBytes=Files.size(root.resolve("inputs/ld_pointers.bin"));if(pointerBytes<8||pointerBytes%4!=0)throw new IOException("Invalid little-endian CSC pointer file");
        int m=Math.toIntExact(pointerBytes/4-1);if(m>c.integer("max_variants"))throw new IOException("Variant count exceeds max_variants");
        List<Variant> variants=new ArrayList<>(m);double[] beta=new double[m],se=new double[m],n=new double[m];Set<String> ids=new java.util.HashSet<>();
        try(BufferedReader s=Files.newBufferedReader(root.resolve("inputs/summary.tsv"),StandardCharsets.UTF_8);BufferedReader v=Files.newBufferedReader(root.resolve("inputs/ld_variants.tsv"),StandardCharsets.UTF_8)) {
            if(!"variant\teffect_allele\tother_allele\tbeta\tbeta_se\tn_eff".equals(s.readLine())||!"variant\teffect_allele\tother_allele".equals(v.readLine()))throw new IOException("Exact summary and LD variant headers required");
            for(int i=0;i<m;i++) {
                String a=s.readLine(),b=v.readLine();if(a==null||b==null)throw new IOException("Variant rows differ from LD dimension");String[] x=fields(a,6),y=fields(b,3);
                for(int j=0;j<3;j++)if(x[j].isEmpty()||x[j].chars().anyMatch(ch->Character.isISOControl(ch)||Character.isWhitespace(ch))||!x[j].equals(y[j]))throw new IOException("Variant order/alleles differ or contain whitespace/control characters at row "+i+"; no automatic matching or flipping");
                if(!ids.add(x[0]))throw new IOException("Duplicate variant identifier: "+x[0]);
                variants.add(new Variant(x[0],x[1],x[2]));beta[i]=numeric(x[3]);se[i]=numeric(x[4]);n[i]=numeric(x[5]);if(se[i]<=0||n[i]<=0)throw new IOException("Positive SE and n_eff required at row "+i);
            }
            if(s.readLine()!=null||v.readLine()!=null)throw new IOException("Extra summary/LD variant rows");
        }
        long entries=Files.size(root.resolve("inputs/ld_values.bin"))/8;
        if(Files.size(root.resolve("inputs/ld_values.bin"))%8!=0||entries>c.integer("max_entries")||Files.size(root.resolve("inputs/ld_rows.bin"))!=entries*4)throw new IOException("LD value/row capacity or byte count mismatch");
        int[] pointers=ints(root.resolve("inputs/ld_pointers.bin"),m+1),rows=ints(root.resolve("inputs/ld_rows.bin"),Math.toIntExact(entries));double[] values=doubles(root.resolve("inputs/ld_values.bin"),Math.toIntExact(entries));
        return new Data(List.copyOf(variants),beta,se,n,LdCorrelation.ofCsc(m,pointers,rows,values),models(root,c));
    }
    private static int[] ints(Path p,int count)throws IOException{int[] x=new int[count];try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(p)))){for(int i=0;i<count;i++)x[i]=Integer.reverseBytes(in.readInt());}return x;}
    private static double[] doubles(Path p,int count)throws IOException{double[] x=new double[count];try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(p)))){for(int i=0;i<count;i++)x[i]=Double.longBitsToDouble(Long.reverseBytes(in.readLong()));}return x;}
    static void weights(Path file,Data data,double[] effects)throws IOException {
        if(effects.length!=data.variants.size())throw new IOException("Effect count differs");
        try(BufferedWriter out=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            out.write("variant\teffect_allele\tother_allele\teffect\n");
            for(int i=0;i<effects.length;i++){if(!Double.isFinite(effects[i]))throw new IOException("Nonfinite accepted effect");Variant v=data.variants.get(i);out.write(v.id+"\t"+v.effectAllele+"\t"+v.otherAllele+"\t"+Double.toString(effects[i])+"\n");}
        }
    }
    static Map<String,String> inventory(Path root)throws IOException {
        Map<String,String> files=new TreeMap<>();
        try(var paths=Files.walk(root)) {
            for(Path p:paths.toList()){if(Files.isSymbolicLink(p))throw new IOException("Bundle aliases are not qualified: "+p);if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))files.put(root.relativize(p).toString().replace('\\','/'),Files.size(p)+"\t"+sha(p));}
        }
        return files;
    }
    static void manifest(Path file,Map<String,String> entries)throws IOException{StringBuilder b=new StringBuilder("path\tbytes\tsha256\n");for(var e:entries.entrySet())b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');write(file,b.toString());}
}
