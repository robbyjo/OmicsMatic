# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify scoped FUMA catalog credit without changing science or unrelated source uses."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
import zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
import fuma_catalog as evidence
from fuma_role_coverage import role_coverage

DATA=ROOT/'docs/literature/omics-2023-2026'


def read(path):return json.loads(path.read_text(encoding='utf-8'))
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable catalog evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--before',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();before,out=a.before.resolve(),a.out.resolve();base=(ROOT/'build/literature-review/fuma-runtime').resolve();assert before.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir()
    try:
        proof,source_roles=evidence.verify(ROOT,DATA)
        prior=read(before/'docs/literature/omics-2023-2026/tool-inventory.json');inventory=read(DATA/'tool-inventory.json');old={t['id']:t for t in prior};new={t['id']:t for t in inventory};assert old.keys()==new.keys() and len(new)==380
        assert all(old[k]==new[k] for k in old if k!='fuma'),'Unrelated tool metadata changed'
        assert old['fuma']['implementation_status']=='missing' and new['fuma']['implementation_status']=='native-subset-validated'
        assert new['fuma']['project_adapter_available'] and not new['fuma']['canonical_adapter_available'] and not new['fuma']['full_hosted_tool_available']
        assert new['fuma']['development_candidate_sha256']==proof['artifact']['sha256']
        assert new['fuma']['historical_development_candidate_sha256']==old['fuma']['development_candidate_sha256']
        assert new['fuma']['historical_implementation_scope']==old['fuma']['implementation_scope'] and new['fuma']['historical_validation_note']==old['fuma']['validation_note']
        summary=read(DATA/'summary.json');previous=read(before/'docs/literature/omics-2023-2026/summary.json');expected=copy.deepcopy(previous);expected['implementation_status_counts']['native-subset-validated']+=1;expected['implementation_status_counts']['missing']-=1;assert summary==expected
        assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,380,2430)
        assert summary['implementation_status_counts']['native-subset-validated']==15 and summary['implementation_status_counts']['upstream-adapter-validated']==48 and summary['implementation_status_counts']['missing']==257
        assert (DATA/'papers.json').read_bytes()==(before/'docs/literature/omics-2023-2026/papers.json').read_bytes(),'A source use or paper changed'
        with zipfile.ZipFile((ROOT/evidence.PROOF).parent/proof['archive']['path']) as archive:
            for name in ['tools/literature/reviewed-uses.json','tools/literature/tool-registry.json']:
                assert (ROOT/name).read_bytes()==archive.read('source/'+name),name
        reviews=read(ROOT/'tools/literature/reviewed-uses.json')['reviews'];assert len(reviews)==775 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==230
        expected_roles=role_coverage(ROOT,DATA);actual_roles=read(DATA/'fuma-role-coverage.json');assert actual_roles==expected_roles and actual_roles['reviewed_roles']==14
        p243=next(r for r in actual_roles['papers'] if r['paper_id']=='P243');assert p243['command_scope']=='source-method-unspecified' and not p243['capability_ids']
        assert all(not p['source_settings_inferred'] and not p['whole_paper_reproduced'] for p in actual_roles['papers'])
        assert not new['fuma']['DEG_generation_available'] and not new['fuma']['heatmap_clustering_available'] and not new['fuma']['variant_annotation_resource_generation_available']
        for path in [DATA/'WARNINGS.md',ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html']:
            text=path.read_text(encoding='utf-8');assert all(s in text for s in ['31cf8700','SKIP3','FAIL2','Cell Ranger remains excluded','historical'])
        assert 'fuma-current-actions' in (ROOT/'site/literature-review.html').read_text(encoding='utf-8')
        # A safe verifier self-check: inject a digest disagreement for exact
        # scientific paths, without mutating any worktree input or evidence.
        guarded=['src/main/java/org/jlinalg/genetics/HardcallLd.java','src/main/java/org/jlinalg/enrichment/FumaExpressionPreparation.java','src/test/resources/org/jlinalg/enrichment/fuma-expression-preparation/independent-R-controls.zip',proof['artifact']['path']]
        actual_pin=evidence.pin;refusals=[]
        try:
            for relative in guarded:
                assert relative not in evidence.CONTEXT;target=(ROOT/relative).resolve()
                def disagreement(path,target=target):
                    value=actual_pin(path)
                    return dict(bytes=value['bytes'],sha256='0'*64) if path.resolve()==target else value
                evidence.pin=disagreement
                try:evidence.verify(ROOT,DATA)
                except ValueError as failure:
                    assert str(target) in str(failure);refusals.append(dict(path=relative,simulated_digest_disagreement_refused=True))
                else:raise AssertionError('Scientific path was incorrectly admitted: '+relative)
        finally:evidence.pin=actual_pin
        evidence.verify(ROOT,DATA)
        result=dict(status='passed-scoped-native-FUMA-catalog-and-source-preservation',papers=250,named_tools=380,observations=2430,reviewed_uses=775,supporting_pointers=230,new_scoped_integrations=63,native_scopes=15,upstream_adapters=48,including_preexisting_adapters=66,missing=257,user_excluded=1,FUMA_source_roles=14,unrelated_tool_entries_preserved=379,source_paper_and_review_bytes_unchanged=True,unexposed_functions_not_credited=True,historical_candidates_and_counts_labeled=True,scientific_validation_reexecuted=False,immutable_scientific_checkpoint=evidence.PROOF,scientific_digest_disagreement_self_checks=refusals,artifact=proof['artifact'],files={p.relative_to(ROOT).as_posix():evidence.pin(p) for p in [Path(__file__),ROOT/'tools/literature/fuma_catalog.py',ROOT/'tools/literature/fuma_role_coverage.py',ROOT/'tools/literature/fuma_source_closure.py',ROOT/'tools/literature/render_review.py',ROOT/'tools/literature/check_review_ui.cjs',DATA/'fuma-source-closure.json',DATA/'fuma-role-coverage.json',DATA/'FUMA_SCOPED_WORKFLOWS.md',DATA/'tool-inventory.json',DATA/'summary.json',DATA/'papers.json',DATA/'WARNINGS.md',ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html']})
        write(out/'COMPLETED.json',result);print(json.dumps({k:result[k] for k in ['status','new_scoped_integrations','native_scopes','upstream_adapters','missing','unrelated_tool_entries_preserved']}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_FUMA_CATALOG',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_FUMA_CATALOG: '+str(failure)+'\nRequired action: preserve the failed attempt, correct the named source/catalog/evidence mismatch and rerun to new paths. Scientific changes require new scientific validation.',file=sys.stderr);sys.exit(2)
