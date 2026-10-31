/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
public final class FavorRuntimeJavaProbe {
 public static void main(String[] args)throws Exception {
  var lines=new ArrayList<String>();lines.add("version\t"+System.getProperty("java.version"));lines.add("home\t"+System.getProperty("java.home"));lines.add("kernel\t"+System.getProperty("os.version"));
  for(String line:Files.readAllLines(Path.of("/proc/self/maps"),StandardCharsets.UTF_8)){int slash=line.indexOf('/');if(slash>=0)lines.add("mapped\t"+line.substring(slash));}
  Files.writeString(Path.of(args[0]),String.join("\n",lines)+"\n",StandardCharsets.UTF_8);
 }
}
