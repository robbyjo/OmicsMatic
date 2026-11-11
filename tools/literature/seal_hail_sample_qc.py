# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the demonstrated native sample-QC component, complete original/native controls and retained failures; no Hail tool credit."""
import json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin,pack

BASE=ROOT/'build/literature-review/hail-runtime'
DEST=ROOT/'docs/literature/hail-sample-qc-development'

def main():
    assert not DEST.exists();build=BASE/'sample-qc-build-3';reference=BASE/'sample-qc-reference-2';validation=BASE/'sample-qc-validation-4';receipt=json.loads((validation/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_COMPLETE_ORIGINAL_NATIVE_HAIL_SAMPLE_QC_THREE_JVM_COMPONENT_ONLY';build_proof=json.loads((build/'COMPLETED.json').read_bytes());jar=ROOT/build_proof['artifact']['path'];assert pin(jar)=={k:build_proof['artifact'][k] for k in ['bytes','sha256']};sources=dict(receipt['source_pins'])
    for relative in ['tools/literature/hail_sample_qc_evidence.py','tools/literature/seal_hail_sample_qc.py','tools/literature/test_hail_sample_qc_validation.py','tools/literature/signac_scan_evidence.py','docs/literature/HAIL_DEVELOPMENT.md','tools/literature/draft_hail_uses.py','tools/literature/intake_hail_sources.py','tools/literature/setup_hail_reference_linux.py']:sources[relative]=pin(ROOT/relative)
    assert all(pin(ROOT/name)==want for name,want in sources.items());files={'current-source/'+name:(ROOT/name).read_bytes() for name in sources}
    def tree(directory,prefix,include=lambda p:True):
        for path in sorted(directory.rglob('*')):
            if path.is_file() and include(path):
                name=prefix+'/'+path.relative_to(directory).as_posix();assert name not in files;files[name]=path.read_bytes()
    tree(build,'build',lambda p:p.suffix!='.jar' and 'classes' not in p.relative_to(build).parts)
    tree(reference,'reference',lambda p:not any(part.startswith(('private-','hail-temp','hail-local-temp')) for part in p.relative_to(reference).parts))
    tree(validation,'validation')
    for path,prefix in [(BASE/'sample-qc-reference-1','history/reference-1'),(BASE/'sample-qc-validation-1','history/validation-1'),(BASE/'sample-qc-validation-2','history/validation-2'),(BASE/'sample-qc-validation-3','history/validation-3')]:tree(path,prefix,lambda p:not any(part.startswith(('private-','hail-temp','hail-local-temp')) for part in p.relative_to(path).parts))
    for path,prefix in [(BASE/'sample-qc-build-2','history/build-2')]:tree(path,prefix,lambda p:p.suffix!='.jar' and 'classes' not in p.relative_to(path).parts)
    for path in (BASE/'reference-setup-139-2').iterdir():
        if path.is_file():files['setup/'+path.name]=path.read_bytes()
    tree(BASE/'reference-setup-139-1','history/setup-1',lambda p:p.suffix in ['.json','.py','.stdout','.stderr','.md'])
    intake=json.loads((BASE/'source-intake-2/COMPLETED.json').read_bytes());files['upstream/source-intake.json']=(BASE/'source-intake-2/COMPLETED.json').read_bytes();original=intake['versions']['0.2.139'];members=['hail/methods/qc.py','hail/expr/functions.py','hail/genetics/allele_type.py','hail/genetics/call.py','hail/expr/aggregators/aggregators.py','hail/version.py'];files['upstream/LICENSE']=(BASE/'source-intake-2/0.2.139/upstream-LICENSE').read_bytes()
    for member in members:files['upstream/source/'+member]=(BASE/'source-intake-2/0.2.139/source'/member).read_bytes()
    for path in ['upstream-config.mk','upstream-hail_Makefile','upstream-hail_methods_qc.py','upstream-hail_backend_spark_backend.py','upstream-hailtop_batch_batch.py','upstream-hailtop_batch_backend.py']:files['upstream/tagged/'+path]=(BASE/'source-intake-2/0.2.139'/path).read_bytes()
    for path in ['official-git-tag.json','official-annotated-tag.json','pypi-metadata.json']:files['upstream/'+path]=(BASE/'source-intake-2/0.2.139'/path).read_bytes()
    tree(BASE/'role-drafts-2','paper-role-drafts',lambda p:p.suffix in ['.json','.py'])
    entries={}
    with zipfile.ZipFile(jar) as bundle:
        for name in build_proof['added_entries']:raw=bundle.read(name);files['candidate-added-entries/'+name]=raw;entries[name]=byte_pin(raw)
    files['history/CLASSIFICATION.json']=(json.dumps(dict(status='retained-development-failures-not-admitted-results',reference_1='Fixture expression used the MatrixTable source before annotation. Corrected the local fixture and repeated all12 matrices with unchanged original Hail.',validation_1='Native DP/GQ extrema used integer serialization while original schema/results usefloat64;420 mismatches retained. Corrected the native Stats API and repeated allcases on3JVMs.',validation_3='Additional JVM receipt check wrongly required a dotted Java25 version; original JVM prints version25 without a dot. No scientific model/source/output changed. Corrected the receipt parser and repeated complete validation.',setup_1='SystemPython lackedensurepip. Preserved thefailed private prefix;bootstrapped exactofficialpipwheel in anewprivateprefix. SystemPython/JDK unchanged.',validation_2='Complete native/source comparisonpassed before adding explicit actualJVMversion capture;finalvalidation4 supersedes it.',scope='All batches remain atomic: failed attempts have no admitted partial results.'),indent=2)+'\n').encode('utf8')
    assert sum(len(raw) for raw in files.values())<134217728;DEST.mkdir();(DEST/'.gitattributes').write_bytes(b'* -text\n');packing=pack(DEST/'controls.zip',files);proof=dict(schema_version=1,status='passed-native-Hail139-sample-QC-component-only',archive=pin(DEST/'controls.zip'),packing=packing,source_pins=sources,artifact=build_proof['artifact'],original_members=members,packaged_entries=entries,checks=receipt['checks'],canonical_executable_unchanged=receipt['canonical_executable_unchanged'],implementation_credit=False,full_Hail_workflow_admitted=False,whole_goal_complete=False,released_or_deployed=False);save(DEST/'VALIDATION.json',proof)
    from hail_sample_qc_evidence import verify
    verify();assert all(pin(ROOT/name)==want for name,want in sources.items());print('Sealed native QC component only:',len(files),'complete files;',pin(DEST/'controls.zip')['bytes'],'archive bytes')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence sealing checks')
    main()
