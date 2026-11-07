# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze actual public Tomtom workflows, portable contracts, standalone setup and documented examples."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def read(path):return json.loads(path.read_bytes())


def capture(matrix):
    inventory=read(matrix/'NATIVE-INVENTORY.json');actual={}
    for path in sorted((matrix/'captured').rglob('*')):
        mode=path.lstat().st_mode;name=path.relative_to(matrix/'captured').as_posix()
        if stat.S_ISDIR(mode):actual[name]=dict(kind='directory')
        else:assert stat.S_ISREG(mode);actual[name]=dict(kind='file',**pin(path))
    assert set(actual)==set(inventory)
    for name,value in inventory.items():assert {k:v for k,v in value.items() if k!='mode'}==actual[name]
    return len(actual)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--setup-example',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();matrix,setup,out=args.matrix.resolve(),args.setup_example.resolve(),args.out.resolve();assert matrix.is_relative_to(BASE.resolve()) and setup.is_relative_to(BASE.resolve()) and out.is_relative_to((ROOT/'docs/literature').resolve()) and not out.exists()
    proof=read(matrix/'COMPLETED.json');example=read(setup/'COMPLETED.json');assert proof['status']=='passed-exact-source-public-Tomtom-workflows-and-portable-main-verification' and example['status']=='passed-exported-source-setup-and-documented-public-workflows'
    assert proof['artifact']==example['artifact'] and proof['canonical_executable_unchanged']==example['canonical_executable_unchanged']
    assert proof['exact_source_bytecode_classes']==64 and len(proof['added_classes'])==16 and proof['complete_public_workflows']==2 and len(proof['portable_actual_main_verifications'])==6
    assert sorted(r['name'] for r in proof['runs'])==['linux-java21','windows-java21','windows-java25'] and all(r['tests']==r['passed']==286 and r['skipped']==0 for r in proof['runs'])
    assert all(r['process']['exit_code']==0 for r in proof['public_main_runs']+proof['portable_actual_main_verifications']) and proof['public_execution_adapter_available']
    assert not any(proof[k] or example[k] for k in ['implementation_credit','released','pushed','deployed','whole_goal_complete'])
    native=example['native_completion'];assert native['documented_workflows']==2 and native['permission_skip'] and native['archive_failure'] and native['public_analysis_admitted'] and not native['biological_accuracy_certified'] and len(native['owned_worker_controls'])==3 and all(not r['owned_live_after_cleanup'] for r in native['owned_worker_controls'])
    sources=dict(proof['source_pins'])
    for name,value in example['source_pins'].items():assert name not in sources or sources[name]==value;sources[name]=value
    for path in [Path(__file__),ROOT/'docs/literature/MEME_TOMTOM_WORKFLOW_DEVELOPMENT.md']:sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert all(pin(ROOT/name)==value for name,value in sources.items()) and pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    nodes=capture(matrix);setup_nodes=capture(setup);history=BASE/'tomtom-workflow-development-1/matrix-1';capture(history)
    prior=ROOT/'docs/literature/meme-native-runtime-development';old=read(prior/'VALIDATION.json');assert pin(prior/'controls.zip')=={k:old['archive'][k] for k in ['bytes','sha256']}
    entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,prefix+'/'+path.relative_to(folder).as_posix())
    def direct(folder,prefix):
        for path in sorted(folder.iterdir()):
            if path.is_file():add(path,prefix+'/'+path.name)
    for name in sources:add(ROOT/name,'source/'+name)
    direct(matrix,'validation');tree(matrix/'source-compiled','validation/exact-source-compiled');tree(matrix/'baseline-source','validation/frozen-owner-baselines');tree(matrix/'captured','public-workflow-controls')
    direct(setup,'setup-example-validation');tree(setup/'captured','exported-setup-and-documented-examples')
    direct(history,'history/initial-public-workflows');tree(history/'source-snapshot','history/initial-public-workflows/source-snapshot');tree(history/'captured','history/initial-public-workflows/captured')
    tree(ROOT/'build/test-results/test','gradle-focused-test-results');add(prior/'VALIDATION.json','prior-native-runtime/VALIDATION.json');add(prior/'SHA256SUMS','prior-native-runtime/SHA256SUMS')
    out.mkdir();archive=out/'controls.zip';members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(members)
        for name,value in members.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95000000 and all(pin(ROOT/name)==value for name,value in sources.items())
    result=dict(schema_version=1,status='passed-public-original-Tomtom-workflow-setup-example-and-portable-contracts',source_pins=sources,artifact=proof['artifact'],platform_validation=proof['runs'],archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,exact_source_bytecode_classes=64,added_archive_entries=proof['added_classes'],changed_classes=proof['changed_classes'],unchanged_prior_archive_entries=proof['unchanged_prior_archive_entries'],original_parent=proof['original_parent'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],complete_public_control_workflows=2,complete_documented_workflows=2,portable_actual_main_verifications=6,original_user_fits=4,fresh_original_health_fits=8,complete_public_control_rows=18,complete_documented_rows=18,complete_native_control_nodes=nodes,complete_setup_example_nodes=setup_nodes,setup_native_completion=native,semantic_mutations_with_recomputed_hashes=14,copied_record_is_runtime_ticket=False,prior_runtime_proof=dict(validation=pin(prior/'VALIDATION.json'),archive=pin(prior/'controls.zip'),unchanged=True),public_execution_adapter_available=True,standalone_setup_available=True,documented_example_validated=True,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,scope='Complete current original5.5.9 DNA motif comparison workflows with unchanged inputs, requested full output family including exact HTML scientific display payload, complete pair/NA ledger, strict original processes, fresh runtime health, portable complete captured contracts and separately permitted exact source setup. Four public synthetic user fits and eight fresh health fits across final public/standalone-example controls. The initial historical public matrix is retained separately. No historical edition equivalence, paper reconstruction, P-value calibration or biological certificate.',required_action='Read MEME_TOMTOM_WORKFLOW_DEVELOPMENT.md and examples/tomtom/README.md. Use permitted explicit standalone source setup; unavailable/forbidden requirements mean SKIP3 and dependent analysis skipped. Integrity/process/capacity/cancellation/finalization failures mean FAIL2 and no admitted result. Preserve all partials/native workers, correct the named cause and use NEW paths. Require overall COMPLETED.json plus successful complete --verify. Catalog/dispatcher compatibility/browser closure remains open before integration credit.')
    (out/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');(out/'SHA256SUMS').write_text(pin(archive)['sha256']+'  controls.zip\n'+pin(out/'VALIDATION.json')['sha256']+'  VALIDATION.json\n',encoding='ascii',newline='\n');(out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n');print(json.dumps(dict(status=result['status'],archive=result['archive'],checks_per_jvm=286,original_user_fits=4,fresh_health_fits=8,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete public workflow/setup/example evidence checks')
    main()
