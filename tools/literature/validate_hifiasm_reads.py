# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete original kseq fields and native admission failures on three JVMs."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_READ_NATIVE_CONTROLS',runs=[],analysis_admitted=False,implementation_credit=False)
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};fixtures=a.fixtures.resolve();producer=json.loads((fixtures/'COMPLETED.json').read_bytes());assert producer['status']=='prepared-six-original-kseq-read-field-controls-and-explicit-admission-rejections' and pin(fixtures/'cases.json')==producer['cases'];driver=ROOT/'tools/literature/java/org/jlinalg/cli/HifiasmReadsAudit.java';classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(driver)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode()
        for label,java,is_linux,major in [('windows21',str(JAVA21/'java.exe'),False,'21'),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False,'25'),('linux21','/usr/bin/java',True,'21')]:
            home=out/label;home.mkdir();path=lambda p:linux(p) if is_linux else str(p);prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java] if is_linux else [java];version=subprocess.run(prefix+['-version'],capture_output=True,timeout=60);(home/'version.stderr').write_bytes(version.stderr);s=(version.stdout+version.stderr).decode();assert version.returncode==0 and ('"'+major+'.' in s or '"'+major+'"' in s);command=prefix+['-Xmx1g','-cp',path(classes)+(':' if is_linux else ';')+path(jar),'org.jlinalg.cli.HifiasmReadsAudit',path(fixtures/'cases.json'),path(ROOT),path(home/'native')]
            with (home/'native.stdout').open('xb') as first,(home/'native.stderr').open('xb') as second:run=subprocess.run(command,stdout=first,stderr=second,cwd=ROOT,timeout=240)
            save(home/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read '+str(home/'native.stderr');report=json.loads((home/'native/COMPLETED.json').read_bytes());assert report['valid_cases']==producer['valid_cases'] and report['invalid_cases']==producer['invalid_cases'] and report['pair_cases']==producer['pair_cases'];state['runs'].append(dict(jvm=label,report=report));print(label,report['status'],flush=True)
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());state.update(status='passed-three-JVM-complete-original-kseq-fields-and-native-admission-contracts-only',artifact=build['artifact'],source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [driver,Path(__file__).resolve()]})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete native/source controls')
    raise SystemExit(main())
