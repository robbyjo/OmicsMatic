"""Replay the frozen 250 primary sources and distinguish five WGCNA source roles."""
import argparse,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as collector
from catalog_fixed_cohort import verify_fixed_cohort
from nfcore_review_source_audit import read,pin,identity,encoded,require
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/wgcna-source-audit'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:hd|py|r)?WGCNA(?![A-Za-z0-9_])|weighted\s+(?:gene\s+)?co[- ]?expression\s+network',re.I)
ROLES={
 'P059':dict(paragraphs=[128],usage='R WGCNA defined transcription-factor coexpression modules from average subclass-level expression of 534 marker TF genes, with power6, signed TOM and detectCutHeight0.998. Grey and neuron-enriched genes were excluded; modules were then manually split/reordered for visualization.',caution='The WGCNA edition and complete author network options are unreported. The typed original adapter supports the named network operations, but does not infer the author recipe or reproduce the subsequent filtering/manual module edits.',runtime='R-WGCNA; edition unreported',qualified_author_recipe=False),
 'P062':dict(paragraphs=[2,9,41,43,86,91],usage='The article describes WGCNA coexpression modules in results, but its Python package list, snRNA-seq Methods and code-availability statements identify Python pyWGCNA2.2.1 and the PyWGCNA Quick Start. Scanpy/Pearson-residual and SysVI processing are separate steps.',caution='The actual named execution host is PyWGCNA2.2.1. R WGCNA1.72-1 and its Java components do not substitute for this Python workflow; this paper earns no R-WGCNA execution credit. The existing separate PyWGCNA definition is preserved.',runtime='PyWGCNA2.2.1; R execution not established',qualified_author_recipe=False),
 'P089':dict(paragraphs=[35],usage='WGCNA1.72-1 rankPvalue evaluated consistently high/low ranks for 19,676 candidate gene pairs across three rankings: mean gene-A expression, mean gene-B expression and Spearman correlation across 11 DLBCL cell lines. The source describes a CLT rank test; it does not report WGCNA network fitting here.',caution='Native rankPvalue is limited to explicit calculateQvalue=FALSE and supported deterministic ties/missingness/weights; original R preserves original qvalue calls and errors. Source defaults cannot be treated as reported author arguments. Pair construction, TAD membership, mutations and hypermutation filters remain separate.',runtime='R-WGCNA1.72-1 rankPvalue',qualified_author_recipe=False),
 'P108':dict(paragraphs=[51,55],usage='WGCNA network-connectivity scores identified adult-brain reference samples below -3 for removal; the reported final panel has 2,115 samples. The study separately describes DESeq2 VST, sva ComBat and further model adjustments.',caution='The exact sample-network construction, normalization, correlation and power recipe is not specified by the named context. The prepared gene/module connectivity component does not establish this sample-QC recipe, and the adapter does not infer or perform the -3 filtering. Gene and isoform panel processing remain distinct.',runtime='WGCNA sample-connectivity QC; edition/complete recipe unreported',qualified_author_recipe=False),
 'P182':dict(paragraphs=[26],usage='The osteoarthritis-cartilage analysis describes a signed, power7 network (scale-free R-squared0.88) using a previously described robust rWGCNA variant, module eigengenes, biweight midcorrelation, WGCNA signedKME and intramodularConnectivity. limma batch removal is an upstream step.',caution='The original adapter supports declared Pearson/bicor network, signedKME and connectivity calls. A standard bicor network is not admitted as the complete referenced robust rWGCNA recipe; supplementary/predecessor details and author inputs are still required.',runtime='robust rWGCNA variant plus named R-WGCNA functions; edition unreported',qualified_author_recipe=False)
}
def collect(root,cache):
 papers=read(root/'docs/literature/omics-2023-2026/papers.json');verify_fixed_cohort(root,root/'docs/literature/omics-2023-2026',papers);census=dict(schema_version=1,pattern=PATTERN.pattern,collector=pin(Path(collector.__file__)),sources={},contexts=[],other_structures=[]);selected={}
 for paper in papers:
  with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb')as stream:raw=stream.read(16000001)
  require(len(raw)<=16000000 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],'Primary XML bytes changed');tree=ET.fromstring(raw);require(any((n.text or'').strip().lower()==paper['doi'].lower()for n in tree.findall('front/article-meta/article-id')),'Primary DOI differs');ps=list(collector.paragraphs(tree));census['sources'][paper['paper_id']]=dict(pmcid=paper['pmcid'],doi=paper['doi'],**identity(raw))
  for p in ps:
   if PATTERN.search(p['match_text']):census['contexts'].append(dict(paper_id=paper['paper_id'],paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()))
  for location in ['front/article-meta/abstract','back','floats-group','body//supplementary-material']:
   for node in tree.findall(location):
    if PATTERN.search(collector.matching_words(node)):census['other_structures'].append(dict(paper_id=paper['paper_id'],location=location,sha256=hashlib.sha256(ET.tostring(node)).hexdigest()))
  if paper['paper_id']in ROLES:selected[paper['paper_id']]=(paper,ps)
 require(set(selected)==set(ROLES)and len(census['sources'])==250,'Wrong frozen source population');roles=[];reviews=[]
 for pid,spec in ROLES.items():
  paper,ps=selected[pid];by={p['paragraph_index']:p for p in ps};use=next(t for t in paper['tools']if t['tool_id']=='wgcna');anchors=[]
  for index in spec['paragraphs']:
   p=by[index];require(PATTERN.search(p['match_text']),'Named role lost primary source');anchors.append(dict(paragraph_index=index,section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest(),matching_text_sha256=hashlib.sha256(p['match_text'].encode()).hexdigest()))
  require(any(a['paragraph_index']==use['evidence_paragraph_index']and a['paragraph_sha256']==use['evidence_paragraph_sha256']for a in anchors),'Selected raw observation differs');note=spec['caution']+' Source review alone does not qualify an executed author recipe or whole paper.'
  row=dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],tool_id='wgcna',runtime=spec['runtime'],primary_anchors=anchors,use_summary=spec['usage'],implementation_scope_note=note,reported_version_preserved=use['version_reported_near_name'],qualified_author_recipe=False,whole_paper_reproduced=False);roles.append(row)
  reviews.append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='wgcna',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=row['use_summary'],implementation_scope_note=note,supporting_evidence=[a for a in anchors if a['paragraph_index']!=use['evidence_paragraph_index']]))
 census['additional_host_boundary']='P085 hdWGCNA is a distinct spatial-network host. P157 code-availability statements identify PyWGCNA with literal version1.20.3, which is not normalized or admitted as R-WGCNA execution. Neither additional host receives implementation or source-review credit in this five-use checkpoint.'
 return census,dict(schema_version=1,roles=roles,new_source_reviews=5,source_audit_admits_runtime=False,whole_goal_complete=False),dict(schema_version=1,reviews=reviews)
def audit(root,cache):
 values=collect(root,cache)
 for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json'],values):require(read(root/FOLDER/name)==value,'Primary role audit changed: '+name)
 return dict(status='PASS250_WGCNA_PRIMARY_ROLES',sources=250,contexts=len(values[0]['contexts']),reviewed_uses=5,source_audit_admits_runtime=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--xml-cache',type=Path,required=True);p.add_argument('--write',action='store_true');a=p.parse_args();root=a.root.resolve()
 try:
  if a.write:
   values=collect(root,a.xml_cache);folder=root/FOLDER;folder.mkdir()
   for name,value in zip(['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json'],values):
    with(folder/name).open('xb')as out:out.write(encoded(value))
  print(json.dumps(audit(root,a.xml_cache),indent=2))
 except Exception as e:raise SystemExit('FAIL2: no WGCNA source review admitted; retain original evidence and correct cause: '+str(e))
