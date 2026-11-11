# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify frozen Rsubread result-value observations; never refit R or claim a complete analysis adapter."""
import argparse,hashlib,io,json,struct,zipfile
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
DEFAULT=ROOT/'docs/literature/rsubread-result-development'
BASE='build/literature-review/rsubread-runtime/'


def byte_pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return byte_pin(path.read_bytes())


def verify(emit=True,directory=None,archive_only=False):
    home=Path(directory).resolve() if directory else DEFAULT;p=json.loads((home/'VALIDATION.json').read_bytes())
    assert p['status']=='passed-source-bound-native-Rsubread-result-values-and-relationships-on-three-JVMs'
    assert p['native_result_value_component_available'] and not any(p[n] for n in ['implementation_credit','full_adapter_available','production_process_supervision','independent_read_counting','independent_junction_counting','whole_package_implemented','whole_paper_reproduced','whole_goal_complete','scientific_source_exception_added'])
    assert p['original_results']==50 and p['retained_original_results']==32 and p['fresh_original_results']==18
    archive=home/p['archive']['file'];assert pin(archive)=={k:p['archive'][k] for k in ['bytes','sha256']};files={}
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(p['files'])
        for entry in z.infolist():
            name=entry.filename;rel=PurePosixPath(name)
            assert not rel.is_absolute() and str(rel)==name and '..' not in rel.parts and '\\' not in name and ':' not in name
            assert entry.file_size<=50_000_000 and entry.file_size==p['files'][name]['bytes']
            files[name]=z.read(name);assert byte_pin(files[name])==p['files'][name]
    assert sum(len(v) for v in files.values())<=100_000_000
    for name,value in p['source_pins'].items():
        assert byte_pin(files['source/'+name])==value
        if not archive_only:assert pin(ROOT/name)==value,('Scientific source changed; preserve this checkpoint and validate a NEW milestone',name)
    def receipt(name):return json.loads(files[BASE+name+'/COMPLETED.json'])
    build=receipt('result-api-build-1');other=receipt('result-api-build-25-1')
    assert build['status']==other['status']=='passed-JDK-only-native-Rsubread-result-value-API-build'
    assert build['previous_matrix_class_family_unchanged'] and other['previous_matrix_class_family_unchanged']
    raw=files[build['artifact']['path']];assert byte_pin(raw)=={k:build['artifact'][k] for k in ['bytes','sha256']}
    assert raw==files[other['artifact']['path']] and build['class_entries']==other['class_entries']
    assert p['artifact']['sha256']==build['artifact']['sha256'] and p['artifact']['bytes']==len(raw)
    if not archive_only:assert (home/p['artifact']['file']).read_bytes()==raw
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        classes={n:z.read(n) for n in z.namelist() if n.endswith('.class')};assert len(classes)==14
        for name,data in classes.items():
            assert byte_pin(data)==build['class_entries'][name] and struct.unpack('>H',data[6:8])[0]==61
            assert data==files[BASE+'result-api-build-1/classes/'+name]==files[BASE+'result-api-build-25-1/classes/'+name]
        assert z.read('org/jlinalg/omics/rsubread_result_wire.R')==files['source/src/main/resources/org/jlinalg/omics/rsubread_result_wire.R']
    old=json.loads(files['prior-matrix/VALIDATION.json']);assert len(old['native_class_entries'])==6
    for name,value in old['native_class_entries'].items():assert byte_pin(classes[name])==value
    exports=receipt('result-exports-1');native=receipt('result-native-1');fresh=receipt('result-edges-1');edges=receipt('result-edges-native-1')
    assert exports['status']=='passed-two-original-Rsubread-result-value-export-families-without-refitting' and exports['original_results']==32
    assert native['status']=='passed-two-original-Rsubread-result-value-families-on-three-JVMs-and-back-in-R'
    assert fresh['status']=='passed-eighteen-fresh-original-Rsubread-result-edge-and-BAM-controls-only' and fresh['original_cases']==18 and not fresh['namespace_patched']
    assert edges['status']=='passed-eighteen-fresh-original-Rsubread-results-on-three-JVMs-and-eight-complete-SAM-BAM-comparisons'
    for document in [build,other,exports,native,fresh,edges]:
        for name,value in document['source_pins'].items():assert byte_pin(files['source/'+name])==value
    for profile in ['2260','2232-R441']:
        for runtime in ['windows21','windows25','linux21']:
            a=native['runs'][profile][runtime];b=edges['runs'][profile][runtime]
            assert (a['original_results'],a['frames'],a['scalar_cells'],a['contract_rejections'])==(16,55,1056,37)
            assert b['original_results']==9 and b['contract_rejections']==4
            assert json.loads(files[BASE+'result-native-1/'+profile+'-'+runtime+'/audit.json'])==a
            assert json.loads(files[BASE+'result-edges-native-1/'+profile+'-'+runtime+'/audit.json'])==b
        assert files[BASE+'result-native-1/'+profile+'-R-roundtrip.txt'].strip()==b'PASS16_NATIVE_RESULT_VALUE_ROUNDTRIPS_AND3_SCALAR_CONTROLS_IN_ORIGINAL_R'
        assert files[BASE+'result-edges-native-1/'+profile+'-SAM-BAM/R-status.txt'].strip()==b'PASS4_COMPLETE_ORIGINAL_SAM_BAM_COUNT_ANNOTATION_SUMMARY_JUNCTION_COMPARISONS_WITH_EXPLICIT_TARGET_LEDGER'
        original=(PurePosixPath(exports['references'][profile]['original_receipt']['path']).parent)
        for name,value in exports['references'][profile]['original_objects'].items():assert byte_pin(files[str(original/name)])==value
    assert files[BASE+'result-edges-1/codec-single.stdout'].strip()==b'PASS_COMPLETE_SAM_BAM_RECORDS_AND_ORDER 11'
    assert files[BASE+'result-edges-1/codec-paired.stdout'].strip()==b'PASS_COMPLETE_SAM_BAM_RECORDS_AND_ORDER 10'
    for name,value in fresh['input_pins'].items():assert byte_pin(files[BASE+'result-edges-1/inputs/'+name])==value
    author=json.loads(files[BASE+'author-code-audit-1/AUTHOR-CODE-AUDIT.json'])
    assert author['status']=='passed-read-only-P133-linked-author-code-audit' and author['commit']=='3aeb503348e52b1f0b88d47839427380250c0ea5'
    if not archive_only:assert (home/'AUTHOR-CODE-AUDIT.json').read_bytes()==files[BASE+'author-code-audit-1/AUTHOR-CODE-AUDIT.json']
    assert not author['scripts_executed'] and not author['raw_author_scripts_redistributed']
    assert not author['atac']['checkFragLength_reported'] and not author['atac']['countReadPairs_reported'] and not author['atac']['package_edition_identified']
    assert not any(n.startswith(BASE+'p133-code-intake-1/') for n in files)
    example=receipt('result-documentation-1');assert example['status']=='passed-four-exact-beginner-Java-examples' and example['examples']==4
    for name,value in example['source_pins'].items():assert byte_pin(files['source/'+name])==value
    assert p['catalog_snapshot']['new_scoped_integrations']==89 and p['catalog_snapshot']['missing']==252
    if not archive_only:
        from rsubread_matrix_evidence import verify as prior_verify
        previous,original_files=prior_verify(False)
        assert previous==old
        for profile in ['2260','2232-R441']:
            original=PurePosixPath(exports['references'][profile]['original_receipt']['path']).parent
            for name in exports['references'][profile]['original_objects']:
                key=str(original/name);assert files[key]==original_files[key]
    if emit:print('Verified50 frozen original result-value observations, both reference families, three-JVM/R checks and unchanged matrix classes; no refit or full-adapter credit'+(' (archive only)' if archive_only else ''))
    return p,files


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true');parser.add_argument('--export-values',type=Path);args=parser.parse_args()
    p,files=verify(archive_only=args.archive_only)
    if args.export_values:
        assert args.archive_only,'Export requires --archive-only; historical values are not a fresh analysis'
        out=args.export_values.resolve();assert not out.exists();out.mkdir(parents=True)
        for target,source in p['value_exports'].items():
            rel=PurePosixPath(target);assert not rel.is_absolute() and '..' not in rel.parts and str(rel)==target and ':' not in target and '\\' not in target
            file=out/target;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(files[source])
        (out/'EXPORT.json').write_text(json.dumps(dict(status='exported-frozen-original-Rsubread-result-values-only',original_results=50,source_archive=p['archive'],analysis_admitted=False,R_refitted=False,whole_goal_complete=False),indent=2)+'\n',encoding='utf8',newline='\n')
        print('Exported50 historical result-value bundles to',out)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence/source/population checks')
    main()
