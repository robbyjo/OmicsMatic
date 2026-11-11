/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Complete source reports and model-selection-stage checkpoints; no parameter refitting. */
final class IqtreeReport {
    private IqtreeReport() { }
    record Limits(int bytes,int candidates) {
        Limits {if(bytes<1||bytes>268435456||candidates<1||candidates>1000000)throw new IllegalArgumentException("Invalid IQ-TREE complete report capacities");}
        static Limits defaults(){return new Limits(67108864,100000);}
    }
    private static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?");
    /** Original decimal token; nonfinite auxiliary scores are represented as text. */
    record Numeric(String text) {
        boolean finite(){return DECIMAL.matcher(text).matches()&&Double.isFinite(Double.parseDouble(text));}
        double value(){return Double.parseDouble(text);}
        BigDecimal exact(){return new BigDecimal(text);}
        double half(){return exact().ulp().abs().doubleValue()/2;}
    }
    record Scores(Numeric logLikelihood,int parameters,Numeric aic,Numeric aicc,Numeric bic,boolean classicalAiccDefined) { }
    record Rate(int category,Numeric rate,Numeric proportion) { }
    record Weight(String marker,Numeric value) { }
    record Candidate(String model,Numeric logLikelihood,Numeric aic,Weight aicWeight,Numeric aicc,Weight aiccWeight,
                     Numeric bic,Weight bicWeight,int parameters,boolean classicalAiccDefined) { }
    record Cached(Numeric logLikelihood,int parameters,Numeric treeLength) { }
    record Selection(String criterion,String selectedModel,List<Candidate> candidates,Map<String,Cached> fittedModels,
                     Map<String,String> checkpointMetadata,int announcedModels,int actuallyFittedModels) {
        Selection {candidates=List.copyOf(candidates);fittedModels=Map.copyOf(fittedModels);checkpointMetadata=Map.copyOf(checkpointMetadata);}
    }
    record Result(int taxa,int sites,String unit,Map<String,Integer> alignmentStatistics,String model,Scores scores,Numeric treeLength,
                  String frequencyDescription,Map<String,Numeric> frequencies,Map<String,Numeric> exchangeabilities,
                  String rateDescription,List<Rate> rates,Numeric invariantProportion,Numeric gammaShape,
                  Selection selection,Map<String,String> ignoredIdenticalTaxa,Map<String,IqtreeFiles.Identity> identities,List<String> warnings) {
        Result {alignmentStatistics=Map.copyOf(alignmentStatistics);frequencies=Map.copyOf(frequencies);exchangeabilities=Map.copyOf(exchangeabilities);rates=List.copyOf(rates);ignoredIdenticalTaxa=Map.copyOf(ignoredIdenticalTaxa);identities=Map.copyOf(identities);warnings=List.copyOf(warnings);}
    }
    static Result read(Path report,Path modelCache,Path log,Limits limits,Runnable cancellation)throws IOException {
        var identities=new TreeMap<String,IqtreeFiles.Identity>();
        String text=readText(report,false,limits.bytes(),cancellation,identities,"report");String[] lines=text.split("\\R",-1);
        var input=Pattern.compile("([0-9]+) sequences with ([0-9]+) (nucleotide|amino-acid|codon) sites").matcher(one(lines,"Input data:"));
        if(!input.matches())throw new IOException("Unrecognized complete IQ-TREE alignment dimensions");
        int taxa=positive(input.group(1)),sites=positive(input.group(2));String unit=input.group(3),model=one(lines,"Model of substitution:");modelName(model);
        var quality=new LinkedHashMap<String,Integer>();
        for(String prefix:List.of("Number of constant sites:","Number of invariant (constant or ambiguous constant) sites:","Number of parsimony informative sites:","Number of distinct site patterns:")) {
            int count=nonnegative(one(lines,prefix).split("\\s+",2)[0]);if(count>sites)throw new IOException("Source alignment statistic exceeds its complete site count");quality.put(prefix.substring(10,prefix.length()-1),count);
        }
        String likelihood=one(lines,"Log-likelihood of the tree:");String ll=likelihood.split("\\s+",2)[0];
        int parameters=nonnegative(one(lines,"Number of free parameters (#branches + #model parameters):"));
        var scores=new Scores(finite(ll),parameters,finite(one(lines,"Akaike information criterion (AIC) score:")),
                numeric(one(lines,"Corrected Akaike information criterion (AICc) score:")),finite(one(lines,"Bayesian information criterion (BIC) score:")),sites>(long)parameters+1);
        validateScores(scores,sites);
        var warnings=new ArrayList<String>();
        if(!scores.classicalAiccDefined())warnings.add("CLASSICAL AICc UNDEFINED: sites <= parameters + 1. The original runtime's finite score uses a denominator clamped to one. Its exact source value is retained; do not report it as classical AICc. Use adequately sized data or a justified different criterion.");
        Numeric treeLength=finite(one(lines,"Total tree length (sum of branch lengths):"));if(treeLength.value()<0)throw new IOException("Negative source total tree length");
        String frequencyDescription=oneAllowEmpty(lines,"State frequencies:");var frequencies=new LinkedHashMap<String,Numeric>();
        Matcher pi=Pattern.compile("pi\\(([^)]+)\\)\\s*=\\s*([^\\s]+)").matcher(text);
        while(pi.find()) {var value=finite(pi.group(2));probability(value);if(frequencies.putIfAbsent(pi.group(1),value)!=null)throw new IOException("Duplicate reported state frequency");}
        if(frequencyDescription.contains("equal frequencies")) {
            if(!frequencies.isEmpty())throw new IOException("Conflicting equal/explicit source frequencies");
        } else if(unit.equals("amino-acid")&&frequencyDescription.equals("(model)")) {
            if(!frequencies.isEmpty())throw new IOException("Conflicting implicit/explicit source protein frequencies");
            warnings.add("Protein stationary frequencies are supplied by the pinned builtin model and are not printed in this report. No equal-frequency assumption or independent fitted-model reconstruction is made.");
        } else {
            Set<String> states=states(unit);if(!frequencies.keySet().equals(states))throw new IOException("Incomplete or unknown source state-frequency vector");
            intervalSum(frequencies.values(),1,"state frequencies");
        }
        var exchangeabilities=new LinkedHashMap<String,Numeric>();
        for(String line:lines) {
            Matcher rate=Pattern.compile("\\s*([ACGT]-[ACGT]):\\s*(\\S+)\\s*").matcher(line);
            if(rate.matches()) {Numeric value=finite(rate.group(2));if(value.value()<0||exchangeabilities.putIfAbsent(rate.group(1),value)!=null)throw new IOException("Invalid/duplicate source exchangeability");}
        }
        if(!exchangeabilities.isEmpty()&&!exchangeabilities.keySet().equals(Set.of("A-C","A-G","A-T","C-G","C-T","G-T")))throw new IOException("Incomplete source DNA exchangeabilities");
        String description=one(lines,"Model of rate heterogeneity:");Numeric invariant=optional(lines,"Proportion of invariable sites:"),gamma=optional(lines,"Gamma shape alpha:");
        if(invariant!=null)probability(invariant);if(gamma!=null&&gamma.value()<=0)throw new IOException("Invalid source Gamma shape");
        var rates=new ArrayList<Rate>();int categories=0;
        if(!description.equals("Uniform")) {
            Matcher type=Pattern.compile("(Invar\\+)?(FreeRate|Gamma) with ([0-9]+) categories").matcher(description);
            boolean onlyInvariant=description.equals("Invar");
            if(!onlyInvariant&&!type.matches())throw new IOException("Unadmitted source rate-heterogeneity schema: "+description);
            categories=onlyInvariant?1:positive(type.group(3));boolean invar=onlyInvariant||type.group(1)!=null;
            boolean isGamma=!onlyInvariant&&type.group(2).equals("Gamma");
            if(invar!=(invariant!=null)||isGamma!=(gamma!=null))throw new IOException("Incomplete/inconsistent source rate parameters");
            int header=-1;
            for(int i=0;i<lines.length;i++)if(lines[i].trim().matches("Category\\s+Relative_rate\\s+Proportion")){if(header>=0)throw new IOException("Duplicate rate table");header=i;}
            if(header<0)throw new IOException("Missing complete source rate-category table");
            for(int i=header+1;i<lines.length&&!lines[i].isBlank();i++) {
                if(lines[i].startsWith("Relative rates are computed as "))break;
                String[] row=lines[i].trim().split("\\s+");if(row.length!=3)throw new IOException("Malformed source rate-category row");
                int id=nonnegative(row[0]);Numeric rate=finite(row[1]),weight=finite(row[2]);probability(weight);
                if(rate.value()<0)throw new IOException("Negative source category rate");rates.add(new Rate(id,rate,weight));
            }
            if(rates.size()!=categories+(invar?1:0))throw new IOException("Incomplete source rate-category table");
            for(int i=0;i<rates.size();i++)if(rates.get(i).category()!=i+(invar?0:1))throw new IOException("Out-of-order/duplicate source rate categories");
            if(invar && (rates.get(0).rate().value()!=0||!overlap(rates.get(0).proportion(),invariant)))throw new IOException("Invariant category disagrees with its reported proportion");
            intervalSum(rates.stream().map(Rate::proportion).toList(),1,"category proportions");
            double mean=0,error=0;
            for(Rate r:rates) {
                double a=r.rate().value(),b=r.proportion().value(),ea=r.category()==0?0:r.rate().half(),eb=r.proportion().half();
                mean+=a*b;error+=Math.abs(a)*eb+Math.abs(b)*ea+ea*eb;
            }
            if(Math.abs(mean-1)>error+1e-10)throw new IOException("Source category mean rate is not one within printed rounding intervals");
        } else if(invariant!=null||gamma!=null)throw new IOException("Unexpected parameters for uniform source rates");
        if(log==null)throw new IOException("Complete source reports require the original log");
        String logging=readText(log,false,limits.bytes(),cancellation,identities,"log");var ignored=new LinkedHashMap<String,String>();
        Matcher ignoredLine=Pattern.compile("(?m)^NOTE: (\\S+) \\(identical to (\\S+)\\) is ignored but added at the end\\s*$").matcher(logging);
        while(ignoredLine.find()){String before=ignored.putIfAbsent(ignoredLine.group(1),ignoredLine.group(2));if(before!=null&&!before.equals(ignoredLine.group(2)))throw new IOException("Conflicting source duplicate-removal relationships");}
        if(ignored.size()>=taxa)throw new IOException("Invalid source analyzed-population count");
        Selection selection=null;boolean selects=text.contains("Best-fit model according to ");
        if(selects) {
            if(modelCache==null||log==null)throw new IOException("Complete model selection requires its model checkpoint and log");
            String cache=readText(modelCache,true,limits.bytes(),cancellation,identities,"model_checkpoint");
            selection=selection(lines,cache,logging,taxa-ignored.size(),sites,model,limits,cancellation);
            long undefined=selection.candidates().stream().filter(c->!c.classicalAiccDefined()).count();
            if(undefined>0)warnings.add("Source model selection contains "+undefined+" candidates for which classical AICc is undefined. Original clamped source scores and confidence markers are retained separately from statistical validity.");
        } else if(modelCache!=null)throw new IOException("Unexpected model-selection checkpoint for a fixed-model report");
        for(String line:lines)if(line.startsWith("WARNING"))warnings.add(line);
        return new Result(taxa,sites,unit,quality,model,scores,treeLength,frequencyDescription,frequencies,exchangeabilities,description,rates,invariant,gamma,selection,ignored,identities,warnings);
    }
    private static Selection selection(String[] lines,String cache,String log,int taxa,int sites,String finalModel,Limits limits,Runnable cancellation)throws IOException {
        String criterion=null,selected=null;
        for(String line:lines)if(line.startsWith("Best-fit model according to ")) {
            if(criterion!=null)throw new IOException("Duplicate source model-selection result");
            Matcher m=Pattern.compile("Best-fit model according to (AIC|AICc|BIC): (\\S+)").matcher(line);if(!m.matches())throw new IOException("Invalid source selected-model header");criterion=m.group(1);selected=m.group(2);
        }
        if(criterion==null||!selected.equals(finalModel))throw new IOException("Selected and final fitted models disagree");
        String sorted=one(lines,"List of models sorted by");if(!sorted.equals(criterion+" scores:"))throw new IOException("Source model table criterion differs");
        int header=-1;for(int i=0;i<lines.length;i++)if(lines[i].trim().matches("Model\\s+LogL\\s+AIC\\s+w-AIC\\s+AICc\\s+w-AICc\\s+BIC\\s+w-BIC")){if(header>=0)throw new IOException("Duplicate model table");header=i;}
        if(header<0)throw new IOException("Missing complete model-candidate table");
        var rows=new ArrayList<Candidate>();var names=new HashSet<String>();
        for(int i=header+1;i<lines.length&&!lines[i].isBlank();i++) {
            cancellation.run();String[] r=lines[i].trim().split("\\s+");if(r.length!=11||rows.size()>=limits.candidates())throw new IOException("Malformed/over-capacity complete model table");modelName(r[0]);
            if(!names.add(r[0]))throw new IOException("Duplicate source candidate model");
            rows.add(new Candidate(r[0],finite(r[1]),finite(r[2]),weight(r[3],r[4]),numeric(r[5]),weight(r[6],r[7]),finite(r[8]),weight(r[9],r[10]),0,false));
        }
        if(rows.isEmpty())throw new IOException("Empty model table");
        var fitted=new LinkedHashMap<String,Cached>();var metadata=new LinkedHashMap<String,String>();String section=null;
        String[] cacheLines=cache.split("\\R",-1);if(cacheLines.length<2||!cacheLines[0].equals("--- # IQ-TREE Checkpoint ver >= 1.6"))throw new IOException("Unrecognized complete model checkpoint");
        for(int i=1;i<cacheLines.length;i++) {
            cancellation.run();String line=cacheLines[i];if(line.isBlank())continue;int colon=line.indexOf(':');if(colon<1)throw new IOException("Malformed complete model checkpoint");
            boolean nested=Character.isWhitespace(line.charAt(0));String key=line.substring(0,colon).trim(),value=line.substring(colon+1).trim();
            if(nested) {if(section==null)throw new IOException("Orphan model-checkpoint field");key=section+"."+key;}
            else if(value.isEmpty()){section=key;continue;}else section=null;
            String[] tokens=value.split("\\s+");
            if(!nested&&tokens.length==3&&DECIMAL.matcher(tokens[0]).matches()&&tokens[1].matches("[0-9]+")&&DECIMAL.matcher(tokens[2]).matches()) {
                modelName(key);if(fitted.size()>=limits.candidates()||fitted.putIfAbsent(key,new Cached(finite(tokens[0]),nonnegative(tokens[1]),finite(tokens[2])))!=null)throw new IOException("Duplicate/over-capacity cached models");
            } else if(metadata.putIfAbsent(key,value)!=null)throw new IOException("Duplicate model-checkpoint metadata");
        }
        if(!fitted.keySet().equals(names))throw new IOException("Reported candidate set differs from the complete fitted-model checkpoint");
        var complete=new ArrayList<Candidate>();
        for(Candidate row:rows) {
            Cached actual=fitted.get(row.model());if(!overlap(row.logLikelihood(),actual.logLikelihood()))throw new IOException("Model table likelihood differs from its source checkpoint");
            validateScores(new Scores(actual.logLikelihood(),actual.parameters(),row.aic(),row.aicc(),row.bic(),sites>(long)actual.parameters()+1),sites);
            complete.add(new Candidate(row.model(),row.logLikelihood(),row.aic(),row.aicWeight(),row.aicc(),row.aiccWeight(),row.bic(),row.bicWeight(),actual.parameters(),sites>(long)actual.parameters()+1));
        }
        for(String c:List.of("AIC","AICc","BIC")) {
            String best=metadata.get("best_model_"+c),score=metadata.get("best_score_"+c);if(best==null||score==null||!fitted.containsKey(best))throw new IOException("Incomplete source best-model metadata");
            double minimumUpper=Double.POSITIVE_INFINITY;
            for(Candidate row:complete){Numeric value=criterion(row,c);if(value.finite())minimumUpper=Math.min(minimumUpper,value.value()+value.half());}
            Candidate row=complete.stream().filter(x->x.model().equals(best)).findFirst().orElseThrow();Numeric value=criterion(row,c),bestScore=finite(score);
            if(!overlap(value,bestScore)||value.value()-value.half()>minimumUpper+1e-9)throw new IOException("Cached best model is not best under its reported criterion");
            weights(complete,c);
        }
        if(!selected.equals(metadata.get("best_model_"+criterion)))throw new IOException("Selected criterion/model differs from its model checkpoint");
        String ordered=metadata.get("best_model_list_"+criterion);if(ordered==null||!List.of(ordered.split("\\s+")).equals(complete.stream().map(Candidate::model).toList()))throw new IOException("Incomplete or reordered source model table");
        for(var entry:metadata.entrySet())if(entry.getKey().startsWith("best_tree_")||entry.getKey().equals("initTree")||entry.getKey().endsWith(".newick")) {
            var tree=org.jlinalg.phylogeny.NewickTree.parse(entry.getValue());tree.requireMetricLengths();var expected=new HashSet<String>();for(int i=0;i<taxa;i++)expected.add(String.valueOf(i));
            if(!tree.taxa().equals(expected))throw new IOException("Incomplete indexed taxon set in model-selection-stage tree");
        }
        int announced=0;Matcher limit=Pattern.compile("ModelFinder will test (?:up to )?([0-9]+) .*? models \\(sample size: ([0-9]+)").matcher(log);
        while(limit.find()){if(announced!=0)throw new IOException("Duplicate planned-model count");announced=positive(limit.group(1));if(positive(limit.group(2))!=sites)throw new IOException("ModelFinder sample size differs from alignment");}
        if(announced<complete.size())throw new IOException("ModelFinder planned count is absent or smaller than the complete fitted table");
        return new Selection(criterion,selected,complete,fitted,metadata,announced,complete.size());
    }
    private static Numeric criterion(Candidate row,String criterion){return switch(criterion){case "AIC"->row.aic();case "AICc"->row.aicc();default->row.bic();};}
    private static Weight criterionWeight(Candidate row,String criterion){return switch(criterion){case "AIC"->row.aicWeight();case "AICc"->row.aiccWeight();default->row.bicWeight();};}
    private static void weights(List<Candidate> rows,String criterion)throws IOException {
        double reference=Double.POSITIVE_INFINITY;for(Candidate row:rows)if(criterion(row,criterion).finite())reference=Math.min(reference,criterion(row,criterion).value());
        double[] low=new double[rows.size()],high=new double[rows.size()];double sumLow=0,sumHigh=0;
        for(int i=0;i<rows.size();i++){Numeric value=criterion(rows.get(i),criterion);if(!value.finite())continue;low[i]=Math.exp(-.5*(value.value()+value.half()-reference));high[i]=Math.exp(-.5*(value.value()-value.half()-reference));sumLow+=low[i];sumHigh+=high[i];}
        for(int i=0;i<rows.size();i++) {
            Numeric displayed=criterionWeight(rows.get(i),criterion).value();double lo=low[i]/(low[i]+sumHigh-high[i]),hi=high[i]/(high[i]+sumLow-low[i]);
            if(displayed.value()+displayed.half()+1e-10<lo||displayed.value()-displayed.half()-1e-10>hi)throw new IOException("Source model weight disagrees with all complete "+criterion+" score intervals");
        }
    }
    private static Weight weight(String marker,String text)throws IOException {if(!Set.of("+","-").contains(marker))throw new IOException("Unknown model confidence marker");Numeric value=finite(text);probability(value);return new Weight(marker,value);}
    private static void validateScores(Scores scores,int n)throws IOException {
        if(scores.logLikelihood().value()>0)throw new IOException("Positive source whole-alignment log likelihood");
        double k=scores.parameters(),base=-2*scores.logLikelihood().value(),error=2*scores.logLikelihood().half();
        close(scores.aic(),base+2*k,error,"AIC");close(scores.bic(),base+k*Math.log(n),error+1e-8,"BIC");
        if(scores.aicc().finite())close(scores.aicc(),base+2*k+2*k*(k+1)/Math.max(1,n-k-1),error+1e-8,"source AICc");
        else if(scores.classicalAiccDefined())throw new IOException("Nonfinite source AICc despite a defined denominator");
    }
    private static void close(Numeric printed,double expected,double error,String field)throws IOException {if(!Double.isFinite(expected)||Math.abs(printed.value()-expected)>printed.half()+error+1e-9)throw new IOException("Source "+field+" disagrees with reported likelihood/parameter/site dimensions");}
    private static boolean overlap(Numeric first,Numeric second){return Math.abs(first.value()-second.value())<=first.half()+second.half()+1e-10;}
    private static void intervalSum(Iterable<Numeric> values,double expected,String role)throws IOException {double sum=0,error=0;for(Numeric value:values){sum+=value.value();error+=value.half();}if(Math.abs(sum-expected)>error+1e-10)throw new IOException("Source "+role+" do not sum to "+expected+" within printed precision");}
    private static Set<String> states(String unit){var result=new HashSet<String>();if(unit.equals("codon")){for(char a:"ACGT".toCharArray())for(char b:"ACGT".toCharArray())for(char c:"ACGT".toCharArray()){String s=""+a+b+c;if(!Set.of("TAA","TAG","TGA").contains(s))result.add(s);}}else for(char c:(unit.equals("nucleotide")?"ACGT":"ACDEFGHIKLMNPQRSTVWY").toCharArray())result.add(String.valueOf(c));return result;}
    private static void probability(Numeric value)throws IOException {if(value.value()<0||value.value()>1)throw new IOException("Source probability outside [0,1]");}
    private static void modelName(String text)throws IOException {if(text.length()>512||!text.matches("[A-Za-z0-9][A-Za-z0-9_.]*(?:\\+[A-Za-z0-9]+)*"))throw new IOException("Unknown source builtin model-name syntax");}
    private static Numeric numeric(String text)throws IOException {if(text==null||text.length()>128)throw new IOException("Invalid source numeric token");if(DECIMAL.matcher(text).matches()){Numeric value=new Numeric(text);if(!value.finite()||Math.abs((long)value.exact().scale())>4096)throw new IOException("Source numeric range/precision exceeds contract");return value;}if(Set.of("inf","-inf","nan","-nan","Infinity","-Infinity","NaN").contains(text))return new Numeric(text);throw new IOException("Invalid source numeric token: "+text);}
    private static Numeric finite(String text)throws IOException {Numeric value=numeric(text);if(!value.finite())throw new IOException("Nonfinite source core quantity");return value;}
    private static int positive(String text)throws IOException {int value=nonnegative(text);if(value==0)throw new IOException("Expected positive source dimension");return value;}
    private static int nonnegative(String text)throws IOException {try{if(!text.matches("[0-9]{1,10}"))throw new NumberFormatException();return Integer.parseInt(text);}catch(NumberFormatException failure){throw new IOException("Invalid source integer dimension",failure);}}
    private static String one(String[] lines,String prefix)throws IOException {String result=oneAllowEmpty(lines,prefix);if(result.isEmpty())throw new IOException("Empty required IQ-TREE report field: "+prefix);return result;}
    private static String oneAllowEmpty(String[] lines,String prefix)throws IOException {String result=null;for(String line:lines)if(line.startsWith(prefix)){if(result!=null)throw new IOException("Duplicate IQ-TREE report field: "+prefix);result=line.substring(prefix.length()).trim();}if(result==null)throw new IOException("Missing IQ-TREE report field: "+prefix);return result;}
    private static Numeric optional(String[] lines,String prefix)throws IOException {int count=0;String value=null;for(String line:lines)if(line.startsWith(prefix)){count++;value=line.substring(prefix.length()).trim();}if(count>1)throw new IOException("Duplicate optional IQ-TREE report field");return value==null?null:finite(value);}
    private static String readText(Path path,boolean gzip,int maximum,Runnable cancellation,Map<String,IqtreeFiles.Identity> identities,String role)throws IOException {
        var before=IqtreeFiles.identity(path,maximum,cancellation);byte[] raw;
        try(var input=Files.newInputStream(path)){raw=input.readNBytes(maximum+1);}if(raw.length>maximum)throw new IOException("Complete IQ-TREE report byte capacity exceeded");
        byte[] decoded=gzip?gunzip(raw,maximum,cancellation):raw;
        String result=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(decoded)).toString();
        if(!before.equals(IqtreeFiles.identity(path,maximum,cancellation)))throw new IOException("IQ-TREE source report changed during its complete read");identities.put(role,before);return result;
    }
    private static byte[] gunzip(byte[] raw,int maximum,Runnable cancellation)throws IOException {
        if(raw.length<18||(raw[0]&255)!=31||(raw[1]&255)!=139||raw[2]!=8||raw[3]!=0)throw new IOException("Unrecognized original single-member model gzip header");
        var inflater=new Inflater(true);var result=new ByteArrayOutputStream();var crc=new CRC32();
        try {
            inflater.setInput(raw,10,raw.length-10);byte[] buffer=new byte[65536];
            while(!inflater.finished()) {
                cancellation.run();int n=inflater.inflate(buffer);
                if((long)result.size()+n>maximum)throw new IOException("Decoded complete model-checkpoint capacity exceeded");
                if(n>0){result.write(buffer,0,n);crc.update(buffer,0,n);}else if(!inflater.finished())throw new IOException("Incomplete/invalid source model gzip stream");
            }
            int end=raw.length-inflater.getRemaining();if(end+8!=raw.length)throw new IOException("Extra gzip members or trailing source model bytes");
            long expectedCrc=0,expectedSize=0;for(int i=0;i<4;i++){expectedCrc|=(long)(raw[end+i]&255)<<(8*i);expectedSize|=(long)(raw[end+4+i]&255)<<(8*i);}
            if(expectedCrc!=crc.getValue()||expectedSize!=result.size())throw new IOException("Source model gzip checksum/length mismatch");return result.toByteArray();
        }catch(DataFormatException invalid){throw new IOException("Malformed source model gzip deflate payload",invalid);}finally{inflater.end();}
    }
}
