/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** UTF-8 request-file entry point; preserves paths lost by legacy Windows argv conversion. */
public final class SignacLsiRequestCli {
    private SignacLsiRequestCli() { }
    private static final Set<String> PATHS=Set.of("counts","features","cells","out","rscript","r-library","r-base-library");
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,"SignacLsiRequestCli --request UTF8_REQUEST.json\nUse an ASCII-only request-file path on legacy Windows launchers; Unicode data/output paths belong INSIDE the UTF-8 JSON.\nFIT: {schema_version:1, mode:FIT, options:{...typed CLI options...}}\nVERIFY: {schema_version:1, mode:VERIFY, analysis:captured-directory}\nJSON keys and strings require quotes. Relative paths resolve against the request file's directory.\nThe underlying LSI workflow's dependency, source-profile and development-scope warnings apply.");return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_REQUEST.json; use --help");
            Path request=Path.of(args[1]).toAbsolutePath().normalize();return SignacLsiToolsCli.run(arguments(request),out,err);
        }catch(Exception error){emit(err,"FAIL2: invalid Signac LSI UTF-8 request; no request was executed: "+error+"\nUse a request file in an ASCII-only path on legacy Windows launchers; place exact Unicode paths inside UTF-8 JSON. Correct the named schema/path error and use a NEW output directory.");return 2;}
    }
    static String[] arguments(Path request)throws IOException{
        if(!Files.isRegularFile(request)||Files.size(request)>1_000_000L)throw new IOException("Request file unavailable or exceeds one million bytes");
        Object parsed=SimpleJson.parse(Files.readString(request,StandardCharsets.UTF_8));if(!(parsed instanceof Map<?,?> map))throw new IOException("Request must be a JSON object");
        Object schema=map.get("schema_version");if(!(schema instanceof Number n)||n.doubleValue()!=1)throw new IOException("Require schema_version1");
        Path parent=request.toAbsolutePath().normalize().getParent();Object mode=map.get("mode");
        if("VERIFY".equals(mode)){
            if(!map.keySet().equals(Set.of("schema_version","mode","analysis"))||!(map.get("analysis") instanceof String value)||value.isBlank())throw new IOException("VERIFY requires only schema_version, mode and an analysis path");
            return new String[]{"--verify",resolve(parent,value)};
        }
        if(!"FIT".equals(mode)||!map.keySet().equals(Set.of("schema_version","mode","options"))||!(map.get("options") instanceof Map<?,?> options))throw new IOException("FIT requires only schema_version, mode and typed options");
        ArrayList<String> args=new ArrayList<>();
        for(var entry:options.entrySet()){
            if(!(entry.getKey() instanceof String key)||!(entry.getValue() instanceof String||entry.getValue() instanceof Number||entry.getValue() instanceof Boolean))throw new IOException("Option values must be strings, numbers or booleans");
            String value=entry.getValue().toString();if(value.isBlank())throw new IOException("Empty option value: "+key);
            if(PATHS.contains(key)){if(!(entry.getValue() instanceof String))throw new IOException("Path option requires a string: "+key);value=resolve(parent,value);}
            args.add("--"+key);args.add(value);
        }
        return args.toArray(String[]::new);
    }
    private static String resolve(Path parent,String value)throws IOException{try{return parent.resolve(Path.of(value)).normalize().toString();}catch(java.nio.file.InvalidPathException error){throw new IOException("Invalid request path",error);}}
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
