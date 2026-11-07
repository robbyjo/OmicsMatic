# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native output-component evidence without crediting a scientific execution adapter."""
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
    matrix=args.matrix.resolve();out=args.out.resolve();assert out.is_relative_to((ROOT/'docs/literature').resolve()) and not out.exists()
    proof=read(matrix/'COMPLETED.json');assert proof['status']=='passed-exact-source-three-JVM-MEME-native-output-contracts'
    assert sorted(r['name'] for r in proof['runs'])==['linux-java21','windows-java21','windows-java25']
    assert all(r['tests']==r['passed']==73 and r['skipped']==0 for r in proof['runs'])
    assert proof['exact_source_bytecode_classes']==19 and len(proof['added_classes'])==19 and proof['complete_original_Tomtom_contracts']==30 and proof['complete_original_reported_rows']==6351
    assert not any(proof[k] for k in ['runtime_admitted','user_analysis_admitted','public_execution_adapter_available','implementation_credit','released','pushed','deployed','whole_goal_complete'])
    sources=dict(proof['source_pins'])
    extra=[Path(__file__),ROOT/'docs/literature/MEME_OUTPUT_CONTRACTS_DEVELOPMENT.md',ROOT/'tools/literature/collect_tomtom_edge_controls.py',ROOT/'tools/literature/audit_streme_original_statistics.py']
    for path in extra:sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert all(pin(ROOT/name)==value for name,value in sources.items())
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    entries={}
    def add(path,name):
        assert path.is_file() and not path.is_symlink() and name not in entries
        entries[name]=path
    def tree(path,prefix):
        for member in sorted(path.rglob('*')):
            if member.is_file():add(member,prefix+'/'+member.relative_to(path).as_posix())
    for name in sources:add(ROOT/name,'source/'+name)
    for path in sorted(matrix.iterdir()):
        if path.is_file():add(path,'validation/'+path.name)
    for area in ['original-controls-1','original-controls-2','tomtom-edge-controls-1','tomtom-edge-controls-2','tomtom-edge-controls-3','streme-statistics-1','streme-statistics-2','streme-statistics-3','streme-statistics-4']:
        folder=BASE/area
        for path in sorted(folder.iterdir()):
            if path.is_file():add(path,'context/'+area+'/'+path.name)
    for area in ['tomtom-edge-controls-1','tomtom-edge-controls-2','tomtom-edge-controls-3']:
        tree(BASE/area/'captured','original-boundaries/'+area)
    failed=BASE/'tomtom-contract-development-1/package-1'
    add(failed/'FAILURE.json','history/untested-compile-failure/FAILURE.json');tree(failed/'source-snapshot','history/untested-compile-failure/source-snapshot')
    # The selected original XML/TSV/text fixtures above cover whole declared
    # populations. Runtime binaries/JARs and unselected report assets stay in
    # the named original development captures; they are not redistributed.
    out.mkdir();archive=out/'controls.zip';census={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw)
            census[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(census)
        for name,value in census.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95_000_000 and all(pin(ROOT/name)==value for name,value in sources.items())
    result=dict(schema_version=1,status='passed-native-MEME-XML-and-Tomtom-complete-output-components',source_pins=sources,artifact=proof['artifact'],platform_validation=proof['runs'],
        archive=dict(path=archive.name,**pin(archive),entries=len(census)),archive_files=census,
        exact_source_bytecode_classes=19,unchanged_prior_archive_entries=proof['unchanged_prior_archive_entries'],original_parent=proof['original_parent'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],
        original_XML_documents=14,original_Tomtom_contracts=30,complete_reported_rows=6351,selected_fixture_scope='Complete selected XML/table/text formats, input populations, commands and original warnings. The fixture manifests name omitted report assets, which remain in original development captures. Original boundary HTML/report files are retained separately here under original-boundaries.',
        source_failure_scope='Earlier22 parameter attempts reused one sub-unit-invalid input and all failed before fitting. The new23-family pass has21 zero exits and two retained original failures; five additional independent probes pass. No failed or unsupported family counts as a passing output contract. Text mode and source-exclusion ledger admission stay open.',
        streme_independent_statistics=read(BASE/'streme-statistics-4/COMPLETED.json'),
        runtime_admitted=False,user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        required_action='Read MEME_OUTPUT_CONTRACTS_DEVELOPMENT.md. These native components require independently verified inputs/settings and complete captured files; they do not launch/admit original analyses. Skip dependent work for unavailable/forbidden Java/upstream software/helpers/resources/permissions or unsupported source populations. Preserve every warning, exclusion and failed attempt; never add unrequested pseudocounts, relabel/zero-fill omissions or recompute q-values from reported matches. Complete input/runtime/process/report/example/catalog gates before public execution credit.')
    (out/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'SHA256SUMS').write_text(pin(archive)['sha256']+'  controls.zip\n'+pin(out/'VALIDATION.json')['sha256']+'  VALIDATION.json\n',encoding='ascii',newline='\n')
    (out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive=result['archive'],cases_per_jvm=73,original_Tomtom_contracts=30,complete_reported_rows=6351,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete frozen-component identity checks')
    main()
