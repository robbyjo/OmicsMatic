/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;

/** Reviewed field and typed category conversions against an inspected reference registry. */
final class ScviQueryBridge {
    private ScviQueryBridge() { }
    record Value(String source,ScviModelMetadata.Scalar target) {
        Value{Objects.requireNonNull(source);Objects.requireNonNull(target);if(source.isBlank())throw new IllegalArgumentException("Nonblank query category required");}
    }
    record Request(String targetLayer,Map<String,String> rename,Map<String,List<Value>> values,String review) {
        Request{
            Objects.requireNonNull(targetLayer);Objects.requireNonNull(review);rename=Map.copyOf(rename);
            var copy=new TreeMap<String,List<Value>>();values.forEach((k,v)->copy.put(k,List.copyOf(v)));values=Collections.unmodifiableMap(copy);
            if(targetLayer.isBlank()||review.isBlank()||review.length()>16384)throw new IllegalArgumentException("Explicit target layer and query metadata conversion review required");
        }
    }
    record Prepared(Request request,Map<String,Object> protocol,String batch,List<String> categorical,List<String> continuous,String labels,String layer) {
        Prepared{protocol=frozenMap(protocol);categorical=List.copyOf(categorical);continuous=List.copyOf(continuous);}
    }
    static Prepared prepare(ScviCovariates.Prepared query,String sourceLayer,ScviModelMetadata.Info reference,Request request)throws IOException{
        var spec=query.spec();String targetLayer=reference.inputAttribute().equals("X")?"X":reference.inputKey();
        if(!request.targetLayer.equals(targetLayer))throw new IOException("Reviewed query target layer differs from the actual saved reference layer");
        String batch=optional(reference.setup().get("batch_key")),labels=optional(reference.setup().get("labels_key"));
        List<String> cats=reference.categorical().stream().map(ScviModelMetadata.Category::key).toList(),continuous=reference.continuous();
        var expected=new LinkedHashMap<String,String>();pair(spec.batch(),batch,expected);pair(spec.labels(),labels,expected);
        if(spec.categorical().size()!=cats.size()||spec.continuous().size()!=continuous.size())throw new IOException("Query covariate role counts differ from the actual reference;no missing/extra role is inferred");
        for(int i=0;i<cats.size();i++)pair(spec.categorical().get(i),cats.get(i),expected);
        for(int i=0;i<continuous.size();i++)pair(spec.continuous().get(i),continuous.get(i),expected);
        for(var e:request.rename.entrySet())if(!expected.containsKey(e.getKey())||!expected.get(e.getKey()).equals(e.getValue()))throw new IOException("Query field mapping differs from selected reference roles: "+e.getKey());
        for(var e:expected.entrySet())if(!e.getKey().equals(e.getValue())&&!e.getValue().equals(request.rename.get(e.getKey())))throw new IOException("Explicit reviewed query field rename required: "+e.getKey()+" -> "+e.getValue());
        var finalNames=new HashSet<String>();for(String column:query.columns())if(!finalNames.add(request.rename.getOrDefault(column,column)))throw new IOException("Query metadata rename collides with a retained column");
        var registered=new LinkedHashMap<String,List<ScviModelMetadata.Scalar>>();
        if(spec.batch()!=null)registered.put(spec.batch(),reference.batches());
        if(spec.labels()!=null)registered.put(spec.labels(),reference.labels());
        for(int i=0;i<cats.size();i++)registered.put(spec.categorical().get(i),reference.categorical().get(i).values());
        for(String column:request.values.keySet())if(!registered.containsKey(column))throw new IOException("Typed category mapping is not a selected categorical role: "+column);
        var converted=new TreeMap<String,Object>();
        for(var e:registered.entrySet()){
            Set<String> observed=query.observedCategoryCounts().get(e.getKey()).keySet();List<Value> explicit=request.values.get(e.getKey());var mapping=new TreeMap<String,ScviModelMetadata.Scalar>();
            if(explicit==null){
                if(e.getValue().stream().anyMatch(x->!x.kind().equals("string")))throw new IOException("Reference categories retain non-string types;provide an explicit typed value mapping for "+e.getKey());
                for(String source:observed)mapping.put(source,new ScviModelMetadata.Scalar("string",source));
            }else for(Value value:explicit)if(mapping.put(value.source,value.target)!=null)throw new IOException("Duplicate query category source mapping: "+e.getKey()+"/"+value.source);
            if(!mapping.keySet().equals(observed))throw new IOException("Query category conversion must cover exactly every observed source value: "+e.getKey());
            if(e.getKey().equals(spec.labels())){
                if(spec.labelUse()!=ScviCovariates.Labels.UNLABELED_QUERY&&spec.labelUse()!=ScviCovariates.Labels.KNOWN_REFERENCE_QUERY)throw new IOException("Declare unlabeled-only or known-reference-label query semantics");
                for(var value:mapping.values())if(!reference.labels().contains(value))throw new IOException("Mapped query label is outside the inspected reference registry");
                if(reference.family()==ScviModelRequest.Family.SCANVI){if(reference.unlabeled()==null)throw new IOException("Saved SCANVI reference has no inspected unlabeled category");if(observed.contains(spec.unlabeledCategory())&&!reference.unlabeled().equals(mapping.get(spec.unlabeledCategory())))throw new IOException("Query unlabeled source token must map to the actual saved unlabeled category");}
                else if(spec.labelUse()!=ScviCovariates.Labels.KNOWN_REFERENCE_QUERY||observed.contains(spec.unlabeledCategory()))throw new IOException("Label-bearing SCVI queries require known reference labels for every cell;this family has no SCANVI unlabeled-classifier semantics");
            }
            var rows=new ArrayList<Object>();mapping.forEach((source,target)->rows.add(Map.of("source",source,"target",target.json())));converted.put(e.getKey(),rows);
        }
        var protocol=new TreeMap<String,Object>();protocol.put("rename",request.rename);protocol.put("values",converted);protocol.put("source_layer",sourceLayer);protocol.put("reference_layer",targetLayer);protocol.put("review",request.review);protocol.put("no_numeric_units_conversion",true);
        return new Prepared(request,protocol,batch,cats,continuous,labels,targetLayer.equals("X")?null:targetLayer);
    }
    private static String optional(Object value)throws IOException{if(value==null)return null;if(value instanceof String text)return text;throw new IOException("Invalid inspected reference role");}
    private static void pair(String source,String target,Map<String,String> result)throws IOException{if(source==null||target==null){if(source!=target)throw new IOException("Query/reference metadata roles differ");return;}if(result.put(source,target)!=null)throw new IOException("Duplicate query role");}
    static void verifyTransferredRegistry(ScviModelMetadata.Info reference,ScviModelMetadata.Info query)throws IOException{
        if(!reference.genes().equals(query.genes())||reference.family()!=query.family()||!reference.labels().equals(query.labels())||!reference.classes().equals(query.classes())||!Objects.equals(reference.unlabeled(),query.unlabeled())||!reference.continuous().equals(query.continuous()))throw new IOException("Original query transfer changed protected reference gene/label/class/continuous registries");
        prefix(reference.batches(),query.batches());if(reference.categorical().size()!=query.categorical().size())throw new IOException("Transferred categorical roles differ");
        for(int i=0;i<reference.categorical().size();i++){var a=reference.categorical().get(i);var b=query.categorical().get(i);if(!a.key().equals(b.key()))throw new IOException("Transferred category key/order differs");prefix(a.values(),b.values());}
    }
    static void verifyTransferredRegistry(ScviModelMetadata.Info reference,ScviModelMetadata.Info query,Prepared bridge)throws IOException{
        verifyTransferredRegistry(reference,query);var actual=new HashMap<String,List<ScviModelMetadata.Scalar>>();String batch=optional(query.setup().get("batch_key")),labels=optional(query.setup().get("labels_key"));if(batch!=null)actual.put(batch,query.batches());if(labels!=null)actual.put(labels,query.labels());for(var cat:query.categorical())actual.put(cat.key(),cat.values());
        Map<?,?> values=(Map<?,?>)bridge.protocol.get("values");Map<?,?> rename=(Map<?,?>)bridge.protocol.get("rename");
        for(var e:values.entrySet()){String source=(String)e.getKey();Object mapped=rename.get(source);String target=mapped==null?source:(String)mapped;var registry=actual.get(target);if(registry==null)throw new IOException("Transferred model omitted a reviewed categorical target: "+target);for(Object value:(List<?>)e.getValue()){var row=(Map<?,?>)value;var scalar=(Map<?,?>)row.get("target");var expected=new ScviModelMetadata.Scalar((String)scalar.get("kind"),(String)scalar.get("value"));if(!registry.contains(expected))throw new IOException("Original query setup coerced or omitted a reviewed typed category in "+target+";the requested type/value must survive without conversion");}}
    }
    private static void prefix(List<ScviModelMetadata.Scalar> original,List<ScviModelMetadata.Scalar> extended)throws IOException{if(extended.size()<original.size()||!extended.subList(0,original.size()).equals(original))throw new IOException("Original query transfer reordered or coerced existing typed reference categories");}
    private static Map<String,Object> frozenMap(Map<?,?> source){var result=new TreeMap<String,Object>();for(var e:source.entrySet())result.put((String)e.getKey(),frozen(e.getValue()));return Collections.unmodifiableMap(result);}
    private static Object frozen(Object value){if(value instanceof Map<?,?> map)return frozenMap(map);if(value instanceof List<?> list){var result=new ArrayList<Object>();for(Object x:list)result.add(frozen(x));return Collections.unmodifiableList(result);}return value;}
}
