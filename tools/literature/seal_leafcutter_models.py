# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete original model controls, lossless retained-fit binding, native audits, source/runtime preparation and failures; no full-tool credit."""
import json,subprocess,sys,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin,pack

BASE=ROOT/'build/literature-review/leafcutter-runtime'
DEST=ROOT/'docs/literature/leafcutter-models-development'


def main():
    assert not DEST.exists();build=BASE/'model-build-4';validation=BASE/'model-validation-3';compiled=json.loads((build/'COMPLETED.json').read_bytes());receipt=json.loads((validation/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_MODEL_AUDIT_THREE_JVM_ONLY';jar=ROOT/compiled['artifact']['path'];assert pin(jar)=={k:compiled['artifact'][k] for k in ['bytes','sha256']};sources=dict(receipt['source_pins'])
    for home in ['r-library-setup-1','model-original-2','model-precision-1']:
        for name,want in json.loads((BASE/home/'COMPLETED.json').read_bytes())['source_pins'].items():assert name not in sources or sources[name]==want;sources[name]=want
    for name in ['intake_leafcutter_r_package.py','intake_leafcutter_r_addons.py','intake_leafcutter_compatibility_deps.py','leafcutter_models_evidence.py','seal_leafcutter_models.py','test_leafcutter_models_comparison.py','signac_scan_evidence.py']:sources['tools/literature/'+name]=pin(ROOT/'tools/literature'/name)
    sources['docs/literature/LEAFCUTTER_MODELS_DEVELOPMENT.md']=pin(ROOT/'docs/literature/LEAFCUTTER_MODELS_DEVELOPMENT.md');assert all(pin(ROOT/n)==want for n,want in sources.items());files={'current-source/'+name:(ROOT/name).read_bytes() for name in sources}
    def tree(directory,prefix,top_only=False):
        for file in sorted(directory.iterdir() if top_only else directory.rglob('*')):
            if file.is_file() and file.suffix!='.jar' and 'classes' not in file.relative_to(directory).parts and '__pycache__' not in file.parts:assert not file.is_symlink();files[prefix+'/'+file.relative_to(directory).as_posix()]=file.read_bytes()
    for directory,prefix in [(build,'build'),(validation,'validation'),(BASE/'model-original-2','original'),(BASE/'model-precision-1','precision'),(BASE/'r-source-intake-1','R-source'),(BASE/'r-addon-source-intake-1','R-addons'),(BASE/'r-compatibility-source-intake-1','R-compatibility'),(BASE/'model-original-1','history/original-1'),(BASE/'model-build-1','history/build-1'),(BASE/'model-build-2','history/build-2'),(BASE/'model-build-3','history/build-3'),(BASE/'model-validation-1','history/validation-1'),(BASE/'model-validation-2','history/validation-2')]:tree(directory,prefix)
    library=BASE/'r-library-setup-1';tree(library,'library',top_only=True);stage=json.loads((library/'original-source-stage.json').read_bytes())
    for name,want in stage['members'].items():file=library/'work-source/leafcutter'/name;assert pin(file)==want;files['library-stage-source/'+name]=file.read_bytes()
    test=ROOT/'tools/literature/test_leafcutter_models_comparison.py';command=[sys.executable,'-B',str(test)];run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);files['comparison-tests/stdout']=run.stdout;files['comparison-tests/stderr']=run.stderr;files['comparison-tests/process.json']=(json.dumps(dict(command=command,exit_code=run.returncode,source_pin=pin(test)),indent=2)+'\n').encode();assert run.returncode==0;entries={}
    with zipfile.ZipFile(jar) as bundle:
        for name in compiled['added_entries']:raw=bundle.read(name);files['candidate-added-entries/'+name]=raw;entries[name]=byte_pin(raw)
    history=dict(status='all-local-failures-retained-no-partial-batch-admission',original_1='A local assertion used Python lexical cluster order instead of R locale order. Original R stopped after writing provisional results. The complete failed batch, original namespace, binary fits and process/runtime receipts remain retained. Corrected only the identity/order assertion; the successful original2 batch repeats every control unchanged.',build_1='Incorrect Java import for existing JDistLib MathFunctions; compilation failed before creating a candidate artifact.',build_2='Test driver used checked-I/O request helpers inside stream lambdas without propagating checked exceptions; compilation failed. Corrected driver iteration; production model source unchanged.',validation_1='Compiled driver3 expected Java Number objects instead of lossless Nextflow NumberToken inputs. The actual JVM failed before a terminal marker. Driver4 uses the existing numeric parser; production model evaluation unchanged.',validation_2='All56 actual model evaluations ran on all3JVMs, but two near-null tails per JVM disagreed after 15-significant-digit JSON export rounded nearly equal original objectives. No partial batch was admitted. Original binary RDS fits are retained. A NEW lossless binding reads those exact fits and repeats fixed-parameter log_prob only; no optimizer/refit or tolerance change occurred. Validation3 compares the same build4 JAR against lossless outputs.',scope='Independent evaluation at supplied parameters, effects and LRT tails only. Original optimizers are not independently refitted and no global-optimum/biological calibration claim is made. Full execution adapter and paper-use reviews remain open; no full named-tool credit.')
    files['history/CLASSIFICATION.json']=(json.dumps(history,indent=2)+'\n').encode();assert sum(map(len,files.values()))<134217728;DEST.mkdir();(DEST/'.gitattributes').write_bytes(b'* -text\n');packing=pack(DEST/'controls.zip',files)
    save(DEST/'VALIDATION.json',dict(schema_version=1,status='passed-native-LeafCutter-original-model-evaluation-component-only',archive=pin(DEST/'controls.zip'),packing=packing,source_pins=sources,artifact=compiled['artifact'],packaged_entries=entries,checks=receipt['checks'],original_checks=receipt['original_checks'],canonical_executable_unchanged=receipt['canonical_executable_unchanged'],implementation_credit=False,full_LeafCutter_workflow_admitted=False,whole_goal_complete=False,released_or_deployed=False))
    from leafcutter_models_evidence import verify
    verify();assert all(pin(ROOT/n)==want for n,want in sources.items());print('Sealed',len(files),'complete model evidence files;',pin(DEST/'controls.zip')['bytes'],'archive bytes; no full LeafCutter credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/native model checks')
    main()
