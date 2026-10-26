/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Explicit local-only full-text acquisition; public identifiers are the only URL input. */
final class StudyReferenceText {
    private StudyReferenceText() { }
    static Map<String,Object> fetch(Context c,StudyPublicEvidence client,List<Object> articles,String reference,String rationale,int sequence)throws IOException {
        var result=new LinkedHashMap<String,Object>();result.put("id",String.format("reference-text-%04d",sequence));result.put("reference",reference);result.put("status","unavailable");
        var matches=articles.stream().map(StudyPublicEvidence::map).filter(a->text(a.get("pmcid")).matches("PMC[0-9]+")).toList();if(matches.isEmpty()||matches.stream().map(a->a.get("pmcid")).distinct().count()!=1){result.put("reason","One unambiguous PMCID from exact metadata matches is required; no article text was guessed");return result;}
        var article=matches.stream().filter(a->"MED".equals(a.get("source"))).findFirst().orElse(matches.get(0));String pmcid=text(article.get("pmcid"));String url="https://www.ebi.ac.uk/europepmc/webservices/rest/"+pmcid+"/fullTextXML";var reply=client.getText(c,"Europe PMC full-text XML",url,"https://europepmc.org/RestfulWebService",rationale,2000000);result.put("url",url);result.put("audit",reply.audit());result.put("pmcid",pmcid);
        if(!reply.ok()){result.put("reason",reply.reason());result.put("retrieval_status",reply.status());return result;}
        try {
            var projection=project(text(reply.data()),article);String body=text(projection.get("text"));if(body.getBytes(StandardCharsets.UTF_8).length>2000000)throw new IOException("Full-text projection exceeds 2 MB");String relative="artifacts/full-text/"+result.get("id")+".txt";Files.createDirectories(c.outputDirectory().resolve("artifacts/full-text"));Files.writeString(c.outputDirectory().resolve(relative),body);result.put("status","available");result.put("scope","full_text");result.put("text_file",relative);result.put("sha256",FollowupSupport.hash(c.outputDirectory().resolve(relative)));result.put("license",projection.get("license"));result.put("projection",projection.get("projection"));result.put("interpretation","Textual XML projection with the original response retained. Figures, supplementary files, source validity and scientific support still require review; acquisition does not certify claim support.");
        }catch(IOException bad){result.put("reason",bad.getMessage());result.put("status","unavailable_invalid_or_unsupported_xml");}
        return result;
    }
    static Map<String,Object> project(String xml,Map<String,Object> article)throws IOException {
        try{
            var factory=DocumentBuilderFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);var builder=factory.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){@Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}});var document=builder.parse(new InputSource(new StringReader(xml)));Element root=document.getDocumentElement();if(!root.getTagName().equals("article"))throw new IOException("Expected a JATS article response");
            var fronts=root.getElementsByTagName("article-meta");if(fronts.getLength()!=1)throw new IOException("Expected one article metadata record");var meta=(Element)fronts.item(0);var identifiers=new LinkedHashMap<String,String>();var ids=meta.getElementsByTagName("article-id");for(int i=0;i<ids.getLength();i++){var e=(Element)ids.item(i);identifiers.put(e.getAttribute("pub-id-type"),e.getTextContent().strip());}
            String doi=text(article.get("doi")),pmid=text(article.getOrDefault("pmid","MED".equals(article.get("source"))?article.get("id"):""));if(!doi.isBlank()&&!doi.equalsIgnoreCase(identifiers.get("doi")))throw new IOException("Full-text DOI conflicts with its matched metadata");if(!pmid.isBlank()&&identifiers.containsKey("pmid")&&!pmid.equals(identifiers.get("pmid")))throw new IOException("Full-text PMID conflicts with its matched metadata");if(doi.isBlank()&&!pmid.equals(identifiers.get("pmid")))throw new IOException("Full-text identity cannot be bound to its matched metadata");
            var bodies=root.getElementsByTagName("body");if(bodies.getLength()!=1||bodies.item(0).getTextContent().isBlank())throw new IOException("Full-text response has no unique article body");String license=meta.getElementsByTagName("permissions").getLength()>0?plain(meta.getElementsByTagName("permissions").item(0).getTextContent()):"not reported; source reuse conditions require review";
            String title=meta.getElementsByTagName("article-title").getLength()>0?plain(meta.getElementsByTagName("article-title").item(0).getTextContent()):text(article.get("title"));var text=new StringBuilder("DOI: ").append(doi.isBlank()?text(identifiers.get("doi")):doi).append("\nPMID: ").append(pmid).append("\nTitle: ").append(title).append("\nSource: https://www.ebi.ac.uk/europepmc/webservices/rest/").append(article.get("pmcid")).append("/fullTextXML\nLicense: ").append(license).append("\nProjection: JATS textual content; images and external supplements require separate review.\n\n");
            var abstracts=meta.getElementsByTagName("abstract");for(int i=0;i<abstracts.getLength();i++){text.append("ABSTRACT\n");append(abstracts.item(i),text);text.append('\n');}text.append("BODY\n");append(bodies.item(0),text);String normalized=text.toString().replaceAll("[\\t\\x0B\\f\\r ]+"," ").replaceAll(" *\\n *","\n").replaceAll("\\n{3,}","\n\n").strip()+"\n";
            return Map.of("text",normalized,"license",license,"projection","Secure JATS DOM; source identity checked against matched DOI/PMID; abstract/body text nodes retained with block line breaks; no external entities/DTD, images or external supplements resolved");
        }catch(IOException ex){throw ex;}catch(Exception ex){throw new IOException("Unable to safely project the public full-text XML",ex);}
    }
    private static String plain(String s){return s.replaceAll("(?U)\\s+"," ").strip();}
    private static void append(Node node,StringBuilder text){if(node.getNodeType()==Node.TEXT_NODE||node.getNodeType()==Node.CDATA_SECTION_NODE){text.append(node.getNodeValue());return;}boolean block=List.of("title","p","sec","fig","table","tr","list-item","caption","label").contains(node.getNodeName());if(block)text.append('\n');for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling())append(child,text);if(block)text.append('\n');}
}
