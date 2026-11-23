from pathlib import Path
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';folder=T/'src/main/java/org/jlinalg/cli'
audit=(folder/'SmartpcaWorkflowAudit.java').read_text()
for old,new in [('SmartpcaWorkflowAudit','SmartpcaFstAudit'),('SmartpcaToolsCli','SmartpcaFstCli'),('SmartpcaWorkflowEvaluation.Evaluated','SmartpcaFstEvaluation.Evaluated'),('SmartpcaWorkflowEvaluation.evaluate','SmartpcaFstEvaluation.evaluate'),('SmartpcaWorkflowEvaluation.class','SmartpcaFstEvaluation.class'),('SmartpcaWorkflowTables.class','SmartpcaFstTables.class'),('SmartpcaWorkflowContracts.PROFILE','SmartpcaFstContracts.PROFILE'),('SmartpcaWorkflowContracts.SCOPE','SmartpcaFstContracts.SCOPE'),('SmartpcaWorkflowContracts.ACTION','SmartpcaFstContracts.ACTION'),('SmartpcaWorkflowContracts.validate','SmartpcaFstContracts.validate')]:audit=audit.replace(old,new)
audit=audit.replace('SmartpcaFstTables.class))','SmartpcaFstTables.class,org.jlinalg.genetics.SmartpcaFst.class))').replace('omicsmatic-smartpca-native-','omicsmatic-smartpca-fst-')
def section(text,start,end,body):
 assert text.count(start)==1 and text.count(end)==1,(start,end)
 a=text.index(start);b=text.index(end,a);return text[:a]+body+'\n'+text[b:]
audit=section(audit,'    static void checkOutputs(','    static void namespace(','''    static void checkOutputs(Path root,SmartpcaFstEvaluation.Evaluated value)throws IOException {SmartpcaFstTables.verifyOutputs(root,value);}
''')
audit=section(audit,'    static void namespace(','    static void outputBytes(','''    static void namespace(Path root,Map<String,Object> manifest,Map<String,Object> result)throws IOException {
        var expected=new HashSet<>(List.of("source-request.json","canonical-request.json","request-binding.json","PROVISIONAL.json","producer.json","input-binding.json","compute-process.json","input-summary.json","native-result.json","WARNINGS.md","analysis-status.json","source/SOURCE.json","source/LICENSE.txt","source/original-source.tar.gz","locus-contributions.tsv"));
        expected.addAll(SmartpcaFstTables.TABLES);for(String name:SmartpcaWorkflowData.NAMES.values())expected.add("inputs/"+name);
        if(Files.exists(root.resolve("replay.json"),LinkOption.NOFOLLOW_LINKS)){expected.addAll(PARENT_FILES.values());expected.addAll(Set.of("replay.json","replay-comparison.json"));}
        require(object(manifest.get("files")).keySet().equals(expected),"Unexpected/absent admitted FST files");
        var directories=list(manifest.get("directories"));require(directories.size()==2&&new HashSet<>(directories).equals(Set.of("inputs","source")),"Unexpected/absent FST capture directories");
    }
''')
audit=section(audit,'    static Map<String,Object> status(','    static String warnings(','''    static Map<String,Object> status(Map<String,Object> result)throws IOException {
        boolean admitted=Boolean.TRUE.equals(result.get("results_admitted"));long invalid=number(result,"unreportable_estimate_rows");
        return map("schema_version",1,"profile",SmartpcaFstContracts.PROFILE,"status",!admitted?"FAIL2_NO_COMPLETE_RESULT":invalid>0?"COMPLETED_WITH_UNREPORTABLE_FIELDS":"COMPLETED_WITH_SCOPE_WARNINGS","results_admitted",admitted,"unreportable_estimate_rows",invalid,"original_C_executed",false);
    }
''')
start=audit.index('    static String warnings(')
audit=audit[:start]+'''    static String warnings(Map<String,Object> result)throws IOException {
        return "# Warnings and required actions\\n\\n"+SmartpcaFstContracts.SCOPE+"\\n\\n"+SmartpcaFstContracts.ACTION+
            "\\n\\nOriginal paper parameters and historical editions are not recovered. The source package edition7.2.1 and program16000 differ.\\n"+
            "\\nGenetic-position convention applied by the source: "+result.get("position_convention")+". Read markers.tsv for the effective Morgans and every original position token. Genome build is a literal declaration, not inferred.\\n"+
            "\\nThe fstonly source path bypasses PCA normalization, minor-allele-count and maxmissing filters. Nonautosomal markers and markers with at most one observed selected sample are explicitly excluded; raw inputs remain captured.\\n"+
            "\\nBlock weights count all source-retained markers, including loci unavailable for a particular pair. The source1e-10 denominator term is retained; it does not make zero-denominator estimates identifiable.\\n"+
            "\\nNegative finite FST estimates are retained without clamping: "+result.get("negative_FST_estimates_retained")+". Zero source standard errors: "+result.get("zero_source_standard_errors")+". This profile provides no P values or Z-score inference.\\n"+
            "\\nUnreportable estimate rows: "+result.get("unreportable_estimate_rows")+". One-block diploid source zeros remain ineligible; one-block inbreeding follows the original refusal. Zero/nonpositive total denominator and nonfinite fields remain ineligible.\\n"+
            "\\nExplicit diagnostic retention policy: "+result.get("allow_unreportable_fields")+"; results admitted under that policy: "+result.get("results_admitted")+". Read every source_status and fields_admitted flag; retention never validates undefined inference.\\n";
    }
}
'''
assert not (folder/'SmartpcaFstAudit.java').exists();(folder/'SmartpcaFstAudit.java').write_text(audit,encoding='utf-8')
cli=(folder/'SmartpcaToolsCli.java').read_text()
for old,new in [('SmartpcaToolsCli','SmartpcaFstCli'),('SmartpcaWorkflowAudit','SmartpcaFstAudit'),('SmartpcaWorkflowEvaluation.evaluate','SmartpcaFstEvaluation.evaluate'),('SmartpcaWorkflowTables.writeOutputs','SmartpcaFstTables.writeOutputs'),('SmartpcaWorkflowContracts.validate','SmartpcaFstContracts.validate'),('SmartpcaWorkflowContracts.PROFILE','SmartpcaFstContracts.PROFILE'),('SmartpcaWorkflowContracts.SCOPE','SmartpcaFstContracts.SCOPE'),('SmartpcaWorkflowContracts.ACTION','SmartpcaFstContracts.ACTION')]:cli=cli.replace(old,new)
cli=cli.replace('for(String name:Set.of("inputs","passes","coordinates"))Files.createDirectory(root.resolve(name));','Files.createDirectory(root.resolve("inputs"));')
cli=cli.replace('SmartpcaFstAudit.checkOutputs(parent,evaluated);','SmartpcaFstTables.compareTables(parent,root);')
cli=cli.replace('Native outlier diagnostics are unreportable under the declared policy','Native source FST/inbreeding estimates are unreportable under the declared policy')
cli=cli.replace('Prepared EIGENSTRAT PCA:','Prepared EIGENSTRAT FST:')
start=cli.index('    private static String help()')
cli=cli[:start]+'''    private static String help(){return "smartpca FST --request JSON | --verify CAPTURE | --help\\nRUN schema1,engine=native,profile="+SmartpcaFstContracts.PROFILE+",operation=FST,out,inputs{genotypes,markers,samples,populations:[literal names]|null,genome_build}. Source options: inbreed=false,blgsize=.05. PCA normalization/allele-count settings are not FST options. Original-source blocks, weighted jackknife, negative/raw estimates and per-row admission are preserved. VERIFY fully recomputes; REPLAY verifies the parent and recalculates into a disjoint NEW output. No external EIGENSOFT runtime is required.\\n"+SmartpcaFstContracts.SCOPE+"\\n"+SmartpcaFstContracts.ACTION;}
}
'''
assert not (folder/'SmartpcaFstCli.java').exists();(folder/'SmartpcaFstCli.java').write_text(cli,encoding='utf-8')
print('Created FST controller/audit from the qualified owned native workflow without modifying its frozen source.')
