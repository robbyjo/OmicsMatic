# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only integrity checks/materialization of frozen CellChat1.0.0/2.1.2 evidence."""
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
    proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_ORIGINAL_CELLCHAT_1_0_0_AND_2_1_2_INFERENCE_DEVELOPMENT_ONLY'
    assert not proof['new_catalog_credit'] and not proof['whole_goal_complete'] and not proof['all_cellchat_roles_completed'];assert (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'])==(93,96,248)
    for name,wanted in {**proof['development_jars'],**proof['example_and_addendum_files']}.items():assert pin(home/name)==wanted
    if check_current:
        for name,wanted in {**proof['source_pins'],**proof['catalog_unchanged']}.items():assert pin(ROOT/name)==wanted,name
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    with Evidence(home,proof) as e:
        for name,wanted in proof['source_pins'].items():assert fingerprint(e.raw('source/'+name))==wanted
        for label,count in [('1.0.0',36),('2.1.2',54)]:
            ref=e.json(f'evidence/inference-direct-reference-{label}-2/refits/COMPLETED.json');assert ref['status']=='PASS_SEPARATE_ORIGINAL_CELLCHAT_EDITION_REFITS' and len(ref['checks'])==count and not ref['historical_paper_reproduction']
            exported=e.json(f'evidence/inference-export-reference-{label}-1/checks/COMPLETED.json');assert exported['status']=='PASS_EXPORTED_FIELDS_MATCH_SEPARATE_ORIGINAL_REFITS' and len(exported['checks'])==(8 if label=='1.0.0' else 12)
        total=0
        for suite in proof['portable_suites']:
            d=e.json('evidence/'+suite+'/COMPLETED.json');assert d['status']=='PASS_COMPLETE_ORIGINAL_INFERENCE_OFFLINE_BUNDLES' and d['bundles']==4 and not d['R_executed'];assert len(d['records'])==4 and all(r['exit_code']==0 for r in d['records']);total+=4
        assert total==16
        binary=e.json('evidence/inference-binary-probes-1/COMPLETED.json');assert binary['status']=='PASS_SIX_PORTABLE_BINARY_PROBES' and binary['invalid_binary_refusals']==36 and all(r['exit_code']==0 for r in binary['records'])
        admission=e.json('evidence/inference-admission-controls-2/COMPLETED.json');assert admission['status']=='PASS_INFERENCE_SKIP_FAILURE_AND_REHASHED_REFUSALS' and (admission['analysis_dispositions'],admission['offline_tamper_refusals'])==(16,7) and admission['owned_timeout_terminal'];assert len(admission['records'])==23 and all(r['exit_code']==r['expected_exit'] for r in admission['records'])
        for case in proof['complete_bundles']:
            prefix='evidence/'+case+'/';complete=e.json(prefix+'COMPLETED.json');assert complete['result_admitted'] is True
            assert {n for n in proof['members'] if n.startswith(prefix)}=={prefix+n for n in complete['files']}|{prefix+'COMPLETED.json'}
            for name,wanted in complete['files'].items():assert fingerprint(e.raw(prefix+name))==wanted
            report=e.json(prefix+'results/worker-report.json');assert report['status']=='COMPLETED_ORIGINAL_CELLCHAT_INFERENCE_WORKER_ONLY' and not report['analysis_admitted']
            provenance=e.json(prefix+'provenance.json');warning=e.raw(prefix+'WARNINGS.md').decode('utf8');assert complete['required_action'] in warning
            for field in ['original_centrality_fallbacks','original_nonfinite_centrality_fields','original_out_of_range_p_fields']:
                if provenance[field]>0:assert field+'='+str(provenance[field]) in warning
            if 'inference-controls-' in case:
                label=provenance['source_label'];assert len(report['cases'])==(2 if label=='1.0.0' else 3);assert all(c['interactions']==35 and c['cells']==108 and c['pathways']==4 for c in provenance['cases']);assert provenance['projection_cells']==(7128 if label=='1.0.0' else 10692)
                ppi=e.json(prefix+'results/ppi-resource.json');assert ppi['rows']==ppi['cols']==4815 and ppi['genes']==ppi['columns'];assert fingerprint(e.raw(prefix+'results/ppi-values.f64le'))==dict(bytes=185473800,sha256='325a4e79caa1b68b3d4fbd8169201afc55f18197a1787972e54565f49d52a73a')
                if label=='1.0.0':assert len(rows(e.raw(prefix+'results/selected-database-interactions.tsv')))==1939
        boundary=e.json('evidence/inference-filter-boundary-2/COMPLETED.json');assert boundary['status']=='PASS_ORIGINAL_2_1_2_OPTIONAL_FILTER_TEN_CELL_BOUNDARY' and boundary['positive_other_edges']==dict(unfiltered=175,filtered=0)
        for tag,wanted in [('unfiltered',175),('filtered',0)]:
            values=rows(e.raw(f'evidence/inference-filter-boundary-2/{tag}/analysis/results/cases/condition-1/probabilities-filtered.tsv'));assert sum(float(r['value'])>0 and (r['source']=='other' or r['target']=='other') for r in values)==wanted
        addendum=json.loads((home/'paper-source-addendum.json').read_bytes());assert addendum['status']=='PASS_SOURCE_BOUND_INFERENCE_PAPER_ADDENDUM_ONLY' and not addendum['P090']['historical_R_reproduced'] and not addendum['P100']['filterCommunication_called'] and not addendum['P100']['raw_author_code_redistributed']
        if materialize is not None:
            assert target is not None and not target.exists();prefix=materialize.rstrip('/')+'/';selected=[n for n in proof['members'] if n.startswith(prefix)];assert selected,'Unknown evidence prefix';target.mkdir(parents=True)
            for name in sorted(selected,key=lambda n:(n.endswith('/COMPLETED.json'),n)):
                relative=PurePosixPath(name[len(prefix):]);dest=target.joinpath(*relative.parts);assert dest.resolve().is_relative_to(target.resolve());dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(e.raw(name))
            print('Materialized exact evidence prefix to NEW directory:',target)
    print(json.dumps(dict(status='PASS_READ_ONLY_ORIGINAL_CELLCHAT_INFERENCE_EVIDENCE',complete_bundles=6,portable_bundle_checks=16,separate_original_refit_checks=90,separate_reference_export_checks=20,analysis_failure_skip_controls=16,offline_tamper_refusals=7,new_catalog_credit=False,whole_goal_complete=False),indent=2));return proof

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen evidence checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=ROOT/'docs/literature/cellchat-inference-development');p.add_argument('--check-current-source',action='store_true');p.add_argument('--materialize');p.add_argument('--out',type=Path);a=p.parse_args()
    try:verify(a.home,a.check_current_source,a.materialize,a.out)
    except Exception as error:raise SystemExit('FAIL2 CELLCHAT INFERENCE EVIDENCE: '+repr(error)+'\nRequired action: preserve the incomplete evidence, correct the named source/file mismatch and retry. Materialization alone does not admit analysis. No R/Java refit or installation was performed.')
