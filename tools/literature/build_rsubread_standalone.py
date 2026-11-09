# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the portable native matrix API with a JDK only, optionally comparing the earlier additive class family."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--javac',default='javac');p.add_argument('--additive-build',type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_RSUBREAD_STANDALONE_MATRIX_BUILD',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False)
    try:
        javac=Path(shutil.which(a.javac) or a.javac).resolve();assert javac.is_file(),'Supply a permitted JDK17+ javac; skip this build when unavailable';source=ROOT/'src/main/java/org/jlinalg/sequence/RsubreadCountMatrix.java';classes=out/'classes';classes.mkdir();command=[str(javac),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),str(source)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');payload={file.relative_to(classes).as_posix():file.read_bytes() for file in classes.rglob('*.class')};assert len(payload)==6;comparison=None
        if a.additive_build:
            old=json.loads((a.additive_build/'COMPLETED.json').read_bytes());assert old['status']=='passed-additive-native-Rsubread-count-matrix-component-only' and set(payload)==set(old['added_entries']);assert all(raw==(a.additive_build/'classes'/name).read_bytes() for name,raw in payload.items());comparison=dict(receipt=dict(path=(a.additive_build/'COMPLETED.json').resolve().relative_to(ROOT).as_posix(),**pin(a.additive_build/'COMPLETED.json')),complete_class_family_byte_identical=True)
        jar=out/'rsubread-count-matrix-api.jar';manifest=b'Manifest-Version: 1.0\nImplementation-Title: OmicsMatic native Rsubread matrix component\nImplementation-Version: 0.3.7-development\n\n';licensefile=ROOT/'LICENSE';assert licensefile.is_file()
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(dict(payload,**{'META-INF/MANIFEST.MF':manifest,'META-INF/LICENSE':licensefile.read_bytes()}).items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
        assert all((classes/name).read_bytes()==raw for name,raw in payload.items());state.update(status='passed-standalone-JDK-only-native-Rsubread-matrix-library',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),class_entries={name:byte_pin(raw) for name,raw in payload.items()},class_major=61,additive_comparison=comparison,source_pins={file.relative_to(ROOT).as_posix():pin(file) for file in [Path(__file__).resolve(),source,licensefile]},runtime_dependencies='JDK only; no R, hifiasm, numerical library, canonical executable or parent JAR required')
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native source/class-family/build gates')
    raise SystemExit(main())
