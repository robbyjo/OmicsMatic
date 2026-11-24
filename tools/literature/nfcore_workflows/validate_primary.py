# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject expanded nf-core source claims without touching the canonical catalog."""
from pathlib import Path
import argparse,copy,json,sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from nfcore_workflows import primary as p

def validate(cache,out):
    out.mkdir(exist_ok=False);papers=(p.ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes()
    census=(p.ROOT/p.FOLDER/'CENSUS.json').read_bytes();roles=json.loads((p.ROOT/p.FOLDER/'ROLES.json').read_bytes());cases=[]
    result=p.audit(papers,cache)
    def changed(label,mutate):
        root=out/label;folder=root/p.FOLDER;folder.mkdir(parents=True)
        value=copy.deepcopy(roles);mutate(value);(folder/'CENSUS.json').write_bytes(census);(folder/'ROLES.json').write_bytes(p.encoded(value))
        try:p.audit(papers,cache,root)
        except ValueError as e:cases.append(dict(case=label,status='REFUSED',diagnostic=str(e)))
        else:raise ValueError('Expanded source claim accepted: '+label)
    changed('qualified-before-execution',lambda r:r['roles'][0].update(paper_edition_qualified=True))
    changed('assign-edition-to-ATAQ-SEQ',lambda r:r['roles'][3]['pipelines'][1].update(version_reported='1.2.1',version_literal='1.2.1'))
    changed('drop-source-paragraph',lambda r:r['roles'][0]['reviewed_paragraphs'].pop())
    changed('repair-reported-samtools-version',lambda r:r['roles'][0]['pipelines'][1]['literal_settings'].__setitem__(1,'samtools v1.12'))
    changed('drop-unversioned-capture-branch',lambda r:r['roles'][0]['pipelines'].pop(0))
    changed('overwrite-raw-near-name-version',lambda r:r['roles'][1]['source']['original_observation'].update(version_reported_near_name='1.0.4'))
    missing=out/'missing-primary';missing.mkdir()
    try:p.audit(papers,missing)
    except FileNotFoundError:cases.append(dict(case='missing-primary',status='SKIP3_PREREQUISITE_MISSING'))
    else:raise ValueError('Missing primary XML accepted')
    proof=dict(status='PASS_FULL_PRIMARY_AUDIT_AND_SEVEN_REFUSALS',primary_audit=result,cases=cases,canonical_modified=False,whole_goal_complete=False)
    (out/'RESULT.json').write_bytes(p.encoded(proof));print(json.dumps(proof,indent=2));return proof
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--cache',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();validate(args.cache,args.out)
