# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile native SNPRelate profiles and check complete source matrices/QC populations without running R."""
import argparse
import csv
import json
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--compiler',choices=['javac21','javac25'],required=True)
    p.add_argument('--reference',type=Path,action='append',required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_SNPRELATE_SOURCE_MATRIX_VALIDATION',new_catalog_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        source_names=['src/main/java/org/jlinalg/genetics/KingRobustKinship.java',
                      'src/main/java/org/jlinalg/genetics/SnprelateKingRobust.java','src/main/java/org/jlinalg/genetics/SnprelateVariantQc.java',
                      'src/test/java/org/jlinalg/genetics/SnprelateReferenceProbe.java']
        sources=[ROOT/name for name in source_names];pins={name:pin(ROOT/name) for name in [*source_names,Path(__file__).resolve().relative_to(ROOT).as_posix()]}
        for name in pins:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
        tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath')
        classes=out/'classes';classes.mkdir()
        command=[str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),*map(str,sources)]
        r=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr)
        save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr[-3000:]
        artifacts={}
        for name,probe in [('snprelate-native-api.jar',False),('snprelate-native-reference-probe.jar',True)]:
            jar=out/name
            payload={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class') if probe or not f.name.startswith('SnprelateReferenceProbe')}
            payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\n\n';payload['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes()
            payload['META-INF/NOTICE']=b'Native scalar/QC profiles of SNPRelate1.46; upstream arithmetic: Xiuwen Zheng, GPL-3.0. Own code and unchanged project count kernel. No original R/standalone KING software is bundled or required. Not whole SNPRelate, LD pruning, GRM, GENESIS, paper or biological completion.\n'
            with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
                for member,raw in sorted(payload.items()):
                    item=zipfile.ZipInfo(member,(1980,1,1,0,0,0));item.compress_type,item.external_attr=zipfile.ZIP_DEFLATED,0o100644<<16;z.writestr(item,raw)
            artifacts[name]=pin(jar)
        invocations=[]
        for reference in a.reference:
            reference=reference.resolve();metadata=json.loads((reference/'COMPLETED.json').read_bytes());assert metadata['status'].startswith('PASS_ORIGINAL_')
            for host in ['windows','linux']:
                convert=linux if host=='linux' else lambda p:str(p.resolve())
                launcher=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if host=='linux' else [str(tools/'java.exe')]
                label=reference.name+'-'+host;summary=out/(label+'.tsv')
                command=[*launcher,'-Xmx1g','-cp',convert(out/'snprelate-native-reference-probe.jar'),'org.jlinalg.genetics.SnprelateReferenceProbe',
                         convert(reference/'inputs/cohort'),convert(reference/'original-results'),convert(summary)]
                r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
                (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
                assert r.returncode==0,(label,r.stdout[-2000:],r.stderr[-3000:])
                with summary.open(encoding='utf8',newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
                record=dict(reference=reference.relative_to(ROOT).as_posix(),host=host,cases=len(rows),complete_matrix_cells=sum(int(r['matrix_cells']) for r in rows),
                            source_empty_selection_rejections=sum(r['status']=='ORIGINAL_EMPTY_SELECTION_REJECTED' for r in rows),complete_fields_bit_identical=True)
                record['source_empty_selection_matrix_returns']=sum(r['status']=='ORIGINAL_EMPTY_SELECTION_RETURNED_NAN_MATRIX' for r in rows)
                invocations.append(record);print(json.dumps(record),flush=True)
        assert pin(canonical)==before and all(pin(ROOT/name)==value for name,value in pins.items())
        state.update(status='PASS_NATIVE_SNPRELATE146_COMPLETE_ORIGINAL_MATRIX_QC_AND_TRANSPORT_FIELDS',compiler=a.compiler,source_pins=pins,
                     artifacts=artifacts,invocations=invocations,canonical_executable_unchanged=before,scope='Native scalar/QC components and complete reference verification only. No LD/GRM/GENESIS or full tool/catalog credit.')
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2800],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/native scientific field checks')
    raise SystemExit(main())
