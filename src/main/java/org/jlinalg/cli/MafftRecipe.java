/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.MafftAddMap;
import org.jlinalg.sequence.MafftSequences;

/** Closed source-edition/algorithm choices; no arbitrary flags, commands or inferred alphabet. */
final class MafftRecipe {
    private MafftRecipe() { }
    enum Operation { DE_NOVO, ADD }
    enum Strategy { SOURCE_DEFAULT, AUTO, LOCAL_PAIR, GLOBAL_PAIR, GENAFPAIR, FFT_NS_1, FFT_NS_2, FFT_NS_I }
    enum InputGaps { FORBID, REMOVE, PRESERVE_ALIGNMENT }
    record Scoring(boolean sourceDefaults,Integer blosum,Double gapOpen,Double offset) { }
    record Parameters(int threads,Integer progressiveThreads,Integer iterationThreads,MafftSequences.OutputOrder outputOrder,boolean keepLength) { }
    record Capacities(long inputBytes,MafftSequences.Limits fasta,MafftAddMap.Limits map,RsemProcess.Limits process,
                      RsemHost.Limits host,long bundleBytes,int bundleFiles,int diagnosticWarnings) {
        Capacities {if(inputBytes<1||inputBytes>1099511627776L||bundleBytes<1048576||bundleBytes>1099511627776L||bundleFiles<64||bundleFiles>1000000||diagnosticWarnings<1||diagnosticWarnings>1000000)throw new IllegalArgumentException("Invalid complete MAFFT input/bundle capacities");}
        static Capacities defaults(){return new Capacities(268435456,MafftSequences.Limits.defaults(),MafftAddMap.Limits.defaults(),
            new RsemProcess.Limits(3600000,4294967296L,3600,1073741824,512,2147483648L,4096,64,256,2147483648L,50),
            MafftHost.defaults(),4294967296L,20000,10000);}
    }
    record Spec(Path source,IqtreeFiles.Identity identity,String profile,Operation operation,Path input,Path additions,
                MafftSequences.Alphabet alphabet,MafftSequences.Symbols symbols,MafftSequences.Headers headers,
                InputGaps inputGaps,InputGaps additionGaps,Strategy strategy,Integer maximumIterations,
                Scoring scoring,Parameters parameters,Capacities limits) {
        void verify()throws IOException{if(!identity.equals(IqtreeFiles.identity(source,2097152,()->{})))throw new IOException("MAFFT original recipe changed");}
        List<String> arguments(Path preparedInput,Path preparedAdditions)throws IOException {
            for(Path p:preparedAdditions==null?List.of(preparedInput):List.of(preparedInput,preparedAdditions))
                if(!p.isAbsolute()||!p.toRealPath().equals(p)||!p.toString().matches("/[A-Za-z0-9/._+-]+"))throw new IOException("MAFFT prepared paths must be canonical ASCII files");
            return declaredArguments(preparedInput.toString(),preparedAdditions==null?null:preparedAdditions.toString());
        }
        /** Reconstructs declared original Linux argument strings without accessing that host. */
        List<String> declaredArguments(String preparedInput,String preparedAdditions)throws IOException {
            for(String path:preparedAdditions==null?List.of(preparedInput):List.of(preparedInput,preparedAdditions)) {
                if(!path.matches("/(?:[A-Za-z0-9._+-]+/)*[A-Za-z0-9._+-]+"))throw new IOException("MAFFT recorded prepared paths must be absolute canonical ASCII paths");
                for(String part:path.substring(1).split("/"))if(part.equals(".")||part.equals(".."))throw new IOException("MAFFT recorded prepared path contains a relative segment");
            }
            var result=new ArrayList<>(List.of("--thread",Integer.toString(parameters.threads()),parameters.outputOrder()==MafftSequences.OutputOrder.INPUT?"--inputorder":"--reorder",alphabet==MafftSequences.Alphabet.NUCLEOTIDE?"--nuc":"--amino"));
            if(parameters.progressiveThreads()!=null)result.addAll(List.of("--threadtb",parameters.progressiveThreads().toString()));
            if(parameters.iterationThreads()!=null)result.addAll(List.of("--threadit",parameters.iterationThreads().toString()));
            if(symbols==MafftSequences.Symbols.ANY_PRINTABLE)result.add("--anysymbol");
            switch(strategy){
                case SOURCE_DEFAULT -> { }
                case AUTO -> result.add("--auto");
                case LOCAL_PAIR -> result.add("--localpair");
                case GLOBAL_PAIR -> result.add("--globalpair");
                case GENAFPAIR -> result.add("--genafpair");
                case FFT_NS_1 -> result.addAll(List.of("--retree","1","--maxiterate","0"));
                case FFT_NS_2 -> result.addAll(List.of("--retree","2","--maxiterate","0"));
                case FFT_NS_I -> result.addAll(List.of("--retree","2"));
            }
            if(maximumIterations!=null)result.addAll(List.of("--maxiterate",maximumIterations.toString()));
            if(!scoring.sourceDefaults()){
                if(scoring.blosum()!=null)result.addAll(List.of("--bl",scoring.blosum().toString()));
                result.addAll(List.of("--op",scoring.gapOpen().toString(),"--ep",scoring.offset().toString()));
            }
            if(operation==Operation.ADD){result.addAll(List.of("--add",Objects.requireNonNull(preparedAdditions)));if(parameters.keepLength())result.addAll(List.of("--keeplength","--mapout"));}
            result.add(preparedInput);return List.copyOf(result);
        }
    }
    static Spec read(Path supplied)throws IOException {
        Path source=supplied.toAbsolutePath().normalize();var identity=IqtreeFiles.identity(source,2097152,()->{});
        byte[] raw;try(var in=Files.newInputStream(source)){raw=in.readNBytes(2097153);}if(raw.length>2097152)throw new IOException("Complete MAFFT recipe byte capacity exceeded");
        Map<?,?> r=object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()));
        for(String capability:List.of("rna_structure","dash","custom_matrix","codon_translation","adjust_direction","batch","add_fragments","add_profile"))if(r.containsKey(capability))throw new MafftRuntime.Unavailable("This requested MAFFT capability has no implemented contract: "+capability+"; skip it rather than discarding the setting");
        keys(r,Set.of("schema_version","profile","operation","input","additions","alphabet","symbols","headers","input_gaps","addition_gaps","strategy","maximum_iterations","scoring","parameters","limits"));
        String profile=text(r,"profile");if(integer(r.get("schema_version"),1,Integer.MAX_VALUE)!=1||!MafftRuntime.profiles().contains(profile))throw new MafftRuntime.Unavailable("Exact MAFFT schema/edition unavailable; major-only v7 and other editions do not select a fallback");
        Operation operation=enumeration(Operation.class,text(r,"operation"));
        var alphabet=enumeration(MafftSequences.Alphabet.class,text(r,"alphabet"));var symbols=enumeration(MafftSequences.Symbols.class,text(r,"symbols"));var headers=enumeration(MafftSequences.Headers.class,text(r,"headers"));
        InputGaps gaps=enumeration(InputGaps.class,text(r,"input_gaps"));InputGaps addGaps=r.containsKey("addition_gaps")?enumeration(InputGaps.class,text(r,"addition_gaps")):null;
        Path input=resolve(source,text(r,"input")),added=r.containsKey("additions")?resolve(source,text(r,"additions")):null;
        if(operation==Operation.ADD&&(added==null||gaps!=InputGaps.PRESERVE_ALIGNMENT||addGaps==null||addGaps==InputGaps.PRESERVE_ALIGNMENT)
                ||operation==Operation.DE_NOVO&&(added!=null||addGaps!=null||gaps==InputGaps.PRESERVE_ALIGNMENT))throw new IOException("MAFFT operation, gap handling and added-file roles conflict");
        Strategy strategy=enumeration(Strategy.class,text(r,"strategy"));boolean iterative=Set.of(Strategy.LOCAL_PAIR,Strategy.GLOBAL_PAIR,Strategy.GENAFPAIR,Strategy.FFT_NS_I).contains(strategy);
        Integer maximum=r.containsKey("maximum_iterations")?integer(r.get("maximum_iterations"),0,1000):null;
        if(iterative!=(maximum!=null))throw new IOException("Pairwise/FFT iterative strategies require an explicit maximum_iterations; other strategies cannot silently override it");
        Map<?,?> s=object(r.get("scoring"));keys(s,Set.of("source_defaults","blosum","gap_open","offset"));boolean defaults=bool(s,"source_defaults");
        Scoring scoring;
        if(defaults){if(s.size()!=1)throw new IOException("Source-default scoring cannot discard explicit scoring parameters");scoring=new Scoring(true,null,null,null);}
        else{
            Integer matrix=s.containsKey("blosum")?integer(s.get("blosum"),1,100):null;
            if(alphabet==MafftSequences.Alphabet.PROTEIN&&(matrix==null||!Set.of(30,45,62,80).contains(matrix))||alphabet==MafftSequences.Alphabet.NUCLEOTIDE&&matrix!=null)throw new IOException("Declare a supported protein BLOSUM matrix only for protein input");
            double opening=finite(s.get("gap_open")),offset=finite(s.get("offset"));if(opening<0||opening>1000||Math.abs(offset)>1000)throw new IOException("Explicit MAFFT scoring parameters exceed this contract");
            scoring=new Scoring(false,matrix,opening,offset);
        }
        Map<?,?> p=object(r.get("parameters"));keys(p,Set.of("threads","progressive_threads","iteration_threads","output_order","keep_length"));
        int threads=integer(p.get("threads"),1,256);Integer progressive=p.containsKey("progressive_threads")?integer(p.get("progressive_threads"),1,threads):null;
        Integer refinement=p.containsKey("iteration_threads")?integer(p.get("iteration_threads"),0,threads):null;
        var parameters=new Parameters(threads,progressive,refinement,enumeration(MafftSequences.OutputOrder.class,text(p,"output_order")),bool(p,"keep_length"));
        if(operation==Operation.DE_NOVO&&parameters.keepLength())throw new IOException("MAFFT keep_length applies only to an explicitly supplied backbone/addition workflow");
        if(operation==Operation.ADD&&maximum!=null&&maximum>0&&(parameters.keepLength()||profile.equals("linux-x86_64-mafft-7.313")))throw new MafftRuntime.Unavailable("Original MAFFT does not support iterative addition with keep-length, or iterative addition in7.313; do not silently reduce the requested iterations");
        Capacities limits=r.containsKey("limits")?(Capacities)override(Capacities.defaults(),r.get("limits")):Capacities.defaults();
        if(threads>limits.process().threads())throw new IOException("Requested MAFFT threads exceed the declared process capacity");
        var result=new Spec(source,identity,profile,operation,input,added,alphabet,symbols,headers,gaps,addGaps,strategy,maximum,scoring,parameters,limits);result.verify();return result;
    }
    private static Object override(Object defaults,Object value)throws IOException {
        Map<?,?> overrides=object(value);var components=defaults.getClass().getRecordComponents();var names=new HashSet<String>();for(var c:components)names.add(c.getName());keys(overrides,names);
        var types=new Class<?>[components.length];var arguments=new Object[components.length];
        try {
            for(int i=0;i<components.length;i++){
                var c=components[i];types[i]=c.getType();Object original=c.getAccessor().invoke(defaults),v=overrides.get(c.getName());
                if(!overrides.containsKey(c.getName()))arguments[i]=original;
                else if(types[i].isRecord())arguments[i]=override(original,v);
                else if(types[i]==int.class)arguments[i]=integer(v,Integer.MIN_VALUE,Integer.MAX_VALUE);
                else if(types[i]==long.class){if(!(v instanceof Long))throw new IOException("Require exact integral MAFFT capacity");arguments[i]=v;}
                else throw new IOException("Unsupported MAFFT capacity type");
            }
            return defaults.getClass().getDeclaredConstructor(types).newInstance(arguments);
        }catch(ReflectiveOperationException|IllegalArgumentException failure){Throwable cause=failure instanceof InvocationTargetException?failure.getCause():failure;throw new IOException("Invalid complete MAFFT capacity override: "+cause,cause);}
    }
    private static Path resolve(Path recipe,String name)throws IOException {if(name.isEmpty()||name.indexOf('\0')>=0)throw new IOException("Invalid MAFFT data path");return recipe.getParent().resolve(name).toAbsolutePath().normalize();}
    private static Map<?,?> object(Object value)throws IOException{if(!(value instanceof Map<?,?> map))throw new IOException("Require MAFFT JSON object");return map;}
    private static void keys(Map<?,?> value,Set<String> allowed)throws IOException{for(Object key:value.keySet())if(!(key instanceof String)||!allowed.contains(key))throw new IOException("Unknown MAFFT setting must not be discarded: "+key);}
    private static String text(Map<?,?> r,String key)throws IOException{if(!(r.get(key) instanceof String text)||text.isEmpty())throw new IOException("Require MAFFT text setting: "+key);return text;}
    private static int integer(Object value,int low,int high)throws IOException{if(!(value instanceof Long n)||n<low||n>high)throw new IOException("Invalid exact MAFFT integer");return n.intValue();}
    private static boolean bool(Map<?,?> r,String key)throws IOException{if(!(r.get(key) instanceof Boolean value))throw new IOException("Require MAFFT boolean: "+key);return value;}
    private static double finite(Object value)throws IOException{if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException("Require finite MAFFT scoring value");return n.doubleValue();}
    private static <T extends Enum<T>> T enumeration(Class<T> type,String text)throws IOException{try{return Enum.valueOf(type,text);}catch(IllegalArgumentException unknown){throw new IOException("Unsupported MAFFT "+type.getSimpleName()+": "+text,unknown);}}
}
