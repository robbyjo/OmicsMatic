# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Use an immutable verified scientific checkpoint across catalog-count updates.

The inherited summary.json pin is explicitly historical metadata, as already
recorded in catalog_at_validation. Only the shared CLI dispatcher can use a
reviewed exact source inverse. Numerical/analysis/runtime sources and scientific
artifacts remain byte-exact; no scientific path receives compatibility treatment.
"""
import json,zipfile,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import pin
from catalog_source_integrity import verify_source
ROOT=Path(__file__).resolve().parents[2]
PROOF='docs/literature/mashr-proxy-command/VALIDATION.json'
EXPECTED=dict(bytes=700751,sha256='aea8121d81fa99ac1015d953569b547422a06e3de670f0a6a5689b009cda5b5b')
CONTEXT='docs/literature/omics-2023-2026/summary.json'


def exact(path,expected):
    if path==ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java':
        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026','src/main/java/org/jlinalg/cli/JLinAlgCli.java',expected['sha256'])
        return
    assert path.stat().st_size==expected['bytes'] and pin(path)==expected,str(path)


def verify(emit=True):
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    # This exact checkpoint previously passed its full retained-evidence verifier.
    # Its global proof/archive hashes also pin every recorded protocol and claim.
    exact(ROOT/PROOF,EXPECTED);proof=json.loads((ROOT/PROOF).read_text(encoding='utf-8'))
    assert proof['status']=='passed-public-native-proxy-summary-command-development-checkpoint'
    assert (proof['tests_per_JVM'],proof['new_command_tests_per_JVM'],proof['public_protocol_commands'],proof['cross_produced_bundle_checks'],proof['fresh_original_R_score_decisions'])==(477,37,30,6,360)
    assert proof['claims']['standalone_public_proxy_command_available'] and not proof['claims']['joint_posterior_probability_computed'] and not proof['claims']['whole_paper_reproduced']
    historical={k:proof['catalog_at_validation'][k] for k in ['bytes','sha256']}
    assert proof['unchanged_prior_source_pins'][CONTEXT]==historical
    for name in ['source_pins','unchanged_prior_source_pins','baseline_dependencies']:
        for relative,expected in proof[name].items():
            if relative==CONTEXT:
                assert name=='unchanged_prior_source_pins'
                continue
            exact(ROOT/relative,expected)
    exact(ROOT/proof['artifact']['path'],{k:proof['artifact'][k] for k in ['bytes','sha256']})
    exact(ROOT/'build/cli/omicsmatic-0.3.7.jar',proof['canonical_executable_unchanged'])
    archive_path=(ROOT/PROOF).parent/proof['archive']['path'];exact(archive_path,{k:proof['archive'][k] for k in ['bytes','sha256']})
    with zipfile.ZipFile(archive_path) as archive:
        entries=archive.infolist();assert len(entries)==len({e.filename for e in entries})==3116==proof['archive']['entries']
        assert {e.filename for e in entries}==set(proof['archive_files'])
        key=proof['catalog_at_validation']['archive_path'];assert proof['archive_files'][key]==historical
        raw=archive.read(key);assert len(raw)==historical['bytes']
        import hashlib
        assert hashlib.sha256(raw).hexdigest()==historical['sha256']
        assert json.loads(raw)['implementation_status_counts']['native-subset-validated']==13
        for relative,value in proof['source_pins'].items():assert proof['archive_files']['source/'+relative]==value
        for name in ['matrix-3','protocols-1']:
            prefix='evidence/'+name+'/';record=json.loads(archive.read(prefix+'COMPLETED.json'))
            assert {n[len(prefix):]:v for n,v in proof['archive_files'].items() if n.startswith(prefix) and n!=prefix+'COMPLETED.json'}==record['files']
            if name=='matrix-3':assert record['tests_per_JVM']==477 and all(r['process']['exit_code']==0 for r in record['runs'])
            else:
                assert all(c['exit_code']==c['expected_exit_code'] for r in record['runs'] for c in r['commands'])
                assert len(record['cross_produced_bundles'])==6 and all(r['exit_code']==0 and r['source_unchanged'] for r in record['cross_produced_bundles'])
                assert sum(r['scores_and_decisions'] for r in record['original_R_runs'])==360 and all(r['process']['exit_code']==0 for r in record['original_R_runs'])
    result=dict(status='passed-immutable-mashr-science-with-historical-catalog-context',tests_per_JVM=477,
                public_protocol_commands=30,cross_produced_bundle_checks=6,fresh_original_R_score_decisions=360,
                historical_context_path=CONTEXT,scientific_sources_unchanged=True,analysis_reexecuted=False)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    try:verify()
    except Exception as failure:
        import sys
        print('FAIL2_MASHR_CATALOG_EVIDENCE: '+str(failure)+'\nRequired action: preserve the immutable checkpoint and correct the named source/artifact mismatch. Only the exact archived catalog summary is historical context; scientific changes require new validation.',file=sys.stderr);sys.exit(2)
