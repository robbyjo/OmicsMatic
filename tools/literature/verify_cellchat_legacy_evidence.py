# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify/materialize frozen original CellChat1.1.3 evidence; no scientific execution."""
import argparse,csv,functools,hashlib,io,json,math,zipfile
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
            file=self.home/name;assert pin(file)==wanted;z=zipfile.ZipFile(file);self.archives[name]=z
            assert len(z.namelist())==len(set(z.namelist()))
            for info in z.infolist():
                assert info.filename.startswith('blobs/') and info.filename not in actual and len(info.filename)==70
                digest=info.filename[6:];assert all(c in '0123456789abcdef' for c in digest)
                raw=z.read(info);p=fingerprint(raw);assert p['sha256']==digest;actual[digest]=dict(archive=name,**p);total+=len(raw)
        assert actual==self.proof['blobs'] and total<1073741824 and len(actual)<30000
        for name,p in self.proof['members'].items():
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
            assert p['sha256'] in actual and {k:actual[p['sha256']][k] for k in ['bytes','sha256']}==p
        return self
    def __exit__(self,*args):
        self.raw.cache_clear()
        for z in self.archives.values():z.close()
    @functools.lru_cache(maxsize=16)
    def raw(self,name):
        p=self.proof['members'][name];blob=self.proof['blobs'][p['sha256']];raw=self.archives[blob['archive']].read('blobs/'+p['sha256']);assert fingerprint(raw)==p;return raw
    def json(self,name):return json.loads(self.raw(name))

def verify(home,check_current=False,materialize=None,target=None):
    proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_ORIGINAL_CELLCHAT_1_1_3_CONDITION_WORKFLOW_DEVELOPMENT_ONLY'
    assert not proof['new_catalog_credit'] and not proof['whole_goal_complete'] and (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'])==(93,96,248)
    for name,wanted in {**proof['development_jars'],**proof['example_and_addendum_files']}.items():assert pin(home/name)==wanted
    if check_current:
        for name,wanted in {**proof['source_pins'],**proof['catalog_unchanged']}.items():assert pin(ROOT/name)==wanted,name
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    with Evidence(home,proof) as e:
        for name,wanted in proof['source_pins'].items():assert fingerprint(e.raw('source/'+name))==wanted
        portable=0
        for name in proof['portable_suites']:
            d=e.json('evidence/'+name+'/COMPLETED.json');assert d['status']=='PASS_COMPLETE_LEGACY_OFFLINE_BUNDLES_AND_NATIVE_API' and d['bundles']==5 and not d['R_executed'];assert all(r['exit_code']==0 for r in d['records']);portable+=5
        assert portable==30
        direct=e.json('evidence/legacy-direct-reference-1/COMPLETED.json');assert direct['status']=='PASS_SEPARATE_FIXED_ORIGINAL_CELLCHAT_1_1_3_REFITS' and len(direct['checks'])==58 and direct['separate_original_condition_refits']==2
        faults=e.json('evidence/legacy-admission-controls-1/COMPLETED.json');assert faults['status']=='PASS_LEGACY_WORKFLOW_SKIP_FAILURE_AND_REHASHED_REFUSALS' and (faults['analysis_dispositions'],faults['offline_tamper_refusals'])==(17,9) and faults['owned_timeout_terminal'];assert len(faults['records'])==26 and all(r['exit_code']==r['expected_exit'] for r in faults['records'])
        for case in proof['complete_bundles']:
            prefix='evidence/'+case+'/';completion=e.json(prefix+'COMPLETED.json');assert completion['result_admitted'] is True
            expected={prefix+n for n in completion['files']}|{prefix+'COMPLETED.json'};assert {n for n in proof['members'] if n.startswith(prefix)}==expected
            for name,wanted in completion['files'].items():assert fingerprint(e.raw(prefix+name))==wanted
            report=e.json(prefix+'results/worker-report.json');assert report['status']=='COMPLETED_ORIGINAL_CELLCHAT_LEGACY_WORKER_ONLY' and not report['analysis_admitted'] and len(report['cases'])==2
            assert len(e.json(prefix+'results/guarded-original-functions.json'))==31
            provenance=e.json(prefix+'provenance.json');warning=e.raw(prefix+'WARNINGS.md').decode('utf8')
            for field in ['original_out_of_range_p_fields','original_duplicate_neighbor_entries','original_similarity_outside_unit_interval','original_omitted_strictly_closer_neighbors','original_ranknet_zero_fallbacks']:
                if provenance[field]>0:assert field+'='+str(provenance[field]) in warning
            assert 'rankNet compares paired cell-group edges' in warning
            abnormal=duplicates=0
            for kind in ['functional','structural']:
                for row in rows(e.raw(prefix+'results/comparison/'+kind+'-similarity.tsv')):
                    x=float(row['value']);abnormal+=int(not math.isfinite(x) or x<0 or x>1)
                seen={}
                for row in rows(e.raw(prefix+'results/comparison/'+kind+'-knn.tsv')):
                    values=seen.setdefault(row['feature'],set());neighbor=int(float(row['neighbor']));duplicates+=int(neighbor in values);values.add(neighbor)
            assert (abnormal,duplicates)==(provenance['original_similarity_outside_unit_interval'],provenance['original_duplicate_neighbor_entries'])
        edge='evidence/legacy-prefilter-boundary-1/analysis/results/cases/';excluded=[]
        for i,count in [(1,30),(2,29)]:
            d=rows(e.raw(edge+f'condition-{i}/cell-dispositions.tsv'));removed=[r for r in d if r['included']=='FALSE'];assert len(removed)==count and all(r['reason']=='explicit-any-condition-min-group-prefilter' for r in removed);excluded.extend(removed)
        assert len(excluded)==59
        for i in [1,2]:
            d=e.json(f'evidence/legacy-projected-mouse-controls-2/analysis/results/cases/condition-{i}/projection-report.json');assert d['projection_consumed'] and d['overlap_count']==1 and d['overlapping_genes']==['Fgfr2']
        assert e.json('evidence/legacy-snn-reference-1/COMPLETED.json')['status']=='PASS_ORIGINAL_SNN_UNIQUE_DUPLICATE_INFINITY_CONTROLS'
        if materialize is not None:
            assert target is not None and not target.exists();prefix=materialize.rstrip('/')+'/';selected=[n for n in proof['members'] if n.startswith(prefix)];assert selected,'Unknown materialization prefix'
            target.mkdir(parents=True)
            for name in sorted(selected,key=lambda n:(n.endswith('/COMPLETED.json'),n)):
                relative=PurePosixPath(name[len(prefix):]);dest=target.joinpath(*relative.parts);assert dest.resolve().is_relative_to(target.resolve());dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(e.raw(name))
            print('Materialized exact evidence prefix to NEW directory:',target)
    print(json.dumps(dict(status='PASS_READ_ONLY_ORIGINAL_CELLCHAT_LEGACY_EVIDENCE',complete_bundles=5,portable_bundle_checks=30,separate_original_refit_checks=58,analysis_failure_skip_controls=17,offline_tamper_refusals=9,new_catalog_credit=False,whole_goal_complete=False),indent=2))
    return proof

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence integrity checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=ROOT/'docs/literature/cellchat-legacy-development');p.add_argument('--check-current-source',action='store_true');p.add_argument('--materialize');p.add_argument('--out',type=Path);a=p.parse_args()
    try:verify(a.home,a.check_current_source,a.materialize,a.out)
    except Exception as error:raise SystemExit('FAIL2 CELLCHAT LEGACY EVIDENCE: '+repr(error)+'\nRequired action: preserve incomplete evidence, correct the named file/source mismatch and retry verification. Materialization is not analysis admission. No R/Java refit or installation was performed.')
