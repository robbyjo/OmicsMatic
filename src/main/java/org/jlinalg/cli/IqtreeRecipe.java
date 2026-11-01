/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.phylogeny.BootstrapSupport;
import org.jlinalg.phylogeny.NewickTree;
import org.jlinalg.phylogeny.PhylogeneticAlignment;

/** Closed IQ-TREE recipes; input cannot choose an executable, extra flags or a reflected class. */
final class IqtreeRecipe {
    private IqtreeRecipe() { }
    enum Operation { INFER, EVALUATE }
    enum Format { FASTA, PHYLIP }
    enum ModelSelection { FIXED, MODEL_FINDER, STANDARD_SELECTION }
    enum Criterion { BIC, AIC, AICc }
    enum Bootstrap { NONE, STANDARD, ULTRAFAST }
    enum IdenticalSequences { KEEP, SOURCE_REMOVE_RESTORE }
    enum TreeMode { STARTING, FIX_TOPOLOGY, FIX_TOPOLOGY_AND_LENGTHS }
    record Alignment(Path path,Format format,PhylogeneticAlignment.Kind kind,
                     PhylogeneticAlignment.HeaderPolicy headerPolicy,PhylogeneticAlignment.Layout layout,
                     PhylogeneticAlignment.NameField nameField,boolean namedContinuations) { }
    record Model(ModelSelection selection,String name,Criterion criterion,List<String> candidates) {
        Model {candidates=List.copyOf(candidates);}
    }
    record Support(Bootstrap bootstrap,int replicates,int shAlrtReplicates,boolean bnni,boolean writeBranchLengths) { }
    record Parameters(int seed,int threads,IdenticalSequences identicalSequences,boolean safeMode,List<String> outgroup) {
        Parameters {outgroup=List.copyOf(outgroup);}
    }
    record Tree(Path path,TreeMode mode) { }
    record Capacities(long inputBytes,long bundleBytes,int bundleFiles,PhylogeneticAlignment.Limits alignment,NewickTree.Limits tree,
                      BootstrapSupport.Limits bootstrap,IqtreeProcess.Limits process,IqtreeReport.Limits report) {
        Capacities {
            if(inputBytes<1 || inputBytes>1099511627776L)throw new IllegalArgumentException("Invalid complete IQ-TREE input byte capacity");
            if(bundleBytes<1||bundleBytes>1099511627776L||bundleFiles<1||bundleFiles>1000000)throw new IllegalArgumentException("Invalid complete IQ-TREE bundle capacities");
        }
        static Capacities defaults() {
            return new Capacities(1073741824,4294967296L,10000,PhylogeneticAlignment.Limits.defaults(),NewickTree.Limits.defaults(),BootstrapSupport.Limits.defaults(),
                    new IqtreeProcess.Limits(86400000,17179869184L,86400,134217728,256,536870912,1024,8,256,12884901888L,25),IqtreeReport.Limits.defaults());
        }
    }
    record Spec(Path source,IqtreeFiles.Identity identity,String profile,Operation operation,Alignment alignment,
                Model model,Support support,Parameters parameters,Tree tree,Capacities limits) {
        void verify()throws IOException {
            if(!identity.equals(IqtreeFiles.identity(source,2097152,()->{})))throw new IOException("IQ-TREE recipe changed; no result is admitted");
        }
        List<String> arguments(Path preparedAlignment,Path preparedTree,Path prefix,Map<String,String> originalToOpaque)throws IOException {
            if(originalToOpaque.isEmpty() || new HashSet<>(originalToOpaque.values()).size()!=originalToOpaque.size()
                    ||originalToOpaque.values().stream().anyMatch(v->!v.matches("T[0-9]{9}")))
                throw new IOException("Require complete unique native opaque taxon identities");
            var result=new ArrayList<String>(List.of("-s",preparedAlignment.toString(),"-st",
                    switch(alignment.kind()){case DNA->"DNA";case AMINO_ACID->"AA";case CODON_STANDARD->"CODON";},"-m",
                    switch(model.selection()){case FIXED->model.name();case MODEL_FINDER->"MFP";case STANDARD_SELECTION->"TEST";},
                    "-wsl","-seed",String.valueOf(parameters.seed()),"-nt",String.valueOf(parameters.threads()),"-pre",prefix.toString()));
            if(model.selection()!=ModelSelection.FIXED) {
                result.addAll(List.of("-merit",model.criterion().name()));
                if(!model.candidates().isEmpty())result.addAll(List.of("-mset",String.join(",",model.candidates())));
            }
            if(parameters.safeMode())result.add("-safe");
            if(parameters.identicalSequences()==IdenticalSequences.KEEP)result.add("-keep-ident");
            if(!parameters.outgroup().isEmpty()) {
                if(parameters.outgroup().size()>=originalToOpaque.size())throw new IOException("Outgroup must be a proper subset of alignment taxa");
                var mapped=new ArrayList<String>();
                for(String taxon:parameters.outgroup()) {
                    String opaque=originalToOpaque.get(taxon);if(opaque==null)throw new IOException("Outgroup taxon is absent from the exact alignment: "+taxon);mapped.add(opaque);
                }
                result.addAll(List.of("-o",String.join(",",mapped)));
            }
            if(tree!=null) {
                if(preparedTree==null)throw new IOException("Missing completely admitted prepared tree");
                result.addAll(List.of(tree.mode()==TreeMode.STARTING?"-t":"-te",preparedTree.toString()));
                if(tree.mode()==TreeMode.FIX_TOPOLOGY_AND_LENGTHS)result.add("-blfix");
            } else if(preparedTree!=null)throw new IOException("Undeclared prepared tree");
            if(support.bootstrap()!=Bootstrap.NONE) {
                result.addAll(List.of(support.bootstrap()==Bootstrap.STANDARD?"-b":profile.endsWith("-1.6.12")?"-bb":"-B",String.valueOf(support.replicates())));
                result.add(support.writeBranchLengths()?"-wbtl":"-wbt");
            }
            if(support.shAlrtReplicates()>0)result.addAll(List.of("-alrt",String.valueOf(support.shAlrtReplicates())));
            if(support.bnni())result.add("-bnni");
            return List.copyOf(result);
        }
    }
    static Spec read(Path supplied)throws IOException {
        Path source=supplied.toAbsolutePath().normalize();var identity=IqtreeFiles.identity(source,2097152,()->{});
        byte[] raw;
        try(var input=Files.newInputStream(source)){raw=input.readNBytes(2097153);}
        if(raw.length>2097152)throw new IOException("IQ-TREE recipe exceeds complete byte capacity");
        Map<?,?> root=object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()));
        for(String capability:List.of("partitions","dating","gene_tree_batch"))if(root.containsKey(capability))
            throw new IqtreeRuntime.Unavailable("IQ-TREE "+capability+" workflow is not implemented through this typed command; skip this requested analysis rather than discarding the setting");
        keys(root,Set.of("schema_version","profile","operation","alignment","model","support","parameters","tree","limits"));
        String profile=text(root,"profile");
        if(integer(root.get("schema_version"),1,Integer.MAX_VALUE)!=1 || !IqtreeRuntime.profiles().contains(profile))
            throw new IqtreeRuntime.Unavailable("Unsupported exact IQ-TREE schema/profile; do not substitute another edition");
        Operation operation=enumeration(Operation.class,text(root,"operation"));
        Map<?,?> a=object(root.get("alignment"));keys(a,Set.of("path","format","kind","header_policy","layout","name_field","named_continuations"));
        String formatName=text(a,"format");if(Set.of("NEXUS","CLUSTAL","MSF").contains(formatName))throw new IqtreeRuntime.Unavailable("This typed IQ-TREE input-format contract is not implemented: "+formatName+". Preserve the input; an explicit complete conversion or a tested format implementation is required.");
        Format format=enumeration(Format.class,formatName);PhylogeneticAlignment.Kind kind=enumeration(PhylogeneticAlignment.Kind.class,text(a,"kind"));
        var alignment=new Alignment(resolve(source,text(a,"path")),format,kind,
                format==Format.FASTA?enumeration(PhylogeneticAlignment.HeaderPolicy.class,text(a,"header_policy")):null,
                format==Format.PHYLIP?enumeration(PhylogeneticAlignment.Layout.class,text(a,"layout")):null,
                format==Format.PHYLIP?enumeration(PhylogeneticAlignment.NameField.class,text(a,"name_field")):null,
                format==Format.PHYLIP&&bool(a,"named_continuations"));
        if(format==Format.FASTA && (a.containsKey("layout")||a.containsKey("name_field")||a.containsKey("named_continuations"))
                ||format==Format.PHYLIP&&a.containsKey("header_policy")
                ||alignment.layout()==PhylogeneticAlignment.Layout.SEQUENTIAL&&alignment.namedContinuations())
            throw new IOException("IQ-TREE alignment options conflict with the explicit input format/layout");
        Map<?,?> m=object(root.get("model"));keys(m,Set.of("selection","name","criterion","candidates"));
        ModelSelection selection=enumeration(ModelSelection.class,text(m,"selection"));
        Model model;
        if(selection==ModelSelection.FIXED) {
            if(m.containsKey("criterion")||m.containsKey("candidates"))throw new IOException("Fixed IQ-TREE model cannot include selection settings");
            String name=text(m,"name");validateModel(name,kind);model=new Model(selection,name,null,List.of());
        } else {
            if(m.containsKey("name"))throw new IOException("Model selection cannot silently replace a supplied fixed model");
            var candidates=new ArrayList<String>();
            if(m.containsKey("candidates")) {
                for(Object item:list(m.get("candidates"))) {
                    if(!(item instanceof String name))throw new IOException("Require explicit builtin candidate model names");
                    validateModel(name,kind);if(name.contains("+"))throw new IOException("Candidate model sets contain base models; rate/frequency selection is upstream");candidates.add(name);
                }
                if(candidates.isEmpty() || new HashSet<>(candidates).size()!=candidates.size())throw new IOException("Require nonempty unique candidate models");
            }
            model=new Model(selection,null,enumeration(Criterion.class,text(m,"criterion")),candidates);
        }
        Map<?,?> s=object(root.get("support"));keys(s,Set.of("bootstrap","replicates","sh_alrt_replicates","bnni","write_branch_lengths"));
        var support=new Support(enumeration(Bootstrap.class,text(s,"bootstrap")),integer(s.get("replicates"),0,1000000),
                integer(s.get("sh_alrt_replicates"),0,1000000),bool(s,"bnni"),bool(s,"write_branch_lengths"));
        if(support.bootstrap()==Bootstrap.NONE&&support.replicates()!=0
                ||support.bootstrap()==Bootstrap.STANDARD&&support.replicates()<1
                ||support.bootstrap()==Bootstrap.ULTRAFAST&&support.replicates()<1000
                ||support.shAlrtReplicates()>0&&support.shAlrtReplicates()<1000
                ||support.bnni()&&support.bootstrap()!=Bootstrap.ULTRAFAST
                ||support.writeBranchLengths()&&support.bootstrap()==Bootstrap.NONE)
            throw new IOException("Inconsistent explicit IQ-TREE bootstrap/SH-aLRT settings");
        Map<?,?> p=object(root.get("parameters"));keys(p,Set.of("seed","threads","identical_sequences","safe_mode","outgroup"));
        var outgroup=new ArrayList<String>();
        for(Object item:list(p.get("outgroup"))) {
            if(!(item instanceof String name) || name.isEmpty() || name.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid exact outgroup identity");outgroup.add(name);
        }
        if(new HashSet<>(outgroup).size()!=outgroup.size())throw new IOException("Duplicate IQ-TREE outgroup identities");
        var parameters=new Parameters(integer(p.get("seed"),1,Integer.MAX_VALUE),integer(p.get("threads"),1,256),
                enumeration(IdenticalSequences.class,text(p,"identical_sequences")),bool(p,"safe_mode"),outgroup);
        Tree tree=null;
        if(root.containsKey("tree")) {
            Map<?,?> t=object(root.get("tree"));keys(t,Set.of("path","mode"));tree=new Tree(resolve(source,text(t,"path")),enumeration(TreeMode.class,text(t,"mode")));
        }
        if(operation==Operation.EVALUATE && (tree==null||tree.mode()==TreeMode.STARTING)
                ||operation==Operation.INFER&&tree!=null&&tree.mode()!=TreeMode.STARTING
                ||operation==Operation.EVALUATE&&(support.bootstrap()!=Bootstrap.NONE||support.shAlrtReplicates()!=0||selection!=ModelSelection.FIXED))
            throw new IOException("IQ-TREE operation, tree role, model selection or support settings conflict");
        Capacities limits=Capacities.defaults();if(root.containsKey("limits"))limits=(Capacities)override(limits,root.get("limits"));
        if(parameters.threads()>limits.process().threads() || support.replicates()>limits.bootstrap().collection().trees())
            throw new IOException("Declared IQ-TREE threads/replicates exceed complete process/collection capacities");
        var result=new Spec(source,identity,profile,operation,alignment,model,support,parameters,tree,limits);result.verify();return result;
    }
    private static final Set<String> DNA=Set.of("JC","JC69","F81","K2P","K80","HKY","HKY85","TN","TN93","TNe","K3P","K81","K81u","TPM2","TPM2u","TPM3","TPM3u","TIM","TIMe","TIM2","TIM2e","TIM3","TIM3e","TVM","TVMe","SYM","GTR");
    private static final Set<String> AA=Set.of("Poisson","LG","WAG","JTT","VT","Dayhoff","DCMut","JTTDCMut","mtREV","mtMAM","mtART","mtZOA","mtMET","mtVER","mtINV","PMB","HIVb","HIVw","FLU","Blosum62","rtREV","cpREV");
    private static final Set<String> CODON=Set.of("MG","MGK","MG1KTS","MG1KTV","MG2K","MGKAP2","MGKAP3","MGKAP4","GY","GY0K","GY1KTS","GY1KTV","GY2K","GYKAP1","GYKAP2","GYKAP3","GYKAP4","ECM","ECMK07","KOSI07","ECMrest","ECMS05","SCHN05");
    static void validateModel(String name,PhylogeneticAlignment.Kind kind)throws IOException {
        if(name.length()>128)throw new IOException("IQ-TREE builtin model token exceeds capacity");
        String[] parts=name.split("\\+",-1);String base=parts[0];
        boolean admitted=switch(kind){case DNA->DNA.contains(base)||base.matches("[0-5]{6}");case AMINO_ACID->AA.contains(base);case CODON_STANDARD->CODON.contains(base);};
        if(!admitted)throw new IOException("Builtin IQ-TREE model does not match the declared alphabet: "+name+". Retain reported settings and resolve the effective author model; custom matrix files and mixture models need separate contracts.");
        boolean frequency=false,rate=false,invariant=false,ascertainment=false;
        for(int i=1;i<parts.length;i++) {
            String part=parts[i];
            if(Set.of("F","FQ","FO","F1X4","F3X4","F3x4").contains(part)) {
                if(frequency || part.startsWith("F1")&&kind!=PhylogeneticAlignment.Kind.CODON_STANDARD
                        ||part.startsWith("F3")&&kind!=PhylogeneticAlignment.Kind.CODON_STANDARD)
                    throw new IOException("Incompatible or duplicate literal IQ-TREE frequency setting: "+name);
                frequency=true;
            } else if(part.equals("I")) {if(invariant)throw new IOException("Duplicate invariant setting");invariant=true;}
            else if(part.equals("ASC")) {if(ascertainment)throw new IOException("Duplicate ascertainment setting");ascertainment=true;}
            else if(part.matches("[GR](?:[1-9][0-9]{0,2})?")) {
                if(rate)throw new IOException("Conflicting rate categories in IQ-TREE model");rate=true;
                if(part.length()>1 && Integer.parseInt(part.substring(1))<2)throw new IOException("Require at least two explicit rate categories");
            } else throw new IOException("Unadmitted literal IQ-TREE model suffix: "+name);
        }
    }
    private static Object override(Object defaults,Object supplied)throws IOException {
        Class<?> type=defaults.getClass();String name=type.getName();
        if(!type.isRecord() || !(name.startsWith("org.jlinalg.cli.Iqtree")||name.startsWith("org.jlinalg.phylogeny.")))throw new IOException("Unowned IQ-TREE capacity record");
        Map<?,?> values=object(supplied);var components=type.getRecordComponents();var allowed=new HashSet<String>();
        for(var component:components)allowed.add(component.getName());keys(values,allowed);
        Object[] arguments=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
        try {
            for(int i=0;i<components.length;i++) {
                var c=components[i];types[i]=c.getType();Object base=c.getAccessor().invoke(defaults);
                if(!values.containsKey(c.getName()))arguments[i]=base;
                else if(c.getType().isRecord())arguments[i]=override(base,values.get(c.getName()));
                else if(c.getType()==int.class)arguments[i]=integer(values.get(c.getName()),1,Integer.MAX_VALUE);
                else if(c.getType()==long.class)arguments[i]=longInteger(values.get(c.getName()),1,1099511627776L);
                else throw new IOException("Unsupported IQ-TREE capacity field");
            }
            return type.getDeclaredConstructor(types).newInstance(arguments);
        } catch(ReflectiveOperationException invalid) {
            Throwable cause=invalid instanceof InvocationTargetException nested?nested.getCause():invalid;
            throw new IOException("Invalid complete IQ-TREE capacities: "+cause,cause);
        }
    }
    private static Path resolve(Path recipe,String name){Path path=Path.of(name);return (path.isAbsolute()?path:recipe.getParent().resolve(path)).toAbsolutePath().normalize();}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require an IQ-TREE JSON object");return map;}
    private static List<?> list(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Require an IQ-TREE JSON array");return list;}
    private static String text(Map<?,?> map,String key)throws IOException {if(!(map.get(key) instanceof String value)||value.isBlank()||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Missing/invalid IQ-TREE text: "+key);return value;}
    private static boolean bool(Map<?,?> map,String key)throws IOException {if(!(map.get(key) instanceof Boolean value))throw new IOException("Require explicit IQ-TREE boolean: "+key);return value;}
    private static void keys(Map<?,?> map,Set<String> allowed)throws IOException {for(Object key:map.keySet())if(!(key instanceof String)||!allowed.contains(key))throw new IOException("Unknown IQ-TREE recipe field: "+key);}
    private static long longInteger(Object value,long minimum,long maximum)throws IOException {if(!(value instanceof Long number)||number<minimum||number>maximum)throw new IOException("Require an exact bounded IQ-TREE integer");return number;}
    private static int integer(Object value,int minimum,int maximum)throws IOException {return Math.toIntExact(longInteger(value,minimum,maximum));}
    private static <T extends Enum<T>> T enumeration(Class<T> type,String value)throws IOException {try{return Enum.valueOf(type,value);}catch(IllegalArgumentException invalid){throw new IOException("Unknown explicit IQ-TREE "+type.getSimpleName()+": "+value,invalid);}}
}
