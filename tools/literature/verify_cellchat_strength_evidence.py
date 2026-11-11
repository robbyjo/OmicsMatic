# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only integrity checks/materialization of frozen CellChat2.2.0 strength evidence."""
import argparse,csv,functools,hashlib,io,json,zipfile
from pathlib import Path,PurePosixPath
ROOT=Path(__file__).resolve().parents[2]
def fingerprint(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return fingerprint(path.read_bytes())
def rows(raw):return list(csv.DictReader(io.StringIO(raw.decode('utf8')),delimiter='\t'))

class Evidence:
    def __init__(self,home,proof):self.home=home;self.proof=proof;self.archives={}
    def __enter__(self):
        actual={};total=0
        for name,wanted in self.proof['archives'].items():
            assert pin(self.home/name)==wanted;z=zipfile.ZipFile(self.home/name);self.archives[name]=z;assert len(z.namelist())==len(set(z.namelist()))
            for info in z.infolist():
                assert info.filename.startswith('blobs/') and len(info.filename)==70;digest=info.filename[6:];assert digest not in actual and all(c in '0123456789abcdef' for c in digest)
                value=fingerprint(z.read(info));assert value['sha256']==digest;actual[digest]=dict(archive=name,**value);total+=value['bytes']
        assert actual==self.proof['blobs'] and total<1073741824 and len(actual)<30000
        for name,value in self.proof['members'].items():
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name;assert {k:actual[value['sha256']][k] for k in ['bytes','sha256']}==value
        return self
    def __exit__(self,*args):
        self.raw.cache_clear()
        for z in self.archives.values():z.close()
    @functools.lru_cache(maxsize=8)
    def raw(self,name):
        p=self.proof['members'][name];b=self.proof['blobs'][p['sha256']];raw=self.archives[b['archive']].read('blobs/'+p['sha256']);assert fingerprint(raw)==p;return raw
    def json(self,name):return json.loads(self.raw(name))

def verify(home,check_current=False,materialize=None,target=None):
    proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_ORIGINAL_CELLCHAT_STRENGTH_WORKFLOW_DEVELOPMENT_ONLY'
    assert not proof['new_catalog_credit'] and not proof['whole_goal_complete'] and not proof['all_cellchat_roles_completed'];assert (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'])==(93,96,248)
    for name,wanted in {**proof['development_jars'],**proof['example_and_addendum_files']}.items():assert pin(home/name)==wanted
    if check_current:
        for name,wanted in {**proof['source_pins'],**proof['catalog_unchanged']}.items():assert pin(ROOT/name)==wanted,name
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    with Evidence(home,proof) as e:
        for name,wanted in proof['source_pins'].items():assert fingerprint(e.raw('source/'+name))==wanted
        direct=e.json('evidence/strength-direct-reference-1/refits/COMPLETED.json');assert direct['status']=='PASS_SEPARATE_ORIGINAL_DEFAULT_STRENGTH_WORKFLOW_AND_COMPARISONS' and direct['separate_original_fits']==9 and len(direct['checks'])==174 and not direct['historical_paper_reproduction']
        exported=e.json('evidence/strength-export-reference-1/checks/COMPLETED.json');assert exported['status']=='PASS_ORIGINAL_STRENGTH_EXPORTS_AND_PARAMETER_SPECIFIC_TESTS' and len(exported['checks'])==175 and len(exported['bundle_rows'])==7 and not exported['CellChat_inference_refitted']
        total=0
        for suite in proof['portable_suites']:
            d=e.json('evidence/'+suite+'/COMPLETED.json');assert d['status']=='PASS_COMPLETE_ORIGINAL_STRENGTH_OFFLINE_BUNDLES' and d['bundles']==7 and not d['R_executed'];assert len(d['records'])==7 and all(r['exit_code']==0 for r in d['records']);total+=7
        assert total==42
        prepared=e.json('evidence/strength-prepared-probes-2/COMPLETED.json');assert prepared['status']=='PASS_SIX_PREPARED_STRENGTH_PROBES' and prepared['native_comparisons']==780 and prepared['invalid_input_refusals']==48 and len(prepared['records'])==6 and all(r['exit_code']==0 for r in prepared['records'])
        admission=e.json('evidence/strength-admission-controls-1/COMPLETED.json');assert admission['status']=='PASS_STRENGTH_SKIP_FAILURE_AND_REHASHED_REFUSALS' and (admission['analysis_dispositions'],admission['offline_tamper_refusals'])==(15,8) and admission['owned_timeout_terminal'];assert len(admission['records'])==23 and all(r['exit_code']==r['expected_exit'] for r in admission['records'])
        final=e.json('evidence/strength-final-refusals-1/COMPLETED.json');assert final['status']=='PASS_FINAL_STRENGTH_REHASHED_REFUSALS' and (final['variants'],final['refusals'])==(9,18) and not final['R_executed'] and all(r['exit_code']==2 for r in final['records'])
        delta=e.json('evidence/strength-published-audit-delta-1/COMPLETED.json');assert delta['status']=='PASS_ONLY_NATIVE_STRENGTH_HEADER_CHECK_CHANGED' and delta['original_R_worker_and_owned_controller_unchanged'];assert all(v==['org/jlinalg/cli/CellChatStrengthComparison.class'] for v in delta['changed_members'].values())
        cases=0
        for case in proof['complete_bundles']:
            prefix='evidence/'+case+'/';complete=e.json(prefix+'COMPLETED.json');assert complete['result_admitted'] is True
            assert {n for n in proof['members'] if n.startswith(prefix)}=={prefix+n for n in complete['files']}|{prefix+'COMPLETED.json'}
            for name,wanted in complete['files'].items():assert fingerprint(e.raw(prefix+name))==wanted
            report=e.json(prefix+'results/worker-report.json');assert report['status']=='COMPLETED_ORIGINAL_CELLCHAT_STRENGTH_WORKER_ONLY' and not report['analysis_admitted'];cases+=len(report['cases'])
            provenance=e.json(prefix+'provenance.json');warning=e.raw(prefix+'WARNINGS.md').decode('utf8');assert complete['required_action'] in warning
            for field in ['not_comparable_pooled_strengths','not_tested_strength_comparisons','original_nonfinite_strength_test_fields','original_strength_test_warning_count','missing_case_strengths']:
                if provenance[field]>0:assert field+'='+str(provenance[field]) in warning
            full=rows(e.raw(prefix+'results/comparison/pooled-and-sample-strengths.tsv'));selected=rows(e.raw(prefix+'results/comparison/selected-strengths.tsv'));long=rows(e.raw(prefix+'results/comparison/strengths-by-case.tsv'))
            assert len(full)==provenance['strength_comparison_rows'] and selected==[r for r in full if r['selected']=='TRUE'] and len(selected)==provenance['selected_strength_comparisons'];assert len(long)==len(full)*len(report['cases'])
            assert sum(r['value'].strip()=='NA' for r in long)==provenance['missing_case_strengths'];assert sum(r['status']=='original-nonfinite-wilcox-result' for r in full)==provenance['original_nonfinite_strength_test_fields']
            if 'strength-controls-3x4-' in case:assert len(report['cases'])==9 and len(full)==234 and len(selected)==26 and provenance['tested_strength_comparisons']==26
        assert cases==60
        for label,expected in [('missing-group',504),('missing-feature',12),('missing-pooled-feature',30)]:assert e.json(f'evidence/strength-edge-{label}-1/analysis/provenance.json')['missing_case_strengths']==expected
        pooled=e.json('evidence/strength-edge-missing-pooled-feature-1/analysis/provenance.json');assert pooled['not_comparable_pooled_strengths']==6
        asym=e.json('evidence/strength-edge-asymptotic-zero-1/analysis/provenance.json');assert asym['original_nonfinite_strength_test_fields']==38
        nonfinite=rows(e.raw('evidence/strength-edge-asymptotic-zero-1/analysis/results/comparison/pooled-and-sample-strengths.tsv'));assert sum(r['wilcox_pvalue'].strip()=='NaN' for r in nonfinite)==38
        for co in [21,25]:assert e.json(f'evidence/strength-controls-3x4-javac{co}-1/analysis/COMPLETED.json')['result_admitted'] is False
        addendum=json.loads((home/'P145-source-addendum.json').read_bytes());assert addendum['status']=='PASS_PRIMARY_BOUND_P145_STRENGTH_ROLE_DEVELOPMENT_ONLY' and not addendum['primary_text_redistributed'] and not addendum['tested_candidate']['historical_environment_reproduced']
        if materialize is not None:
            assert target is not None and not target.exists();prefix=materialize.rstrip('/')+'/';selected=[n for n in proof['members'] if n.startswith(prefix)];assert selected,'Unknown evidence prefix';target.mkdir(parents=True)
            for name in sorted(selected,key=lambda n:(n.endswith('/COMPLETED.json'),n)):
                relative=PurePosixPath(name[len(prefix):]);dest=target.joinpath(*relative.parts);assert dest.resolve().is_relative_to(target.resolve());dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(e.raw(name))
            print('Materialized exact evidence prefix to NEW directory:',target)
    print(json.dumps(dict(status='PASS_READ_ONLY_ORIGINAL_CELLCHAT_STRENGTH_EVIDENCE',complete_bundles=7,original_admitted_fits=60,portable_bundle_checks=42,separate_original_refit_and_strength_checks=174,parameter_specific_export_checks=175,native_prepared_checks=780,analysis_failure_skip_controls=15,final_rehashed_refusals=18,new_catalog_credit=False,whole_goal_complete=False),indent=2));return proof

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen evidence checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=ROOT/'docs/literature/cellchat-strength-development');p.add_argument('--check-current-source',action='store_true');p.add_argument('--materialize');p.add_argument('--out',type=Path);a=p.parse_args()
    try:verify(a.home,a.check_current_source,a.materialize,a.out)
    except Exception as error:raise SystemExit('FAIL2 CELLCHAT STRENGTH EVIDENCE: '+repr(error)+'\nRequired action: preserve incomplete evidence, correct the named source/file mismatch and retry. Materialization alone does not admit analysis. No R/Java fit or installation was performed.')
