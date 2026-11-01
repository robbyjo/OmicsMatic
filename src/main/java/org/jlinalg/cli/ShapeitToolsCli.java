/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Public typed common-stage analysis with prominent skip/failure/recovery and final bundle integrity. */
final class ShapeitToolsCli {
    private ShapeitToolsCli() { }
    private static final String ACTION="Required action: inspect WARNINGS.md, session.json and the retained preparation/process/output-audit records. A public result requires COMPLETED.json with the matching manifest hash. Missing or forbidden runtime/capability requires SKIP3; invalid input, integrity, execution or capacity requires FAIL2. Preserve failed/provisional output, correct the stated cause and rerun to a NEW directory. Analysis never installs or substitutes source profiles. Export setup with shapeit-tools --export-runtime-setup NEW_DIRECTORY; the current adopter needs existing permitted exact build members and does not download or compile. Use Linux x86_64 Python3.11+ and Linux Java (WSL on Windows).";
    static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
        Path work=null;boolean owns=false;
        try{
            if(args.length==2&&args[0].equals("--export-runtime-setup")){export(Path.of(args[1]));emit(out,"SHAPEIT runtime-only setup exported: "+args[1]+"\n"+ACTION);return 0;}
            var options=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2){if(i+1>=args.length||!Set.of("--recipe","--home","--out").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique supported SHAPEIT --recipe/--home/--out value options");}
            if(!options.containsKey("--recipe")||!options.containsKey("--out"))throw new IOException("SHAPEIT requires --recipe and --out");
            work=Path.of(options.get("--out")).toAbsolutePath().normalize();parent(work);
            if(options.containsKey("--home")){Path home=Path.of(options.get("--home")).toAbsolutePath().normalize();if(work.startsWith(home)||home.startsWith(work))throw new IOException("SHAPEIT runtime and public output must be separate");}
            Files.createDirectory(work);owns=true;var session=new LinkedHashMap<String,Object>();session.put("tool","SHAPEIT");session.put("requested_options",options);int code=0;var warnings=new ArrayList<String>();ShapeitRecipe.Spec spec=null;ShapeitCommonPhasing.Result result=null;
            try{
                if(!options.containsKey("--home"))throw new ShapeitRuntime.Unavailable("No exact managed SHAPEIT --home was supplied");
                spec=ShapeitRecipe.read(Path.of(options.get("--recipe")),Path.of(options.get("--home")));session.put("recipe",spec);
                byte[] request;try(var input=Files.newInputStream(spec.source())){request=input.readNBytes(2097153);}if(request.length>2097152)throw new IOException("SHAPEIT recipe exceeds byte bound");Files.write(work.resolve("recipe-request.json"),request,StandardOpenOption.CREATE_NEW);spec.verify();
                result=ShapeitCommonPhasing.run(spec.request(),work.resolve("analysis"),()->{});warnings.addAll(result.warnings());spec.verify();session.put("result",result);session.put("status","completed-audited-common-stage");
            }catch(ShapeitRuntime.Unavailable unavailable){code=3;session.put("status","SKIP3-unavailable-runtime-or-capability");session.put("error",unavailable.toString());warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage());}
            catch(Exception error){code=2;session.put("status","FAIL2-no-public-result-admitted");session.put("error",error.toString());warnings.add("ANALYSIS FAILED: "+error.getMessage());}
            session.put("exit_code",code);session.put("warnings",warnings);session.put("scientific_result_admitted",code==0);session.put("phase_accuracy_certified",false);
            Files.writeString(work.resolve("WARNINGS.md"),"# SHAPEIT "+(code==0?"WARNINGS REQUIRE REVIEW":code==3?"SKIP3 ANALYSIS SKIPPED":"FAIL2 ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            write(work.resolve("session.json"),session);var files=inventory(work);Path manifest=work.resolve("manifest.json");write(manifest,Map.of("schema_version",1,"status",session.get("status"),"files",files));
            if(code==0){Objects.requireNonNull(spec).verify();var r=Objects.requireNonNull(result);if(!r.outputIdentity().equals(ShapeitRawIndex.identity(r.output(),r.outputIdentity().bytes(),()->{}))||!r.targetCountsIdentity().equals(ShapeitRawIndex.identity(r.targetCounts(),r.targetCountsIdentity().bytes(),()->{})))throw new IOException("SHAPEIT final output/count table changed");
                var finalFiles=inventory(work);finalFiles.remove("manifest.json");if(!finalFiles.equals(files))throw new IOException("SHAPEIT final bundle census or member hashes changed");
                write(work.resolve("COMPLETED.json"),Map.of("status","completed-audited-common-stage","manifest_sha256",ShapeitRawIndex.identity(manifest,16777216,()->{}).sha256(),"scientific_result_admitted",true,"phase_accuracy_certified",false));}
            emit(code==0?out:err,"SHAPEIT "+session.get("status")+"; output: "+work+"\n"+String.join("\n",warnings)+"\n"+ACTION);return code;
        }catch(Exception failure){emit(err,"SHAPEIT FAIL2: "+failure+"\n"+ACTION);
            if(owns)try{Files.writeString(work.resolve("FINALIZATION_FAILED.md"),"# SHAPEIT FAIL2 incomplete bundle\n\n"+failure+"\n\n"+ACTION+"\n",StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){emit(err,"Diagnostic write failed: "+diagnostic);}return 2;}
    }
    private static void parent(Path path)throws IOException{if(path.getParent()==null||!path.getParent().toRealPath().equals(path.getParent()))throw new IOException("Require a canonical existing parent and a fresh SHAPEIT output directory");}
    private static Map<String,ShapeitRawIndex.Identity> inventory(Path root)throws IOException{
        var result=new TreeMap<String,ShapeitRawIndex.Identity>();int[] count={0};Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{if(++count[0]>1000000||Files.isSymbolicLink(path))throw new IOException("SHAPEIT bundle link/file-count failure");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{if(++count[0]>1000000||!attrs.isRegularFile())throw new IOException("SHAPEIT bundle nonregular output/capacity failure");result.put(root.relativize(path).toString().replace('\\','/'),ShapeitRawIndex.identity(path,attrs.size(),()->{}));return FileVisitResult.CONTINUE;}
        });return result;
    }
    private static void export(Path supplied)throws IOException{
        Path root=supplied.toAbsolutePath().normalize();parent(root);Files.createDirectory(root);
        for(String name:List.of("install_shapeit.py","shapeit-runtime-lock.json")){
            String resource=name.endsWith(".py")?"/org/jlinalg/omics/shapeit-runtime-setup/"+name:"/org/jlinalg/omics/"+name;
            try(var input=ShapeitToolsCli.class.getResourceAsStream(resource)){if(input==null)throw new IOException("Missing bundled SHAPEIT setup resource");byte[] raw=input.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized SHAPEIT setup resource");Files.write(root.resolve(name),raw,StandardOpenOption.CREATE_NEW);}
        }
        Files.writeString(root.resolve("README.txt"),"Runtime-only exact-build adopter. No runtime binaries, download or compiler are included.\nThe source manifest maps every locked bin/lib/share member name to an existing permitted source file with the exact hash. Setup copies all 32 members into a fresh private home and cannot admit a scientific analysis.\npython3 install_shapeit.py --home NEW_PRIVATE_HOME --source-manifest EXACT_MEMBER_LOCATORS.json --allow-install --report NEW_REPORT.json\n"+ACTION+"\n",StandardOpenOption.CREATE_NEW);
    }
    private static void write(Path path,Object value)throws IOException{Files.writeString(path,ShapeitJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void emit(PrintStream output,String text){output.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        shapeit-tools --recipe COMMON_RECIPE.json --home EXACT_PRIVATE_HOME --out NEW_DIRECTORY
        shapeit-tools --export-runtime-setup NEW_DIRECTORY
        Original SHAPEIT5 c34d common-stage ordinary diploid-GT analysis only.
        Inputs: indexed BGZF BCF2.2; exact target/reference/scaffold sample identities,
        explicit count and missing-call policies, region, parameters and full-population capacities.
        Preserves raw inputs, original join, source AN behavior, target AC/AN and complete output audits.
        Linux x86_64 AVX2/FMA Java is required. Setup accepts only the exact controlled build.
        Runtime installation alone is not scientific admission. No arbitrary engine flags or substitution.
        Binary/VCF staging, SHAPEIT2/4, pedigree/haploid/phase-set, rare, ligation and
        historical singleton-posterior workflows remain unavailable through this command.
        Public success requires COMPLETED.json and its matching manifest hash; phase accuracy is not certified.
        """+ACTION;}
}
