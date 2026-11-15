#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package retained direct original controls; no R or Java algorithms run here."""
from pathlib import Path
import argparse,hashlib,json,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',required=True,type=Path)
 p.add_argument('--archive',required=True,type=Path);p.add_argument('--group-types',required=True,type=Path)
 p.add_argument('--out',required=True,type=Path);a=p.parse_args()
 reference=json.loads((a.reference/'REFERENCE.json').read_bytes())
 if reference['status']!='PASS_COMPLETE_ORIGINAL_DENSE_MARKER_REFERENCE_GENERATED':raise ValueError('Incomplete original reference')
 profile=json.loads((HERE/'native-source-profile.json').read_bytes())
 archive=a.archive.read_bytes()
 if identity(archive)!=profile['upstream_archive']:raise ValueError('Original source archive differs')
 files={p.relative_to(a.reference).as_posix():p.read_bytes() for p in a.reference.rglob('*') if p.is_file()}
 files['upstream/scrattch.bigcat-'+profile['upstream_commit']+'.tar.gz']=archive
 files['provenance/GenerateMarkerReference.R']=(HERE/'GenerateMarkerReference.R').read_bytes()
 files['marker-group-types.tsv']=(a.group_types/'marker-group-types.tsv').read_bytes()
 export=json.loads((a.group_types/'EXPORT.json').read_bytes())
 if export['cases']!=reference['cases'] or export['input_sha256']!=identity(files['complete-original-reference.rds'])['sha256']:
  raise ValueError('Input-type export does not belong to the complete saved original records')
 files['provenance/ExportMarkerGroupTypes.R']=(HERE/'ExportMarkerGroupTypes.R').read_bytes()
 files['provenance/EXPORT.json']=(a.group_types/'EXPORT.json').read_bytes()
 files['provenance/native-source-profile.json']=(HERE/'native-source-profile.json').read_bytes()
 files['LICENSE-GPL-3.txt']=subprocess.check_output(['git','cat-file','blob','HEAD:LICENSE'],cwd=ROOT)
 manifest=dict(schema='omicsmatic-original-bigcat005-native-reference-v1',files={n:identity(raw) for n,raw in files.items()})
 files['BUNDLE-MANIFEST.json']=(json.dumps(manifest,indent=2)+'\n').encode()
 if a.out.exists():raise FileExistsError('Preserve existing frozen reference; select a new output')
 a.out.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.out,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,raw in sorted(files.items()):
   info=zipfile.ZipInfo(name,date_time=(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
   info.external_attr=0o100644<<16;z.writestr(info,raw,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 print(json.dumps(dict(status='PASS_COMPLETE_ORIGINAL_REFERENCE_PACKAGED',identity=identity(a.out.read_bytes()),members=len(files),reference=reference)))
if __name__=='__main__':main()
