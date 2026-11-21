from pathlib import Path
import hashlib,importlib.util,json
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';O=B/'source-role-review-1';O.mkdir();path=T/'tools/literature/gcta_source_roles.py';spec=importlib.util.spec_from_file_location('gcta_roles',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
census=json.loads((B/'source-intake-1/CANDIDATES.json').read_bytes());byid={p['paper_id']:p for p in census['candidates']};assert set(byid)==set(m.ROLES)|set(m.EXCLUSIONS) and len(m.ROLES)==18 and len(m.EXCLUSIONS)==4
records=[]
for pid,decision in m.ROLES.items():
    candidate=byid[pid];anchors={a['paragraph_index']:a for a in candidate['anchors']};assert all(p in anchors for p in decision['paragraphs']),(pid,decision['paragraphs'],list(anchors))
    existing=[t['tool_id'] for t in candidate['existing_observations']];tools=existing or ['gcta'];assert bool(existing)==(pid!='P112')
    for tool in tools:
        records.append(dict(paper_id=pid,pmcid=candidate['pmcid'],doi=candidate['doi'],tool_id=tool,reported_edition=decision['edition'],operations=decision['operations'],usage_summary=decision['usage'],required_caution=decision['caution'],primary_evidence=[dict(paragraph_index=p,section=anchors[p]['section'],paragraph_sha256=anchors[p]['paragraph_sha256'],matching_text_sha256=anchors[p]['matching_text_sha256']) for p in decision['paragraphs']],paper_reproduced=False))
assert len(records)==26 and sum(r['tool_id']=='gcta' for r in records)==18 and sum(r['tool_id']=='cojo' for r in records)==8
exclusions=[]
for pid,decision in m.EXCLUSIONS.items():
    candidate=byid[pid];anchors={a['paragraph_index']:a for a in candidate['anchors']};assert all(p in anchors for p in decision['paragraphs']);exclusions.append(dict(paper_id=pid,pmcid=candidate['pmcid'],doi=candidate['doi'],reason=decision['reason'],paragraphs=[dict(paragraph_index=p,paragraph_sha256=anchors[p]['paragraph_sha256']) for p in decision['paragraphs']],existing_tool_ids=[t['tool_id'] for t in candidate['existing_observations']]))
assert exclusions[0]['existing_tool_ids']==['cojo'] and all(not r['existing_tool_ids'] for r in exclusions[1:])
result=dict(status='PASS_REVIEWED_GCTA_COJO_26_POSITIVE_LABEL_ROLES_18_PAPERS_AND_FOUR_EXCLUSIONS_NOT_ADMITTED',source_module=dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest()),source_census=dict(bytes=(B/'source-intake-1/CANDIDATES.json').stat().st_size,sha256=hashlib.sha256((B/'source-intake-1/CANDIDATES.json').read_bytes()).hexdigest()),roles=records,exclusions=exclusions,proposed_addition=m.PROPOSED_ADDITION,proposed_removal=m.PROPOSED_REMOVAL,expected_observation_delta=0,one_implementation_for_GCTA_and_COJO_not_two_independent_packages=True,canonical_metadata_modified=False,new_canonical_review_credit=0,whole_goal_complete=False)
(O/'SOURCE-ROLES.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print('PASS26 source label roles on18 executed papers;4 exclusions;canonical admission pending',flush=True)
