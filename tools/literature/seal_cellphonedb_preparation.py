# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete original full-method references, exact native preparation, source/license profiles and failed attempts; full public tool adapter remains open."""
import json,subprocess,sys,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin,pack

BASE=ROOT/'build/literature-review/cellphonedb-runtime'
DEST=ROOT/'docs/literature/cellphonedb-preparation-development'


def main():
    assert not DEST.exists();build=BASE/'preparation-build-2';validation=BASE/'preparation-validation-2';original=BASE/'workflow-original-6';compiled=json.loads((build/'COMPLETED.json').read_bytes());receipt=json.loads((validation/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_EXACT_ORIGINAL_NATIVE_CELLPHONEDB_PREPARATION_THREE_JVMS_ONLY';jar=ROOT/compiled['artifact']['path'];assert pin(jar)=={k:compiled['artifact'][k] for k in ['bytes','sha256']};sources=dict(receipt['source_pins'])
    for name in ['collect_cellphonedb_workflows_linux.py','generate_cellphonedb_workflow_fixtures.py','intake_cellphonedb_database.py','intake_cellphonedb_preparation_sources.py','cellphonedb_preparation_evidence.py','seal_cellphonedb_preparation.py','test_cellphonedb_preparation_comparison.py','signac_scan_evidence.py','inventory_hail_runtime_linux.py','validate_cellphonedb_statistics.py']:sources['tools/literature/'+name]=pin(ROOT/'tools/literature'/name)
    sources['docs/literature/CELLPHONEDB_PREPARATION_DEVELOPMENT.md']=pin(ROOT/'docs/literature/CELLPHONEDB_PREPARATION_DEVELOPMENT.md');assert all(pin(ROOT/n)==want for n,want in sources.items());files={'current-source/'+name:(ROOT/name).read_bytes() for name in sources}
    def tree(directory,prefix,top_only=False):
        for path in sorted(directory.iterdir() if top_only else directory.rglob('*')):
            relative=path.relative_to(directory)
            if relative.parts[0].startswith('private-') or path.suffix=='.jar' or 'classes' in relative.parts or '__pycache__' in relative.parts:continue
            if path.is_file():assert not path.is_symlink();files[prefix+'/'+relative.as_posix()]=path.read_bytes()
    for directory,prefix in [(build,'build'),(validation,'validation'),(original,'original'),(BASE/'workflow-fixtures-5','fixtures'),(BASE/'preparation-source-intake-1','pandas-source')]:tree(directory,prefix)
    for i in range(1,6):tree(BASE/('workflow-original-'+str(i)),'history/original-'+str(i))
    for i in range(1,5):tree(BASE/('workflow-fixtures-'+str(i)),'history/fixtures-'+str(i))
    tree(BASE/'preparation-build-1','history/preparation-build-1');tree(BASE/'preparation-validation-1','history/preparation-validation-1')
    # Preserve official database provenance only. Its bytes and extracted curated tables remain private.
    database=BASE/'database-intake-1';tree(database,'database-provenance',True);tree(database/'trees','database-provenance/trees')
    command=[sys.executable,'-B',str(ROOT/'tools/literature/test_cellphonedb_preparation_comparison.py'),'--reference',str(original),'--validation',str(validation)];run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);files['comparison-tests/stdout']=run.stdout;files['comparison-tests/stderr']=run.stderr;files['comparison-tests/process.json']=(json.dumps(dict(command=command,exit_code=run.returncode),indent=2)+'\n').encode();assert run.returncode==0,run.stderr.decode('utf8',errors='replace');entries={}
    with zipfile.ZipFile(jar) as bundle:
        for name in compiled['added_entries']:raw=bundle.read(name);files['candidate-added-entries/'+name]=raw;entries[name]=byte_pin(raw)
    history=dict(status='complete-failed-atomic-reference-batches-retained',original_1='A synthetic database contained two distinct gene annotations for one protein; original result assembly raised InvalidIndexError. No partial batch was admitted. The failing input family remains an explicit error control using its own alias database. Positive families use a separately declared unambiguous database; no original analysis is repaired by dropping aliases.',original_2='The observer used an ambiguous output glob matching both means and significant_means. Full original outputs were retained but the batch was unadmitted. Only the new observer was changed to exact method/name/suffix file binding.',original_3='The observer expected every returned value to be a DataFrame instance. Original CellSign states can instead be the DataFrame class itself. Exact class-valued states are now retained explicitly; no empty frame or TF result is fabricated.',original_4='Synthetic protein IDs and multidata IDs differed; original scoring assumes coincident numeric identities and failed with ValueError. An explicit noncoincident-ID error control remains. The separate positive synthetic database follows the invariant observed in all1355 official v5.0.0 protein rows. No user database is renumbered or source-patched.',original_5='Original simple gene_name analysis with no complete complex failed during empty-complex deconvolution with InvalidIndexError. That family remains an explicit source error and its preparation is still audited. A separate Ensembl-identified no-complex input tests its distinct successful source path; neither replaces the original.',original_6='All19 controls completed:13 full original workflows and6 expected source failures. Fifteen preparation families then matched exactly on three actual JVMs. No native numerical correction or tolerance widening was needed.',scope='Full original reference workflows and typed preparation parity only. Public typed setup/execution adapter, independent final-output scientific audit and catalog application remain open. No curated database redistribution or named-tool credit.')
    history['native_diagnostic_update']='Build1/validation1 already passed every numeric/identity field on all three JVMs. Build2 adds explicit warnings for the observed downstream distinct-alias and gene_name/no-complete-complex boundaries; numerical preparation is unchanged. Validation2 repeats the same complete original reference with those required warning checks. The prior passing source/JAR identities and reports remain retained.'
    files['history/CLASSIFICATION.json']=(json.dumps(history,indent=2)+'\n').encode();assert sum(map(len,files.values()))<134217728;DEST.mkdir();(DEST/'.gitattributes').write_bytes(b'* -text\n');packing=pack(DEST/'controls.zip',files)
    save(DEST/'VALIDATION.json',dict(schema_version=1,status='passed-exact-native-CellPhoneDB-preparation-and-original-full-method-references-only',archive=pin(DEST/'controls.zip'),packing=packing,source_pins=sources,artifact=compiled['artifact'],parent_artifact=compiled['parent_artifact'],packaged_entries=entries,checks=receipt['checks'],ledger_checks=receipt['ledger_checks'],original_checks=receipt['original_checks'],canonical_executable_unchanged=receipt['canonical_executable_unchanged'],implementation_credit=False,public_execution_adapter_admitted=False,whole_goal_complete=False,released_or_deployed=False))
    from cellphonedb_preparation_evidence import verify
    verify();assert all(pin(ROOT/n)==want for n,want in sources.items());print('Sealed',len(files),'complete preparation/full-reference evidence files;',pin(DEST/'controls.zip')['bytes'],'ZIP bytes; CellPhoneDB still missing')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/control sealing')
    main()
