# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify recorded original Rsubread execution evidence and current sources/API; do not refit or launch Java."""
import argparse,hashlib,io,json,struct,zipfile
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
DEFAULT=ROOT/'docs/literature/rsubread-execution-development'
BASE='build/literature-review/rsubread-runtime/'


def byte_pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return byte_pin(path.read_bytes())


def unpack(home,proof):
    path=home/proof['archive']['file'];assert pin(path)=={k:proof['archive'][k] for k in ['bytes','sha256']};blobs={};files={}
    expected={v['sha256']:v['bytes'] for v in proof['files'].values()}
    with zipfile.ZipFile(path) as z:
        assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())=={'blobs/'+h for h in expected}
        assert sum(expected.values())<=250000000
        for h,size in expected.items():
            entry=z.getinfo('blobs/'+h);assert entry.file_size==size and size<=50000000;raw=z.read(entry);assert byte_pin(raw)==dict(bytes=size,sha256=h);blobs[h]=raw
    for name,value in proof['files'].items():
        rel=PurePosixPath(name);assert not rel.is_absolute() and str(rel)==name and '..' not in rel.parts and ':' not in name and '\\' not in name
        files[name]=blobs[value['sha256']];assert byte_pin(files[name])==value
    return files


def verify(emit=True,directory=None,archive_only=False):
    home=Path(directory).resolve() if directory else DEFAULT;p=json.loads((home/'VALIDATION.json').read_bytes())
    assert p['status']=='passed-source-bound-original-Rsubread-bulk-counting-workflow' and p['declared_counting_workflow_available']
    assert not any(p[n] for n in ['implementation_credit','whole_package_implemented','whole_paper_reproduced','whole_goal_complete','scientific_source_exception_added','independent_read_counting','independent_junction_counting','analysis_installs_software','paper_binary_identified'])
    f=unpack(home,p)
    def document(path):return json.loads(f[path])
    def receipt(name):return document(BASE+name+'/COMPLETED.json')
    for name in ['runtime-build-3','runtime-build-25-1','workflow-build-4','workflow-build-25-1','runtime-binding-tests-3','runtime-package-exports-1','workflow-public-3','workflow-public-25-1','workflow-portability-1','workflow-portability-25-1','workflow-failures-2','workflow-failures-25-1','process-controls-1','process-controls-25-1','workflow-documentation-1']:
        for source,value in receipt(name).get('source_pins',{}).items():assert byte_pin(f['source/'+source])==value
    for name,value in p['source_pins'].items():
        assert byte_pin(f['source/'+name])==value
        if not archive_only:assert pin(ROOT/name)==value,('Scientific source changed; validate a NEW milestone rather than refreshing hashes',name)
    parent=document('prior-result/VALIDATION.json');parentjar=f['prior-result/rsubread-result-values-api.jar']
    with zipfile.ZipFile(io.BytesIO(parentjar)) as z:oldclasses={n:byte_pin(z.read(n)) for n in z.namelist() if n.endswith('.class')}
    assert len(oldclasses)==14
    artifacts={}
    for label,buildname in [('javac21','workflow-build-4'),('javac25','workflow-build-25-1')]:
        build=receipt(buildname);assert build['status']=='passed-additive-JDK-only-Rsubread-original-counting-workflow-build';raw=f[build['artifact']['path']]
        assert byte_pin(raw)=={k:build['artifact'][k] for k in ['bytes','sha256']}=={k:p['artifacts'][label][k] for k in ['bytes','sha256']}
        if not archive_only:assert (home/p['artifacts'][label]['file']).read_bytes()==raw
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            entries={n:z.read(n) for n in z.namelist()};classes={n:b for n,b in entries.items() if n.endswith('.class')};assert len(classes)==23
            assert all(byte_pin(classes[n])==v for n,v in oldclasses.items())
            assert all(struct.unpack('>H',b[6:8])[0]==61 for b in classes.values())
            for n,v in build['added_entries'].items():assert byte_pin(entries[n])==v
            for n,v in build['preserved_parent_entries'].items():assert byte_pin(entries[n])==v
            assert entries['org/jlinalg/omics/rsubread_count_worker.R']==f['source/src/main/resources/org/jlinalg/omics/rsubread_count_worker.R']
            assert entries['org/jlinalg/omics/rsubread_result_wire.R']==f['source/src/main/resources/org/jlinalg/omics/rsubread_result_wire.R']
            artifacts[label]=entries
    differing=[n for n in artifacts['javac21'] if artifacts['javac21'][n]!=artifacts['javac25'][n]]
    assert sorted(differing)==p['compiler_variant_differing_entries'] and len(differing)==4 and all(n.endswith('.class') for n in differing)
    assert not p['compiler_variants_byte_identical']
    runtime=receipt('runtime-binding-tests-3');assert runtime['status']=='passed-two-public-original-Rsubread-runtime-bindings-six-real-skips-and-existing-output-preservation' and runtime['real_skips']==6 and runtime['exported_original_packages_used']
    package=receipt('runtime-package-exports-1');assert package['status']=='passed-two-exact-original-GPL-private-package-and-source-exports-and-one-real-forbidden-skip'
    for name in ['2260','2232-R441']:
        bound=receipt('runtime-binding-tests-3/'+name+'-bound');assert bound['runtime_bound'] and not bound['analysis_admitted']
        binding=document(BASE+'runtime-binding-tests-3/'+name+'-bound/binding.json');assert byte_pin(f[BASE+'runtime-binding-tests-3/'+name+'-bound/binding.json'])==bound['binding']
        profile=document('source/src/main/resources/org/jlinalg/omics/rsubread-workflow-reference-v2/'+name+'/profile.json')
        exported=receipt('runtime-package-exports-1/'+name);assert exported['runtime_exported'] and not exported['R_executed'] and exported['package_files']==profile['known_package_files']
        assert len(binding['runtime_files'])>1000 and len(binding['guarded_symbols'])>=10
        for root in ['workflow-public-3','workflow-public-25-1']:
            proof=receipt(root);assert proof['status']=='passed-fifty-public-original-Rsubread-complete-count-list-executions-and-separate-original-comparisons'
            result=receipt(root+'/'+name+'-analysis');assert result['analysis_admitted'] and result['status']=='completed-original-counting-with-warnings' and len(result['summaries'])==25
            assert f[BASE+root+'/'+name+'-comparison/R-status.txt'].strip()==b'PASS25_COMPLETE_PUBLIC_ORIGINAL_COUNT_LIST_COMPARISONS_WITH_EXPLICIT_POSITIONAL_TARGET_LEDGER'
            for row in result['summaries']:
                worker=BASE+root+'/'+name+'-analysis/cases/'+row['id']+'/worker/'
                assert f[worker+'analysis-status.txt'].strip()==b'COMPLETE_ORIGINAL_RSUBREAD_COUNT_LIST' and f[worker+'complete-original-result.rds']
                process=document(worker+'process.json');assert process['exit_code']==0 and process['owned_pid']>0
                assert all(not r['same_owned_process_alive'] for r in process['owned_process_cleanup'])
    for root in ['workflow-portability-1','workflow-portability-25-1']:
        q=receipt(root);assert q['status']=='passed-eight-JVM25-original-comparisons-four-portable-verifications-and-two-actual-Linux-runtime-skips' and q['fresh_original_executions']==8 and not q['Linux_original_R_counting_performed']
    for root in ['process-controls-1','process-controls-25-1']:
        q=receipt(root);assert q['status']=='passed-eight-actual-original-R-owned-process-controls-on-two-JVMs'
        for jvm in ['windows21','windows25']:
            for control in ['timeout','output-cap','cancellation','interruption']:
                data=document(BASE+root+'/'+jvm+'/'+control+'/worker/process.json');assert data['owned_pid']>0 and all(not r['same_owned_process_alive'] for r in data['owned_process_cleanup'])
    for root in ['workflow-failures-2','workflow-failures-25-1']:
        q=receipt(root);assert q['status']=='passed-public-Rsubread-real-skips-late-original-native-errors-cancellation-and-deeper-verification-rejections' and len(q['controls'])==11
        assert q['controls']['missing-binding']['exit_code']==3 and q['controls']['forbidden']['exit_code']==3
        for name in ['late-original-annotation-error','late-native-capacity-error','late-original-alignment-error','cancel-after-provisional']:
            failed=receipt(root+'/'+name);assert not failed['analysis_admitted'] and failed['status'].startswith('FAIL2')
        for name in ['changed-annotation-length','extra-result-member']:
            assert q['controls'][name]['outer_hashes_recomputed'] and q['controls'][name]['exit_code']==2
    first=receipt('workflow-public-1/2260-analysis');assert not first['analysis_admitted'] and 'NoSuchFileException' in first['error']
    failed=receipt('workflow-failures-1');assert failed['status']=='FAIL2_RSUBREAD_PUBLIC_FAILURE_VALIDATION' and 'missing-binding' in failed['error']
    doc=receipt('workflow-documentation-1');assert doc['status']=='passed-exact-beginner-package-data-export-binding-counting-and-verification-examples' and doc['paths_only_substituted']
    assert p['catalog_snapshot']['reviewed_roles']==1000 and p['catalog_snapshot']['new_scoped_integrations']==89 and p['catalog_snapshot']['missing']==252
    assert p['current_artifact_original_comparisons_per_compiler']==58
    if not archive_only:
        from rsubread_result_evidence import verify as prior_verify
        old,oldfiles=prior_verify(False);assert old==parent
        for name in p['separate_original_reference_objects']:assert f[name]==oldfiles[name]
    if emit:print('Verified both source-bound bulk-counting artifacts,58 separate-original comparisons per compiler, real skip/failure/process/portable checks and retained failures; no fresh JVM/R run'+(' (archive only)' if archive_only else ''))
    return p,f


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true');args=parser.parse_args();verify(archive_only=args.archive_only)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source, complete-population and original-runtime evidence checks')
    main()
