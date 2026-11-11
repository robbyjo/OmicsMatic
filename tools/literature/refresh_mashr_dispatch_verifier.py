# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify and retain a dispatcher-only extension to the immutable mashr catalog evidence reader."""
import hashlib,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools/literature'))
from mashr_catalog_evidence import verify,exact
from mashr_original_runtime import pin

def main():
    if not __debug__:raise RuntimeError('Never disable scientific evidence checks')
    data=ROOT/'docs/literature/omics-2023-2026';catalog_path=data/'mashr-catalog-validation.json';catalog=json.loads(catalog_path.read_bytes());archive_path=data/catalog['archive']['path'];assert pin(archive_path)=={k:catalog['archive'][k] for k in ('bytes','sha256')}
    relative='tools/literature/mashr_catalog_evidence.py';old_pin=catalog['source_pins'][relative]
    with zipfile.ZipFile(archive_path) as archive:old=archive.read('source/'+relative)
    assert dict(bytes=len(old),sha256=hashlib.sha256(old).hexdigest())==old_pin
    current=(ROOT/relative).read_bytes();restored=current.replace(b'from catalog_source_integrity import verify_source\n',b'',1).replace(b"    if path==ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java':\n        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026','src/main/java/org/jlinalg/cli/JLinAlgCli.java',expected['sha256'])\n        return\n",b'',1)
    restored=restored.replace(b'recorded in catalog_at_validation. Only the shared CLI dispatcher can use a\nreviewed exact source inverse. Numerical/analysis/runtime sources and scientific\nartifacts remain byte-exact; no scientific path receives compatibility treatment.\n',b'recorded in catalog_at_validation. Every other source/artifact dependency stays\nexact. No changed scientific path receives metadata compatibility treatment.\n',1);assert restored==old,'Unreviewed verifier edit'
    before=dict(catalog=pin(catalog_path),archive=pin(archive_path));result=verify(emit=False)
    proof_path=ROOT/'docs/literature/mashr-proxy-command/VALIDATION.json';proof=json.loads(proof_path.read_bytes());rejections=[]
    bindings={name:expected for field in ('source_pins','unchanged_prior_source_pins','baseline_dependencies') for name,expected in proof[field].items()};science=next((ROOT/name,expected) for name,expected in bindings.items() if name.startswith('src/main/java/org/jlinalg/shrinkage/'))
    for label,path,expected in [('numerical-size',science[0],dict(science[1],bytes=science[1]['bytes']+1)),('numerical-hash',science[0],dict(science[1],sha256='0'*64)),('dispatcher-unreviewed-hash',ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java',dict(bytes=1,sha256='0'*64))]:
        try:exact(path,expected)
        except (AssertionError,ValueError):rejections.append(label)
        else:raise AssertionError('Guard silently admitted '+label)
    assert len(rejections)==3 and pin(catalog_path)==before['catalog'] and pin(archive_path)==before['archive']
    target=data/'mashr-nextflow-dispatch-verifier-supplement.json';assert not target.exists();target.write_text(json.dumps(dict(schema_version=1,status='passed-dispatcher-only-mashr-verifier-extension',old_catalog=before,scientific_checkpoint=dict(path=str(proof_path.relative_to(ROOT)),**pin(proof_path)),old_verifier=old_pin,current_verifier=dict(path=relative,**pin(ROOT/relative)),current_source_without_exact_documented_extension_equals_archived_verifier=True,scientific_verification=result,deliberate_mismatch_rejections=rejections,original_scientific_archive_preserved=True,numerical_source_exception=False,analysis_reexecuted=False),indent=2)+'\n',encoding='utf-8',newline='\n');print('Exact earlier verifier reconstructed; unchanged mashr science verified; all three deliberate guard mismatches rejected.')
if __name__=='__main__':main()
