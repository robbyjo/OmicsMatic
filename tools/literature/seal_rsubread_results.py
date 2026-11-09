# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal this immutable result-value checkpoint; preserve the catalog, canonical executable and eighteen unrelated files."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve()
    assert out==ROOT/'docs/literature/rsubread-result-development' and not out.exists();out.mkdir(parents=True)
    base=ROOT/'build/literature-review/rsubread-runtime';files={};source_names=set()
    names=['result-api-build-1','result-api-build-25-1','result-exports-1','result-native-1','result-edges-1','result-edges-native-1','author-code-audit-1','result-documentation-1']
    for name in names:
        home=base/name;receipt=json.loads((home/'COMPLETED.json').read_bytes());assert receipt['status'].startswith('passed-')
        for path in home.rglob('*'):
            if path.is_file():assert not path.is_symlink();files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
        source_names.update(receipt['source_pins'])
    source_names.update(['tools/literature/rsubread_result_evidence.py','tools/literature/seal_rsubread_results.py','docs/literature/RSUBREAD_RESULT_VALUES.md'])
    for name in source_names:files['source/'+name]=(ROOT/name).read_bytes()
    from rsubread_matrix_evidence import verify as previous_verify
    prior,prior_files=previous_verify(False);files['prior-matrix/VALIDATION.json']=(ROOT/'docs/literature/rsubread-matrix-development/VALIDATION.json').read_bytes()
    exports=json.loads(files[(base/'result-exports-1/COMPLETED.json').relative_to(ROOT).as_posix()]);value_exports={}
    for profile,reference in exports['references'].items():
        home=(ROOT/reference['original_receipt']['path']).parent
        for name in reference['original_objects']:
            for path in [(home/name),(home/name).with_suffix('.R')]:
                key=path.relative_to(ROOT).as_posix();assert path.read_bytes()==prior_files[key];files[key]=path.read_bytes()
        for name in ['original-cases.tsv']:
            path=home/'original'/name;files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
        wirehome=base/'result-exports-1'/profile
        for path in wirehome.rglob('*'):
            if path.is_file() and path.parent!=wirehome and path.suffix in ['.frame','.matrix','.txt']:
                value_exports[profile+'/retained/'+path.relative_to(wirehome).as_posix()]=path.relative_to(ROOT).as_posix()
        freshhome=base/'result-edges-1'/profile
        for path in freshhome.glob('*/values/*'):
            if path.is_file():value_exports[profile+'/fresh/'+path.parent.parent.name+'/'+path.name]=path.relative_to(ROOT).as_posix()
    assert len({str(Path(n).parent) for n in value_exports})==50
    checkpoint=ROOT/'build/literature-review/push-checkpoint-2026-11-09-rsubread-matrix-1/selection.json';selection=json.loads(checkpoint.read_bytes())
    assert all(pin(ROOT/n)==v for n,v in selection['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==selection['canonical']
    catalog={}
    for name in ['docs/literature/omics-2023-2026/papers.json','docs/literature/omics-2023-2026/tool-inventory.json','docs/literature/omics-2023-2026/summary.json','tools/literature/reviewed-uses.json']:
        raw=(ROOT/name).read_bytes();catalog[name]=byte_pin(raw);files['catalog-snapshot/'+name]=raw
    assert len(json.loads(files['catalog-snapshot/docs/literature/omics-2023-2026/papers.json']))==250
    reviews=json.loads(files['catalog-snapshot/tools/literature/reviewed-uses.json'])['reviews'];assert len(reviews)==1000 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==347
    build=json.loads(files[(base/'result-api-build-1/COMPLETED.json').relative_to(ROOT).as_posix()]);other=json.loads(files[(base/'result-api-build-25-1/COMPLETED.json').relative_to(ROOT).as_posix()]);jar=ROOT/build['artifact']['path']
    assert jar.read_bytes()==(ROOT/other['artifact']['path']).read_bytes();(out/'rsubread-result-values-api.jar').write_bytes(jar.read_bytes())
    archive=out/'controls.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    (out/'AUTHOR-CODE-AUDIT.json').write_bytes((base/'author-code-audit-1/AUTHOR-CODE-AUDIT.json').read_bytes())
    (out/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    proof=dict(schema_version=1,status='passed-source-bound-native-Rsubread-result-values-and-relationships-on-three-JVMs',source_pins={n:pin(ROOT/n) for n in sorted(source_names)},
               archive=dict(file=archive.name,**pin(archive)),files={n:byte_pin(raw) for n,raw in sorted(files.items())},artifact=dict(file='rsubread-result-values-api.jar',**pin(jar)),
               value_exports=value_exports,retained_original_results=32,fresh_original_results=18,original_results=50,original_R_value_roundtrip_families=2,original_complete_SAM_BAM_comparisons=8,
               contract_rejections_per_family_per_JVM=41,JVMs=['Windows21','Windows25','Linux21'],native_result_value_component_available=True,implementation_credit=False,full_adapter_available=False,
               production_process_supervision=False,independent_read_counting=False,independent_junction_counting=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
               scientific_source_exception_added=False,canonical_executable_unchanged_at_checkpoint=selection['canonical'],catalog_snapshot=dict(files=catalog,papers=250,reviewed_roles=1000,supporting_pointers=347,new_scoped_integrations=89,native=17,upstream=72,including_existing=92,missing=252,source_role_drafts_applied=False),
               paper_binary_identified=False,author_code_redistributed=False,prior_matrix_proof=dict(path='docs/literature/rsubread-matrix-development/VALIDATION.json',**pin(ROOT/'docs/literature/rsubread-matrix-development/VALIDATION.json')),
               integrity_scope='SHA-256 integrity and source-bound recorded checks, not a signature or an independent fresh fit. Default verification checks current scientific source/API; archive-only checks historical observations. Complete original RDS files retain attributes that the value bridge does not represent.')
    save(out/'VALIDATION.json',proof)
    (out/'README.md').write_bytes(b'# Native Rsubread result-value component\n\nRead ../RSUBREAD_RESULT_VALUES.md first. The original analysis adapter remains unfinished. Rsubread receives no implementation credit.\n\ncontrols.zip retains50 complete original count-list objects and literal value outputs, original/fresh/native/R comparison logs, source and build receipts, both JDK builds and all rejection controls. Raw paper-author scripts are not redistributed; AUTHOR-CODE-AUDIT.json provides immutable links and hashes.\n\nRun python tools/literature/rsubread_result_evidence.py from the repository root. This checks frozen observations/current source/API without refitting R or starting Java. Use --archive-only for historical integrity and --archive-only --export-values NEW to read all50 historical bundles with the Java API.\n\nKeep the earlier matrix evidence: it contains original package source/runtime history and earlier retained failures. This checkpoint preserves the old matrix proof and catalog; no scientific source hash is refreshed.\n')
    from rsubread_result_evidence import verify
    verify();assert all(pin(ROOT/n)==v for n,v in selection['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==selection['canonical']
    print('Sealed',len(files),'logical files;',archive.stat().st_size,'archive bytes; catalog, canonical executable and18 unrelated files unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source, complete-population and preservation gates')
    main()
