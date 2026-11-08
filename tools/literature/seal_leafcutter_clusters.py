# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal source-bound native LeafCutter clustering/count controls and failures; no full named-tool credit."""
import json,subprocess,sys,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin,pack

BASE=ROOT/'build/literature-review/leafcutter-runtime'
DEST=ROOT/'docs/literature/leafcutter-clusters-development'

def main():
    assert not DEST.exists();build=BASE/'cluster-build-2';validation=BASE/'cluster-validation-2';receipt=json.loads((validation/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_CLUSTER_COMPONENT_THREE_JVM_ONLY';compiled=json.loads((build/'COMPLETED.json').read_bytes());jar=ROOT/compiled['artifact']['path'];assert pin(jar)=={k:compiled['artifact'][k] for k in ['bytes','sha256']};sources=dict(receipt['source_pins'])
    for name in ['leafcutter_clusters_evidence.py','seal_leafcutter_clusters.py','test_leafcutter_cluster_comparison.py','collect_leafcutter_cluster_controls.py','generate_leafcutter_cluster_fixtures.py','intake_leafcutter_sources.py','signac_scan_evidence.py']:sources['tools/literature/'+name]=pin(ROOT/'tools/literature'/name)
    sources['docs/literature/LEAFCUTTER_DEVELOPMENT.md']=pin(ROOT/'docs/literature/LEAFCUTTER_DEVELOPMENT.md');files={'current-source/'+name:(ROOT/name).read_bytes() for name in sources};assert all(pin(ROOT/n)==v for n,v in sources.items())
    def tree(directory,prefix):
        for file in sorted(directory.rglob('*')):
            if file.is_file() and file.suffix!='.jar' and 'classes' not in file.relative_to(directory).parts and '__pycache__' not in file.parts:files[prefix+'/'+file.relative_to(directory).as_posix()]=file.read_bytes()
    for path,prefix in [(build,'build'),(validation,'validation'),(BASE/'source-intake-2','source-intake'),(BASE/'cluster-original-windows-1','reference/windows'),(BASE/'cluster-original-linux-1','reference/linux'),(BASE/'cluster-fixtures-1','fixtures'),(BASE/'source-intake-1','history/source-intake-1'),(BASE/'cluster-build-1','history/build-1'),(BASE/'cluster-validation-1','history/validation-1')]:tree(path,prefix)
    test=ROOT/'tools/literature/test_leafcutter_cluster_comparison.py';run=subprocess.run([sys.executable,'-B',str(test)],cwd=ROOT,capture_output=True,timeout=60);assert run.returncode==0;files['comparison-tests/stdout']=run.stdout;files['comparison-tests/stderr']=run.stderr;files['comparison-tests/process.json']=(json.dumps(dict(command=[sys.executable,'-B',str(test)],exit_code=run.returncode,source_pin=pin(test)),indent=2)+'\n').encode();entries={}
    with zipfile.ZipFile(jar) as bundle:
        for name in compiled['added_entries']:raw=bundle.read(name);files['candidate-added-entries/'+name]=raw;entries[name]=byte_pin(raw)
    classification=dict(status='retained-local-failures-no-partial-analysis-admission',source_intake_1='The first local intake incorrectly required the later RegTools script in the older official v0.2.6 tag. Its exact original files, Git tree and error remain retained. The new intake requires the common older script and records optional later scripts; no original source/version was substituted.',build_1='The native test-driver passed a long integer helper result to an int sample capacity. Compile failed; added an exact narrowing check and recompiled into build2. Production method source was unchanged.',validation_1='The complete Windows21 native driver succeeded. The outer validator stopped at a warning-phrase assertion before completing the full comparison. Corrected only that assertion; repeated all88 cases on three JVMs with the exact same build2 JAR and original outputs. The failed validator/outputs remain retained without partial comparison credit.',linux_parent_launch='An initial parent collector invocation used Python -I while importing a sibling repository helper. It failed before main/output creation. The corrected parent launch removes that import-path restriction; every actual original analysis child still uses -I -B -X utf8. No original source was patched.',scope='This exact current-source RegTools clustering/count component remains distinct from historical Git tags, R package version labels, phenotype/PCA, R/Stan models, junction extraction, whole-paper reproduction and full named-tool implementation credit.');files['history/CLASSIFICATION.json']=(json.dumps(classification,indent=2)+'\n').encode();assert sum(len(raw) for raw in files.values())<134217728;DEST.mkdir();(DEST/'.gitattributes').write_bytes(b'* -text\n');packing=pack(DEST/'controls.zip',files);save(DEST/'VALIDATION.json',dict(schema_version=1,status='passed-native-LeafCutter-RegTools-clustering-counts-component-only',archive=pin(DEST/'controls.zip'),packing=packing,source_pins=sources,artifact=compiled['artifact'],packaged_entries=entries,checks=receipt['checks'],canonical_executable_unchanged=receipt['canonical_executable_unchanged'],implementation_credit=False,full_LeafCutter_workflow_admitted=False,whole_goal_complete=False,released_or_deployed=False))
    from leafcutter_clusters_evidence import verify
    verify();assert all(pin(ROOT/n)==v for n,v in sources.items());print('Sealed',len(files),'complete component evidence files;',pin(DEST/'controls.zip')['bytes'],'archive bytes; no LeafCutter tool credit')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/evidence checks')
    main()
