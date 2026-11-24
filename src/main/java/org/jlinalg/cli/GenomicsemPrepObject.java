/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Independently read primitive typed exports, without interpreting opaque RDS. */
final class GenomicsemPrepObject {
    private GenomicsemPrepObject() { }
    record Cell(String token,String type,String value,boolean missing,boolean nonfinite) {
        static Cell parse(String token,String type)throws IOException {
            if(token.equals("N"))return new Cell(token,type,"NA",true,false);
            if(Set.of("Q","P","M").contains(token)){require(type.equals("double"),"Nonfinite token outside a double vector");return new Cell(token,type,token,false,true);}
            require(token.length()>=2&&token.charAt(1)==':',"Malformed original primitive token");char kind=token.charAt(0);String value=token.substring(2);
            switch(type){
                case "character"->{require(kind=='S',"Expected original character token");value=GenomicsemRuntime.unhex(value);}
                case "logical"->require(kind=='B'&&Set.of("TRUE","FALSE").contains(value),"Expected original logical token");
                case "integer"->{require(kind=='I'&&value.matches("-?(?:0|[1-9][0-9]*)"),"Expected original integer token");try{int x=Integer.parseInt(value);require(x!=Integer.MIN_VALUE,"NA integer must retain its missing marker");}catch(NumberFormatException e){throw new IOException("Original integer overflow",e);}}
                case "double"->{require(kind=='D',"Expected original double token");GenomicsemData.numeric(value);}
                default->throw new IOException("Unsupported primitive type");
            }return new Cell(token,type,value,false,false);
        }
        double number()throws IOException {require(!missing&&!nonfinite,"Unavailable/nonfinite original numeric field");return GenomicsemData.numeric(value);}
        String string()throws IOException {require(!missing&&!nonfinite,"Unavailable original label");return value;}
    }
    static final class Node {
        final String kind,type,file;final long length;final List<String> values;final List<Node> children;final Map<String,Node> attributes;
        Node(String k,String t,String f,long n,List<String>v,List<Node>c,Map<String,Node>a){kind=k;type=t;file=f;length=n;values=v;children=c;attributes=a;}
        Node attribute(String name)throws IOException {Node n=attributes.get(name);require(n!=null,"Missing original attribute: "+name);return n;}
        List<String> strings(Tree tree)throws IOException {require(kind.equals("atomic")&&type.equals("character"),"Expected original character vector");List<String> out=new ArrayList<>();try(var cursor=tree.cursor(this)){for(Cell c;(c=cursor.next())!=null;)out.add(c.string());}return out;}
        long[] integers(Tree tree)throws IOException {require(kind.equals("atomic")&&type.equals("integer")&&length<=1000000,"Expected bounded original integer metadata");long[] out=new long[Math.toIntExact(length)];try(var cursor=tree.cursor(this)){for(int i=0;i<out.length;i++)out[i]=Math.round(Objects.requireNonNull(cursor.next()).number());}return out;}
        List<String> names(Tree tree)throws IOException {return attribute("names").strings(tree);}
        Map<String,Node> named(Tree tree)throws IOException {require(kind.equals("list"),"Expected original list/data frame");var names=names(tree);require(names.size()==children.size(),"Original names/list width differs");Map<String,Node> out=new LinkedHashMap<>();for(int i=0;i<names.size();i++)require(out.put(names.get(i),children.get(i))==null,"Duplicated original component name");return out;}
        Node component(Tree tree,String name)throws IOException {Node n=named(tree).get(name);require(n!=null,"Missing original component: "+name);return n;}
        boolean frame(Tree tree)throws IOException {Node n=attributes.get("class");return kind.equals("list")&&n!=null&&n.strings(tree).equals(List.of("data.frame"));}
        double[] numeric(Tree tree)throws IOException {require(kind.equals("atomic")&&Set.of("double","integer").contains(type)&&length<=Integer.MAX_VALUE,"Expected bounded original numeric vector");double[] out=new double[(int)length];try(var cursor=tree.cursor(this)){for(int i=0;i<out.length;i++){Cell c=cursor.next();require(c!=null,"Incomplete original numeric vector");out[i]=c.number();}}return out;}
    }
    static final class Cursor implements AutoCloseable {
        final Node node;final BufferedReader in;final long lineLimit;long count;boolean ended;
        Cursor(Path root,Node n,long limit)throws IOException {node=n;lineLimit=limit;in=n.file==null?null:GenomicsemData.reader(root.resolve(n.file));if(in!=null)require("token".equals(GenomicsemData.line(in,limit)),"Missing original vector header");}
        Cell next()throws IOException {
            require(!ended||count==node.length,"Changed original vector end");if(ended)return null;
            String raw=in==null?(count<node.values.size()?node.values.get((int)count):null):GenomicsemData.line(in,lineLimit);
            if(raw==null){ended=true;require(count==node.length,"Incomplete original primitive vector");return null;}
            require(++count<=node.length,"Extra original primitive value");return Cell.parse(raw,node.type);
        }
        public void close()throws IOException {if(in!=null)in.close();}
    }
    static final class Tree {
        final Path directory;final Node root;final Map<String,Object> request;final Set<String> vectorFiles;
        Tree(Path p,Node n,Map<String,Object>q,Set<String>files){directory=p;root=n;request=q;vectorFiles=files;}
        Cursor cursor(Node n)throws IOException {require(n.kind.equals("atomic"),"Expected atomic original vector");return new Cursor(directory,n,GenomicsemContracts.cap(request,"maximum_line_characters"));}
        Map<String,Object> validate()throws IOException {
            long[] counts={0,0,0};var queue=new ArrayDeque<Node>();queue.add(root);
            while(!queue.isEmpty()){
                Node n=queue.removeFirst();queue.addAll(n.children);queue.addAll(n.attributes.values());if(!n.kind.equals("atomic"))continue;
                try(var c=cursor(n)){for(Cell v;(v=c.next())!=null;){counts[0]++;if(v.missing)counts[1]++;if(v.nonfinite)counts[2]++;}}
            }return map("primitive_cells",counts[0],"missing_cells",counts[1],"nonfinite_cells",counts[2],"vector_files",vectorFiles.size(),"opaque_RDS_interpreted",false);
        }
    }
    private static final class Parser {
        final Map<String,Object>request;long nodes,cells;final Set<String>files=new TreeSet<>();
        Parser(Map<String,Object>q){request=q;}
        Node node(Object value,int depth)throws IOException {
            require(++nodes<=100000&&depth<=48,"Original structure node/depth capacity exceeded");var m=object(value);String kind=text(m.get("kind"),"primitive node kind");
            if(kind.equals("null")){require(m.keySet().equals(Set.of("kind")),"Unexpected null metadata");return new Node(kind,null,null,0,List.of(),List.of(),Map.of());}
            require(Set.of("list","atomic").contains(kind),"Unsupported original structure kind");long length=integer(m.get("length"),0,200000000);Map<String,Node>attributes=new LinkedHashMap<>();
            for(Object a:list(m.get("attributes"))){var attr=object(a);require(attr.keySet().equals(Set.of("name_hex","value")),"Unknown original attribute metadata");String name=GenomicsemRuntime.unhex(text(attr.get("name_hex"),"attribute name"));require(Set.of("names","dim","dimnames","class","row.names","levels","na.action").contains(name)&&!attributes.containsKey(name),"Unsupported/duplicated original attribute");attributes.put(name,node(attr.get("value"),depth+1));}
            if(kind.equals("list")){
                require(m.keySet().equals(Set.of("kind","length","attributes","children")),"Unknown original list metadata");var children=list(m.get("children"));require(children.size()==length,"Original child population differs");List<Node> result=new ArrayList<>();for(Object child:children)result.add(node(child,depth+1));return new Node(kind,null,null,length,List.of(),result,attributes);
            }
            String type=text(m.get("type"),"primitive type");require(Set.of("double","integer","logical","character").contains(type),"Unsupported original primitive type");cells=Math.addExact(cells,length);require(cells<=GenomicsemContracts.cap(request,"maximum_values")&&cells<=GenomicsemContracts.cap(request,"maximum_result_rows"),"Primitive result capacity exceeded");
            boolean external=m.containsKey("file");require(m.keySet().equals(Set.of("kind","type","length","attributes",external?"file":"values")),"Unknown/ambiguous original vector metadata");String file=null;List<String> values=new ArrayList<>();
            if(external){file=text(m.get("file"),"vector file");require(length>128&&file.matches("tables/table-[0-9]{6}\\.tsv")&&files.add(file),"Unsafe/duplicated/noncanonical original vector file");}
            else{var supplied=list(m.get("values"));require(length<=128&&supplied.size()==length,"Changed inline original vector population");for(Object v:supplied){String token=text(v,"primitive token");Cell.parse(token,type);values.add(token);}}
            return new Node(kind,type,file,length,values,List.of(),attributes);
        }
    }
    static Tree readTree(Path directory,Map<String,Object>request)throws IOException {
        require(directory.toRealPath().equals(directory.toAbsolutePath().normalize()),"Use a physical typed object directory");var meta=read(directory.resolve("tree.json"));require(meta.keySet().equals(Set.of("schema_version","root","vector_files","primitive_cells","structure_nodes","opaque_RDS_interpreted"))&&number(meta,"schema_version")==1&&Boolean.FALSE.equals(meta.get("opaque_RDS_interpreted")),"Unsupported typed object schema");
        var parser=new Parser(request);Node root=parser.node(meta.get("root"),0);require(parser.cells==number(meta,"primitive_cells")&&parser.nodes==number(meta,"structure_nodes"),"Changed primitive/node counts");Set<String> declared=new TreeSet<>();for(Object f:list(meta.get("vector_files")))require(declared.add(text(f,"vector file")),"Duplicated vector inventory");require(declared.equals(parser.files),"Unindexed/omitted vector references");
        Set<String> actual=new TreeSet<>();try(var stream=Files.walk(directory)){
            for(Path p:stream.toList()){
                require(!Files.isSymbolicLink(p)&&p.toRealPath().equals(p.toAbsolutePath().normalize()),"Typed object alias/reparse member");if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;String name=directory.relativize(p).toString().replace('\\','/');if(name.equals("tree.json"))continue;require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&name.matches("tables/table-[0-9]{6}\\.tsv"),"Unexpected typed object member");actual.add(name);
            }
        }require(actual.equals(parser.files),"Typed vector file population differs");Tree result=new Tree(directory,root,request,parser.files);var summary=result.validate();require(number(summary,"primitive_cells")==parser.cells,"Original primitive-value population changed");return result;
    }
}
