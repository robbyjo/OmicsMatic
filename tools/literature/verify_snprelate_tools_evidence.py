# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check complete frozen SNPRelate controls; optional Java replay launches no R or installer."""
import argparse
import csv
import io
import json
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source
HOME=ROOT/'docs/literature/snprelate-tools-development'
BASE='build/literature-review/snprelate-runtime/'
STATUS='PASS_FROZEN_SNPRELATE146_FOUR_ORIGINAL_WORKFLOWS_AND_NATIVE_AUDITS'


def table(raw):return list(csv.DictReader(io.StringIO(raw.decode('utf8')),delimiter='\t'))


def verify(home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']==STATUS and not proof['whole_goal_complete'] and not proof['whole_paper_reproduced']
    for name,value in proof['source_pins'].items():
        if name=='src/main/java/org/jlinalg/cli/JLinAlgCli.java':
            verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',name,value['sha256'])
        else:assert pin(ROOT/name)==value,('Changed live scientific or verifier source',name)
    assert pin(home/'controls.zip')==proof['archive'];files=unpack(home/'controls.zip',proof)
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    for name,value in proof['artifacts'].items():assert pin(home/name)==value
    for compiler in [21,25]:
        with zipfile.ZipFile(home/f'snprelate-javac{compiler}.jar') as single,zipfile.ZipFile(home/f'snprelate-root-javac{compiler}.jar') as routed:
            for name in single.namelist():
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/snprelate/'):
                    assert single.read(name)==routed.read(name),name
    def obj(name):return json.loads(files[BASE+name])
    cases=proof['cases'];assert len(cases)==len({r['bundle'] for r in cases})==320
    assert sum(r['expected']==0 for r in cases)==266 and sum(r['expected']==2 for r in cases)==54
    assert {r['mode'] for r in cases}=={'king-robust','ld-pruning','grm','pca'}
    native_cells=pca_cells=0
    for case in cases:
        bundle=case['bundle'];done=json.loads(files[bundle+'/COMPLETED.json'])
        assert done['result_admitted'] is (case['expected']==0)
        captured={n.removeprefix(bundle+'/'):byte_pin(raw) for n,raw in files.items() if n.startswith(bundle+'/') and n!=bundle+'/COMPLETED.json'}
        assert captured==done['files'],('Incomplete original bundle inventory',bundle)
        if case['expected']:
            assert not done.get('original_analysis_executed',False) and b'FAIL2' in files[bundle+'/WARNINGS.md'];continue
        assert done['original_analysis_executed'] and done['scientific_audit']['source_nonfinite_values_repaired'] is False
        ref=case['reference'];work=bundle+'/worker/'
        actual=table(files[work+'original-selected-variants.tsv']);expected=table(files[ref+'/original-selected-variants.tsv'])
        if case['mode']=='king-robust':
            variant_map=table(files[ref.rsplit('/',1)[0]+'/original-variant-map.tsv']);mapped=[]
            for row in expected:
                original=variant_map[int(row['input_row'])-1]
                assert original['input_row']==row['input_row'] and original['gds_snp_id']==row['original_snp_id']
                assert original['rs_id_node_present']=='TRUE' and original['rs_id_na']=='FALSE'
                mapped.append(dict(result_row=row['result_row'],input_variant_row=row['input_row'],original_snp_id=original['rs_id']))
            expected=mapped
        fields=['result_row','input_variant_row','original_snp_id']
        assert [{k:r[k] for k in fields} for r in actual]==[{k:r[k] for k in fields} for r in expected]
        if case['mode']=='pca':
            for name in ['original-eigenvalues.tsv','original-eigenvectors.tsv','original-PCA-summary.tsv']:
                assert table(files[ref+'/'+name])==table(files[work+name]),('Changed full PCA source fields/signs/placeholders',bundle,name)
            if ref+'/original-normalized-genmat.tsv' in files:
                assert table(files[ref+'/original-normalized-genmat.tsv'])==table(files[work+'original-normalized-genmat.tsv'])
            pca_cells+=len(table(files[work+'original-eigenvectors.tsv']))
        elif case['mode']=='grm':
            assert table(files[ref+'/original-complete-matrix.tsv'])==table(files[work+'original-complete-matrix.tsv'])
            assert (ref+'/original-average.tsv' in files)==(work+'original-average.tsv' in files)
            if work+'original-average.tsv' in files:assert table(files[ref+'/original-average.tsv'])==table(files[work+'original-average.tsv'])
            native_cells+=len(table(files[work+'original-complete-matrix.tsv']))
        elif case['mode']=='king-robust':
            first=table(files[ref+'/original-complete-matrices.tsv']);second=table(files[work+'original-complete-matrix.tsv'])
            for old,new in zip(first,second,strict=True):
                for a,b in [('row_index','row_index'),('col_index','col_index'),('kinship_hex','value_hex'),('kinship_na','value_na'),('kinship_nan','value_nan'),('IBS0_hex','IBS0_hex'),('IBS0_na','IBS0_na'),('IBS0_nan','IBS0_nan')]:assert old[a]==new[b]
            native_cells+=len(second)
    assert native_cells==11520 and pca_cells==proof['complete_PCA_eigenvector_cells']
    current=[row for part in range(4) for row in obj(f'current-prior-replay-1-part{part}/COMPLETED.json')['records']]
    assert len(current)==480 and all(r['expected']==r['actual'] for r in current)
    assert {(r['bundle'],r['host']) for r in current}=={(r['bundle'],host) for r in cases if r['mode']!='pca' for host in ['windows','linux']}
    unit=obj('adapter-unit-contracts-3/COMPLETED.json');assert unit['test_methods']==20 and unit['test_executions']==60
    semantic=obj('adapter-semantics-1/COMPLETED.json');assert len(semantic['records'])==44 and all(r['actual']==r['expected'] for r in semantic['records'])
    root=obj('dispatch-validation-1/COMPLETED.json');assert len(root['records'])==33 and root['original_upstream_analyses']==4
    assert all(r['actual']==r['expected'] for r in root['records'])
    assert proof['canonical_executable_unchanged']['sha256']=='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    return proof,files


def replay(home,out,java,compiler):
    proof,files=verify(home);out=Path(out).resolve();assert not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_OFFLINE_SNPRELATE_TOOLS_REPLAY',original_R_analysis_executed=False,downloads_or_installation=False,whole_goal_complete=False);records=[]
    try:
        prefix=out/'retained'
        wanted={r['bundle'] for r in proof['cases']}
        # Restore complete saved byte inventories, including literal producer paths.
        # No runtime programs, packages or path rewrites are needed by --verify.
        for name,raw in files.items():
            if any(name.startswith(folder+'/') for folder in wanted):
                target=prefix/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        jar=Path(home)/f'snprelate-javac{compiler}.jar';artifact=pin(jar)
        for case in proof['cases']:
            bundle=prefix/case['bundle'];r=subprocess.run([str(java),'-Xmx1g','-jar',str(jar),'--verify',str(bundle)],stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
            label=bundle.name;(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr)
            assert r.returncode==case['expected'],(label,r.returncode,case['expected'],r.stderr[-3500:])
            records.append(dict(case=label,expected=case['expected'],actual=r.returncode))
        assert pin(jar)==artifact
        state.update(status='PASS320_OFFLINE_SNPRELATE_TOOLS_SAVED_BUNDLE_REPLAYS_WITHOUT_R',compiler=compiler,records=records,archive=proof['archive'])
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:3000],flush=True)
    return 0 if state['status'].startswith('PASS320_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete evidence, source, population or scientific checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=HOME);p.add_argument('--out',type=Path)
    p.add_argument('--java',type=Path);p.add_argument('--compiler',type=int,choices=[21,25],default=21);a=p.parse_args()
    if a.out:
        assert a.java,'Explicit Java required for saved replay';raise SystemExit(replay(a.home.resolve(),a.out,a.java,a.compiler))
    proof,_=verify(a.home);print(STATUS,'320 complete original-case dispositions; no R execution')
