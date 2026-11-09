# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete current matrix/reference controls and explicit historical diagnostics without whole-tool credit."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from rsubread_matrix_evidence import STATUS,BASE,DIRECTORY,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir();base=ROOT/BASE;built=json.loads((base/'native-build-1/COMPLETED.json').read_bytes());standalone=json.loads((base/'standalone-build-1/COMPLETED.json').read_bytes());assert built['status']=='passed-additive-native-Rsubread-count-matrix-component-only' and standalone['status']=='passed-standalone-JDK-only-native-Rsubread-matrix-library';sources={q.relative_to(ROOT).as_posix() for q in (ROOT/'tools/literature').glob('*rsubread*') if q.is_file() and q.suffix in ['.py','.R']};sources.update(['src/main/java/org/jlinalg/sequence/RsubreadCountMatrix.java','tools/literature/java/org/jlinalg/cli/RsubreadMatrixAudit.java','tools/literature/java/org/jlinalg/cli/RsubreadStandaloneMatrixAudit.java','docs/literature/RSUBREAD_MATRIX_COMPONENT.md']);pins={name:pin(ROOT/name) for name in sorted(sources)};files={'current-source/'+name:(ROOT/name).read_bytes() for name in pins};jar=ROOT/standalone['artifact']['path'];files['packaged-api/rsubread-count-matrix-api.jar']=jar.read_bytes();(candidate/'rsubread-count-matrix-api.jar').write_bytes(jar.read_bytes())
    with zipfile.ZipFile(jar) as z:files['standalone-documentation/LICENSE']=z.read('META-INF/LICENSE')
    def collect(directory,recursive=True,metadata_only=False):
        for file in sorted(directory.rglob('*') if recursive else directory.iterdir()):
            if not file.is_file() or '__pycache__' in file.parts or file.suffix=='.jar':continue
            if metadata_only and not(file.suffix in ['.json','.md','.stdout','.stderr','.traceback','.py','.R'] or file.name=='error.traceback'):continue
            files[file.relative_to(ROOT).as_posix()]=file.read_bytes()
    current=['primary-roles-1','reference-intake-1','reported-source-2','reference-setup-3','native-build-1','original-controls-3','matrix-native-1','r441-reported-build-1','original2232-controls-1','matrix2232-native-1','standalone-build-1','standalone-native-1']
    for name in current:assert (base/name).is_dir();collect(base/name)
    for name in ['reference-setup-1','reference-setup-2','reference-forbidden-1','reported-source-1','original-controls-1','original-controls-2','reported-build-1','official-history-1','official2232-archive-probe-1','build-tools-intake-1','compiler-setup-1','r441-intake-1','r441-locked-intake-1','r441-setup-1','r441-binding-1']:
        collect(base/name,False,True)
    for name in ['R441','Rtools44']:collect(base/'r441-setup-1'/name,False,True)
    for directory in [base/'build-tools-intake-1/innounp',base/'r441-locked-intake-1/unpacker']:collect(directory,False,True)
    snapshot=['docs/literature/omics-2023-2026/papers.json','docs/literature/omics-2023-2026/tool-inventory.json','docs/literature/omics-2023-2026/summary.json','docs/literature/omics-2023-2026/SHA256SUMS','tools/literature/reviewed-uses.json'];catalog={name:pin(ROOT/name) for name in snapshot}
    for name in snapshot:files['catalog-snapshot/'+name]=(ROOT/name).read_bytes()
    held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-hifiasm-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/name)==value for name,value in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();archive=out/'controls.zip';record=pack(archive,files);physical=dict(pin=pin(archive),**record)
    if archive.stat().st_size<=90000000:(candidate/'controls.zip').write_bytes(archive.read_bytes())
    else:
        physical['parts']=[]
        with archive.open('rb') as stream:
            number=1
            while raw:=stream.read(90000000):file=candidate/('controls.zip.part'+str(number).zfill(3));file.write_bytes(raw);physical['parts'].append(dict(name=file.name,pin=pin(file)));number+=1
    with zipfile.ZipFile(jar) as z:classes={name:byte_pin(z.read(name)) for name in built['added_entries']}
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,archives={'controls.zip':physical},artifact=dict(path=DIRECTORY+'/rsubread-count-matrix-api.jar',**pin(jar)),built_artifact=standalone['artifact'],additive_artifact=built['artifact'],historical_additive_parent_artifact=built['parent_artifact'],native_class_entries=classes,canonical_executable_unchanged_at_checkpoint=built['canonical_executable_unchanged'],catalog_snapshot=catalog,catalog_snapshot_scope='Frozen1000-role/89-new-integration milestone, not a requirement that future catalog metadata remain unchanged. The current-checkpoint publication separately requires it.',original_count_matrices=32,successful_original_bindings=18,original_binding_errors=2,contract_rejections_per_family_per_jvm=42,native_matrix_component_available=True,implementation_credit=False,full_adapter_available=False,production_runtime_supervision_available=False,independent_alignment_assignment=False,independent_junction_counting=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,scientific_source_exception_added=False,prior_project_JAR_required=False,R_runtime_required_for_matrix_API=False,source_profiles=['Official2.26.0 Windows binary on R4.6.1','Original2.23.2 commit666b63dc50fa5d0e5cd4c9b7e805b6f6aa2f12f6 built unchanged on private R4.4.1/GCC13'],paper_binary_identified=False,earlier_failures_relabelled=False,history_scope='Current source/reference/control populations are lossless. Earlier failures retain exact receipts/logs/producer snapshots; complete older payloads remain locally unchanged. R/Rtools/unpacker installers and private base/toolchain binaries are not redistributed; their exact URLs, download hashes and extracted file inventories are recorded.',integrity_scope='Checksums and source relationships, not authenticity, independent count assignment, statistical refitting or a biological certificate.')
    save(candidate/'VALIDATION.json',proof);(candidate/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n');verify(False,candidate,True);assert all(pin(ROOT/name)==value for name,value in pins.items()) and all(pin(ROOT/name)==value for name,value in held.items());destination=ROOT/DIRECTORY;assert not destination.exists();destination.mkdir()
    for file in candidate.iterdir():(destination/file.name).write_bytes(file.read_bytes())
    verify(require_current_catalog=True);save(out/'COMPLETED.json',dict(status='passed-native-Rsubread-matrix-component-sealed-without-tool-credit',archive=pin(archive),logical_files=len(files),unrelated_files_preserved=18,catalog_changed=False,implementation_credit=False,whole_goal_complete=False));print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes; whole Rsubread adapter remains unfinished')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/archive/native-component seal gates')
    main()
