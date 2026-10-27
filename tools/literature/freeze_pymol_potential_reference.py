#!/usr/bin/env python3
"""Freeze actual original surface ledgers and verify against an independent raw invocation.

The ledger fixture tests native auditing;it is not independent proof of its own
adapter. Full field/pixel comparisons against the earlier independent invocation
are recorded separately and explicitly scoped.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
from PIL import Image

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--analysis',required=True,type=Path);p.add_argument('--independent-original',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False);original=json.loads((a.analysis/'original-surface.json').read_text());audit=json.loads((a.analysis/'native-audit.json').read_text());independent=json.loads((a.independent_original/'verification.json').read_text());assert original['status']=='original-execution-completed-requires-native-audit' and independent['status']=='passed-original-full-map-export-explicit-PQR-radii-and-potential-surface'
    assert sha(a.analysis/'map-float32-le.bin')==sha(a.independent_original/'map-float32-le.bin')==audit['float32_field_sha256'];assert audit['atoms_checked']==642 and audit['grid_nodes_checked']==274625 and audit['surface_zero_radius_atoms']==236
    current=Image.open(a.analysis/'surface.png').convert('RGBA');raw=Image.open(a.independent_original/'surface.png').convert('RGBA');assert current.size==raw.size==(1200,1000);rectangle=(0,0,1200,850);current_body=current.crop(rectangle).tobytes();original_body=raw.crop(rectangle).tobytes();assert current_body==original_body,'Independent raw original body pixels differ;do not label adapter ledger data as independent parity'
    files=['source.pqr','potential.dx','original-surface.json','worker-recipe.json','map-float32-le.bin','surface.png'];archive=a.out/'actual-surface-ledger.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as target:
        for name in files:
            info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;target.writestr(info,(a.analysis/name).read_bytes())
    props={'archive.sha256':sha(archive),'atoms':'642','nodes':'274625','field.float32_le_sha256':audit['float32_field_sha256'],'zero_radius':'236'};(a.out/'reference.properties').write_text(''.join(k+'='+v+'\n' for k,v in sorted(props.items())),encoding='ascii',newline='\n')
    provenance=dict(schema_version=1,status='frozen-actual-original-surface-ledger-with-independent-field-and-body-pixel-check',original_pymol_version=original['pymol_version'],source_files_sha256={name:sha(a.analysis/name) for name in files},freezer_sha256=sha(__file__),independent_original_verification_sha256=sha(a.independent_original/'verification.json'),independent_body_pixel_rectangle=list(rectangle),independent_body_RGBA_sha256=hashlib.sha256(current_body).hexdigest(),independent_body_pixels=1200*850,independent_map_float32_nodes=274625,scope='Ledger fixture captures actual original PyMOL execution through a typed development command for native audit regression tests. Independent earlier raw original invocation4 supplies the complete float32 field and the upper850-row molecule-body pixels. The raw original GUI color widget below that rectangle is deliberately hidden by the typed command;its unit-labeled standalone SVG is tested separately. No source-paper reconstruction or experimental electrostatic validation is implied.')
    (a.out/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');(a.out/'SHA256SUMS').write_text(''.join(sha(f)+'  '+f.name+'\n' for f in sorted(a.out.iterdir()) if f.name!='SHA256SUMS'));print(json.dumps({k:provenance[k] for k in ['status','independent_body_pixels','independent_map_float32_nodes']}))

if __name__=='__main__':main()
