/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.jlinalg.cli.FollowupSupport.*;

/** Original pinned Trimmomatic applications, with explicit JVM and isolated outputs. */
final class TrimmomaticRuntime {
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String message,Exception cause){super(message,cause);}}
    private final Path home,java,jar;private final String version;private final int javaMajor;private final Map<Path,String> protectedFiles=new LinkedHashMap<>();private final Map<String,Object> session;
    TrimmomaticRuntime(Path supplied,Path suppliedJava,Path dir,Map<String,Object> session)throws Exception{
        this.session=session;home=supplied.toAbsolutePath().normalize();
        try{
            Path marker=home.resolve(".jlinalg-trimmomatic.json");Map<?,?> m=RegenieRuntime.object(marker);version=String.valueOf(m.get("version"));String expectedJar,expectedArchive;
            if(version.equals("0.41")){expectedJar="63382643321833f844ebe6099cfd95d9cb653832d9624d793b8643d6bb56793f";expectedArchive="1367951f1f55852430112e56fe5a72d3b71548c6e636e12e4af9aecaf699b189";}else if(version.equals("0.39")){expectedJar="95681299ca2099a24095103c7d9b52041f2c1cab87e7f73735be3560c2e967a3";expectedArchive="2f7a5de11416899e5b38d3131019bc655946f770f9b7df5355d6a36acbcc488c";}else throw new IOException("Managed profile must be Trimmomatic0.39or0.41");
            if(!expectedArchive.equals(m.get("archive_sha256"))||!(m.get("files") instanceof Map<?,?> files)||files.isEmpty())throw new IOException("Pinned program marker differs");jar=home.resolve("trimmomatic-"+version+".jar");for(var e:files.entrySet()){Path p=RegenieRuntime.contained(home,String.valueOf(e.getKey()));String h=String.valueOf(e.getValue());if(!h.matches("[0-9a-f]{64}")||!Files.isRegularFile(p)||!hash(p).equals(h))throw new IOException("Runtime member changed: "+p);protectedFiles.put(p,h);}if(!expectedJar.equals(protectedFiles.get(jar)))throw new IOException("Pinned original JAR differs");protectedFiles.put(marker,hash(marker));
            java=(suppliedJava==null?Path.of(String.valueOf(m.get("java_executable"))):suppliedJava).toAbsolutePath().normalize();if(!Files.isRegularFile(java))throw new IOException("Selected Java executable unavailable: "+java);protectedFiles.put(java,hash(java));
            Path javaHome=java.getParent().getParent(),javaMarker=javaHome.resolve(".jlinalg-java-runtime.json");if(Files.isRegularFile(javaMarker)){Map<?,?> jm=RegenieRuntime.object(javaMarker);if(!"4c95451cea98556def2c54f7782933f52a26d4a36bd85e1d59f0364464828b07".equals(jm.get("archive_sha256"))||!(jm.get("files") instanceof Map<?,?> jf)||jf.isEmpty())throw new IOException("Managed private Java identity differs");for(var e:jf.entrySet()){Path p=RegenieRuntime.contained(javaHome,String.valueOf(e.getKey()));String h=String.valueOf(e.getValue());if(!Files.isRegularFile(p)||!hash(p).equals(h))throw new IOException("Private Java member changed: "+p);protectedFiles.put(p,h);}protectedFiles.put(javaMarker,hash(javaMarker));session.put("java_origin","Pinned private Temurin25.0.4.1+1 Windows x64 JRE; all members hashed");}else session.put("java_origin","Explicit existing Java installation; executable hashed, external runtime libraries not sealed");
            execute(List.of(java.toString(),"-version"),dir,"java-version",60);String text=Files.readString(dir.resolve("java-version.log"));var matcher=Pattern.compile("(?:java|openjdk) version \"(\\d+)(?:\\.(\\d+))?").matcher(text);if(!matcher.find())throw new IOException("Actual Java version not recognized");int major=Integer.parseInt(matcher.group(1));javaMajor=major==1?Integer.parseInt(matcher.group(2)):major;if(javaMajor<(version.equals("0.41")?25:8))throw new IOException("Trimmomatic"+version+" requires Java"+(version.equals("0.41")?25:8)+"+; actual Java"+javaMajor);
            execute(List.of(java.toString(),"-jar",jar.toString(),"-version"),dir,"program-version",60);if(!Files.readString(dir.resolve("program-version.log")).strip().equals(version))throw new IOException("Actual Trimmomatic version differs");session.put("upstream_version",version);session.put("upstream_jar_sha256",expectedJar);session.put("java_major",javaMajor);session.put("java_executable_sha256",hash(java));
        }catch(IOException|IllegalArgumentException e){throw new Unavailable("Trimmomatic/JVM dependency unavailable or changed: "+e.getMessage(),e);}
    }
    String version(){return version;}
    Path adapter(String name)throws IOException{if(!name.matches("[A-Za-z0-9_.-]+"))throw new IOException("Named private adapter must be a simple filename");Path p=home.resolve("adapters").resolve(name);if(!protectedFiles.containsKey(p))throw new IOException("Named private adapter is absent from verified runtime: "+name);return p;}
    void trim(Path dir,List<String> options,int threads,int memory,int timeout)throws Exception{var command=new ArrayList<>(List.of(java.toString(),"-Xmx"+memory+"m"));if(javaMajor>=10)command.add("-XX:ActiveProcessorCount="+threads);command.addAll(List.of("-jar",jar.toString()));command.addAll(options);execute(command,dir,"analysis",timeout);if(!Files.readString(dir.resolve("analysis.log")).contains("Completed successfully"))throw new IOException("Upstream application did not report successful completion; inspect analysis.log");}
    private void execute(List<String> command,Path dir,String stage,int timeout)throws Exception{
        session.put(stage+"_command",List.copyOf(command));var builder=new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve(stage+".log").toFile());builder.environment().put("LC_ALL","C");builder.environment().put("MALLOC_ARENA_MAX","2");Process p;try{p=builder.start();}catch(IOException e){throw new Unavailable("Cannot launch selected Java/application: "+e.getMessage(),e);}try{if(!p.waitFor(timeout,TimeUnit.SECONDS)){p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();p.waitFor(30,TimeUnit.SECONDS);throw new IOException("Trimmomatic/JVM timed out; retained stage logs");}}catch(InterruptedException e){p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();Thread.currentThread().interrupt();throw e;}session.put(stage+"_exit_code",p.exitValue());if(p.exitValue()!=0)throw new IOException("Upstream Java/application failed, exit"+p.exitValue()+"; inspect "+stage+".log");verify();
    }
    void verify()throws IOException{RegenieRuntime.verify(protectedFiles);}
}
