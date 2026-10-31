/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FavorCsvJoin;
import org.jlinalg.genetics.FavorCsvReader;
import org.jlinalg.genetics.FavorVcfReader;

/** Protected JSON command entry for the original offline FAVOR annotation workflow. */
public final class FavorAnnotatorCli {
    private FavorAnnotatorCli() { }
    /**
     * Run the same entry used by the application's favor-annotate command.
     * @param args --request FILE --request-sha256 SHA256, or --help
     */
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        if(args.length==1&&args[0].equals("--help")){stdout.println(help());return 0;}
        try {
            Map<String,String> options=options(args);var request=parse(Path.of(options.get("request")),options.get("request-sha256"));
            if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch"))){stderr.println("FAVOR SKIPPED (exit 3): the pinned original runtime requires Linux x86_64. Required action: run Linux Java inside a permitted Linux x86_64 environment,including WSL on Windows,and provide the pinned runtime inventory and source checkout. No analysis was started.");return 3;}
            var result=FavorAnnotatorWorkflow.run(request,()->{if(Thread.currentThread().isInterrupted())throw new IllegalStateException("FAVOR interrupted;owned processes must stop and partial results remain incomplete");});
            if(result.exitCode()==0){stdout.println("FAVOR complete: "+result.session());stdout.println("Scalar values: "+result.values());stdout.println("Cell states: "+result.cellStates());stderr.println("FAVOR WARNING: the original full-table exporter is unsupported in this runtime. Use the audited scalar tables with cell states and canonical provenance. Required action: review "+result.warnings()+" and retain the entire bundle.");}
            else {stderr.println("FAVOR "+(result.exitCode()==3?"SKIPPED":"FAILED")+" (exit "+result.exitCode()+"): "+result.warnings());try(var reader=Files.newBufferedReader(result.warnings())){char[] buffer=new char[16384];int n=reader.read(buffer);if(n>0)stderr.print(new String(buffer,0,n));}stderr.println("\nRequired action: inspect the full WARNINGS.md and session.json,correct the named cause and rerun to a NEW output. Provisional outputs are not complete analyses.");}
            return result.exitCode();
        } catch(Exception failure){stderr.println("FAVOR FAILED (exit 2): "+failure.getMessage()+"\nRequired action: correct the named request,protected hash,input,capacity or output-path cause and rerun to a NEW output. Preserve existing outputs. Use favor-annotate --help for the supported request schema.");if(failure instanceof InterruptedException)Thread.currentThread().interrupt();return 2;}
    }
    static FavorAnnotatorWorkflow.Request parse(Path supplied,String expected)throws IOException {
        if(expected==null||!expected.matches("[a-f0-9]{64}"))throw new IOException("Require an explicit lowercase request SHA256");Path file=supplied.toAbsolutePath().normalize();
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!file.toRealPath().equals(file))throw new IOException("Require a canonical nonlinked regular FAVOR request file");byte[] raw=AdmixtureInputs.raw(file,1048576);if(!AdmixtureInputs.digest(raw).equals(expected))throw new IOException("Protected FAVOR request SHA256 differs");Map<?,?> m=AdmixtureInputs.decode(raw);
        Set<String> required=Set.of("schema_version","runtime","vcf","build","chromosome","mode","chromosome_policy","blank_records","reference","output");known(m,union(required,Set.of("limits")));if(!m.keySet().containsAll(required)||number(m,"schema_version",-1,1,1)!=1)throw new IOException("Unsupported or incomplete FAVOR request schema");
        try {
            Map<?,?> runtime=object(m.get("runtime"));exact(runtime,Set.of("inventory","source_root"));Path base=file.getParent();var rt=new FavorAnnotatorWorkflow.RuntimeInput(input(runtime.get("inventory"),base),path(runtime,"source_root",base));
            Map<?,?> reference=object(m.get("reference"));exact(reference,Set.of("id","build","split","shards"));if(!(reference.get("shards") instanceof List<?> array)||array.size()<2||array.size()>4096)throw new IOException("Require2..4096 ordered protected shard files");var shards=new ArrayList<FavorAnnotatorWorkflow.Input>();for(Object row:array)shards.add(input(row,base));var ref=new FavorAnnotatorWorkflow.Reference(text(reference,"id"),text(reference,"build"),input(reference.get("split"),base),shards);
            return new FavorAnnotatorWorkflow.Request(rt,input(m.get("vcf"),base),text(m,"build"),(int)number(m,"chromosome",-1,1,22),FavorVcfReader.Mode.valueOf(text(m,"mode")),FavorVcfReader.Chromosomes.valueOf(text(m,"chromosome_policy")),FavorCsvReader.BlankRecords.valueOf(text(m,"blank_records")),ref,path(m,"output",base),limits(m.containsKey("limits")?object(m.get("limits")):Map.of()),new FavorAnnotatorWorkflow.Input(file,expected));
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid typed FAVOR request: "+invalid.getMessage(),invalid);}
    }
    private static FavorAnnotatorWorkflow.Limits limits(Map<?,?> m)throws IOException {
        known(m,Set.of("runtime","vcf","csv","join","gds","scalar","timeout_seconds","xsv_timeout_ms","workspace_bytes","upstream_rss_bytes","chunk_rows"));var d=FavorAnnotatorWorkflow.Limits.defaults();
        Map<?,?> r=group(m,"runtime",Set.of("metadata_bytes","entries","host_files","loaders","file_bytes","total_bytes"));var runtime=new FavorRuntimeFingerprint.Limits(i(r,"metadata_bytes",d.runtime().metadataBytes(),1,Integer.MAX_VALUE),i(r,"entries",d.runtime().entries(),1,1000000),i(r,"host_files",d.runtime().hostFiles(),1,10000),i(r,"loaders",d.runtime().loaders(),1,10000),n(r,"file_bytes",d.runtime().fileBytes(),1),n(r,"total_bytes",d.runtime().totalBytes(),1));
        Map<?,?> v=group(m,"vcf",Set.of("max_bytes","max_decoded_bytes","max_line_bytes","max_header_lines","max_header_bytes","max_samples","max_variants","max_calls"));var vcf=new FavorVcfReader.Limits(n(v,"max_bytes",d.vcf().maximumBytes(),1),n(v,"max_decoded_bytes",d.vcf().maximumDecodedBytes(),1),i(v,"max_line_bytes",d.vcf().maximumLineBytes(),1,Integer.MAX_VALUE),i(v,"max_header_lines",d.vcf().maximumHeaderLines(),1,Integer.MAX_VALUE),n(v,"max_header_bytes",d.vcf().maximumHeaderBytes(),1),i(v,"max_samples",d.vcf().maximumSamples(),0,1000000),n(v,"max_variants",d.vcf().maximumVariants(),1),n(v,"max_calls",d.vcf().maximumCalls(),0));
        Map<?,?> c=group(m,"csv",Set.of("max_bytes","max_rows","max_cells","max_columns","max_field_bytes","max_record_bytes"));var csv=new FavorCsvReader.Limits(n(c,"max_bytes",d.csv().maximumBytes(),1),n(c,"max_rows",d.csv().maximumRows(),0),n(c,"max_cells",d.csv().maximumCells(),1),i(c,"max_columns",d.csv().maximumColumns(),1,1024),i(c,"max_field_bytes",d.csv().maximumFieldBytes(),1,Integer.MAX_VALUE),i(c,"max_record_bytes",d.csv().maximumRecordBytes(),1,Integer.MAX_VALUE));
        Map<?,?> j=group(m,"join",Set.of("max_variants","max_database_rows","max_cells","max_joined_rows","max_operations"));var join=new FavorCsvJoin.Limits(i(j,"max_variants",d.join().maximumVariants(),1,Integer.MAX_VALUE),n(j,"max_database_rows",d.join().maximumDatabaseRows(),0),n(j,"max_cells",d.join().maximumCells(),1),i(j,"max_joined_rows",d.join().maximumJoinedRows(),0,Integer.MAX_VALUE),n(j,"max_operations",d.join().maximumOperations(),1));
        Map<?,?> g=group(m,"gds",Set.of("metadata_bytes","max_nodes","max_node_values","max_canonical_bytes"));var gds=new FavorGdsAudit.Limits(i(g,"metadata_bytes",d.gds().bytes(),1,Integer.MAX_VALUE),i(g,"max_nodes",d.gds().nodes(),1,Integer.MAX_VALUE),n(g,"max_node_values",d.gds().nodeValues(),1),n(g,"max_canonical_bytes",d.gds().canonicalBytes(),1));
        Map<?,?> a=group(m,"scalar",Set.of("metadata_bytes","max_columns","max_rows","max_cells","max_scalar_bytes","max_canonical_bytes","block_cells","buffer_bytes","max_output_bytes"));var scalar=new FavorAnnotationAudit.Limits(i(a,"metadata_bytes",d.scalar().metadataBytes(),1,Integer.MAX_VALUE),i(a,"max_columns",d.scalar().columns(),2,4096),i(a,"max_rows",d.scalar().rows(),1,Integer.MAX_VALUE),n(a,"max_cells",d.scalar().cells(),1),i(a,"max_scalar_bytes",d.scalar().scalarBytes(),1,16777216),n(a,"max_canonical_bytes",d.scalar().canonicalBytes(),1),i(a,"block_cells",d.scalar().blockCells(),1,Integer.MAX_VALUE),n(a,"buffer_bytes",d.scalar().bufferBytes(),1024),n(a,"max_output_bytes",d.scalar().outputBytes(),1));
        return new FavorAnnotatorWorkflow.Limits(runtime,vcf,csv,join,gds,scalar,i(m,"timeout_seconds",d.timeoutSeconds(),1,86400),number(m,"xsv_timeout_ms",d.xsvTimeoutMillis(),1,3600000),n(m,"workspace_bytes",d.workBytes(),1),n(m,"upstream_rss_bytes",d.upstreamRssBytes(),1),i(m,"chunk_rows",d.chunkRows(),1,1000000));
    }
    private static Map<String,String> options(String[] args)throws IOException {var out=new LinkedHashMap<String,String>();if(args.length!=4)throw new IOException("Require --request FILE --request-sha256 SHA256");for(int i=0;i<args.length;i+=2){String flag=args[i];if(!Set.of("--request","--request-sha256").contains(flag)||args[i+1].isEmpty()||out.putIfAbsent(flag.substring(2),args[i+1])!=null)throw new IOException("Unknown,missing or repeated FAVOR option: "+flag);}return out;}
    private static FavorAnnotatorWorkflow.Input input(Object value,Path base)throws IOException {Map<?,?> m=object(value);exact(m,Set.of("path","sha256"));return new FavorAnnotatorWorkflow.Input(path(m,"path",base),text(m,"sha256"));}
    private static Path path(Map<?,?> m,String key,Path base)throws IOException {String s=text(m,key);return base.resolve(s).toAbsolutePath().normalize();}
    private static String text(Map<?,?> m,String key)throws IOException {if(!(m.get(key) instanceof String s)||s.isEmpty()||s.length()>8192||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require bounded explicit FAVOR text: "+key);return s;}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require typed FAVOR JSON object");return map;}
    private static Map<?,?> group(Map<?,?> m,String key,Set<String> keys)throws IOException {Map<?,?> value=m.containsKey(key)?object(m.get(key)):Map.of();known(value,keys);return value;}
    private static void known(Map<?,?> m,Set<String> allowed)throws IOException {if(!allowed.containsAll(m.keySet()))throw new IOException("Unknown FAVOR request fields: "+m.keySet());}
    private static void exact(Map<?,?> m,Set<String> fields)throws IOException {if(!m.keySet().equals(fields))throw new IOException("Unknown or missing typed FAVOR request fields");}
    private static Set<String> union(Set<String> first,Set<String> second){var out=new java.util.HashSet<String>(first);out.addAll(second);return Set.copyOf(out);}
    private static int i(Map<?,?> m,String key,int fallback,int minimum,int maximum)throws IOException {return (int)number(m,key,fallback,minimum,maximum);}
    private static long n(Map<?,?> m,String key,long fallback,long minimum)throws IOException {return number(m,key,fallback,minimum,4503599627370496L);}
    private static long number(Map<?,?> m,String key,long fallback,long minimum,long maximum)throws IOException {if(!m.containsKey(key))return fallback;Object value=m.get(key);if(!(value instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()!=Math.rint(number.doubleValue())||number.doubleValue()<minimum||number.doubleValue()>maximum)throw new IOException("Invalid complete FAVOR integer capacity/field: "+key);return number.longValue();}
    static String help(){return """
        favor-annotate --request FILE --request-sha256 SHA256
        Original offline FAVOR CSV annotation,with fresh scientific runtime health,
        exact native join,GDS preservation,command-ledger and audited scalar export.

        JSON schema_version=1 requires runtime{inventory{path,sha256},source_root},
        vcf{path,sha256},build,chromosome(1..22),mode(VARIANTS_ONLY|DIPLOID_GT),
        chromosome_policy(EXACT|SOURCE_IGNORE_CHR),blank_records(ERROR|IGNORE),
        reference{id,build,split{path,sha256},shards:[{path,sha256},...]},output.
        All paths resolve relative to FILE. Supply2..4096 ordered chromosome shards.
        Optional limits groups: runtime,vcf,csv,join,gds,scalar;process controls:
        timeout_seconds,xsv_timeout_ms,workspace_bytes,upstream_rss_bytes,chunk_rows.
        Omitted limits use bounded defaults;effective capacities are retained in session.json.

        Requires permitted pinned Linux x86_64 R4.4.3/Java21/xsv software and
        unmodified FAVORannotator source. Windows: use Linux Java inside WSL.
        Work/output must be NEW,canonical ASCII without whitespace;input paths may
        contain Unicode. Each run first checks a fixed two-mode synthetic fixture.
        Input/resource builds must agree;no liftover or allele normalization occurs.
        Original full-table export is unsupported;use scalar values,cell states and
        canonical provenance. Keep the entire output and read WARNINGS.md.
        No analysis installs software or registers for services. Unavailable/forbidden
        prelaunch runtime: SKIP3. Invalid input,execution,capacity or post-launch
        integrity failure: FAIL2. Interrupted/failed outputs remain incomplete.
        """;}
}
