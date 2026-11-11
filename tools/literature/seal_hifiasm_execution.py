# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal separate lossless current/native/history evidence archives without changing any earlier receipt."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from hifiasm_execution_evidence import STATUS,BASE,DIRECTORY,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir();base=ROOT/BASE;built=json.loads((base/'execution-build-9/COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-additive-hifiasm-foundation-only';required={'public-workflows-4':'passed-twenty-seven-fresh-public-original-hifiasm-and-native-graph-workflows','final-failure-evidence-1':'passed-eleven-source-bound-hifiasm-skip-owned-failure-and-final-admission-controls','large-parent-workflow-1':'passed-full-large-parent-gzip-trio-ul-workflow-under128MiB-Java-heap','portable-controls-4':'passed-three-JVM-complete-typed-hifiasm-requests-source-export-and-forbidden-install','retained-results-2':'passed-three-JVM-complete-retained-result-integrity-and-sixteen-corruption-controls','native-current-4':'passed-unchanged-complete-native-class-families-bound-to-current-hifiasm-candidate','yak-stream-native-1':'passed-three-JVM-complete-original-streaming-YAK-with27000027-entries-under128MiB-heap'}
    for name,status in required.items():assert json.loads((base/name/'COMPLETED.json').read_bytes())['status']==status,name
    example=json.loads((base/'example-3/result/COMPLETED.json').read_bytes());assert example['analysis_admitted'];sources=set(built['source_pins']);sources.add('docs/literature/HIFIASM.md');excluded={'hifiasm_catalog.py','hifiasm-use-reviews.json'}
    for file in (ROOT/'tools/literature').glob('*hifiasm*'):
        if file.is_file() and file.name not in excluded and 'catalog' not in file.name and not file.name.startswith(('curate_','audit_')):sources.add(file.relative_to(ROOT).as_posix())
    for file in (ROOT/'tools/literature/java/org/jlinalg/cli').glob('Hifiasm*.java'):sources.add(file.relative_to(ROOT).as_posix())
    pins={name:pin(ROOT/name) for name in sorted(sources)};current={'current-source/'+name:(ROOT/name).read_bytes() for name in pins};native={};history={}
    def collect(folder,target,history_only=False):
        for file in sorted(folder.rglob('*')):
            if not file.is_file() or '__pycache__' in file.parts or file.suffix in ['.jar','.class','.o']:continue
            if history_only and not(file.suffix in ['.json','.md','.txt','.stdout','.stderr','.traceback','.py','.java','.cpp'] or file.name in ['error.traceback']):continue
            target[file.relative_to(ROOT).as_posix()]=file.read_bytes()
    current_names=['source-intake-2','revision-r592-1','source-resources-1','execution-build-9','execution-build-8','reference-builds-2','reference-forbidden-install-1','bound-runtimes-2','paper-mode-fixtures-1','paper-modes-1','public-workflows-4','final-failure-controls-2','final-failure-selected-1','final-failure-evidence-1','large-parent-fixtures-1','large-parent-workflow-1','portable-controls-4','retained-results-2','native-current-4','example-3','example-2']
    for name in current_names:assert (base/name).is_dir(),name;collect(base/name,current)
    native_names=['parental-reference-build-2','parental-fixtures-1','parental-native-2','gfa-fixtures-2','gfa-native-3','read-fixtures-2','read-native-3','yak-stream-fixtures-1','yak-stream-native-1']
    for name in native_names:assert (base/name).is_dir(),name;collect(base/name,native)
    for build in ['foundation-build-2','foundation-build-4','execution-build-4','execution-build-8']:
        record=json.loads((base/build/'COMPLETED.json').read_bytes());jar=ROOT/record['artifact']['path']
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if name.startswith('org/jlinalg/assembly/Hifiasm') and name.endswith('.class'):native['closed-native-class/'+build+'/'+name]=z.read(name)
        collect(base/build,history,True)
    for folder in sorted(base.iterdir()):
        if folder.is_dir() and folder.name not in set(current_names+native_names) and folder!=out:collect(folder,history,True)
    # Failed Yak intake did not establish an overall package license. Only its
    # failure receipt and our producer are retained; no Yak source is redistributed.
    history={name:raw for name,raw in history.items() if '/source-intake-1/' not in name or name.endswith(('/COMPLETED.json','/intake-producer-attempt.py','/error.traceback'))};archives={}
    for filename,files in [('execution-controls.zip',current),('native-controls.zip',native),('history-controls.zip',history)]:
        archive=out/filename;record=pack(archive,files);parts=[]
        with archive.open('rb') as stream:
            number=1
            while raw:=stream.read(90000000):
                part=candidate/(filename+'.part'+str(number).zfill(3));part.write_bytes(raw);parts.append(dict(name=part.name,pin=pin(part)));number+=1
        archives[filename]=dict(pin=pin(archive),parts=parts,**record);print(filename,len(files),'logical files',archive.stat().st_size,'archive bytes in',len(parts),'Git-safe parts',flush=True)
    proof=dict(status=STATUS,scope='Declared six-source-profile original hifiasm workflows only, full native input/graph/FASTA and streaming-parental admission, whole-requested-batch atomicity and retained-summary consistency. Original correction/overlap/consensus/phasing remain upstream.',artifact=built['artifact'],parent_artifact=built['parent_artifact'],canonical_executable_unchanged=built['canonical_executable_unchanged'],source_pins=pins,archives=archives,positive_workflows=27,complete_graph_views=246,large_parent_workflow_additional=True,skip_failure_controls=11,retained_corruptions=16,full_declared_hifiasm_workflow_admitted=True,implementation_credit=False,new_catalog_credit=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,scientific_source_exception_added=False,independent_consensus=False,independent_phasing=False,yak_runtime_included=False,older_hic_excluded=['0.14.2-r315','0.15.1-r328'],supervision_scope='Hard per-original-process AS/CPU/file/nofile limits; owned-process cleanup and wall/workspace/thread/process/sample-RSS checks. Native parsing is byte/entry/record bounded and requires caller heap. No hard aggregate RSS or independent biological certificate.',retained_history='All original attempts remain locally unchanged. Published history retains exact diagnostics, receipts, metadata and our producer source; unsupported Yak source is excluded. The observer-race retry supplies one missing fault injection without relabeling the original suite.')
    save(candidate/'VALIDATION.json',proof);(candidate/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n');verify(False,candidate);assert all(pin(ROOT/name)==value for name,value in pins.items());destination=ROOT/DIRECTORY;assert not destination.exists();destination.mkdir()
    for file in candidate.iterdir():(destination/file.name).write_bytes(file.read_bytes())
    verify();save(out/'COMPLETED.json',dict(status='passed-new-complete-hifiasm-execution-proof-sealed-and-verified',validation=pin(destination/'VALIDATION.json'),archives={name:record['pin'] for name,record in archives.items()},whole_goal_complete=False));print('Sealed complete declared hifiasm execution evidence; catalog credit remains separate')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete seal/source/archive gates')
    main()
