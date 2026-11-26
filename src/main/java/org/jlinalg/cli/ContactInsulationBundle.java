/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.ContactInsulationRequest.require;

/** Complete prepared-input capture with exact producer and portable full numerical replay. */
final class ContactInsulationBundle {
    static final String PROFILE="native-prepared-cis-insulation-fixed-boundaries-v1";
    static final String ACTION="Required action: read WARNINGS.md and OUTPUT-AUDIT.json, including missing-value and outside-region counts. Require COMPLETED.json and contact-insulation --verify using the exact producing JAR. Keep original inputs, failed attempts and producers. A prepared-map result admits only the declared insulation and fixed-threshold boundary calculation. Supply separately permitted software/references for omitted operations, or skip dependent analysis when they are unavailable or installation is forbidden. FAIL2 admits no complete result: correct the named input, capacity, cancellation or consistency problem and retry NEW paths. Do not reorder/consolidate pixels, impute bad weights, change thresholds or refresh old seals.";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String value){super(value);}}
    private ContactInsulationBundle() {}
    static Pin producer()throws IOException {
        try {
            Path p=Path.of(ContactInsulationBundle.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if(!p.getFileName().toString().endsWith(".jar"))throw new Unavailable("Build and retain the complete executable producing JAR; class-directory capture is unavailable");
            return pin(p,1000000000L,()->{});
        }catch(java.net.URISyntaxException|NullPointerException e){throw new Unavailable("Exact producing JAR unavailable; restore it before dependent analysis");}
    }
    static Runnable bounded(ContactInsulationRequest.Spec s,Runnable cancellation) {
        long start=System.nanoTime();return ()->{cancellation.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>s.capacity().wallMillis())throw new IllegalStateException("Cooperative wall limit/cancellation exceeded; no complete insulation result");};
    }
    static Path capture(Path supplied,ContactInsulationRequest.Spec s,Runnable cancellation)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();require(supplied.toAbsolutePath().equals(root)&&root.getParent()!=null,"Require a NEW canonical capture path");
        directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Capture path exists; use a NEW path");
        Runnable check=bounded(s,cancellation);var originals=Map.of("bins.tsv",Path.of(s.bins()),"pixels.tsv",Path.of(s.pixels()));var pins=new TreeMap<String,Pin>();long total=0;
        for(var entry:originals.entrySet()) {
            Path p=entry.getValue();if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(p))throw new Unavailable("Complete permitted input unavailable: "+p);
            Pin identity=pin(p,s.capacity().inputBytes(),check);pins.put(entry.getKey(),identity);total=Math.addExact(total,identity.bytes());
        }
        require(total<=s.capacity().inputBytes(),"Combined complete input bytes exceed capacity");Pin producer=producer();boolean created=false;String stage="CREATE_CAPTURE";
        try {
            Files.createDirectory(root);created=true;Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("outputs"));
            writeJson(root.resolve("INCOMPLETE.json"),Map.of("analysis_admitted",false));writeJson(root.resolve("request.json"),s.value());
            Files.writeString(root.resolve("WARNINGS.md"),warnings(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            stage="CAPTURE_COMPLETE_INPUTS";
            for(var entry:originals.entrySet()) {
                Files.copy(entry.getValue(),root.resolve("inputs/"+entry.getKey()));
                require(pins.get(entry.getKey()).equals(pin(entry.getValue(),s.capacity().inputBytes(),check))&&pins.get(entry.getKey()).equals(pin(root.resolve("inputs/"+entry.getKey()),s.capacity().inputBytes(),check)),"Original input changed during capture");
            }
            writeJson(root.resolve("source-inputs.json"),Map.of("original_bins",s.bins(),"original_pixels",s.pixels(),"pins",pins,"complete_original_bytes",true));
            stage="COMPLETE_PREPARED_INSULATION";writeJson(root.resolve("OUTPUT-AUDIT.json"),ContactInsulationCompute.run(root,s,false,check));
            unchanged(originals,pins,s,producer,check);stage="FINALIZE_AND_REPLAY";var files=files(root,s,check);files.remove("INCOMPLETE.json");
            writeJson(root.resolve("manifest.json"),Map.of("schema_version",1,"profile",PROFILE,"producer",producer,"files",files,"analysis_admitted",true,"whole_package_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),Map.of("schema_version",1,"status","COMPLETE_NATIVE_PREPARED_CIS_INSULATION_FIXED_BOUNDARIES","manifest",pin(root.resolve("manifest.json"),1048576,check),"analysis_admitted",true,"whole_package_admitted",false));
            verify(root,true,check);unchanged(originals,pins,s,producer,check);Files.delete(root.resolve("INCOMPLETE.json"));return root;
        }catch(IOException|RuntimeException|Error e){if(created)failure(root,stage,e);throw e;}
    }
    private static void unchanged(Map<String,Path> originals,Map<String,Pin> pins,ContactInsulationRequest.Spec s,Pin producer,Runnable check)throws IOException {
        for(var entry:originals.entrySet())require(pins.get(entry.getKey()).equals(pin(entry.getValue(),s.capacity().inputBytes(),check)),"Original input changed before admission");
        require(producer.equals(producer()),"Producing JAR changed during calculation");
    }
    static void verify(Path supplied,boolean precommit,Runnable cancellation)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS)&&Files.exists(root.resolve("INCOMPLETE.json"),LinkOption.NOFOLLOW_LINKS)==precommit,"Failed/incomplete insulation capture is not admitted");
        var done=object(json(root.resolve("COMPLETED.json"),1048576));keys(done,"schema_version","status","manifest","analysis_admitted","whole_package_admitted");integer(done.get("schema_version"),1,1);
        require("COMPLETE_NATIVE_PREPARED_CIS_INSULATION_FIXED_BOUNDARIES".equals(done.get("status"))&&Boolean.TRUE.equals(done.get("analysis_admitted"))&&Boolean.FALSE.equals(done.get("whole_package_admitted")),"Missing or expanded native analysis admission");
        Pin m=pin(root.resolve("manifest.json"),1048576,cancellation);require(m.equals(fingerprint(done.get("manifest"),1048576)),"Saved manifest changed");
        var saved=object(json(root.resolve("manifest.json"),1048576));keys(saved,"schema_version","profile","producer","files","analysis_admitted","whole_package_admitted");integer(saved.get("schema_version"),1,1);
        require(PROFILE.equals(saved.get("profile"))&&Boolean.TRUE.equals(saved.get("analysis_admitted"))&&Boolean.FALSE.equals(saved.get("whole_package_admitted")),"Different or expanded native profile");
        require(producer().equals(fingerprint(saved.get("producer"),1000000000L)),"Restore exact producing JAR; do not reseal another executable");
        var s=ContactInsulationRequest.read(root.resolve("request.json"),false);Runnable check=bounded(s,cancellation);inventory(root,s,saved.get("files"),precommit,check);
        var input=object(json(root.resolve("source-inputs.json"),1048576));keys(input,"original_bins","original_pixels","pins","complete_original_bytes");
        require(s.bins().equals(input.get("original_bins"))&&s.pixels().equals(input.get("original_pixels"))&&Boolean.TRUE.equals(input.get("complete_original_bytes")),"Complete original provenance differs");
        var pins=object(input.get("pins"));keys(pins,"bins.tsv","pixels.tsv");long total=0;
        for(String name:List.of("bins.tsv","pixels.tsv")) {Pin identity=pin(root.resolve("inputs/"+name),s.capacity().inputBytes(),check);require(identity.equals(fingerprint(pins.get(name),s.capacity().inputBytes())),"Complete original input bytes differ");total=Math.addExact(total,identity.bytes());}
        require(total<=s.capacity().inputBytes(),"Combined saved input bytes exceed capacity");
        require(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings()),"Saved warning/action text differs");
        require(NextflowJson.json(ContactInsulationCompute.run(root,s,true,check)).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"),1048576))),"Saved complete numerical audit differs");
        inventory(root,s,saved.get("files"),precommit,check);require(m.equals(pin(root.resolve("manifest.json"),1048576,check))&&producer().equals(fingerprint(saved.get("producer"),1000000000L)),"Manifest or producer changed during replay");
    }
    static Map<String,Object> files(Path root,ContactInsulationRequest.Spec s,Runnable check)throws IOException {
        var files=new TreeMap<String,Object>();long reserve=16777216L,total=Math.addExact(Math.addExact(s.capacity().inputBytes(),s.capacity().outputBytes()),reserve),generated=0;
        for(var row:tree(root,32,total,Math.max(s.capacity().inputBytes(),s.capacity().outputBytes())+reserve,check).entrySet()) {
            require(row.getValue().kind().equals("file"),"Linked capture member refused");Pin p=row.getValue().pin();
            if(!row.getKey().startsWith("inputs/")){generated=Math.addExact(generated,p.bytes());require(generated<=s.capacity().outputBytes()+reserve,"Generated files exceed output capacity plus metadata reserve");}
            files.put(row.getKey(),Map.of("bytes",p.bytes(),"sha256",p.sha256()));
        }
        return files;
    }
    private static void inventory(Path root,ContactInsulationRequest.Spec s,Object declared,boolean precommit,Runnable check)throws IOException {
        var directories=new HashSet<>(Set.of("","inputs","outputs"));
        try(var paths=Files.walk(root)) {
            var all=paths.limit(65).toList();require(all.size()<=64,"Capture namespace exceeds bounded population");
            for(Path p:all){check.run();if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))require(directories.remove(root.relativize(p).toString().replace('\\','/')),"Extra capture directory");}
        }
        require(directories.isEmpty(),"Missing capture directory");var actual=files(root,s,check);
        var expected=new HashSet<>(Set.of("request.json","WARNINGS.md","inputs/bins.tsv","inputs/pixels.tsv","source-inputs.json","outputs/insulation.tsv","OUTPUT-AUDIT.json","manifest.json","COMPLETED.json"));
        if(precommit)expected.add("INCOMPLETE.json");require(actual.keySet().equals(expected),"Missing/extra complete capture file");
        actual.remove("manifest.json");actual.remove("COMPLETED.json");actual.remove("INCOMPLETE.json");require(NextflowJson.json(actual).equals(NextflowJson.json(declared)),"Complete saved capture byte population differs");
    }
    private static void failure(Path root,String stage,Throwable error) {
        try {Files.deleteIfExists(root.resolve("COMPLETED.json"));if(!Files.exists(root.resolve("FAILURE.json")))writeJson(root.resolve("FAILURE.json"),Map.of("status","FAIL2_NO_COMPLETE_NATIVE_INSULATION","stage",stage,"error",error.toString(),"required_action",ACTION,"analysis_admitted",false));}
        catch(IOException suppressed){error.addSuppressed(suppressed);}
    }
    static String warnings() {
        return "CONTACT INSULATION: WARNINGS AND REQUIRED ACTION\n\nThis native calculation accepts complete prepared weighted sparse contact tables. It performs per-region median-normalized diamond insulation, log2 tracks and fixed inclusive numeric boundary-strength calls using the shared selected cooltools 0.5.1/0.5.4 numerical behavior. The settings are explicit user choices. Paper P053 reports strength >0.1; this command's source comparison is >= and must not be silently equated with that statement. No author defaults or software edition is inferred.\n\nInput bins have contiguous zero-based IDs and contiguous sorted nonoverlapping region blocks. Interior bins have the declared width; a last bin may be shorter. Pixel records use that global upper triangle. Their original order and repetitions contribute as supplied. Every record is validated, including records between different regions. Such records remain in the complete captured input and are counted as outside the cis algorithm; they do not contribute to within-region diamonds. No record, weight, identifier or count is repaired, sorted or consolidated.\n\nNaN weights explicitly mark bad bins; zero weights remain valid. Missing sparse pixels contribute zero. Valid-pixel counts include implicit zeros and exclude bad-weight pairs, while raw sums retain stored counts from bad-weight pairs. Source median normalization can produce NaN/Inf intermediate scores; the source log2 transform maps zero/nonfinite tracks to NaN. The requested bad-bin distance mask uses implicit edge positions -1 and N. Global per-window valid-pixel thresholds span every supplied region. Missing score/strength markers, boundary eligibility and calls are separately counted in OUTPUT-AUDIT.json. A NaN strength is not a boundary. Strict source prominence/tied-peak semantics are retained. Unexpected input infinities, arithmetic overflow, malformed records or incomplete populations fail.\n\nLiteral UTF-8 TSV uses exact typed headers, an optional header BOM and CR/LF record delimiters. Quoting is literal; blank records, unknown columns and invalid UTF-8 fail. Exact complete original input bytes are retained. Derived numeric text is canonical, with explicit NaN/Inf markers; it does not preserve original numeric spelling. Row, byte and cooperative wall/cancellation limits are complete-population bounds, not JVM heap or immediate-interruption guarantees. Large requests can fail for resources; preserve the attempt and retry with appropriate resources and NEW paths.\n\nNo upstream runtime is downloaded, installed or executed. Raw Cooler/HDF5 reading, balancing, automatic Li/Otsu thresholds, compartment eigenvectors, dots, ALLCools and whole author workflows are separate. Supply separately permitted prerequisites for those analyses, or skip them when unavailable or forbidden. This result does not establish whole-package or whole-paper reproduction.\n\nSaved verification requires the exact producing JAR and complete file/directory population, then recomputes all input records, bin/window values, masks and boundary decisions and compares the complete output and audit. Original host paths are provenance and are never accessed during portable replay. Hashes establish integrity, not authenticity or biological validity.\n\n"+ACTION+"\n";
    }
}
