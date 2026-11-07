# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete native public conversion evidence and reference byte linkage before catalog promotion."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/bigwig-native'


def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();matrix,out=args.matrix.resolve(),args.out.resolve();assert matrix.is_relative_to(BASE.resolve()) and out.is_relative_to((ROOT/'docs/literature').resolve()) and not out.exists();proof=read(matrix/'COMPLETED.json');assert proof['status']=='passed-exact-source-public-native-bigWig-workflows-and-portable-bundles' and proof['exact_source_classes']==25 and len(proof['new_classes'])==15 and proof['changed_classes']==['org/jlinalg/cli/JLinAlgCli.class']
    assert sorted(r['name'] for r in proof['platform_validation'])==['linux-java21','windows-java21','windows-java25'] and all(r['tests']==r['passed']==74 and r['skipped']==0 and r['public_workflows']==44 and r['complete_intervals']==159896 for r in proof['platform_validation']);assert (proof['packaged_public_workflows'],proof['complete_packaged_intervals'],proof['portable_complete_bundle_verifications'],proof['actual_main_example_fits'],proof['actual_main_example_verifications'])==(132,479688,396,6,6);assert len(proof['portable_validation'])==9 and all(r['complete_bundles']==44 and r['process']['exit_code']==0 for r in proof['portable_validation']) and all(r['fit']['exit_code']==r['verify']['exit_code']==0 for r in proof['actual_main_examples']);assert len(proof['actual_permission_skips']['cases'])==2 and all(r['exit_code']==3 and r['uid']==65534 and r['overall_completion_absent'] for r in proof['actual_permission_skips']['cases']);assert len(proof['actual_malformed_metadata_rejections'])==2 and all(r['process']['exit_code']==2 and r['malformed_coordinate_row_rejected'] and r['overall_completion_absent'] for r in proof['actual_malformed_metadata_rejections']);assert proof['public_workflow_available'] and not any(proof[k] for k in ['implementation_credit','released','pushed','deployed','whole_goal_complete'])
    prior=ROOT/'docs/literature/bigwig-native-development';old=read(prior/'VALIDATION.json');assert pin(prior/'controls.zip')=={k:old['archive'][k] for k in ['bytes','sha256']} and pin(prior/'VALIDATION.json')==proof['original_reference_linkage']['native_checkpoint'];sources=dict(proof['source_pins'])
    for path in [Path(__file__),ROOT/'docs/literature/BIGWIG_WORKFLOW.md']:sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'];entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,prefix+'/'+path.relative_to(folder).as_posix())
    def direct(folder,prefix):
        for path in sorted(folder.iterdir()):
            if path.is_file():add(path,prefix+'/'+path.name)
    for name in sources:add(ROOT/name,'source/'+name)
    direct(matrix,'validation');tree(matrix/'source-compiled','validation/exact-source-compiled');tree(matrix/'baseline-source','validation/parent-dispatcher-source')
    for name in ['windows-java21','windows-java25','linux-java21']:tree(matrix/('produced-'+name),'packaged-public-workflows/'+name)
    tree(matrix/'examples','actual-main-examples');tree(matrix/'permission-controls-captured','actual-permission-skips');tree(matrix/'adversarial-main','actual-main-malformed-metadata')
    for label in ['matrix-1','matrix-2']:
        history=matrix.parent/label
        if history==matrix or not history.exists():continue
        direct(history,'history/'+label);tree(history/'source-snapshot','history/'+label+'/source-snapshot')
        for name in ['windows-java21','windows-java25','linux-java21']:
            if (history/('produced-'+name)).exists():tree(history/('produced-'+name),'history/'+label+'/'+name)
        for part in ['examples','permission-controls-captured','adversarial-main']:
            if (history/part).exists():tree(history/part,'history/'+label+'/'+part)
    tree(matrix.parent/'header-ambiguity-probe-1','history/invalidated-header-ambiguity');add(matrix.parent/'probe-header-ambiguity.py','history/probe-header-ambiguity.py')
    tree(ROOT/'build/test-results/test','gradle-focused-test-results');add(prior/'VALIDATION.json','native-reference-checkpoint/VALIDATION.json');add(prior/'SHA256SUMS','native-reference-checkpoint/SHA256SUMS');out.mkdir();archive=out/'controls.zip';members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(members)
        for name,value in members.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95000000 and all(pin(ROOT/n)==v for n,v in sources.items());result=dict(schema_version=1,status='passed-public-native-bigWig-captured-workflows-and-portable-verification',source_pins=sources,artifact=proof['artifact'],platform_validation=proof['platform_validation'],archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,exact_source_classes=25,new_classes=proof['new_classes'],changed_classes=proof['changed_classes'],unchanged_parent_entries=proof['unchanged_parent_entries'],parent_artifact=proof['parent_artifact'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],packaged_public_workflows=132,complete_packaged_intervals=479688,portable_complete_bundle_verifications=396,portable_validation=proof['portable_validation'],actual_main_example_fits=6,actual_main_example_verifications=6,actual_main_examples=proof['actual_main_examples'],actual_malformed_metadata_rejections=proof['actual_malformed_metadata_rejections'],actual_permission_skips=proof['actual_permission_skips'],original_reference_linkage=proof['original_reference_linkage'],original_UCSC_and_pyBigWig_dependency_required_for_conversion=False,historical_malformed_admission_explicitly_invalidated=True,interrupted_matrix_retained=True,public_workflow_available=True,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,scope=proof['scope'],required_action='Read BIGWIG_WORKFLOW.md and examples/bigwig/README.md. Use native bigwig-tools from the exact development candidate; older canonical/released JARs lack it. Exact literal decimal/rounding, reference dispositions, total-summary/zoom and downstream signed-coordinate warnings remain explicit. Require overall COMPLETED.json with no INCOMPLETE/FAILURE and successful complete --verify. Forbidden resource access means named SKIP3; input/precision/integrity/capacity/I/O/cancellation/finalization means FAIL2 with no admitted result. Preserve attempts and retry corrected requests into NEW paths. Source-role/catalog/browser closure remains necessary before integration credit; Cell Ranger is excluded and the whole250-paper implementation goal remains active.')
    (out/'VALIDATION.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf-8',newline='\n');(out/'SHA256SUMS').write_text(pin(archive)['sha256']+'  controls.zip\n'+pin(out/'VALIDATION.json')['sha256']+'  VALIDATION.json\n',encoding='ascii',newline='\n');(out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n');print(json.dumps(dict(status=result['status'],archive=result['archive'],checks_per_JVM=74,public_workflows=132,portable_verifications=396,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete public native evidence checks')
    main()
