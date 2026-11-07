/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.MemeXml;
import org.jlinalg.sequence.TomtomResults;

/** Complete original report JSON is independently bound to the already audited original XML. */
final class TomtomHtml {
    private TomtomHtml() { }
    record Receipt(NextflowFiles.Pin html,int models,int matches) {
        Map<String,Object> document(){return Map.of("html",MemeRuntimeFiles.pin(html),"complete_models",models,"complete_reported_matches",matches,"all_original_display_data_matches_audited_XML",true,"scope","Complete embedded original scientific/metadata JSON and terminal HTML carrier. Original scripts/styles are retained; this is not a browser-rendering or arbitrary JavaScript security certificate.");}
    }
    static Receipt read(Path path,TomtomResults.Audit audited,int maximumBytes,int maximumNodes,Runnable check)throws IOException {
        var before=NextflowFiles.pin(path,maximumBytes,check);String text=NextflowFiles.utf8(NextflowFiles.bytes(path,maximumBytes));require(text.startsWith("<!DOCTYPE HTML>\n")&&text.endsWith("</html>\n"),"Incomplete original Tomtom HTML carrier");
        String marker="// @JSON_VAR data";int at=text.indexOf(marker);require(at>=0&&at==text.lastIndexOf(marker),"Require exactly one original Tomtom report data marker");int assignment=text.indexOf("var data =",at+marker.length());require(assignment>=0&&text.substring(at+marker.length(),assignment).isBlank(),"Unexpected original HTML data assignment");int start=assignment+"var data =".length();while(start<text.length()&&Character.isWhitespace(text.charAt(start)))start++;
        require(start<text.length()&&text.charAt(start)=='{',"Missing original HTML JSON object");int depth=0,end=-1;boolean string=false,escape=false;
        for(int i=start;i<text.length();i++){
            if((i&4095)==0)check.run();char c=text.charAt(i);if(string){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')string=false;continue;}if(c=='"'){string=true;continue;}if(c=='{'||c=='['){if(++depth>80)throw new IOException("Original HTML JSON depth exceeds capacity");}else if(c=='}'||c==']'){if(--depth==0){end=i+1;break;}if(depth<0)throw new IOException("Invalid original HTML JSON delimiters");}
        }
        require(end>start&&!string,"Truncated original HTML data object");String json=text.substring(start,end);require(!json.toLowerCase(java.util.Locale.ROOT).contains("</script"),"Original report data prematurely closes its script carrier");int semicolon=end;while(semicolon<text.length()&&Character.isWhitespace(text.charAt(semicolon)))semicolon++;require(semicolon<text.length()&&text.charAt(semicolon)==';',"Missing complete original report data terminator");int close=semicolon+1;while(close<text.length()&&Character.isWhitespace(text.charAt(close)))close++;require(text.startsWith("</script>",close),"Unexpected content in original report data script");
        var actual=NextflowFiles.object(NextflowParameters.parse(json,maximumBytes,maximumNodes));var expected=expected(audited.xml().root());equal(actual,expected,"complete original HTML scientific/metadata object");check.run();require(before.equals(NextflowFiles.pin(path,maximumBytes,check)),"Original HTML changed during whole report audit");return new Receipt(before,audited.printedModels(),audited.matches().size());
    }
    private static Map<String,Object> expected(MemeXml.Node root)throws IOException {
        var model=root.one("model");var result=new LinkedHashMap<String,Object>();result.put("program","Tomtom");result.put("version",root.attribute("version"));result.put("release",root.attribute("release"));result.put("cmd",List.of(model.one("command_line").text().split(" ",-1)));
        var bg=model.one("background");var options=new LinkedHashMap<String,Object>();options.put("strands",model.one("strands").text());options.put("distance_measure",model.one("distance_measure").attribute("value"));options.put("threshold_type",model.one("threshold").attribute("type"));options.put("threshold_value",decimal(model.one("threshold").text()));options.put("background_source",bg.attribute("from"));if(bg.attributes().containsKey("file"))options.put("background_file",bg.attribute("file"));result.put("options",options);
        var alphabet=model.one("alphabet");var symbols=new ArrayList<Map<String,Object>>();int core=0;for(var letter:alphabet.children()){var symbol=new LinkedHashMap<String,Object>();for(var a:letter.attributes().entrySet())if(!a.getKey().equals("id"))symbol.put(a.getKey(),a.getValue());if(!symbol.containsKey("equals"))core++;symbols.add(symbol);}result.put("alphabet",Map.of("name",alphabet.attribute("name"),"like",alphabet.attribute("like"),"ncore",core,"symbols",symbols));
        var background=new ArrayList<BigDecimal>();for(String base:List.of("A","C","G","T"))background.add(decimal(bg.attribute(base)));result.put("background",background);
        for(String group:List.of("query_dbs","target_dbs")){var dbs=new ArrayList<Map<String,Object>>();for(var db:root.one(group).children())dbs.add(Map.of("source",db.attribute("source"),"name",db.attribute("name"),"loaded",decimal(db.attribute("loaded")),"excluded",decimal(db.attribute("excluded")),"last_modified",db.attribute("last_mod_date")));result.put(group,dbs);}
        for(String group:List.of("queries","targets")){var motifs=new ArrayList<Map<String,Object>>();for(var motif:root.one(group).children()){
                var m=new LinkedHashMap<String,Object>();m.put("db",decimal(motif.attribute("db")));m.put("id",motif.attribute("id"));m.put("len",decimal(motif.attribute("length")));if(motif.attributes().containsKey("alt"))m.put("alt",motif.attribute("alt"));if(motif.attributes().containsKey("nsites"))m.put("nsites",decimal(motif.attribute("nsites")));if(motif.attributes().containsKey("evalue"))m.put("evalue",motif.attribute("evalue"));if(motif.attributes().containsKey("url")&&!motif.attribute("url").isEmpty())m.put("url",motif.attribute("url"));
                var pwm=new ArrayList<List<BigDecimal>>();for(var row:motif.children()){var values=new ArrayList<BigDecimal>();for(String base:List.of("A","C","G","T"))values.add(decimal(row.attribute(base)));pwm.add(values);}m.put("pwm",pwm);motifs.add(m);
            }result.put(group,motifs);}
        var groups=new ArrayList<Map<String,Object>>();for(var query:root.one("matches").children()){var matches=new ArrayList<Map<String,Object>>();for(var target:query.children()){
                var m=new LinkedHashMap<String,Object>();m.put("idx",decimal(target.attribute("idx")));if(target.attributes().containsKey("rc"))m.put("rc",target.attribute("rc").equals("y"));m.put("off",decimal(target.attribute("off")));for(String key:List.of("pv","ev","qv"))m.put(key,target.attribute(key));matches.add(m);
            }groups.add(Map.of("idx",decimal(query.attribute("idx")),"matches",matches));}result.put("all_matches",groups);
        var runtime=root.one("runtime");result.put("runtime",Map.of("host",model.one("host").text(),"when",model.one("when").text(),"cycles",decimal(runtime.attribute("cycles")),"seconds",decimal(runtime.attribute("seconds"))));return result;
    }
    private static BigDecimal decimal(String text)throws IOException {try{BigDecimal value=new BigDecimal(text);require(Math.abs((long)value.scale())<=4096,"Original HTML numeric exponent exceeds supported domain");return value;}catch(NumberFormatException e){throw new IOException("Invalid original HTML/XML numeric field",e);}}
    private static void equal(Object actual,Object expected,String field)throws IOException {
        if(expected instanceof Map<?,?> map){var a=NextflowFiles.object(actual);require(a.keySet().equals(map.keySet()),"Original HTML keys differ: "+field);for(var e:map.entrySet())equal(a.get(e.getKey()),e.getValue(),field+"/"+e.getKey());}
        else if(expected instanceof List<?> list){var a=NextflowFiles.list(actual);require(a.size()==list.size(),"Original HTML complete population differs: "+field);for(int i=0;i<list.size();i++)equal(a.get(i),list.get(i),field+"["+i+"]");}
        else if(expected instanceof Number number){require(actual instanceof NextflowParameters.NumberToken,"Original HTML number type differs: "+field);require(decimal(((NextflowParameters.NumberToken)actual).text()).compareTo(new BigDecimal(number.toString()))==0,"Original HTML numeric value differs from audited XML: "+field);}
        else require(java.util.Objects.equals(actual,expected),"Original HTML value differs from audited XML: "+field);
    }
    private static void require(boolean value,String message)throws IOException {MemeRuntimeFiles.require(value,message);}
}
