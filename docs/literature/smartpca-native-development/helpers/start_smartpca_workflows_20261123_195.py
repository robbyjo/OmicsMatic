from pathlib import Path
import gzip,hashlib,json,re,sys,unicodedata,xml.etree.ElementTree as ET
from handover_inventory_20261122_111 import git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-1';O=R/'build/git-publish/20261123-smartpca-workflows-195'
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='1709bf5be17e1c6b6a5250b1086f8b01c65341fc'
assert not git('status','--porcelain=v1',cwd=T)
O.mkdir();B.mkdir();owners=snapshot();save(O/'WORKTREES-BEFORE.json',owners)
indexes={}
for owner in owners:
 p=Path(git('rev-parse','--git-path','index',cwd=owner['worktree']).decode().strip())
 if not p.is_absolute():p=Path(owner['worktree'])/p
 indexes[owner['worktree']]=dict(path=str(p),identity=pin(p))
save(O/'INDEXES-BEFORE.json',indexes)
git('switch','-c','codex/smartpca-workflows-20261123',cwd=T)
save(O/'START.json',dict(status='SMARTPCA_SOURCE_AND_NATIVE_WORKFLOW_DEVELOPMENT',previous_goal_turn='progress',
 predecessor='1709bf5be17e1c6b6a5250b1086f8b01c65341fc',whole_goal_complete=False,scoped_families=153,reviewed_uses=1691,pending_uses=1031,
 new_family_credit=0,new_reviews=0))
sys.path.insert(0,str(T/'tools/literature'))
from collect_omics_review import paragraphs,words,matching_words
papers=json.loads((T/'docs/literature/omics-2023-2026/papers.json').read_bytes());assert len(papers)==250
patterns={
 'smartpca':re.compile(r'(?<![A-Za-z0-9_])(?:smartpca|eigensoft)(?![A-Za-z0-9_])',re.I),
 'scenic':re.compile(r'(?<![A-Za-z0-9_])(?:pySCENIC|SCENIC)(?![A-Za-z0-9_+])',re.I),
 'scenicplus':re.compile(r'(?<![A-Za-z0-9_])(?:pySCENIC\+|SCENIC\+)(?![A-Za-z0-9_])',re.I),
 'nfcore':re.compile(r'\bnf[\s-]*core\b|\bnf_core\b|\bnf-co\.re\b|github\.com/nf-core/',re.I)}
def norm(text):return unicodedata.normalize('NFKC',text).translate(str.maketrans({x:'-' for x in '‐‑‒–—−'}))
private=[];public=[];identities=[];supplementary=[]
for paper in papers:
 path=R/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(path.read_bytes())
 assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],paper['paper_id']
 tree=ET.fromstring(raw);dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
 assert len(dois)==1 and dois[0].lower()==paper['doi'].lower()
 identities.append({k:paper[k] for k in ('paper_id','doi','pmcid','xml_sha256')})
 standard=paragraphs(tree)
 for paragraph in standard:
  text=norm(paragraph.get('match_text',paragraph['text']));families=[key for key,pattern in patterns.items() if pattern.search(text)]
  if families:
   anchor=dict(paper_id=paper['paper_id'],doi=paper['doi'],paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],
    paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(),families=families)
   public.append(anchor);private.append(dict(**anchor,private_full_paragraph=paragraph['text']))
 seen={x['text'] for x in standard}
 for tag in ('p','table-wrap','code','preformat'):
  for index,element in enumerate(tree.findall('.//body//'+tag),1):
   display=words(element);text=norm(matching_words(element));families=[key for key,pattern in patterns.items() if pattern.search(text)]
   if families and (tag!='p' or display not in seen):
    item=dict(paper_id=paper['paper_id'],tag=tag,ordinal=index,element_id=element.get('id'),families=families,text_sha256=hashlib.sha256(display.encode()).hexdigest())
    supplementary.append(item);private.append(dict(**item,private_full_text=display))
save(B/'NEXT-PRIORITY-PRIMARY-IDENTITIES.json',identities)
save(B/'PRIVATE-NEXT-PRIORITY-CONTEXTS.json',private)
save(B/'NEXT-PRIORITY-CENSUS.json',dict(status='PASS_ALL250_SOURCE_IDENTITIES_CENSUS_REQUIRES_ROLE_REVIEW',papers_checked=250,
 matching_paragraphs=public,additional_body_table_code_candidates=supplementary,
 candidate_papers={key:sorted({row['paper_id'] for row in public+supplementary if key in row['families']}) for key in patterns},
 new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(papers_checked=250,matching_paragraphs=len(public),additional_candidates=len(supplementary),
 candidate_papers={key:sorted({row['paper_id'] for row in public+supplementary if key in row['families']}) for key in patterns},branch='codex/smartpca-workflows-20261123'),indent=2))
