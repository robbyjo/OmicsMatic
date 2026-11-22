/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Complete original imputation output admission and explicit source-R2 post-QC. */
final class MinimacOutputs {
    private MinimacOutputs() { }
    record Counts(long variants,long retainedVariants,long excludedVariants,long genotypeCells,
            long targetPresentVariants,long referenceOnlyVariants,long observedGenotypes,
            long missingOrPartialGenotypes,long unorderedObservedChanges,long knownCopyConflicts,
            long sameGenotypePhaseSwaps,long renamedVariants,long missingSourceR2,long sourceR2AboveOne,
            long legacyUnidentifiedOrderedHdsCells,long ambiguousHdsCallCopies,double maximumStableR2Difference) { }

    static Counts audit(Path source,MinimacInputs.Prepared prepared,Path work,List<String> warnings)throws Exception{
        var spec=prepared.spec();var target=prepared.target();var budget=prepared.budget();
        if(target==null)throw new IOException("Imputation output cannot be admitted without target identities");
        MinimacVcf.regular(source,spec.limits().workspaceBytes());
        budget.keep(Math.multiplyExact(8L,spec.limits().outputLineCharacters()),"output line buffers");
        budget.keep(96L*prepared.reference().markers().size(),"complete output key inventory");
        var remaining=new LinkedHashMap<MinimacInputs.Key,MinimacInputs.Marker>();
        prepared.reference().markers().forEach((key,value)->{if(MinimacInputs.inside(key,spec.region()))remaining.put(key,value);});
        long cells=Math.multiplyExact((long)remaining.size(),target.columns().size());
        if(cells>spec.limits().vcf().genotypeCells())throw new IOException("Expected complete imputation genotype-cell count exceeds declared work bound");
        int buffer=spec.modern()?((Number)spec.engine().overrides().getOrDefault("temporary_samples",200L)).intValue():Integer.MAX_VALUE;
        long variants=0,kept=0,dropped=0,targetPresent=0,refOnly=0,observed=0,partial=0,genotypeChanges=0,copyConflicts=0,swaps=0,renamed=0,missingR2=0,aboveOne=0,unidentified=0,ambiguous=0;
        double maximumStableDifference=0;
        int previousPosition=0;boolean header=false;String fileformat=null;long metadataCharacters=0;int metadataLines=0;
        try(var lines=new Lines(source,spec);var calls=FileChannel.open(target.calls(),StandardOpenOption.READ);
                var canonical=budget.writer(work.resolve("genotypes.vcf"));
                var rows=budget.writer(work.resolve("variant-results.tsv"));
                var changes=budget.writer(work.resolve("genotype-changes.tsv"))){
            rows.write("contig\tposition\tref\talt\treference_id\ttarget_id\tupstream_id\tretained\tqc_decision\tsource_AF\tsource_MAF\tsource_R2\tsource_R2_compatible_lower\tsource_R2_compatible_upper\tstable_printed_HDS_R2\tobserved_genotypes\tmissing_or_partial_genotypes\tunordered_observed_changes\tknown_copy_conflicts\tsame_genotype_phase_swaps\tupstream_INFO\n");
            changes.write("contig\tposition\tref\talt\tcolumn_id\tadmitted_input_GT\tupstream_GT\tunordered_observed_change\tknown_copy_conflict\tsame_genotype_phase_swap\n");
            String text;
            while((text=lines.next())!=null){
                if(text.startsWith("##")){
                    if(header||++metadataLines>65536||(metadataCharacters+=text.length())>8388608)throw lines.error("Unexpected or oversized original metadata");
                    if(text.startsWith("##fileformat=")){if(fileformat!=null)throw lines.error("Duplicate original fileformat");fileformat=text.substring(13);}
                    canonical.write(text);canonical.write('\n');continue;
                }
                String[] f=text.split("\t",-1);
                if(text.startsWith("#CHROM\t")){
                    if(header||!Set.of("VCFv4.1","VCFv4.2","VCFv4.3").contains(fileformat==null?"":fileformat)
                            ||f.length!=target.columns().size()+9)throw lines.error("Invalid original VCF/sample header");
                    String[] fixed={"#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO","FORMAT"};
                    for(int i=0;i<9;i++)if(!fixed[i].equals(f[i]))throw lines.error("Invalid original fixed header");
                    for(int i=0;i<target.columns().size();i++)if(!target.columns().get(i).equals(f[i+9]))throw lines.error("Original output changed target column order/identity");
                    header=true;canonical.write(text);canonical.write('\n');continue;
                }
                if(!header||f.length!=target.columns().size()+9)throw lines.error("Original output row width differs");
                int position=position(f[1],lines);
                var key=new MinimacInputs.Key(f[0],position,f[3],f[4]);
                MinimacInputs.Marker marker=remaining.remove(key);
                if(marker==null||!f[0].equals(spec.region().contig())||position<previousPosition)throw lines.error("Unexpected, duplicate, unsorted or altered original marker/allele key");
                previousPosition=position;
                if(f[2].isEmpty()||f[2].codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw lines.error("Invalid original variant ID");
                if(!marker.id().equals(f[2])){
                    renamed++;
                    if(spec.idPolicy().equals("require-reference-id"))throw lines.error("Original output ID differs under require-reference-id policy");
                }
                Map<String,String> info=info(f[7],lines);
                var af=MinimacPosterior.number(required(info,"AF",lines),1,spec.modern(),true);
                var maf=MinimacPosterior.number(required(info,"MAF",lines),1,spec.modern(),true);
                if(!maf.range().overlaps(MinimacPosterior.minimumAlleleFrequency(af.range())))throw lines.error("Original MAF disagrees with original AF precision");
                String rawR2=info.get("R2");MinimacPosterior.Num r2=null;
                if(rawR2==null||unreportable(rawR2)){missingR2++;}
                else {r2=MinimacPosterior.number(rawR2,Integer.MAX_VALUE,spec.modern(),true);if(r2.value()>1)aboveOne++;}
                String[] format=f[8].split(":",-1);var tags=new HashSet<String>();
                for(String tag:format)if(!tags.add(tag))throw lines.error("Duplicate original FORMAT tag");
                Set<String> expected=spec.modern()?Set.of("GT","HDS","DS","GP","SD"):Set.of("GT","DS","GP");
                if(!tags.equals(expected))throw lines.error("Original FORMAT differs from the explicitly requested profile fields");
                var before=target.markers().get(key);byte[] input=before==null?null:MinimacInputs.calls(calls,target,before);
                long rowObserved=0,rowPartial=0,rowGenotype=0,rowConflict=0,rowSwap=0;int offset=0;
                var cohort=new MinimacPosterior.Cohort(spec.modern(),buffer);
                for(int i=0;i<target.columns().size();i++){
                    String[] values=f[i+9].split(":",-1);if(values.length!=format.length)throw lines.error("Original sample FORMAT width differs");
                    var fields=new LinkedHashMap<String,String>();for(int j=0;j<format.length;j++)fields.put(format[j],values[j]);
                    int ploidy=target.ploidies().get(i);
                    var posterior=MinimacPosterior.cell(fields,ploidy,spec.modern());cohort.add(posterior);
                    if(!spec.modern()&&ploidy==2)unidentified++;
                    if(spec.modern())for(var h:posterior.haplotypes())if(h.range().lower()<=.5&&h.range().upper()>=.5)ambiguous++;
                    int known=0,knownAlt=0,knownRef=0,afterAlt=posterior.calls().stream().mapToInt(Integer::intValue).sum();boolean conflict=false;
                    var admitted=new ArrayList<String>();
                    for(int copy=0;copy<ploidy;copy++){
                        int prior=input==null?2:input[offset+copy];
                        admitted.add(prior==2?".":Integer.toString(prior));
                        if(prior!=2){known++;if(prior==1)knownAlt++;else knownRef++;if(prior!=posterior.calls().get(copy))conflict=true;}
                    }
                    offset+=ploidy;
                    boolean changed=afterAlt<knownAlt||ploidy-afterAlt<knownRef;
                    boolean swap=known==ploidy&&!changed&&conflict;
                    if(known==ploidy)rowObserved++;else rowPartial++;
                    if(changed)rowGenotype++;if(conflict)rowConflict++;if(swap)rowSwap++;
                    if(changed||conflict){
                        changes.write(key.text()+"\t"+target.columns().get(i)+"\t"+String.join("|",admitted)+"\t"+fields.get("GT")+"\t"+changed+"\t"+conflict+"\t"+swap+"\n");
                        if(changed&&spec.observedCallChanges().equals("error"))throw lines.error("Original imputation changed supplied unordered genotype evidence");
                        if(conflict&&spec.providedPhaseChanges().equals("error"))throw lines.error("Original imputation changed a known supplied ordered haplotype copy");
                    }
                }
                var afBounds=cohort.af();var r2Bounds=cohort.r2();
                if(!af.range().overlaps(afBounds))throw lines.error("Original AF disagrees with admitted copy counts/posterior moment bounds");
                if(r2!=null&&!r2.range().overlaps(r2Bounds))throw lines.error("Original R2 disagrees with source-precision moment bounds; no value is replaced");
                Double stable=cohort.stableR2();
                if(stable!=null&&r2!=null)maximumStableDifference=Math.max(maximumStableDifference,Math.abs(stable-r2.value()));
                String decision=qc(spec.qc(),r2);boolean retain=decision.startsWith("RETAIN");
                if(retain){canonical.write(text);canonical.write('\n');kept++;}else dropped++;
                rows.write(key.text()+"\t"+marker.id()+"\t"+(before==null?"":before.id())+"\t"+f[2]+"\t"+retain+"\t"+decision
                        +"\t"+af.raw()+"\t"+maf.raw()+"\t"+(rawR2==null?".":rawR2)+"\t"+r2Bounds.lower()+"\t"+r2Bounds.upper()
                        +"\t"+(stable==null?".":stable)+"\t"+rowObserved+"\t"+rowPartial+"\t"+rowGenotype+"\t"+rowConflict+"\t"+rowSwap+"\t"+f[7]+"\n");
                variants++;observed+=rowObserved;partial+=rowPartial;genotypeChanges+=rowGenotype;copyConflicts+=rowConflict;swaps+=rowSwap;
                if(before==null)refOnly++;else targetPresent++;
                if(variants%1024==0)budget.check();
            }
            if(!header||!remaining.isEmpty())throw new IOException("Original output omitted declared reference markers; no partial family is admitted");
        }
        budget.check();
        if(kept==0)warnings.add("EMPTY POST-QC RESULT: the complete original output was audited but every variant was explicitly excluded. Required action: inspect variant-results.tsv and the declared source-R2 rule; do not treat a header-only genotypes.vcf as a completed association dataset.");
        if(renamed>0)warnings.add("ORIGINAL VARIANT ID CHANGES: "+renamed+" output IDs differ from reference IDs under explicit retain-upstream-id-with-ledger policy. Required action: join through exact coordinate/REF/ALT and variant-results.tsv; source, target and upstream IDs remain distinct.");
        if(genotypeChanges>0||copyConflicts>0)warnings.add("SUPPLIED GENOTYPE/COPY CHANGES: "+genotypeChanges+" unordered genotype conflicts and "+copyConflicts+" known ordered-copy conflicts were retained under explicit policies. Required action: review genotype-changes.tsv before haplotype or downstream analysis; original imputation need not preserve every supplied call.");
        if(missingR2>0)warnings.add("UNREPORTABLE SOURCE R2: "+missingR2+" original rows lack finite source R2. Required action: inspect the explicit post-QC missing-value decisions; no R2 was imputed, normalized or replaced with a native diagnostic.");
        if(aboveOne>0)warnings.add("SOURCE R2 ABOVE ONE: "+aboveOne+" original values exceed one but remain compatible with the audited source-precision moment bounds. Required action: review variant-results.tsv and numerical cancellation before applying the explicit source-R2 rule; original values were not clamped or replaced.");
        if(unidentified>0)warnings.add("LEGACY PHASE LIMITATION: "+unidentified+" diploid cells expose rounded DS/GP without ordered HDS. Native checks validate posterior identities and symmetric moment bounds, but do not reconstruct phased dosages from GP. Required action: retain original GT and the explicit target-copy checks; do not interpret missing native HDS as inferred phase certainty.");
        warnings.add("NUMERICAL QC SCOPE: source AF/MAF/R2 and all GT/DS/GP/HDS/SD strings remain unmodified. SD is posterior variance. Current six-significant-digit binary32 and historical three/five-decimal rounding envelopes are checked, with declared current temporary-sample groups. Stable printed-HDS R2 is diagnostic, mathematically undefined at AF0/1, and can differ from source float32 summaries (maximum observed difference "+maximumStableDifference+"). Required action: use the explicitly chosen source-R2 gt/ge rule; these are estimated descriptors, not empirical accuracy/calibration or historical/server/full-paper reproduction.");
        return new Counts(variants,kept,dropped,cells,targetPresent,refOnly,observed,partial,genotypeChanges,copyConflicts,swaps,renamed,missingR2,aboveOne,unidentified,ambiguous,maximumStableDifference);
    }

    static String qc(MinimacRecipe.Qc qc,MinimacPosterior.Num r2)throws IOException{
        if(qc==null||qc.kind().equals("none"))return r2==null?"RETAIN_NO_QC_UNREPORTABLE_R2":"RETAIN_NO_QC";
        if(r2==null){
            if(qc.missing().equals("error"))throw new IOException("Source R2 is unreportable under explicit post-QC error policy");
            return qc.missing().equals("retain-with-warning")?"RETAIN_UNREPORTABLE_R2_WITH_WARNING":"EXCLUDE_UNREPORTABLE_R2_WITH_LEDGER";
        }
        int compare=r2.decimal().compareTo(new BigDecimal(qc.threshold()));
        return (qc.operator().equals("gt")?compare>0:compare>=0)?"RETAIN_SOURCE_R2_RULE":"EXCLUDE_SOURCE_R2_RULE";
    }
    private static Map<String,String> info(String text,Lines lines)throws IOException{
        var result=new LinkedHashMap<String,String>();var flags=new HashSet<String>();
        for(String entry:text.split(";",-1)){
            int equals=entry.indexOf('=');String key=equals<0?entry:entry.substring(0,equals);
            if(key.isEmpty()||!flags.add(key))throw lines.error("Invalid/duplicate original INFO key");
            if(equals>=0){if(equals==entry.length()-1)throw lines.error("Empty original INFO value");result.put(key,entry.substring(equals+1));}
        }
        return result;
    }
    private static boolean unreportable(String value){String v=value.toLowerCase(Locale.ROOT);return v.equals(".")||v.matches("[+-]?(?:nan(?:\\([^)]*\\))?|inf(?:inity)?)");}
    private static String required(Map<String,String> info,String field,Lines lines)throws IOException{String value=info.get(field);if(value==null)throw lines.error("Original INFO lacks "+field);return value;}
    private static int position(String value,Lines lines)throws IOException{try{if(!value.matches("[0-9]+"))throw new NumberFormatException();int n=Integer.parseInt(value);if(n<1)throw new NumberFormatException();return n;}catch(NumberFormatException failure){throw lines.error("Invalid original position");}}
    private static final class Lines implements AutoCloseable{
        final Reader reader;final MinimacRecipe.Spec spec;final char[] buffer=new char[8192];int index,size;long number,characters;
        Lines(Path source,MinimacRecipe.Spec spec)throws IOException{
            this.spec=spec;var raw=new BufferedInputStream(Files.newInputStream(source));boolean good=false;
            try{raw.mark(2);int first=raw.read(),second=raw.read();raw.reset();reader=new InputStreamReader(first==31&&second==139?new GZIPInputStream(raw):raw,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));good=true;}finally{if(!good)raw.close();}
        }
        String next()throws IOException{
            if(size<0)return null;var text=new StringBuilder();boolean lf=false;
            for(;;){
                if(index==size){size=reader.read(buffer);index=0;if(size<0)break;if(size==0)continue;}
                char c=buffer[index++];
                if(++characters>spec.limits().vcf().decodedCharacters())throw new IOException("Original output decoded-character bound exceeded");
                if(c=='\n'){lf=true;break;}
                if(text.length()>=spec.limits().outputLineCharacters()||Character.isISOControl(c)&&c!='\t'&&c!='\r')throw new IOException("Original output line "+(number+1)+": line/control bound exceeded");
                text.append(c);
            }
            if(!lf&&text.isEmpty())return null;number++;
            if(!text.isEmpty()&&text.charAt(text.length()-1)=='\r')text.setLength(text.length()-1);
            if(text.indexOf("\r")>=0)throw error("Embedded carriage return");
            return text.toString();
        }
        IOException error(String message){return new IOException("Original Minimac output line "+number+": "+message);}
        @Override public void close()throws IOException{reader.close();}
    }
}

