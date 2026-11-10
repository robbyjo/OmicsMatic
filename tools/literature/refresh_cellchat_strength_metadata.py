# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Remove one verifier EOF blank line; preserve the previous proof and all analysis bytes."""
import argparse,copy,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from verify_cellchat_strength_evidence import Evidence,verify,fingerprint
BASE=ROOT/'build/literature-review/cellchat-runtime';PUBLIC=ROOT/'docs/literature/cellchat-strength-development'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    oldRaw=(PUBLIC/'VALIDATION.json').read_bytes();old=json.loads(oldRaw);previous=json.loads((BASE/'strength-seal-1/COMPLETED.json').read_bytes());assert previous['status']=='PASS_FROZEN_ORIGINAL_CELLCHAT_STRENGTH_CHECKPOINT';assert all(pin(ROOT/n)==v for n,v in previous['published_files'].items());verify(PUBLIC,True)
    name='tools/literature/verify_cellchat_strength_evidence.py';file=ROOT/name;raw=file.read_bytes();assert raw.endswith(b'\n\n') and not raw.endswith(b'\n\n\n') and fingerprint(raw)==old['source_pins'][name]
    with Evidence(PUBLIC,old) as e:assert e.raw('source/'+name)==raw
    (out/'previous-VALIDATION.json').write_bytes(oldRaw);(out/'previous-verifier.py').write_bytes(raw)
    held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-cellchat-inference-1/selection.json').read_bytes())['preserved'];assert all(pin(ROOT/n)==v for n,v in held.items())
    file.write_bytes(raw[:-1]);proof=copy.deepcopy(old);proof['source_pins'][name]=pin(file);helper=Path(__file__).resolve();helperName=helper.relative_to(ROOT).as_posix();proof['source_pins'][helperName]=pin(helper)
    before=dict(proof['members']);proof['members']['history/eof-format-refresh/previous-verifier.py']=fingerprint(raw)
    additions={'source/'+name:file.read_bytes(),'source/'+helperName:helper.read_bytes(),'history/eof-format-refresh/previous-VALIDATION.json':oldRaw}
    receipt=dict(status='ONE_EOF_BLANK_LINE_REMOVED_ONLY',previous_validation=fingerprint(oldRaw),previous_verifier=fingerprint(raw),current_verifier=pin(file),scientific_bytes_changed=False,previous_archives_unchanged=True)
    additions['history/eof-format-refresh/receipt.json']=(json.dumps(receipt,indent=2)+'\n').encode('utf8')
    archive=PUBLIC/'controls-metadata-01.zip';assert not archive.exists()
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for logical,data in additions.items():
            value=fingerprint(data);proof['members'][logical]=value;digest=value['sha256']
            if digest not in proof['blobs']:
                info=zipfile.ZipInfo('blobs/'+digest,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data);proof['blobs'][digest]=dict(archive=archive.name,**value)
    proof['archives'][archive.name]=pin(archive);proof['logical_bytes']=sum(v['bytes'] for v in proof['members'].values());proof['metadata_refresh']=receipt
    assert all(proof['members'][n]==v for n,v in before.items() if n!='source/'+name)
    assert all(pin(PUBLIC/n)==v for n,v in old['archives'].items());assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items());assert all(pin(ROOT/n)==v for n,v in held.items());assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    save(PUBLIC/'VALIDATION.json',proof);verify(PUBLIC,True)
    save(out/'COMPLETED.json',dict(status='PASS_FROZEN_ORIGINAL_CELLCHAT_STRENGTH_FORMAT_REFRESH_ONLY',source_pins=proof['source_pins'],published_files={p.relative_to(ROOT).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file()},catalog_unchanged=proof['catalog_unchanged'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],scientific_bytes_changed=False,unrelated_files_preserved=18,previous_validation=fingerprint(oldRaw),current_validation=pin(PUBLIC/'VALIDATION.json')));print('PASS verifier EOF formatting only; previous proof/source and all scientific bytes preserved')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence preservation checks')
    main()
