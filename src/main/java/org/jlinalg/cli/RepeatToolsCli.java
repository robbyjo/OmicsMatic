/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public typed native repeat checks, masking and complete supplied-mask audits. */
final class RepeatToolsCli {
    private RepeatToolsCli() { }
    private static final String RECOVERY = "Required action: read WARNINGS.md,session.json,analysis-status.json and retained preparation/native diagnostics. A native completed result requires COMPLETED.json,its matching manifest and repeat-tools --verify DIRECTORY. Correct invalid inputs,integrity or capacity errors and rerun to a NEW output directory;never overwrite or admit partial files. Original SEARCH is not implemented in this candidate: SKIP3. Export private payload setup with --export-runtime-setup NEW_DIRECTORY and read its README.md;payload installation does not admit a scientific runtime or SEARCH. Use a candidate with documented admitted runtime/resource support,or run the exact permitted upstream separately and import explicitly identified annotations as EXTERNAL_NOT_CERTIFIED for native MASK/AUDIT. Never substitute an edition or infer biological absence. Java native operations need no upstream installation. No download/install occurs during analysis. Cell Ranger remains excluded.";
    private record Options(Path recipe, Path output, Path source, byte[] sourceBytes, Map<String,String> values) {
        Options { sourceBytes = sourceBytes.clone(); values = Map.copyOf(values); }
        void verify() throws IOException {
            if (source != null && !RepeatInputs.hash(source,1048576,()->{}).equals(AdmixtureInputs.digest(sourceBytes)))
                throw new IOException("Repeat CLI options file changed during analysis");
        }
    }
    static int run(String[] args, PrintStream out, PrintStream err) { return run(args,out,err,()->{}); }
    static int run(String[] args, PrintStream out, PrintStream err, Runnable externalCheck) {
        if (args.length == 1 && args[0].equals("--help")) { emit(out,help()); return 0; }
        Path directory = null; boolean owned = false;
        RepeatBundle.Limits capacity = new RepeatBundle.Limits(4194304,64);
        try {
            if(args.length==2&&args[0].equals("--export-runtime-setup")){
                RepeatSetup.export(Path.of(args[1]),externalCheck);emit(out,"RepeatMasker payload setup exported: "+Path.of(args[1]).toAbsolutePath().normalize());
                emit(err,"PAYLOAD SETUP ONLY: SEARCH NOT ADMITTED. Read the exported README.md and SHA256SUMS before explicit permitted setup;no installation or download occurred.");return 0;
            }
            if (args.length == 2 && Set.of("--verify","--verify-options-file").contains(args[0])) {
                Path source = null; byte[] sourceBytes = null; Path target;
                if (args[0].equals("--verify-options-file")) {
                    source = Path.of(args[1]).toAbsolutePath().normalize(); sourceBytes = AdmixtureInputs.raw(source,1048576);
                    Map<?,?> document = RepeatBundle.object(sourceBytes);
                    if (!document.keySet().equals(Set.of("directory")) || !(document.get("directory") instanceof String value) || value.isBlank())
                        throw new IOException("Verification options require exactly one nonblank directory string");
                    target = Path.of(value);
                } else target = Path.of(args[1]);
                var verified = RepeatBundle.verify(target,externalCheck);
                if (source != null && !RepeatInputs.hash(source,1048576,externalCheck).equals(AdmixtureInputs.digest(sourceBytes)))
                    throw new IOException("Verification options file changed during read-only validation");
                emit(out,"Verified native repeat bundle: "+RepeatJson.json(verified));
                emit(err,"NATIVE SCOPE: verification checks bundle integrity,not authenticity,original-search completion or biological absence."); return 0;
            }
            Options options = options(args); directory = options.output();
            if (directory.getParent() == null || !directory.getParent().toRealPath().equals(directory.getParent()))
                throw new IOException("Require a canonical existing output parent and a NEW native repeat directory");
            Files.createDirectory(directory); owned = true;
            try (var guard = new Guard(directory)) {
                Runnable check = () -> { guard.check(); externalCheck.run(); };
                var session = new LinkedHashMap<String,Object>(); var warnings = new ArrayList<String>(); int code = 0;
                session.put("schema_version",1); session.put("tool",RepeatBundle.TOOL); session.put("started_at",Instant.now().toString());
                RepeatRecipe.Spec requested = null, component = null; RepeatInputs.Prepared prepared = null;
                var result = new LinkedHashMap<String,Object>();
                try {
                    options.verify(); check.run();
                    byte[] recipeRaw = AdmixtureInputs.raw(options.recipe(),2097152);
                    try { requested = RepeatRecipe.read(options.recipe()); }
                    catch (IOException invalid) { writeRequests(directory,options,recipeRaw,capacity,check); throw invalid; }
                    if (!requested.recipeSha256().equals(AdmixtureInputs.digest(recipeRaw))) throw new IOException("Recipe changed between captured request and decoding");
                    capacity = new RepeatBundle.Limits(requested.limits().workspaceBytes(),requested.limits().fileCount());
                    writeRequests(directory,options,recipeRaw,capacity,check);
                    session.put("operation",requested.operation()); session.put("recipe_sha256",requested.recipeSha256()); session.put("requested_limits",requested.limits());
                    component = component(requested,directory,capacity,check); session.put("component_limits",component.limits());
                    session.put("capacity_policy","Complete bundle budget includes root metadata,preparation and native outputs;component budget subtracts existing root bytes/members and reserves256KiB/eight members for final metadata and retained diagnostics.");
                    prepared = RepeatInputs.prepare(component,directory.resolve("prepared"),check); warnings.addAll(prepared.warnings());
                    if (requested.operation() == RepeatRecipe.Operation.SEARCH)
                        throw new RepeatRecipe.UnavailableCapability("Original RepeatMasker scientific runtime/resource/cache admission is unfinished;payload setup alone does not admit SEARCH and this analysis was not run");
                    result.put("operation",requested.operation()); result.put("scope",RepeatBundle.scope(requested.operation()));
                    result.put("completion_flags",RepeatBundle.flags(requested.operation()));
                    result.put("assembly",requested.assembly()); result.put("query_records",prepared.query().staged().sequences().size());
                    result.put("query_bases",prepared.query().staged().residues()); result.put("preparation","prepared/preparation.json");
                    if (prepared.annotations() != null) { result.put("annotation_rows",prepared.annotations().hits().size()); result.put("selected_rows",prepared.annotationValidation().selectedRows()); }
                    switch (requested.operation()) {
                        case MASK -> {
                            var nativeResult = RepeatNativeMask.execute(prepared,directory.resolve("native-mask"),check);
                            result.put("native_masked","native-mask/native-masked.fa"); result.put("coverage","native-mask/native-coverage.tsv"); result.put("native_ledger","native-mask/native-mask.json");
                            result.put("covered_bases",nativeResult.audit().coveredBases()); result.put("changed_characters",nativeResult.audit().changedCharacters());
                            warnings.add("NATIVE MASK ONLY: supplied annotation completeness and biological absence remain uncertified,including zero rows/zero selected rows. Review native-mask/WARNINGS.md and every zero-coverage query.");
                        }
                        case AUDIT -> {
                            var audit = prepared.suppliedMaskAudit();
                            result.put("compared_bases",audit.comparedBases()); result.put("covered_bases",audit.coveredBases()); result.put("changed_characters",audit.changedCharacters());
                            result.put("coverage","audit-coverage.tsv"); writeCoverage(directory,audit,capacity,check);
                            warnings.add("SUPPLIED MASK AUDIT ONLY: all queries and bases match the explicitly selected native mask contract. This does not certify who produced the supplied files,an original search or biological absence.");
                        }
                        case CHECK -> warnings.add("INPUT CHECK ONLY: complete structure and declared computational joins were checked;no mask/search result is produced or certified.");
                        case SEARCH -> throw new AssertionError("Original search cannot reach native execution");
                    }
                    prepared.verify(check); options.verify(); requested.verify();
                    RepeatBundle.write(directory,"results.json",RepeatBundle.json(result),capacity,RepeatBundle.FAILURE_RESERVE,5,check);
                    session.put("analysis_status","COMPLETED_NATIVE");
                } catch (RepeatRecipe.UnavailableCapability unavailable) {
                    code = 3; session.put("analysis_status","SKIP3_UNAVAILABLE"); session.put("error",unavailable.toString());
                    warnings.add(0,"SKIP3 ANALYSIS SKIPPED: "+unavailable.getMessage());
                } catch (Exception failure) {
                    code = 2; session.put("analysis_status","FAIL2_NO_RESULT"); session.put("error",failure.toString());
                    warnings.add(0,"FAIL2 ANALYSIS FAILED: "+failure.getMessage());
                }
                if (requested != null && code == 0) { prepared.verify(check); options.verify(); requested.verify(); }
                session.put("finished_at",Instant.now().toString()); session.put("exit_code",code); session.put("warnings",warnings);
                // A cancellation must still allow retained final diagnostics after the native worker stopped.
                Runnable diagnostics = () -> { };
                RepeatBundle.write(directory,"WARNINGS.md",("# Repeat "+(code == 0?"NATIVE WARNINGS REQUIRE REVIEW":code == 3?"SKIP3 ANALYSIS SKIPPED":"FAIL2 ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n").getBytes(StandardCharsets.UTF_8),capacity,RepeatBundle.FAILURE_RESERVE,4,diagnostics);
                RepeatBundle.write(directory,"session.json",RepeatBundle.json(session),capacity,RepeatBundle.FAILURE_RESERVE,3,diagnostics);
                var status = new LinkedHashMap<String,Object>(); status.put("status",session.get("analysis_status")); status.put("exit_code",code); status.put("requires_completion_marker",true);
                if (code == 0) status.put("completion_flags",RepeatBundle.flags(requested.operation()));
                RepeatBundle.write(directory,"analysis-status.json",RepeatBundle.json(status),capacity,RepeatBundle.FAILURE_RESERVE,2,diagnostics);
                if (code == 0) {
                    prepared.verify(check); options.verify(); requested.verify();
                    RepeatBundle.complete(directory,requested.operation(),capacity,check);
                }
                emit(err,(code == 0?"NATIVE WARNINGS: ":code == 3?"SKIP3 ANALYSIS SKIPPED: ":"FAIL2 ANALYSIS FAILED: ")+directory.resolve("WARNINGS.md"));
                emit(out,"Repeat "+session.get("analysis_status")+": "+directory); return code;
            }
        } catch (Exception failure) {
            emit(err,"Repeat FAIL2: "+failure+"\n"+RECOVERY);
            if (owned && directory != null && Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)) {
                byte[] diagnostic = ("# Repeat FAIL2 incomplete bundle\n\n"+shortMessage(failure)+"\n\n"+RECOVERY+"\n").getBytes(StandardCharsets.UTF_8);
                try {
                    String name = Files.exists(directory.resolve("WARNINGS.md"),LinkOption.NOFOLLOW_LINKS)?"FINALIZATION_FAILED.md":"WARNINGS.md";
                    RepeatBundle.write(directory,name,diagnostic,capacity,0,0,()->{});
                }
                catch (IOException retained) { emit(err,"Diagnostic could not be written: "+retained+". Retain the incomplete directory;do not admit a completion marker."); }
            }
            return 2;
        }
    }
    private static void writeRequests(Path root,Options options,byte[] recipeRaw,RepeatBundle.Limits capacity,Runnable check) throws IOException {
        RepeatBundle.write(root,"recipe-request.json",recipeRaw,capacity,RepeatBundle.FAILURE_RESERVE,8,check);
        RepeatBundle.write(root,"cli-request.json",RepeatBundle.json(options.values()),capacity,RepeatBundle.FAILURE_RESERVE,7,check);
        if (options.source() != null) RepeatBundle.write(root,"cli-options-source.json",options.sourceBytes(),capacity,RepeatBundle.FAILURE_RESERVE,6,check);
    }
    private static RepeatRecipe.Spec component(RepeatRecipe.Spec source,Path root,RepeatBundle.Limits capacity,Runnable check) throws IOException {
        var used = RepeatBundle.inventory(root,capacity,false,check); var old = source.limits();
        long bytes = capacity.bytes()-used.bytes()-RepeatBundle.FINALIZATION_RESERVE; int members = capacity.members()-used.members()-RepeatBundle.FINALIZATION_MEMBERS;
        if (bytes < 65536 || members < 8) throw new IOException("Declared bundle capacity cannot cover protected components plus256KiB/eight reserved finalization members;increase reviewed workspace_bytes/file_count and retry to a NEW output");
        var limits = new RepeatRecipe.Limits(old.fasta(),old.annotations(),old.inputBytes(),old.indexBytes(),old.retainedBytes(),bytes,members);
        return new RepeatRecipe.Spec(source.recipe(),source.recipeSha256(),source.resourceRoot(),source.operation(),source.mode(),source.profile(),source.assembly(),source.review(),source.query(),source.library(),source.annotations(),source.masked(),source.species(),source.staging(),source.mask(),source.selection(),source.parameters(),limits,source.process());
    }
    private static void writeCoverage(Path root,RepeatMaskAudit.Report audit,RepeatBundle.Limits capacity,Runnable check) throws IOException {
        // Compute exact UTF-8 bytes before writing;no prefix is accepted as a coverage result.
        long bytes = "query\tlength\tcovered_bases\tchanged_characters\n".getBytes(StandardCharsets.UTF_8).length;
        for (var sequence : audit.sequences()) { check.run(); bytes = Math.addExact(bytes,coverage(sequence).getBytes(StandardCharsets.UTF_8).length); }
        var used = RepeatBundle.inventory(root,capacity,false,check);
        if (Math.addExact(used.bytes(),Math.addExact(bytes,RepeatBundle.FINALIZATION_RESERVE)) > capacity.bytes() || used.members()+1+RepeatBundle.FINALIZATION_MEMBERS > capacity.members())
            throw new IOException("Complete audit coverage table exceeds bundle capacity;no truncated table admitted");
        try (var writer = Files.newBufferedWriter(root.resolve("audit-coverage.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            writer.write("query\tlength\tcovered_bases\tchanged_characters\n");
            for (var sequence : audit.sequences()) { check.run(); writer.write(coverage(sequence)); }
        }
    }
    private static String coverage(RepeatMaskAudit.Sequence sequence) { return sequence.query()+"\t"+sequence.length()+"\t"+sequence.coveredBases()+"\t"+sequence.changedCharacters()+"\n"; }
    private static Options options(String[] args) throws IOException {
        var values = new LinkedHashMap<String,String>(); Path source = null; byte[] raw = new byte[0];
        if (args.length == 2 && args[0].equals("--options-file")) {
            source = Path.of(args[1]).toAbsolutePath().normalize(); raw = AdmixtureInputs.raw(source,1048576); Map<?,?> document = RepeatBundle.object(raw);
            if (!AdmixtureInputs.digest(raw).equals(RepeatInputs.hash(source,1048576,()->{}))) throw new IOException("Options file changed while parsing");
            if (!document.keySet().equals(Set.of("recipe","out"))) throw new IOException("Options file requires exactly string recipe/out fields;no recursive or arbitrary flags");
            for (var entry : document.entrySet()) { if (!(entry.getValue() instanceof String text) || text.isBlank()) throw new IOException("Options values must be nonblank strings"); values.put(entry.getKey().toString(),text); }
        } else {
            for (int i=0;i<args.length;i+=2) {
                if (i+1 >= args.length || !Set.of("--recipe","--out").contains(args[i]) || args[i+1].isBlank() || values.putIfAbsent(args[i].substring(2),args[i+1]) != null)
                    throw new IOException("Require unique --recipe FILE --out NEW_DIRECTORY,or --options-file FILE");
            }
            if (!values.keySet().equals(Set.of("recipe","out"))) throw new IOException("Require --recipe and --out");
        }
        if (RepeatBundle.json(values).length > 1048576) throw new IOException("Repeat CLI options exceed complete one-MiB capacity");
        return new Options(Path.of(values.get("recipe")).toAbsolutePath().normalize(),Path.of(values.get("out")).toAbsolutePath().normalize(),source,raw,values);
    }
    private static String shortMessage(Exception failure) { String text = failure.toString(); return text.length() > 8192?text.substring(0,8192):text; }
    private static void emit(PrintStream stream,String text) { stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8)); }
    private static final class Guard implements AutoCloseable {
        private final AtomicBoolean stopping = new AtomicBoolean(); private final Thread hook;
        Guard(Path root) {
            hook = new Thread(() -> {
                stopping.set(true);
                try { Files.writeString(root.resolve("INTERRUPTED.md"),"# Repeat FAIL2 interrupted native analysis\n\nNo interrupted result is admitted. Retain partials and run repeat-tools --verify before using any purported completion. Correct the cause and rerun to a NEW directory.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
                catch (IOException failure) { emit(System.err,"Repeat interruption diagnostics failed: "+failure+". Retain partials and verify completion before use."); }
            },"repeat-native-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
        }
        void check() { if (stopping.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("Repeat native analysis interrupted"); }
        @Override public void close() { try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException shuttingDown) { /* shutdown diagnostics remain authoritative */ } }
    }
    static String help() { return """
        repeat-tools --recipe TYPED_RECIPE.json --out NEW_DIRECTORY
        repeat-tools --options-file UTF8_OPTIONS.json
        repeat-tools --verify COMPLETED_DIRECTORY
        repeat-tools --verify-options-file UTF8_VERIFY_OPTIONS.json
        repeat-tools --export-runtime-setup NEW_DIRECTORY
        Native CHECK,MASK,AUDIT from explicit OUT/UCSC annotations and plain/gzip
        FASTA. Native operations need Java17+ and no upstream installation.
        Options JSON contains exactly recipe/out string paths;paths resolve from
        the working directory. Recipe data paths resolve inside resource_root.
        Verification options contain exactly one directory string;use this
        UTF-8 file when a launcher cannot pass a Unicode --verify path intact.
        CHECK is structural only. MASK writes native-mask/native-masked.fa,
        native-coverage.tsv and native provenance. AUDIT compares the entire
        supplied mask and writes audit-coverage.tsv. No original search,
        annotation completeness or biological absence is certified.
        Require COMPLETED.json,matching manifest hashes and --verify success.
        SHA256 checks integrity,not authenticity. Read WARNINGS.md on every run.
        Complete workspace/member budgets include root metadata and components;
        reserve256KiB/eight members for finalization and retained diagnostics.
        Original SEARCH remains unavailable in this candidate: SKIP3.
        Setup export writes pinned private-payload helpers and recovery guidance;
        it performs no installation/download and does not admit original SEARCH.
        Invalid data,integrity,execution,capacity or interruption: FAIL2.
        Existing outputs are never overwritten;retain failures and retry to NEW paths.
        """+RECOVERY; }
}
