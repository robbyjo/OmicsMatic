from pathlib import Path
import hashlib,json,shutil
R=Path('D:/projects/JLinAlg');T=R/'build/mr-3';B=T/'build/twosamplemr-clump-1';D=T/'src/main/resources/org/jlinalg/omics/twosamplemr-clump';D.mkdir();I=R/'build/twosamplemr-clump-intake-20261121-1/ieugwasr'
sources={'ieugwasr-ld_clump.R':I/'R/ld_clump.R','IEUGWASR-DESCRIPTION':I/'DESCRIPTION','IEUGWASR-LICENSE':I/'LICENSE','IEUGWASR-LICENSE.md':I/'LICENSE.md','PLINK-LICENSE.txt':R/'build/omics-plink-official/LICENSE'}
pins={}
for name,path in sources.items():
    raw=path.read_bytes();(D/name).write_bytes(raw);pins[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
worker=(B/'common.R').read_text()+'\n'+(R/'build/twosamplemr-clump-worker-body-20261121.R').read_text();(D/'worker.R').write_text(worker,encoding='utf-8',newline='\n')
profile=dict(profile='TwoSampleMR0.7.14-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64',files=pins,TwoSampleMR_version='0.7.14',TwoSampleMR_commit='c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6',ieugwasr_version='1.2.1',ieugwasr_commit='ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6',plink_sha256='efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b',plink_version='PLINK v1.9.0 64-bit (27 Sep 2026)',TwoSampleMR_source_functions_guarded=152,ieugwasr_source_functions_guarded=7,observed_ieugwasr_bindings=['ld_clump','ld_clump_local'],observation='Original body is an exact nested statement AST with inverse/formals/environment guards; original cleanup executes after artifact copies. Bindings restored after every call.',global_CLI_registered=False,new_catalog_credit=0,whole_goal_complete=False)
(D/'SOURCE-PROFILE.json').write_text(json.dumps(profile,indent=2)+'\n',encoding='utf-8',newline='\n')
cli=T/'src/main/java/org/jlinalg/cli'
s=(cli/'TwoSampleMrWorkflowCli.java').read_text().replace('TwoSampleMrWorkflow','TwoSampleMrClump').replace('original-twosamplemr-prepared-workflows','original-twosamplemr-local-clumping')
start=s.index('    static final String SCOPE=');end=s.index('    public static void main',start)
s=s[:start]+'''    static final String SCOPE="DEVELOPMENT_SCOPE: original TwoSampleMR0.7.14 c08ab8a6 / ieugwasr1.2.1 ce5d0c07 / official PLINK1.9.0 20260927 on WindowsR4.6.1. Literal instrument rows and complete SNP-major BED/BIM/FAM are retained. Original grouping, singleton bypasses, source defaults, duplicates, P-value formatting and parsed-index coercions are preserved. Two ieugwasr functions receive observation prefixes with exact original-statement AST inverses; original cleanup follows artifact copies and bindings are restored. VERIFY checks integrity, literal intake/reference structure and source input/report/selection projections, not an independent refit of every LD likelihood or authentication of coordinated replacement. Original PLINK thread/memory defaults are retained. Native compatible clumping remains separate. Historical editions/global registration/catalog admission remain open.";
    static final String ACTION="Read WARNINGS.md, analysis-status.json, worker/report.json, decisions.tsv and every original PLINK/R log. A singleton may bypass reference/significance checks; a complete empty selection has no downstream MR estimate. No silent SNP/allele/population, p-value, threshold, duplicate, version or estimator repair. FAIL2: NO COMPLETE RESULT; retain inputs, full available original returns/errors, raw reports, conditions and partials, correct the named data/design, numeric, integrity, capacity or process cause and retry NEW output. SKIP3: dependent analysis skipped; supply the named exact permitted runtime/input or retain the skip. Downloads/private installation are allowed where permitted; if prohibited, explain the specific restriction and skip the affected analysis. "+SCOPE;
'''+s[end:]
s=s.replace('RUN: HARMONISE, MR, HARMONISE_AND_MR or MV_HARMONISE on literal canonical package tables.','RUN: original local clump_data on literal SNP rows and explicit BED/BIM/FAM.')
s=s.replace('selected native-statistic checks','source-report/selection checks').replace('exact requested TwoSampleMR/R profile','exact requested original-clumping profile').replace('Completed original TwoSampleMR analysis','Completed original local clumping')
s=s.replace('Set.of("schema_version","mode","analysis","out","rscript","r_libraries")','Set.of("schema_version","mode","analysis","out","rscript","r_libraries","plink_executable","plink_sha256")')
s=s.replace('request.put("out",destination.toString());','''if(raw.containsKey("plink_executable"))request.put("plink_executable",resolve(source.getParent(),text(raw,"plink_executable")).toString());else request.put("plink_executable",text(binding,"plink_executable"));
                if(raw.containsKey("plink_sha256"))request.put("plink_sha256",text(raw,"plink_sha256"));
                request.put("out",destination.toString());''')
s=s.replace('root.resolve("inputs/"+key)','root.resolve(TwoSampleMrClumpContracts.captured(key))').replace('pins.put("inputs/"+key,','pins.put(TwoSampleMrClumpContracts.captured(key),').replace('captured.put(key,"inputs/"+key)','captured.put(key,TwoSampleMrClumpContracts.captured(key))').replace('normalized.put(key,"inputs/"+key)','normalized.put(key,TwoSampleMrClumpContracts.captured(key))').replace('String name="inputs/"+key;','String name=TwoSampleMrClumpContracts.captured(key);')
s=s.replace('TwoSampleMrClumpRuntime.sources(root,false);TwoSampleMrClumpFiles.prepare(root,effective,false);','TwoSampleMrClumpRuntime.sources(root,false);TwoSampleMrClumpFiles.prepare(root,effective,false);')
(cli/'TwoSampleMrClumpCli.java').write_text(s,encoding='utf-8',newline='\n')
s=(cli/'TwoSampleMrWorkflowRuntime.java').read_text().replace('TwoSampleMrWorkflow','TwoSampleMrClump').replace('twosamplemr-workflow','twosamplemr-clump').replace('twosamplemr_workflow.R','twosamplemr_clump.R').replace('TwoSampleMR','local clumping')
start=s.index('    static void sources(');end=s.index('    private static void source(',start)
s=s[:start]+'''    static void sources(Path root,boolean compare)throws IOException {
        TwoSampleMrWorkflowRuntime.sources(root,compare);
        var profile=object(SimpleJson.parse(new String(resource("twosamplemr-clump/SOURCE-PROFILE.json"),StandardCharsets.UTF_8)),"clumping profile");
        if(!TwoSampleMrClumpContracts.PROFILE.equals(profile.get("profile")))throw new IOException("Packaged original-clumping profile differs");
        for(var entry:object(profile.get("files"),"source files").entrySet()) {
            String name=entry.getKey();if(!name.matches("[A-Za-z0-9_.-]+"))throw new IOException("Unsafe clumping source path");
            byte[] bytes=resource("twosamplemr-clump/"+name);var wanted=object(entry.getValue(),"source pin");
            if(!sha(bytes).equals(wanted.get("sha256"))||((Number)wanted.get("bytes")).longValue()!=bytes.length)throw new IOException("Packaged source bytes differ: "+name);
            source(root.resolve("source/clump-reference/"+name),bytes,compare);
        }
        source(root.resolve("source/clump-reference/SOURCE-PROFILE.json"),resource("twosamplemr-clump/SOURCE-PROFILE.json"),compare);
        source(root.resolve("source/twosamplemr_clump.R"),resource("twosamplemr-clump/worker.R"),compare);
    }
'''+s[end:]
s=s.replace('Map<String,Long> baseline=footprint(root);','''Path plink=resolve(requestDirectory,text(r,"plink_executable"));
        if(!Files.isRegularFile(plink,LinkOption.NOFOLLOW_LINKS)||!Files.isExecutable(plink))throw new Unavailable("Declared exact PLINK1.9 executable is missing or execution is prohibited: "+plink);
        String declared=text(r,"plink_sha256");if(!FollowupSupport.hash(plink).equals(declared))throw new IOException("Declared PLINK executable SHA-256 differs; no binary/version substitution");
        if(!declared.equals(TwoSampleMrClumpContracts.PLINK_SHA256))throw new Unavailable("The supplied PLINK edition is not qualified for this original profile; supply the named exact binary or qualify a separate profile");
        if(root.toString().indexOf(' ')>=0)throw new IOException("Windows R4.6.1 rejects a space in R_TempDir; choose NEW space-free capture output for its owned temporary directory. Source input and runtime paths are not renamed.");
        Files.createDirectory(worker.resolve("owned-tmp"));
        json(worker.resolve("plink-binding.json"),Map.of("executable",plink.toString(),"sha256",declared));
        json(worker.resolve("runtime-bindings.json"),Map.of("rscript",rscript.toString(),"libraries",libraries,"request_directory",requestDirectory.toString(),"profile",text(r,"profile"),"plink_executable",plink.toString(),"plink_sha256",declared));
        manifest.put("plink_executable_sha256",declared);manifest.put("plink_resource_policy","Original source defaults: no extra --threads or --memory flags");
        Map<String,Long> baseline=monitorFootprint(root);''')
s=s.replace('manifest.put("command",command);','''builder.environment().putAll(Map.of("TMPDIR",worker.resolve("owned-tmp").toString(),"TEMP",worker.resolve("owned-tmp").toString(),"TMP",worker.resolve("owned-tmp").toString()));
        manifest.put("command",command);''')
s=s.replace('checkOutputFootprint(root,baseline,outputCap,manifest);','checkOutputFootprint(root,baseline,outputCap,manifest);')
s=s.replace('current=footprint(root);','current=monitorFootprint(root);')
s=s.replace('    static Map<String,Long> footprint(Path root)throws IOException {','''    static Map<String,Long> monitorFootprint(Path root)throws IOException {return footprint(root,true);}
    static Map<String,Long> footprint(Path root)throws IOException {return footprint(root,false);}
    private static Map<String,Long> footprint(Path root,boolean monitoring)throws IOException {''')
s=s.replace('        Map<String,Long> result=new TreeMap<>();Path actualRoot=root.toRealPath();int[] directories={0};','''        Map<String,Long> result=new TreeMap<>();Path actualRoot=root.toRealPath();int[] directories={0};
        Path volatileRoot=root.resolve("worker/owned-tmp").toAbsolutePath().normalize();''')
s=s.replace('regularPath(path);if(++directories[0]>4096)', 'try{regularPath(path);}catch(NoSuchFileException missing){if(monitoring&&path.toAbsolutePath().normalize().startsWith(volatileRoot))return FileVisitResult.SKIP_SUBTREE;throw missing;}if(++directories[0]>4096)')
s=s.replace('regularPath(path);if(!attributes.isRegularFile())','try{regularPath(path);}catch(NoSuchFileException missing){if(monitoring&&path.toAbsolutePath().normalize().startsWith(volatileRoot))return FileVisitResult.CONTINUE;throw missing;}if(!attributes.isRegularFile())')
s=s.replace('try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){result.put(root.relativize(path).toString().replace(\'\\\\\',\'/\'),channel.size());}', 'try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){result.put(root.relativize(path).toString().replace(\'\\\\\',\'/\'),channel.size());}catch(NoSuchFileException missing){if(!monitoring||!path.toAbsolutePath().normalize().startsWith(volatileRoot))throw missing;}')
s=s.replace('            @Override public FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attributes)', '''            @Override public FileVisitResult visitFileFailed(Path path,IOException failure)throws IOException {
                if(monitoring&&failure instanceof NoSuchFileException&&path.toAbsolutePath().normalize().startsWith(volatileRoot)&&!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return FileVisitResult.CONTINUE;
                throw failure;
            }
            @Override public FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attributes)''')
s=s.replace('        if(!FollowupSupport.hash(rscript).equals(manifest.get("rscript_sha256")))','        if(!FollowupSupport.hash(plink).equals(manifest.get("plink_executable_sha256")))throw new IOException("Declared PLINK changed during analysis");\n        if(!FollowupSupport.hash(rscript).equals(manifest.get("rscript_sha256")))')
(cli/'TwoSampleMrClumpRuntime.java').write_text(s,encoding='utf-8',newline='\n')
print('Prepared clumping resources and Java lifetime skeleton; earlier workflows unchanged')
