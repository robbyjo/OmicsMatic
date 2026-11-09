# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile isolated Sniffles execution code; no canonical project rebuild or adapter credit."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from prepare_sniffles_execution_sources import generated


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path)
    parser.add_argument('--javac',type=Path,default=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'))
    parser.add_argument('--native-reference',type=Path,default=ROOT/'docs/literature/sniffles-vcf-development/sniffles-vcf-development.jar');a=parser.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_EXECUTION_BUILD',implementation_credit=False,calling_adapter_validated=False,whole_goal_complete=False)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    try:
        folder=ROOT/'src/main/java/org/jlinalg/cli'
        for name,text in generated().items():assert (folder/name).read_bytes()==text.encode(),('Derived primitive differs',name)
        sources=sorted(folder.glob('Sniffles*.java'))+[folder/'SimpleJson.java',ROOT/'src/main/java/org/jlinalg/genetics/SnifflesVcf.java']
        resources=ROOT/'src/main/resources/org/jlinalg/omics/sniffles-execution'
        files=[Path(__file__).resolve(),ROOT/'tools/literature/prepare_sniffles_execution_sources.py',*sources,*sorted(p for p in resources.rglob('*') if p.is_file()),ROOT/'LICENSE']
        pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for path in files:
            target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
        classes=out/'classes';classes.mkdir()
        command=[str(a.javac),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr)
        save(out/'compile.json',dict(command=command,exit_code=run.returncode,compiler=pin(a.javac.resolve())))
        assert run.returncode==0,run.stderr.decode(errors='replace')
        payload={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
        payload.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()})
        payload.update({'META-INF/LICENSE':(ROOT/'LICENSE').read_bytes(),'META-INF/MANIFEST.MF':b'Manifest-Version: 1.0\r\nMain-Class: org.jlinalg.cli.SnifflesRuntimeCli\r\nImplementation-Version: 0.3.7-development\r\n\r\n'})
        jar=out/'sniffles-execution-development.jar'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
            for name,raw in sorted(payload.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;opened.writestr(info,raw)
        native_home=ROOT/'docs/literature/sniffles-vcf-development';native_reference=a.native_reference.resolve()
        native_proof=json.loads((native_home/'VALIDATION.json').read_bytes())
        assert native_reference.parent==native_home and native_reference.name in native_proof['artifacts'] and pin(native_reference)==native_proof['artifacts'][native_reference.name]
        with zipfile.ZipFile(native_reference) as native:
            old_classes={n:native.read(n) for n in native.namelist() if n.endswith('.class')}
            assert all(payload[n]==raw for n,raw in old_classes.items()),'Frozen native API/CLI class family changed'
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(canonical)==before
        state.update(status='PASS_ISOLATED_JAVA17_SNIFFLES_EXECUTION_BUILD_ONLY',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),
                     source_pins=pins,entries=sorted(payload),native_class_family_unchanged=len(old_classes),native_reference=dict(path=native_reference.relative_to(ROOT).as_posix(),**pin(native_reference)),canonical_executable_unchanged=before)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable isolated execution/source derivation checks')
    raise SystemExit(main())
