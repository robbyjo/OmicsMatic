/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.omics.CellChatProjection;

/** Standalone prepared-PPI projection and complete portable replay; installs nothing. */
public final class CellChatProjectionCli {
    private CellChatProjectionCli() { }
    private static final String ACTION="Require exit 0, result_admitted=true in COMPLETED.json and successful --verify before using projected.tsv. Read WARNINGS.md. On FAIL2, admit no result: preserve the complete attempt, correct the named input/capacity/numerical cause and retry a NEW output directory. This native projection installs nothing. It neither selects a PPI nor infers a paper's raw.use setting; upstream inference must explicitly consume projected expression. Limits are logical capacities, not hard RAM/time quotas.";
    private static final Set<String> FILES=Set.of("request.json","expression.tsv","ppi.tsv","projected.tsv","projection.json","WARNINGS.md");
    private record Matrix(String[] rows,String[] columns,double[][] values) { }
    private record Settings(double alpha,CellChatProjection.Normalization normalization,
            CellChatProjection.Limits limits,int inputBytes) { }

    public static void main(String[] args) { System.exit(run(args)); }
    static int run(String[] args) {
        Path out=null;
        try {
            if(args.length==2 && args[0].equals("--verify")) {
                verify(Path.of(args[1]).toAbsolutePath().normalize());System.out.println("PASS_COMPLETE_NATIVE_PROJECTION_REPLAY");return 0;
            }
            require(args.length==2 && args[0].equals("--request"),"Usage: java -jar cellchat-projection-development.jar --request request.json | --verify OUTPUT_DIRECTORY");
            Path request=Path.of(args[1]).toAbsolutePath().normalize();byte[] requestBytes=read(request,2*1024*1024);
            Map<?,?> root=object(SimpleJson.parse(utf8(requestBytes)));Settings settings=settings(root);
            Path base=request.getParent(),target=base.resolve(text(root.get("out"))).toAbsolutePath().normalize();
            require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output directory must be NEW");
            Files.createDirectory(target);out=target;
            Files.write(out.resolve("request.json"),requestBytes,StandardOpenOption.CREATE_NEW);
            Path expressionPath=base.resolve(text(root.get("expression"))).toAbsolutePath().normalize();
            Path ppiPath=base.resolve(text(root.get("ppi"))).toAbsolutePath().normalize();
            byte[] expressionBytes=read(expressionPath,settings.inputBytes),ppiBytes=read(ppiPath,settings.inputBytes);
            require((long)expressionBytes.length+ppiBytes.length<=settings.inputBytes,"Complete inputs exceed total input_bytes capacity");
            Files.write(out.resolve("expression.tsv"),expressionBytes,StandardOpenOption.CREATE_NEW);
            Files.write(out.resolve("ppi.tsv"),ppiBytes,StandardOpenOption.CREATE_NEW);
            Map<String,Object> report=project(out,settings,true);
            require(Arrays.equals(requestBytes,read(request,2*1024*1024))
                && Arrays.equals(expressionBytes,read(expressionPath,settings.inputBytes))
                && Arrays.equals(ppiBytes,read(ppiPath,settings.inputBytes)),"Original request/input changed during projection; no result");
            writeJson(out.resolve("projection.json"),report);
            var warnings=new ArrayList<String>();for(Object warning:list(report.get("warnings"))) warnings.add(text(warning));
            Files.writeString(out.resolve("WARNINGS.md"),"# PROJECTION WARNINGS AND REQUIRED ACTIONS\n\n"+String.join("\n\n",warnings)+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Map<String,Object> manifest=new LinkedHashMap<>();for(String name:FILES) manifest.put(name,pin(read(out.resolve(name),Math.max(settings.inputBytes,2*1024*1024))));
            writeJson(out.resolve("COMPLETED.json"),Map.of("schema_version",1,"status","completed-native-cellchat-ppi-projection","result_admitted",true,"files",manifest,"required_action",ACTION));
            verify(out);System.out.println("PASS_NATIVE_CELLCHAT_PPI_PROJECTION "+out);
            for(String warning:warnings) System.err.println("WARNING: "+warning);
            return 0;
        } catch(Exception failure) {
            System.err.println("FAIL2_CELLCHAT_PROJECTION: "+failure+"\nREQUIRED ACTION: "+ACTION);
            if(out!=null) try {
                if(Files.exists(out.resolve("COMPLETED.json"))) Files.move(out.resolve("COMPLETED.json"),out.resolve("UNADMITTED-completion.json"));
                if(Files.exists(out.resolve("projected.tsv"))) Files.move(out.resolve("projected.tsv"),out.resolve("UNADMITTED-projected.tsv"));
                writeJson(out.resolve("FAILED.json"),Map.of("status","FAIL2_CELLCHAT_PROJECTION","result_admitted",false,"cause",failure.toString(),"required_action",ACTION));
                Files.writeString(out.resolve("WARNINGS.md"),"# FAIL2: NO RESULT ADMITTED\n\n"+failure+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            } catch(IOException diagnostic) { System.err.println("FAIL2 saving failure diagnostics: "+diagnostic); }
            return 2;
        }
    }

    private static Settings settings(Map<?,?> r)throws IOException {
        keys(r,"schema_version","out","expression","ppi","expression_units","alpha","normalization","limits");
        require(integer(r.get("schema_version"),1,1)==1,"Unsupported schema");
        for(String key:List.of("out","expression","ppi")) require(!text(r.get(key)).isEmpty(),"Empty explicit path");
        require(text(r.get("expression_units")).equals("external-log-normalized-nonnegative"),"Explicit externally log-normalized expression required");
        require(Set.of("rows","columns").contains(text(r.get("normalization"))),"Explicit rows or columns normalization required");
        double alpha=number(r.get("alpha"));require(alpha>0 && alpha<1,"Original projectData requires 0 < alpha < 1");
        Map<?,?> l=object(r.get("limits"));keys(l,"input_bytes","max_network_genes","max_expression_genes","max_cells","working_bytes","arithmetic_updates");
        var limits=new CellChatProjection.Limits((int)integer(l.get("max_network_genes"),1,100000),
            (int)integer(l.get("max_expression_genes"),1,1000000),(int)integer(l.get("max_cells"),1,1000000),
            integer(l.get("working_bytes"),1,1099511627776L),integer(l.get("arithmetic_updates"),1,Long.MAX_VALUE));
        return new Settings(alpha,text(r.get("normalization")).equals("rows")?CellChatProjection.Normalization.ROWS:CellChatProjection.Normalization.COLUMNS,
            limits,(int)integer(l.get("input_bytes"),1,512*1024*1024));
    }

    private static Map<String,Object> project(Path out,Settings s,boolean write)throws IOException {
        byte[] expression=read(out.resolve("expression.tsv"),s.inputBytes),ppi=read(out.resolve("ppi.tsv"),s.inputBytes);
        require((long)expression.length+ppi.length<=s.inputBytes,"Complete inputs exceed total byte capacity");
        Matrix e=matrix(expression,s.limits.maxExpressionGenes(),s.limits.maxCells(),s.limits.maxWorkingBytes());
        Matrix a=matrix(ppi,s.limits.maxNetworkGenes(),s.limits.maxNetworkGenes(),s.limits.maxWorkingBytes());
        require(Arrays.equals(a.rows,a.columns),"PPI row and column gene axes must be identical in complete order");
        CellChatProjection.Result result=CellChatProjection.project(e.rows,e.values,a.rows,a.values,s.alpha,s.normalization,s.limits);
        byte[] projected=render(e,result.expression());
        require(projected.length<=s.inputBytes,"Complete output exceeds declared input_bytes transport capacity");
        if(write) {
            Path provisional=out.resolve("projected.partial.tsv");Files.write(provisional,projected,StandardOpenOption.CREATE_NEW);
            Files.move(provisional,out.resolve("projected.tsv"),StandardCopyOption.ATOMIC_MOVE);
        } else require(Arrays.equals(projected,read(out.resolve("projected.tsv"),s.inputBytes)),"Complete numerical projection replay differs");
        Map<String,Object> report=new LinkedHashMap<>();report.put("schema_version",1);report.put("alpha",s.alpha);report.put("normalization",s.normalization.toString());
        report.put("expression_genes",e.rows.length);report.put("cells",e.columns.length);report.put("network_genes",a.rows.length);
        report.put("overlapping_genes",result.overlappingGenes());report.put("network_only_genes",result.networkOnlyGenes());
        report.put("relative_residual",result.relativeResidual());report.put("residual_tolerance",result.residualTolerance());report.put("warnings",result.warnings());
        report.put("expression_pin",pin(expression));report.put("ppi_pin",pin(ppi));report.put("projected_pin",pin(projected));
        report.put("scope","Complete dense PPI solve followed by recombination. No communication inference, historical environment or biological validation.");
        return report;
    }

    private static void verify(Path out)throws IOException {
        require(Files.isDirectory(out,LinkOption.NOFOLLOW_LINKS),"Verification requires a real output directory");
        Map<?,?> completed=object(SimpleJson.parse(utf8(read(out.resolve("COMPLETED.json"),2*1024*1024))));
        keys(completed,"schema_version","status","result_admitted","files","required_action");
        integer(completed.get("schema_version"),1,1);
        require(Boolean.TRUE.equals(completed.get("result_admitted")) && "completed-native-cellchat-ppi-projection".equals(completed.get("status")) && ACTION.equals(completed.get("required_action")),"No admitted projection manifest");
        Map<?,?> manifest=object(completed.get("files"));require(manifest.keySet().equals(FILES),"Incomplete or extra manifest family");
        try(var paths=Files.list(out)) {
            Set<String> actual=new java.util.HashSet<>();for(Path path:paths.toList()) actual.add(path.getFileName().toString());
            var expected=new java.util.HashSet<>(FILES);expected.add("COMPLETED.json");require(actual.equals(expected),"Incomplete or unexpected output file population");
        }
        Map<?,?> root=object(SimpleJson.parse(utf8(read(out.resolve("request.json"),2*1024*1024))));Settings s=settings(root);
        for(String name:FILES) {
            Map<?,?> expected=object(manifest.get(name));keys(expected,"bytes","sha256");
            long count=integer(expected.get("bytes"),0,Math.max(s.inputBytes,2*1024*1024));
            Map<String,Object> actual=pin(read(out.resolve(name),Math.max(s.inputBytes,2*1024*1024)));
            require(count==((Number)actual.get("bytes")).longValue() && text(expected.get("sha256")).equals(actual.get("sha256")),"File hash/size mismatch: "+name);
        }
        Map<String,Object> report=project(out,s,false);
        require((NextflowJson.json(report)+"\n").equals(utf8(read(out.resolve("projection.json"),2*1024*1024))),"Projection diagnostics/replay mismatch");
    }

    private static Matrix matrix(byte[] raw,int maxRows,int maxColumns,long workingBytes)throws IOException {
        String[] lines=utf8(raw).split("\n",-1);int count=lines.length;if(lines[count-1].isEmpty()) count--;
        require(count>=2 && count-1<=maxRows,"Complete matrix row population outside capacity");
        String[] header=line(lines[0]).split("\t",-1);require(header.length>=2 && header.length-1<=maxColumns && header[0].equals("gene_id"),"Expected gene_id and complete named columns within capacity");
        require(32L*(count-1)*(header.length-1)<=workingBytes,"Complete matrix exceeds declared working-byte capacity before allocation");
        String[] columns=Arrays.copyOfRange(header,1,header.length),rows=new String[count-1];identifiers(columns);
        double[][] values=new double[rows.length][columns.length];
        for(int i=1;i<count;i++) {
            String[] fields=line(lines[i]).split("\t",-1);require(fields.length==header.length,"Ragged/blank matrix row: "+i);rows[i-1]=fields[0];
            for(int j=1;j<fields.length;j++) {
                require(!fields[j].isEmpty() && fields[j].equals(fields[j].strip()),"Invalid/implicitly trimmed numeric input at row "+i);
                try { values[i-1][j-1]=Double.parseDouble(fields[j]); } catch(NumberFormatException invalid) { throw new IOException("Invalid numeric matrix field at row "+i,invalid); }
                require(Double.isFinite(values[i-1][j-1]) && values[i-1][j-1]>=0,"Nonfinite/negative matrix input at row "+i);
            }
        }
        identifiers(rows);return new Matrix(rows,columns,values);
    }
    private static String line(String s) { return s.endsWith("\r")?s.substring(0,s.length()-1):s; }
    private static void identifiers(String[] names)throws IOException {
        Set<String> seen=new java.util.HashSet<>();for(String name:names) require(!name.isEmpty() && name.equals(name.strip()) && name.chars().noneMatch(c->Character.isISOControl(c)) && seen.add(name),"Empty/duplicate/implicitly trimmed/control identifier");
    }
    private static byte[] render(Matrix e,double[][] projected) {
        var s=new StringBuilder("gene_id");for(String cell:e.columns) s.append('\t').append(cell);s.append('\n');
        for(int i=0;i<e.rows.length;i++) { s.append(e.rows[i]);for(double value:projected[i]) s.append('\t').append(Double.toString(value));s.append('\n'); }
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] read(Path file,int max)throws IOException {
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && Files.size(file)<=max,"Missing, linked or oversized file: "+file);
        try(var input=Files.newInputStream(file)) { byte[] raw=input.readNBytes(max+1);require(raw.length<=max,"File grew beyond complete-read capacity: "+file);return raw; }
    }
    private static String utf8(byte[] raw)throws IOException {
        try { return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString(); }
        catch(java.nio.charset.CharacterCodingException invalid) { throw new IOException("Malformed UTF-8",invalid); }
    }
    private static Map<String,Object> pin(byte[] raw) {
        try { return Map.of("bytes",raw.length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))); }
        catch(NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    private static void writeJson(Path file,Object value)throws IOException { Files.writeString(file,NextflowJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
    private static Map<?,?> object(Object value)throws IOException { require(value instanceof Map<?,?>,"Expected JSON object");return (Map<?,?>)value; }
    private static List<?> list(Object value)throws IOException { require(value instanceof List<?>,"Expected JSON list");return (List<?>)value; }
    private static String text(Object value)throws IOException { require(value instanceof String,"Expected JSON string");return (String)value; }
    private static void keys(Map<?,?> value,String... names)throws IOException { require(value.keySet().equals(Set.of(names)),"Missing/unknown JSON field: "+value.keySet()); }
    private static double number(Object value)throws IOException { require(value instanceof Number,"Expected JSON number");double n=((Number)value).doubleValue();require(Double.isFinite(n),"Nonfinite parameter");return n; }
    private static long integer(Object value,long min,long max)throws IOException {
        require(value instanceof Number,"Expected JSON integer");
        try { long n=new java.math.BigDecimal(value.toString()).longValueExact();require(n>=min && n<=max,"Integer outside capacity");return n; }
        catch(ArithmeticException invalid) { throw new IOException("Expected exact bounded integer",invalid); }
    }
    private static void require(boolean condition,String message)throws IOException { if(!condition) throw new IOException(message); }
}
