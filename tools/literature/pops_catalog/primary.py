#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit six PoPS roles and the missed mixed-case observation without changing the catalog."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
sys.dont_write_bytecode=True;sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs
from pops_native.primary import ROLES,census as previous_census

PATTERN=r'(?<![A-Za-z0-9_])(?:(?-i:PoPS|PoPs))(?![A-Za-z0-9_])'
METHOD_ANCHOR=dict(P120='0876f6544d61b4752c7b0ec8d16ad276cd0c944edc68c1e52fa429f14cef05a7')
FIT_PAPERS={'P088','P090','P120'}
def sha(raw):return hashlib.sha256(raw).hexdigest()
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def census(root=ROOT,papers_before=None):
    root=Path(root).resolve();legacy=previous_census(root);assert legacy['software_papers']==sorted(ROLES)
    papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes()) if papers_before is None else papers_before
    assert len(papers)==250
    expected=json.loads((root/'docs/literature/pops-native-development/PRIMARY-PINS.json').read_bytes())
    review_rows=[];observations={};source_rows=[];new_ids=[];rejected=[];locator_changes=[]
    for paper in papers:
        pid=paper['paper_id'];raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert sha(raw)==paper['xml_sha256']
        article=ET.fromstring(raw);body=paragraphs(article);parents={c:p for p in article.iter() for c in p};matches={}
        for index,node in enumerate(article.iter('p')):
            original=''.join(node.itertext());text=' '.join(original.split())
            if not re.search(r'(?<![A-Za-z0-9_])pops(?![A-Za-z0-9_])|polygenic\s+priority\s+scor',text,re.I):continue
            digest=sha(text.encode());section=[];parent=node
            while parent in parents:
                parent=parents[parent]
                if parent.tag=='sec':
                    title=parent.find('title')
                    if title is not None:section.append(' '.join(''.join(title.itertext()).split()))
                elif parent.tag in ['fig','table-wrap']:section.append(parent.tag+' '+str(parent.get('id')))
            row=matches.setdefault(digest,dict(paragraph_sha256=digest,raw_itertext_sha256=sha(original.encode()),characters=len(text),locations=[]))
            row['locations'].append(dict(xml_all_p_index=index,section=' / '.join(reversed(section))))
        if not matches:continue
        assert pid in expected['matches'] and set(matches)=={m['paragraph_sha256'] for m in expected['matches'][pid]},pid
        if pid=='P239':
            assert pid in expected['rejected_name_matches'] and not any(re.search(PATTERN,p['match_text']) for p in body);rejected.append(dict(paper_id=pid,reason='POPS membrane lipid, not PoPS software',paragraphs=list(matches.values())));continue
        assert pid in ROLES;role=ROLES[pid];anchor_hash=METHOD_ANCHOR.get(pid,role['anchor']);anchors=[p for p in body if sha(p['text'].encode())==anchor_hash];assert len(anchors)==1,pid
        anchor=anchors[0];match=re.search(PATTERN,anchor['match_text']);assert match is not None,(pid,'Named software anchor absent')
        previous=[t for t in paper['tools'] if t['tool_id']=='pops'];assert len(previous)==(0 if pid=='P236' else 1)
        if previous:
            observation=dict(previous[0]);old_source=next(p for p in body if p['paragraph_index']==observation['evidence_paragraph_index']);assert sha(old_source['text'].encode())==observation['evidence_paragraph_sha256'] and old_source['section']==observation['evidence_section']
            if observation['evidence_paragraph_sha256']!=anchor_hash:
                assert pid=='P090' and observation['evidence_paragraph_index']==27 and anchor['paragraph_index']==30
                assert observation['evidence_paragraph_sha256'] in matches
                locator_changes.append(dict(paper_id=pid,old_body_index=27,new_body_index=30,old_paragraph_sha256=observation['evidence_paragraph_sha256'],new_paragraph_sha256=anchor_hash,old_primary_still_valid=True,old_primary_retained_as_support=True,reason='Use the explicit PoPS v0.1 computation paragraph as primary evidence; retain the prior top-three/eight-source gene-prioritization paragraph as supporting context. No primary XML bytes or old hashes change.'))
                observation.update(evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],matched_text=match.group(),evidence_paragraph_sha256=anchor_hash,evidence_locator=paper['fulltext_url']+' (section: '+anchor['section']+')')
        else:
            new_ids.append(pid);observation=dict(tool_id='pops',tool='PoPS',analysis_role='Effector-gene prioritization using polygenic features',evidence_section=anchor['section'],evidence_paragraph_index=anchor['paragraph_index'],matched_text=match.group(),extraction_score=0,evidence_paragraph_sha256=anchor_hash,evidence_locator=paper['fulltext_url']+' (section: '+anchor['section']+')',source_intake='manual-article-wide-software-result-reuse-review; mixed-case name missed by prior case-sensitive pattern')
        support=[row for digest,row in sorted(matches.items()) if digest!=anchor_hash]
        if pid=='P120':assert any(p['paragraph_sha256']==role['anchor'] for p in support),'Reported v0.2 Code availability anchor must be retained separately from the Methods anchor'
        fresh=pid in FIT_PAPERS
        scope=role['open_scope']+' '+('Native PoPS 0.2 ridge/OLS and the explicit original-edition adapter provide prepared-input estimation; this is not identification of the author commit/options or a whole-paper reproduction.' if fresh else 'This observation concerns reported score use. It does not authorize inferring a new PoPS fit, version or default method; source score releases and downstream comparison/enrichment require their own provenance.')
        review=dict(paper_id=pid,pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],tool_id='pops',evidence_paragraph_sha256=anchor_hash,use_summary=role['summary'],implementation_scope_note=scope,usage_context=role['context'],version_reported_near_name=role['version'],fresh_estimation_reported=fresh,exact_author_binary_build_established=False,author_options_established=False,whole_paper_reproduced=False,source_normalization='Unicode-whitespace collapse over XML p itertext; raw hashes and full XML identities retained separately',supporting_evidence=support)
        observation.update(tool='PoPS',analysis_role='Effector-gene prioritization using polygenic features',use_summary=review['use_summary'],implementation_scope_note=scope,usage_context=role['context'],version_reported_near_name=role['version'],review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-primary-paragraph',supporting_evidence=support,fresh_estimation_reported=fresh,whole_paper_reproduced=False)
        observations[pid]=observation;review_rows.append(review);source_rows.append(dict(paper_id=pid,pmcid=paper['pmcid'],anchor_body_paragraph_index=anchor['paragraph_index'],anchor_section=anchor['section'],anchor_sha256=anchor_hash,matched_name=match.group(),primary_paragraphs=list(matches.values()),source_bytes_changed=False))
    assert set(observations)==set(ROLES) and new_ids==['P236'] and len(rejected)==1 and len(locator_changes)==1
    return dict(status='PASS_ALL_250_POPS_CATALOG_PRIMARY_ROLES_AND_MIXED_CASE_CORRECTION',papers_scanned=250,primary_files=legacy['primary_files'],software_papers=sorted(ROLES),reported_computation_papers=sorted(FIT_PAPERS),benchmark_or_score_reuse_papers=sorted(set(ROLES)-FIT_PAPERS),source_roles=source_rows,new_observation_papers=new_ids,proposed_locator_changes=locator_changes,rejected_name_matches=rejected,observations=observations,reviews=review_rows,proposed_pattern=PATTERN,current_catalog_changed=False,new_catalog_credit=0,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        a.out.resolve().mkdir(parents=True,exist_ok=False);out=a.out.resolve();result=census();(out/'CENSUS.json').write_bytes(encoded(result));(out/'REVIEWS.json').write_bytes(encoded(result['reviews']));print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if out is not None:(out/'FAILURE.json').write_bytes(encoded(dict(reason=repr(error),traceback=traceback.format_exc(),required_action='Retain primary XML and old catalog/normalization pins. Restore permitted missing sources or resolve the named role discrepancy before admission. Do not modify old source hashes, infer author parameters, or count result reuse as a new estimator run.')))
        print(('SKIP3' if code==3 else 'FAIL2')+'_Pops_PRIMARY_REVIEW',repr(error));return code
if __name__=='__main__':raise SystemExit(main())
