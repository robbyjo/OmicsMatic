from pathlib import Path
import json
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';C=T/'src/main/java/org/jlinalg/cli';O=B/'java-generation-1';O.mkdir()
for suffix in ['Cli','Files','Runtime','Audit']:
    source=C/('TwoSampleMrWorkflow'+suffix+'.java');s=source.read_text();(O/source.name).write_bytes(source.read_bytes());s=s.replace('TwoSampleMrWorkflow','TwoSampleMrHistorical').replace('twosamplemr_workflow.R','twosamplemr_historical.R')
    if suffix=='Cli':
        s=s.replace('original-twosamplemr-prepared-workflow','original-twosamplemr-historical-workflow')
        start=s.index('    static final String SCOPE=');end=s.index('    static final String ACTION=',start)
        s=s[:start]+'''    static final String SCOPE="DEVELOPMENT_SCOPE: exact tagged TwoSampleMR0.4.9/0.5.6/0.5.7/0.6.8 original namespace workflows on 64-bit WindowsR4.6.1 with declared present-day dependencies. FORMAT, harmonization, MR/sensitivity/modes and multivariable preparation retain full original returns/errors/RNG and all 146/149 function body/formal guards without rewriting. Source parameter defaults differ across editions and are preserved. The private0.4.9 reference build omits only broken man documentation; all R, namespace and data bytes remain original. VERIFY checks saved integrity, literal intake, source projections and deterministic native fields, not every provider estimate or coordinated replacement authentication. This does not reproduce the paper execution commit, dependency era or historical API reference data. Local/API clumping, historical R hosts, global registration and catalog admission are separate qualifications.";
'''+s[end:]
        s=s.replace('RUN: original MR, HARMONISE, HARMONISE_AND_MR or MV_HARMONISE','RUN: original FORMAT, MR, HARMONISE, HARMONISE_AND_MR or MV_HARMONISE')
    elif suffix=='Files':
        s=s.replace('    static void prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {','''    static void prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        if(text(r,"operation").equals("FORMAT")) {
            Table input=rawTable(root.resolve("inputs/data"),r);long work=Math.multiplyExact(input.rows().size(),input.columns().size());
            if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("Formatting maximum_work exceeded; no source row pruning");
            byte[] bytes=RsemJson.bytes(Map.of("data",Map.of("columns",input.columns(),"rows",input.rows().size(),"sha256",FollowupSupport.hash(root.resolve("inputs/data"))),"operation","FORMAT","source_transformation_policy",r.get("format_policy")));
            Path target=root.resolve("prepared.json");if(compare){if(!Arrays.equals(bytes,Files.readAllBytes(target)))throw new IOException("Literal formatter intake differs");}else Files.write(target,bytes,StandardOpenOption.CREATE_NEW);return;
        }''')
        s=s.replace('object(r.get("parameters"),"parameters").get("nboot")','object(r.get("effective_parameters"),"effective parameters").get("nboot")')
        at=s.rfind('}');s=s[:at]+'''    static Table rawTable(Path path,Map<String,Object> r)throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String header=reader.readLine();if(header==null)throw new IOException("Require raw formatter TSV header");var columns=List.of(header.split("\\t",-1));
            if(columns.size()>((Number)r.get("maximum_columns")).longValue()||new HashSet<>(columns).size()!=columns.size())throw new IOException("Raw formatter column capacity/duplicates");for(String name:columns)identity(name);
            var rows=new ArrayList<List<String>>();String line;while((line=reader.readLine())!=null) {
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Formatter intake interrupted");
                if(rows.size()>=((Number)r.get("maximum_rows")).longValue()||(long)(rows.size()+1)*columns.size()>((Number)r.get("maximum_entries")).longValue())throw new IOException("Formatter input capacity exceeded");
                var row=List.of(line.split("\\t",-1));if(row.size()!=columns.size())throw new IOException("Raw formatter TSV width differs");
                for(String value:row)if(value.length()>4094||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Raw formatter cell contains controls or exceeds capacity");rows.add(row);
            }return new Table(columns,List.copyOf(rows));
        }
    }
'''+s[at:]
    elif suffix=='Runtime':
        s=s.replace('"/org/jlinalg/omics/"+name','"/org/jlinalg/omics/twosamplemr-historical/"+name')
        start=s.index('    static void sources(');end=s.index('    private static void source(',start)
        s=s[:start]+'''    static void sources(Path root,boolean compare)throws IOException {
        var request=read(root.resolve("effective-request.json"));String id=text(request,"profile"),version=TwoSampleMrHistoricalContracts.VERSIONS.get(id);if(version==null)throw new IOException("Unknown historical source profile");
        byte[] registry=resource("PROFILES.json");var profiles=object(SimpleJson.parse(new String(registry,StandardCharsets.UTF_8)),"historical source profiles");
        if(!profiles.keySet().equals(TwoSampleMrHistoricalContracts.PROFILES))throw new IOException("Historical profile population differs");
        var info=object(profiles.get(id),"historical profile");if(!version.equals(info.get("version")))throw new IOException("Historical profile version differs");
        for(var entry:object(info.get("files"),"source resource pins").entrySet()) {
            String name=entry.getKey();if(!name.matches("[A-Za-z0-9_.-]+"))throw new IOException("Unsafe original profile resource");byte[] bytes=resource(version+"/"+name);var wanted=object(entry.getValue(),"resource pin");
            if(!sha(bytes).equals(wanted.get("sha256"))||((Number)wanted.get("bytes")).longValue()!=bytes.length)throw new IOException("Original historical resource differs: "+name);
            source(root.resolve("source/historical-reference/"+name),bytes,compare);
        }
        source(root.resolve("source/historical-profile.json"),resource(version+"/PROFILE.json"),compare);
        source(root.resolve("source/historical-profiles.json"),registry,compare);
        source(root.resolve("source/twosamplemr_historical.R"),resource("worker.R"),compare);
    }
'''+s[end:]
    elif suffix=='Audit':
        s=s.replace('var report=read(worker.resolve("report.json"));','var report=read(worker.resolve("report.json"),64000000);')
        s=s.replace('        if(!"completed-with-warnings"', '        var reference=TwoSampleMrHistoricalContracts.reference(text(r,"profile"));long guardCount=((Number)reference.get("source_functions")).longValue();\n        if(!"completed-with-warnings"',1)
        s=s.replace(',0,152,152)',',0,guardCount,guardCount)').replace('!"0.7.14".equals(runtime.get("version"))','!reference.get("version").equals(runtime.get("version"))').replace('!TwoSampleMrStatistics.SOURCE_COMMIT.equals(runtime.get("commit"))','!reference.get("commit").equals(runtime.get("commit"))').replace('calls.size()>6','calls.size()>8')
        s=s.replace('if(!op.equals("MR"))required.add(op.equals("MV_HARMONISE")?"mv_harmonise_data":"harmonise_data");','if(!op.equals("MR"))required.add(op.equals("FORMAT")?"format_data":op.equals("MV_HARMONISE")?"mv_harmonise_data":"harmonise_data");')
        s=s.replace('TwoSampleMrHistoricalRuntime.resource("twosamplemr-workflow/METHODS.tsv")','TwoSampleMrHistoricalRuntime.resource(reference.get("version")+"/METHODS.tsv")')
        s=s.replace('        if(op.equals("MV_HARMONISE")) {','''        if(op.equals("FORMAT")) {
            var formatted=loaded.get("formatted.tsv");if(formatted==null||!Boolean.TRUE.equals(report.get("preparation_complete"))||!Boolean.TRUE.equals(report.get("no_statistical_estimate_produced")))throw new IOException("Original formatting is incomplete");
            var population=object(report.get("original_population"),"formatter population");int inputRows=TwoSampleMrHistoricalFiles.rawTable(root.resolve("inputs/data"),r).rows().size();
            if(integer(population,"input_rows",-1,0,2000000)!=inputRows||integer(population,"formatted_rows",-1,0,2000000)!=formatted.rows().size())throw new IOException("Original formatter population differs");
        } else if(op.equals("MV_HARMONISE")) {''',1)
        old='''            if(!members.keySet().equals(Set.of("exposure_beta","exposure_pval","exposure_se","outcome_beta","outcome_pval","outcome_se","expname","outname")))throw new IOException("Original multivariable member population differs");
            var beta=loaded.get("mv-outcome_beta.tsv");var names=loaded.get("mv-expname.tsv");if(beta==null||names==null||beta.rows().isEmpty()||names.rows().size()<2)throw new IOException("No complete multivariable instruments/exposures");
            int n=beta.rows().size(),exposures=names.rows().size();List<String> identities=null;'''
        assert old in s;s=s.replace(old,'''            boolean legacy="0.4.9".equals(reference.get("version"));var expected=new HashSet<>(Set.of("exposure_beta","exposure_pval","exposure_se","outcome_beta","outcome_pval","outcome_se"));if(!legacy)expected.addAll(Set.of("expname","outname"));
            if(!members.keySet().equals(expected))throw new IOException("Original edition-specific multivariable member population differs");
            var beta=loaded.get("mv-outcome_beta.tsv");var names=loaded.get("mv-expname.tsv");var exposureBeta=loaded.get("mv-exposure_beta.tsv");
            if(beta==null||exposureBeta==null||beta.rows().isEmpty()||!legacy&&(names==null||names.rows().size()<2))throw new IOException("No complete multivariable instruments/exposures");
            int n=beta.rows().size(),exposures=legacy?exposureBeta.columns().size():names.rows().size();if(exposures<2)throw new IOException("Ambiguous or insufficient original exposure columns");List<String> identities=null;
            if(legacy){var input=TwoSampleMrHistoricalFiles.table(root.resolve("inputs/exposure"),r);var labels=new TreeSet<String>();for(var row:input.rows())labels.add(input.cell(row,"exposure"));if(!new ArrayList<>(labels).equals(exposureBeta.columns()))throw new IOException("Original0.4.9 phenotype-label matrix columns differ; no replacement with modern IDs");}''')
        s=s.replace('        if(TwoSampleMrHistoricalContracts.mr(op))required.add("mr");','        if(TwoSampleMrHistoricalContracts.mr(op))required.add("mr");')
        needle='''        if(!labels.containsAll(required))throw new IOException("Original operation calls missing");'''
        s=s.replace(needle,needle+'''\n        if(TwoSampleMrHistoricalContracts.mr(op)&&(!labels.containsAll(Set.of("default_parameters","mr_method_list"))||!same(read(worker.resolve("effective-parameters.json")),r.get("effective_parameters"))))throw new IOException("Original default/override parameter binding differs");''')
    path=C/('TwoSampleMrHistorical'+suffix+'.java');assert not path.exists();path.write_text(s,encoding='utf-8',newline='\n')
print('Generated historical CLI, files, runtime and audit; prior classes unchanged')
