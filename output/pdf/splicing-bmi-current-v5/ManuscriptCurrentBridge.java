package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.yaml.snakeyaml.Yaml;
import org.jlinalg.study.StudyManuscriptPolicy;

/** Output-local layout bridge; actual prose/math rendering and review use the frozen OmicsMatic JAR. */
public final class ManuscriptCurrentBridge {
  static Map<String,Object> map(Object o){return StudyPublicEvidence.map(o);}
  static List<Object> list(Object o){return new ArrayList<>(StudyPublicEvidence.list(o));}
  static String text(Object o){return StudyPublicEvidence.text(o);}
  static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
  static String prose(String s){
    String h=StudyMathTypography.prose(s);
    Matcher m=Pattern.compile("\\[(\\d+(?:,\\d+)*)\\]").matcher(h); StringBuilder result=new StringBuilder();
    while(m.find()){
      StringBuilder cite=new StringBuilder("<sup class=\"citation\">");
      for(String n:m.group(1).split(","))cite.append("<a href=\"#reference-").append(n).append("\">[").append(n).append("]</a>");
      m.appendReplacement(result,Matcher.quoteReplacement(cite.append("</sup>").toString()));
    }
    m.appendTail(result);return result.toString();
  }
  static void blocks(StringBuilder h,List<Object> blocks,Map<String,Object> tables){
    for(Object raw:blocks){var b=map(raw);String kind=text(b.get("kind")),t=text(b.get("text"));
      switch(kind){
        case "heading" -> h.append("<h2>").append(prose(t)).append("</h2>");
        case "subheading" -> h.append("<h3>").append(prose(t)).append("</h3>");
        case "table" -> table(h,map(tables.get(text(b.get("id")))));
        default -> h.append("<p>").append(prose(t)).append("</p>");
      }
    }
  }
  static void table(StringBuilder h,Map<String,Object> t){
    h.append("<section class=\"table-block\"><h3>").append(prose(text(t.get("title")))).append("</h3><table><colgroup>");
    List<Object> widths=list(t.get("widths"));double sum=widths.stream().mapToDouble(v->((Number)v).doubleValue()).sum();
    for(Object w:widths)h.append("<col style=\"width:").append(((Number)w).doubleValue()/sum*100).append("%\">");
    h.append("</colgroup><thead>");int index=0;
    for(Object row:list(t.get("rows"))){
      if(index==1)h.append("</thead><tbody>");h.append("<tr>");
      for(Object cell:list(row)){String tag=index==0?"th":"td";h.append("<").append(tag).append(">").append(prose(text(cell)).replace("\n","<br>")).append("</").append(tag).append(">");}
      h.append("</tr>");index++;
    }
    h.append("</tbody></table><p class=\"table-note\">").append(prose(text(t.get("note")))).append("</p></section>");
  }
  public static void main(String[] args)throws Exception{
    Path b=Path.of(args[0]);var a=map(new Yaml().load(Files.readString(b.resolve("metadata/authored-content.json"))));
    String css=Files.readString(b.resolve("scripts/manuscript.css"));
    var h=new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>"+esc(text(a.get("title")))+"</title><style>"+css+"</style></head><body>");
    h.append("<h1>").append(esc(text(a.get("title")))).append("</h1><section class=\"abstract\"><h2>Abstract</h2>");
    map(a.get("abstract")).forEach((k,v)->h.append("<p><strong>").append(esc(k)).append(". </strong>").append(prose(text(v))).append("</p>"));h.append("</section>");
    blocks(h,list(a.get("blocks")),map(a.get("tables")));
    h.append("<section class=\"references\"><h2>References</h2>");int n=0;
    for(Object r:list(a.get("references"))){n++;String ref=esc(text(r));Matcher link=Pattern.compile("https?://[^\\s]+" ).matcher(ref);StringBuilder linked=new StringBuilder();while(link.find())link.appendReplacement(linked,Matcher.quoteReplacement("<a href=\""+link.group()+"\">"+link.group()+"</a>"));link.appendTail(linked);h.append("<p id=\"reference-").append(n).append("\">").append(linked).append("</p>");}h.append("</section>");
    for(Object raw:list(a.get("figures"))){var f=map(raw);h.append("<section class=\"figure-page\"><h2>").append(esc(text(f.get("title")))).append("</h2><figure><img src=\"figures/").append(esc(text(f.get("file")))).append("\" alt=\"").append(esc(text(f.get("title")))).append("\"><figcaption>").append(prose(text(f.get("caption")))).append("</figcaption></figure></section>");}
    h.append("<section class=\"supplement\"><h2>Supplementary Methods</h2>");blocks(h,list(a.get("supplement")),Map.of());h.append("</section></body></html>");
    Files.writeString(b.resolve("artifacts/manuscript.html"),StudyMathTypography.document(h.toString()));
    Path nativeOut=b.resolve("artifacts/native-render");Files.createDirectories(nativeOut);
    String plain=Files.readString(b.resolve("artifacts/manuscript-draft.txt"));
    var population=list(new Yaml().load(Files.readString(b.resolve("metadata/native-population-tables.json"))));
    StudyManuscriptWriting.writePolicy(nativeOut);
    Files.writeString(nativeOut.resolve("manuscript.html"),StudyAssistance.manuscript(plain,population));
    Files.writeString(nativeOut.resolve("review.json"),StudyAssistance.json(StudyManuscriptWriting.assessDraft(plain,population)));
    Files.writeString(nativeOut.resolve("participant-characteristics.csv"),StudyPublication.table1Csv(population));
    Files.writeString(nativeOut.resolve("participant-characteristics.html"),"<!doctype html><meta charset=\"utf-8\">"+StudyPublication.table1(population));
    System.out.println("Rendered with manuscript policy "+StudyManuscriptPolicy.VERSION+"; native writing/math review retained.");
  }
}
