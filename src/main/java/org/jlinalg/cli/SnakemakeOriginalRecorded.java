/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Portable audit of Linux producer paths and content-addressed state, never access to those paths. */
final class SnakemakeOriginalRecorded {
    record Node(String kind,Pin pin,int mode,String target,String resolved) { }
    record Located(String path,Node node) { }
    private SnakemakeOriginalRecorded() { }
    static String absolute(String value)throws IOException {if(!value.startsWith("/")||value.length()<2)throw new IOException("Recorded path must be canonical absolute Linux text");member(value.substring(1));return value;}
    static boolean within(String path,String root){return path.equals(root)||path.startsWith(root+'/');}
    static String join(String root,String relative)throws IOException {absolute(root);if(relative.isEmpty())return root;return root+'/'+member(relative);}
    static void equal(Object wanted,Object actual,String label)throws IOException {if(!SnakemakeOriginalJson.json(wanted).equals(SnakemakeOriginalJson.json(actual)))throw new IOException("Recorded Snakemake "+label+" differs");}
    static Map<String,Object> copy(Map<?,?> raw)throws IOException {var result=new LinkedHashMap<String,Object>();for(var row:raw.entrySet())result.put(text(row.getKey()),row.getValue());return result;}
    static Map<String,String> bindings(String producer,SnakemakeOriginalCapture.Snapshot captured)throws IOException {var result=new TreeMap<String,String>();for(var row:captured.aliases().entrySet())result.put(row.getKey(),join(producer,"workspace/resources/"+row.getValue()));return Map.copyOf(result);}
    static Map<String,Node> snapshot(SnakemakeOriginalCapture.Snapshot captured)throws IOException {
        var result=new TreeMap<String,Node>();for(var root:captured.roots())for(var row:root.items().entrySet()) {
            var item=row.getValue();String path=join(root.source(),row.getKey());Node node=new Node(item.kind(),item.original()==null?null:item.original().pin(),item.mode(),item.target(),item.resolved());
            if(result.putIfAbsent(path,node)!=null)throw new IOException("Overlapping recorded source roots");
        }return Map.copyOf(result);
    }
    static String link(String parent,String target)throws IOException {
        if(target.isEmpty()||target.indexOf('\\')>=0||target.chars().anyMatch(c->c<32||c==127))throw new IOException("Invalid recorded link target");
        var parts=new ArrayList<String>();String raw=target.startsWith("/")?target:parent+'/'+target;
        for(String part:raw.split("/",-1)) {if(part.isEmpty()||part.equals("."))continue;if(part.equals("..")){if(!parts.isEmpty())parts.remove(parts.size()-1);}else parts.add(part);}
        return absolute('/'+String.join("/",parts));
    }
    static Located resolve(Map<String,Node> nodes,String supplied,boolean allowMissing)throws IOException {
        String path=absolute(supplied);var seen=new HashSet<String>();
        for(int attempt=0;attempt<=1000;attempt++) {
            if(!seen.add(path))throw new IOException("Recorded symlink cycle");boolean changed=false;String prefix="";
            for(String part:path.substring(1).split("/")) {
                prefix+='/'+part;Node node=nodes.get(prefix);
                if(node!=null&&node.kind().equals("symlink")){String parent=prefix.substring(0,prefix.lastIndexOf('/'));String target=link(parent,node.target());path=target+path.substring(prefix.length());changed=true;break;}
            }
            if(changed)continue;Node node=nodes.get(path);if(node==null&&!allowMissing)throw new IOException("Recorded target is absent from the complete owned state: "+path);return new Located(path,node);
        }throw new IOException("Recorded link-resolution capacity exceeded");
    }
    static Map<String,Node> nodes(Object value,long maximum)throws IOException {
        var result=new TreeMap<String,Node>();for(var row:object(value).entrySet()) {
            String name=text(row.getKey());if(!name.isEmpty())member(name);Map<?,?> item=object(row.getValue());keys(item,"kind","pin","mode","target","resolved");String kind=text(item.get("kind"));
            if(!Set.of("file","directory","symlink","fifo","socket").contains(kind))throw new IOException("Unknown recorded owned member kind");
            Pin pin=kind.equals("file")?fingerprint(item.get("pin"),maximum):null;int mode=(int)integer(item.get("mode"),-1,0777);String target=kind.equals("symlink")?text(item.get("target")):null,resolved=item.get("resolved")==null?null:absolute(text(item.get("resolved")));
            if(!kind.equals("file")&&item.get("pin")!=null||!kind.equals("symlink")&&(item.get("target")!=null||resolved!=null)||kind.equals("symlink")&&mode!=-1||!kind.equals("symlink")&&mode<0)throw new IOException("Owned state member metadata disagree with its kind");
            result.put(name,new Node(kind,pin,mode,target,resolved));
        }
        if(!result.containsKey("")||!result.get("").kind().equals("directory"))throw new IOException("Missing recorded owned root directory");
        for(String name:result.keySet())if(!name.isEmpty()){int slash=name.lastIndexOf('/');String parent=slash<0?"":name.substring(0,slash);if(!result.containsKey(parent)||!result.get(parent).kind().equals("directory"))throw new IOException("Incomplete owned directory census");}
        return Map.copyOf(result);
    }
    static void sources(SnakemakeOriginalRecipe.Request request,SnakemakeOriginalCapture.Snapshot capture)throws IOException {
        if(!capture.aliases().keySet().equals(request.sources().keySet()))throw new IOException("Captured and declared resource IDs differ");
        var reduced=new ArrayList<String>();for(String path:request.sources().values().stream().distinct().sorted(java.util.Comparator.comparingInt((String s)->s.split("/").length).thenComparing(s->s)).toList())if(reduced.stream().noneMatch(root->within(path,root)))reduced.add(path);
        if(reduced.size()!=capture.roots().size())throw new IOException("Captured resource-root reduction differs");
        for(int i=0;i<reduced.size();i++){var root=capture.roots().get(i);String expected=reduced.get(i);if(!root.key().equals("r"+i)||!root.source().equals(expected)||!root.leaf().equals(expected.substring(expected.lastIndexOf('/')+1)))throw new IOException("Recorded resource root/key/basename differs");}
        for(var row:capture.aliases().entrySet()) {
            String[] parts=row.getValue().split("/",3);var root=capture.roots().stream().filter(r->r.key().equals(parts[0])).findFirst().orElseThrow(()->new IOException("Missing declared resource root"));
            String original=root.source()+(parts.length==3?'/'+parts[2]:"");if(!original.equals(request.sources().get(row.getKey())))throw new IOException("Captured resource alias does not bind its declared source");
        }
        Map<String,Node> originals=snapshot(capture);
        for(Node node:originals.values())if(!Set.of("file","directory","symlink").contains(node.kind())||node.kind().equals("symlink")&&(node.resolved()==null||!originals.containsKey(node.resolved())||originals.get(node.resolved()).kind().equals("symlink")))throw new IOException("Original input is special, unresolved or not captured in full");
    }
    static void initial(String producer,SnakemakeOriginalCapture.Snapshot inputs,SnakemakeOriginalCapture.Snapshot before)throws IOException {
        Map<String,Node> actual=snapshot(before),originals=snapshot(inputs);var wanted=new HashSet<String>();wanted.add(producer+"/workspace/resources");var oldKeys=new HashMap<String,String>();var newKeys=new HashMap<String,String>();
        for(var root:inputs.roots()) {
            String parent=producer+"/workspace/resources/"+root.key(),prefix=parent+'/'+root.leaf();wanted.add(parent);
            for(var row:root.items().entrySet()) {
                String name=join(prefix,row.getKey());wanted.add(name);var item=row.getValue();Node got=actual.get(name);
                if(got==null||!got.kind().equals(item.kind())||!item.kind().equals("symlink")&&got.mode()!=item.mode()||item.kind().equals("file")&&!got.pin().equals(item.original().pin()))throw new IOException("Initial staged input bytes, kinds or modes differ");
                if(item.kind().equals("symlink")) {
                    String mapped=null;for(var target:inputs.roots())if(within(item.resolved(),target.source())){mapped=producer+"/workspace/resources/"+target.key()+'/'+target.leaf()+item.resolved().substring(target.source().length());break;}
                    if(mapped==null||!mapped.equals(got.resolved())||!resolve(actual,name,false).path().equals(mapped))throw new IOException("Staged input symlink topology differs");
                }
                if(item.kind().equals("file")) {
                    var staged=before.roots().get(0).items().get(name.substring((producer+"/workspace/").length()));String a=item.original().fileKey(),b=staged.original().fileKey();
                    if(!a.equals("null")&&!b.equals("null")){String prior=oldKeys.putIfAbsent(a,b),reverse=newKeys.putIfAbsent(b,a);if(prior!=null&&!prior.equals(b)||reverse!=null&&!reverse.equals(a))throw new IOException("Declared internal hardlink partition changed during staging");}
                }
            }
        }
        var observed=new HashSet<String>();for(String name:actual.keySet())if(within(name,producer+"/workspace/resources"))observed.add(name);if(!wanted.equals(observed))throw new IOException("Initial complete staged resource census differs");
    }
    static void add(Map<String,Pin> values,Pin pin)throws IOException {Pin prior=values.putIfAbsent(pin.sha256(),pin);if(prior!=null&&!prior.equals(pin))throw new IOException("Inconsistent blob byte count for one SHA256");}
    static void capturePins(SnakemakeOriginalCapture.Snapshot capture,Map<String,Pin> values)throws IOException {for(var root:capture.roots())for(var item:root.items().values())if(item.kind().equals("file"))add(values,item.original().pin());}
}
