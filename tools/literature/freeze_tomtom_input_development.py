# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze exact-source raw input/preprocessing evidence while execution credit remains pending."""
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


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    matrix=args.matrix.resolve();out=args.out.resolve();assert matrix.is_relative_to(BASE.resolve()) and out.is_relative_to((ROOT/'docs/literature').resolve()) and not out.exists()
    proof=read(matrix/'COMPLETED.json');assert proof['status']=='passed-exact-source-three-JVM-Tomtom-native-input-and-output-contracts'
    assert sorted(r['name'] for r in proof['runs'])==['linux-java21','windows-java21','windows-java25']
    assert all(r['tests']==r['passed']==106 and r['skipped']==0 for r in proof['runs'])
    assert proof['exact_source_bytecode_classes']==25 and len(proof['added_classes'])==6
    assert proof['complete_new_original_input_output_contracts']==20 and proof['new_original_reported_rows']==80
    assert proof['regression_original_output_contracts']==30 and proof['regression_original_reported_rows']==6351
    assert not any(proof[k] for k in ['runtime_admitted','user_analysis_admitted','public_execution_adapter_available','implementation_credit','released','pushed','deployed','whole_goal_complete'])
    sources=dict(proof['source_pins'])
    for path in [Path(__file__),ROOT/'docs/literature/MEME_INPUT_CONTRACTS_DEVELOPMENT.md']:sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert all(pin(ROOT/name)==value for name,value in sources.items())
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    old=ROOT/'docs/literature/meme-native-output-development';prior=read(old/'VALIDATION.json')
    assert pin(old/'controls.zip')=={k:prior['archive'][k] for k in ['bytes','sha256']}
    entries={}
    def add(path,name):
        assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,prefix+'/'+path.relative_to(folder).as_posix())
    def direct(folder,prefix):
        for path in sorted(folder.iterdir()):
            if path.is_file():add(path,prefix+'/'+path.name)
    for name in sources:add(ROOT/name,'source/'+name)
    direct(matrix,'validation');tree(matrix/'source-compiled','validation/exact-source-compiled')
    control=BASE/'tomtom-input-controls-1';direct(control,'original-controls/receipts');tree(control/'captured','original-controls/captured')
    failed=BASE/'tomtom-input-development-1/package-1'
    add(failed/'FAILURE.json','history/package-1-failed/FAILURE.json');tree(failed/'source-snapshot','history/package-1-failed/source-snapshot');tree(failed/'failed-test-results','history/package-1-failed/test-results')
    direct(BASE/'tomtom-input-development-1/matrix-1','history/matrix-1-verifier-assumption-failure')
    previous=BASE/'tomtom-input-development-1/matrix-2';direct(previous,'history/matrix-2-103-checks');tree(previous/'source-snapshot','history/matrix-2-103-checks/source-snapshot')
    add(old/'VALIDATION.json','prior-output-component/VALIDATION.json');add(old/'SHA256SUMS','prior-output-component/SHA256SUMS')
    out.mkdir();archive=out/'controls.zip';census={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw)
            census[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(census)
        for name,value in census.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95_000_000 and all(pin(ROOT/name)==value for name,value in sources.items())
    result=dict(schema_version=1,status='passed-native-Tomtom-raw-input-and-preprocessing-components',source_pins=sources,artifact=proof['artifact'],platform_validation=proof['runs'],archive=dict(path=archive.name,**pin(archive),entries=len(census)),archive_files=census,
        exact_source_bytecode_classes=proof['exact_source_bytecode_classes'],added_classes=proof['added_classes'],changed_classes=proof['changed_classes'],unchanged_prior_archive_entries=proof['unchanged_prior_archive_entries'],original_parent=proof['original_parent'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],
        original_input_cases=24,complete_original_input_output_contracts=20,complete_new_reported_rows=80,regression_original_output_contracts=30,complete_regression_reported_rows=6351,original_invalid_inputs=2,original_HTML_successes_reader_unimplemented=2,
        earlier_output_proof=dict(validation=pin(old/'VALIDATION.json'),archive=pin(old/'controls.zip'),unchanged=True),
        scope=proof['scope'],runtime_admitted=False,user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        required_action='Read MEME_INPUT_CONTRACTS_DEVELOPMENT.md. Use independently verified raw files/settings. Preserve format-specific STREME source mappings and every original failure/omission. Unsupported HTML/XML editions/source exclusions need explicit skip or independently retained supported exports, never silent conversion or motif removal. Unavailable/forbidden Java/upstream dependencies/permissions require a prominent named skip and action to supply them on a permitted host. Runtime, typed supervised execution, text-only/full-report-family, public CLI/example and catalog gates remain open; this component does not install or admit analyses.')
    (out/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'SHA256SUMS').write_text(pin(archive)['sha256']+'  controls.zip\n'+pin(out/'VALIDATION.json')['sha256']+'  VALIDATION.json\n',encoding='ascii',newline='\n')
    (out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive=result['archive'],checks_per_jvm=106,complete_original_contracts=50,complete_original_reported_rows=6431,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete frozen component identity checks')
    main()
