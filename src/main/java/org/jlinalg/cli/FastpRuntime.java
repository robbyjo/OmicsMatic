/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact static original-program and setup-evidence binding; opening a context launches no analysis. */
final class FastpRuntime {
    private FastpRuntime() { }
    static final String LOCK_SHA256="06760eb5048a8cb095d6440603cf941c86947a0e93393c111148c0b387745cfe";
    static final String MARKER=".omicsmatic-fastp.json";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable failure){super(message,failure);}
    }
    record External(String requested,String realPath,FastpFiles.Identity identity,int mode) { }
    record Context(Path home,String profile,String version,Path setupReport,String kernel,
                   Map<String,FastpFiles.Identity> payload,Map<String,FastpFiles.Identity> setup,
                   Map<String,External> helpers) {
        Context { payload=Map.copyOf(payload);setup=Map.copyOf(setup);helpers=Map.copyOf(helpers); }
        void verify(Runnable check)throws IOException {
            if(!equals(open(home,profile,setupReport,check)))throw new IOException("fastp runtime,setup evidence or declared helper binding changed");
        }
        List<String> command(){return List.of(home.resolve("fastp").toString());}
        Map<String,String> environment(){return Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","TZ","UTC");}
    }
    static List<String> profiles(){return List.of("linux-x86_64-fastp-0.22.0","linux-x86_64-fastp-0.23.2","linux-x86_64-fastp-0.25.0","linux-x86_64-fastp-1.0.1");}
    static Map<?,?> locked(String requested)throws IOException {
        byte[] bytes;
        try(var input=FastpRuntime.class.getResourceAsStream("/org/jlinalg/omics/fastp-runtime-lock.json")){
            if(input==null)throw new IOException("Missing bundled fastp lock");bytes=input.readNBytes(65537);
        }
        if(bytes.length>65536||!FastpFiles.sha(bytes).equals(LOCK_SHA256))throw new IOException("Bundled fastp lock differs");
        Map<?,?> lock=object(SimpleJson.parse(FastpFiles.utf8(bytes)));if(integer(lock.get("schema_version"),1,1)!=1)throw new IOException("Unsupported fastp lock schema");
        if(!(lock.get("profiles") instanceof List<?> rows)||rows.size()!=4)throw new IOException("Unexpected fastp profile census");
        var names=new HashSet<String>();Map<?,?> found=null;
        for(Object value:rows){Map<?,?> row=object(value);String name=text(row.get("profile"));if(!names.add(name))throw new IOException("Duplicate locked fastp profile");if(name.equals(requested))found=row;}
        if(!names.equals(Set.copyOf(profiles())))throw new IOException("Unsupported locked fastp editions");
        if(found==null)throw new Unavailable("Exact requested fastp edition is unavailable; never silently resolve an unreported patch or substitute another edition");return found;
    }
    static Context open(Path supplied,String requested,Path suppliedReport,Runnable check)throws IOException {
        check.run();String architecture=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(architecture))throw new Unavailable("fastp original runtime requires Linux x86_64 Java; use Linux Java inside WSL on Windows");
        if(!profiles().contains(requested))throw new Unavailable("Exact requested fastp edition is not supported; keep dependent analysis skipped");
        Path home=supplied.toAbsolutePath().normalize(),report=suppliedReport.toAbsolutePath().normalize();
        if(!Files.exists(home,LinkOption.NOFOLLOW_LINKS)||!Files.exists(report,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Required fastp home or completed setup report is absent; run the permitted exact-profile installer and skip dependent analysis until available");
        canonicalDirectory(home);canonicalDirectory(report);
        if(home.startsWith(report)||report.startsWith(home))throw new IOException("fastp runtime and setup report overlap");
        var pin=locked(requested);String version=text(pin.get("version"));Map<?,?> binary=object(pin.get("binary"));
        var payload=new TreeMap<String,FastpFiles.Identity>();
        for(String name:List.of("fastp","LICENSE",MARKER))payload.put(name,FastpFiles.identity(home.resolve(name),16_777_216,check));
        if(!payload.get("fastp").pin().equals(fingerprint(binary)))throw new IOException("Original fastp binary differs from its exact locked edition");
        byte[] notice;
        try(var input=FastpRuntime.class.getResourceAsStream("/org/jlinalg/omics/fastp-MIT-NOTICE.txt")){
            if(input==null)throw new IOException("Missing original fastp MIT notice");notice=input.readNBytes(65537);
        }
        if(!FastpFiles.sha(notice).equals("52e18866a994ee9839aa6d15696bc1fe425e6466631d3a57a75c089dd84ae88e")||!payload.get("LICENSE").pin().equals(new FastpFiles.Pin(notice.length,FastpFiles.sha(notice))))throw new IOException("Original fastp license differs");
        Map<String,Object> expected=Map.of("schema_version",1L,"profile",requested,"version",version,"lock_sha256",LOCK_SHA256,
            "files",Map.of("fastp",Map.of("bytes",payload.get("fastp").pin().bytes(),"sha256",payload.get("fastp").pin().sha256()),"LICENSE",Map.of("bytes",payload.get("LICENSE").pin().bytes(),"sha256",payload.get("LICENSE").pin().sha256())),
            "status","PINNED_DEVELOPMENT_RUNTIME_ONLY","user_analysis_admitted",false,"source_to_binary_reproduction_verified",false);
        if(!expected.equals(object(FastpFiles.json(home.resolve(MARKER),65536))))throw new IOException("fastp runtime marker differs");
        inventory(home,payload.keySet(),16_777_216,check);
        if(!Files.isExecutable(home.resolve("fastp")))throw new Unavailable("fastp binary execution is not permitted; skip dependent analysis");
        // The pinned binaries contain no interpreter or dynamic-library segment.
        // Reuse the existing bounded ELF parser without changing its scientific callers.
        IqtreeRuntime.staticElf(FastpFiles.bytes(home.resolve("fastp"),16_777_216));
        var setup=new TreeMap<String,FastpFiles.Identity>();
        setup.put("manifest.json",FastpFiles.identity(report.resolve("manifest.json"),1_048_576,check));
        setup.put("COMPLETED.json",FastpFiles.identity(report.resolve("COMPLETED.json"),65536,check));
        Map<?,?> completion=object(FastpFiles.json(report.resolve("COMPLETED.json"),65536));
        if(!completion.keySet().equals(Set.of("status","manifest","user_analysis_admitted"))||!"COMPLETE_SETUP_ONLY".equals(completion.get("status"))||!Boolean.FALSE.equals(completion.get("user_analysis_admitted"))||!fingerprint(object(completion.get("manifest"))).equals(setup.get("manifest.json").pin()))throw new IOException("Incomplete/changed fastp setup completion");
        Map<?,?> manifest=object(FastpFiles.json(report.resolve("manifest.json"),1_048_576));integer(manifest.get("schema_version"),1,1);Map<?,?> files=object(manifest.get("files"));
        if(files.size()>64||!files.keySet().containsAll(Set.of("setup.json","reads.fq","processed.fq","fastp.json","fastp.html","version.stdout","version.stderr","version.command.json","health.stdout","health.stderr","health.command.json")))throw new IOException("Incomplete/excessive fastp setup file census");
        for(var row:files.entrySet()){
            String name=FastpFiles.member(text(row.getKey()));if(name.indexOf('/')>=0||setup.containsKey(name))throw new IOException("Unexpected nested/duplicate fastp setup member");
            FastpFiles.Pin wanted=fingerprint(object(row.getValue()));var actual=FastpFiles.identity(report.resolve(name),16_777_216,check);if(!wanted.equals(actual.pin()))throw new IOException("fastp setup member bytes differ: "+name);setup.put(name,actual);
        }
        inventory(report,setup.keySet(),67_108_864,check);
        Map<?,?> installed=object(FastpFiles.json(report.resolve("setup.json"),65536));
        if(!"passed-development-runtime-and-tiny-synthetic-health-only".equals(installed.get("status"))||!home.toString().equals(installed.get("home"))||!requested.equals(installed.get("profile"))||!Boolean.FALSE.equals(installed.get("user_analysis_admitted"))||!expected.equals(object(installed.get("runtime_identity"))))throw new IOException("fastp setup record is not bound to this home/profile");
        checkHealth(home,report,version);
        var helpers=new TreeMap<String,External>();
        for(String name:List.of("/usr/bin/prlimit","/usr/bin/setsid")){
            Path requestedPath=Path.of(name);
            if(!Files.exists(requestedPath)||!Files.isExecutable(requestedPath))throw new Unavailable("Required fastp process helper is unavailable or execution forbidden: "+name);
            Path real=requestedPath.toRealPath();int mode=((Number)Files.getAttribute(real,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&07777;
            helpers.put(name,new External(name,real.toString(),FastpFiles.identity(real,16_777_216,check),mode));
        }
        check.run();return new Context(home,requested,version,report,System.getProperty("os.version"),payload,setup,helpers);
    }
    private static void checkHealth(Path home,Path report,String version)throws IOException {
        String observed=FastpFiles.utf8(FastpFiles.bytes(report.resolve("version.stdout"),65536))+FastpFiles.utf8(FastpFiles.bytes(report.resolve("version.stderr"),65536));
        if(!observed.equals("fastp "+version+"\n"))throw new IOException("fastp exact version diagnostic differs");
        String sequence="ACGCTAGCATCGACTGATCGACTAGCATAGCTACGATCGATGCTAGCTAGCATGCATCGATGCTAGCATCGATGCATCGATGCTAGCATCGATGCATCGATG";var expected=new StringBuilder();
        for(int i=1;i<=32;i++)expected.append("@fixture:").append(i).append('\n').append(sequence).append("\n+\n").append("I".repeat(sequence.length())).append('\n');
        String raw=expected.toString();if(!raw.equals(FastpFiles.utf8(FastpFiles.bytes(report.resolve("reads.fq"),65536)))||!raw.equals(FastpFiles.utf8(FastpFiles.bytes(report.resolve("processed.fq"),65536))))throw new IOException("fastp complete synthetic health population differs");
        Map<?,?> summary=object(object(FastpFiles.json(report.resolve("fastp.json"),1_048_576)).get("summary"));
        for(String stage:List.of("before_filtering","after_filtering")){
            Map<?,?> counts=object(summary.get(stage));if(integer(counts.get("total_reads"),0,32)!=32)throw new IOException("fastp health read count differs");
            for(String key:List.of("total_bases","q20_bases","q30_bases"))if(integer(counts.get(key),0,65536)!=32L*sequence.length())throw new IOException("fastp health quality/base count differs");
        }
        checkCommand(report.resolve("version.command.json"),List.of(home.resolve("fastp").toString(),"--version"));
        checkCommand(report.resolve("health.command.json"),List.of(home.resolve("fastp").toString(),"--in1","reads.fq","--out1","processed.fq","--json","fastp.json","--html","fastp.html","--thread","1","--dont_overwrite"));
    }
    private static void checkCommand(Path source,List<String> command)throws IOException {
        Map<?,?> record=object(FastpFiles.json(source,65536));if(!command.equals(record.get("command"))||integer(record.get("exit_code"),0,0)!=0||integer(record.get("address_space_bytes"),0,Long.MAX_VALUE)!=3L*1024*1024*1024||integer(record.get("cpu_seconds"),0,120)!=60||integer(record.get("output_file_bytes"),0,Long.MAX_VALUE)!=16_777_216||integer(record.get("wall_seconds"),0,120)!=120)throw new IOException("fastp setup process command/status/capacities differ");
    }
    private static void canonicalDirectory(Path path)throws IOException { if(!path.toRealPath().equals(path)||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("fastp requires canonical unlinked directories"); }
    private static void inventory(Path root,Set<String> expected,long maximum,Runnable check)throws IOException {
        var found=new HashSet<String>();long total=0;
        try(var paths=Files.newDirectoryStream(root)){
            for(Path path:paths){check.run();String name=path.getFileName().toString();if(!expected.contains(name)||!found.add(name)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Extra/linked/nonregular fastp runtime/setup member");total=Math.addExact(total,Files.size(path));if(total>maximum)throw new IOException("fastp complete runtime/setup byte capacity exceeded");}
        }
        if(!found.equals(expected))throw new IOException("Missing fastp runtime/setup member");
    }
    static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require fastp JSON object");return map;}
    static String text(Object value)throws IOException {if(!(value instanceof String text))throw new IOException("Require fastp text metadata");return text;}
    static long integer(Object value,long minimum,long maximum)throws IOException {if(!(value instanceof Long n)||n<minimum||n>maximum)throw new IOException("Invalid fastp integer metadata");return n;}
    static FastpFiles.Pin fingerprint(Map<?,?> value)throws IOException {long bytes=integer(value.get("bytes"),0,67_108_864);String hash=text(value.get("sha256"));if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Invalid fastp content hash");return new FastpFiles.Pin(bytes,hash);}
}
