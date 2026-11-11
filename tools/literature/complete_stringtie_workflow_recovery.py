# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the already passed public rejection controls and finish actual cancellation on a stable host."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
from validate_stringtie_gtf import save
from validate_stringtie_workflow_recovery import CANCEL

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--prior',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--linux-root',required=True);args=parser.parse_args()
    jar,prior,out=args.jar.resolve(),args.prior.resolve(),args.out.resolve();base=ROOT/'build/literature-review/stringtie-runtime';assert all(p.is_relative_to(base.resolve()) for p in [jar,prior,out]) and not out.exists()
    assert args.linux_root.startswith('/var/tmp/omicsmatic-stringtie-recovery-') and len(args.linux_root.split('/'))==4
    out.mkdir();save(out/'INCOMPLETE.json',dict(status='COMPLETING_STABLE_HOST_CANCELLATION'));artifact=pin(jar)
    names=['tools/literature/complete_stringtie_workflow_recovery.py','tools/literature/validate_stringtie_workflow_recovery.py','src/test/java/org/jlinalg/cli/StringTieWorkflowCancellationCheck.java'];sources={n:pin(ROOT/n) for n in names}
    try:
        assert json.loads((prior/'FAILURE.json').read_bytes())['status']=='FAIL2_RECOVERY_CONTROLS_FAILED'
        assert 'Host member changed during its complete census: /etc/resolv.conf' in (prior/'native-cancellation.stderr').read_text()
        rejections=[]
        for platform in ['windows-java21','windows-java25','linux-java21']:
            for case in ['table','audit','process','runtime-input','setup-request','extra','guard','missing-table']:
                label=platform+'-'+case;row=json.loads((prior/(label+'.process.json')).read_bytes());assert row['exit_code']==row['expected_exit']==2
                old_jar=Path(row['command'][row['command'].index('-cp')+1]) if platform!='linux-java21' else Path('D:/projects/JLinAlg')/Path(row['command'][row['command'].index('-cp')+1]).relative_to('/mnt/d/projects/JLinAlg')
                assert pin(old_jar)==artifact
                message=(prior/(label+'.stderr')).read_text(encoding='utf-8');assert 'FAIL2' in message and 'NEW output paths' in message
                rejections.append(dict(label=label,process=pin(prior/(label+'.process.json')),stderr=pin(prior/(label+'.stderr'))))
        def command(label,argv):
            argv=list(map(str,argv))
            with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=220,check=False)
            save(out/(label+'.process.json'),dict(command=argv,exit_code=process.returncode));assert process.returncode==0,(label,process.returncode)
        classes=out/'cancellation-classes';classes.mkdir();command('compile-exact-cancellation-driver',[JAVA21/'javac.exe','--release','17','-g','-Xlint:all','-Werror','-cp',jar,'-d',classes,ROOT/names[2]])
        assert pin(classes/'org/jlinalg/cli/StringTieWorkflowCancellationCheck.class')==pin(prior/'cancellation-classes/org/jlinalg/cli/StringTieWorkflowCancellationCheck.class')
        driver=out/'native-cancel.py';driver.write_text(CANCEL,encoding='utf-8',newline='\n');baseline=prior.parent/'workflow-matrix-1/bundles/1.3.6-estimate/metadata/recipe.json'
        command('actual-owned-cancellation',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(driver),args.linux_root,linux(jar),linux(classes),linux(baseline),linux(out)])
        assert 'PASSED_ACTUAL_USER_FIT_CANCELLATION' in (out/'native-cancellation.stdout').read_text()
        assert all(pin(ROOT/n)==p for n,p in sources.items()) and pin(jar)==artifact
        save(out/'COMPLETED.json',dict(status='passed-24-public-rehashed-invalid-bundle-rejections-and-actual-owned-user-fit-cancellation',artifact=artifact,sources=sources,
            retained_prior_attempt=prior.relative_to(ROOT).as_posix(),retained_passed_rejections=rejections,actual_cli_rejections=24,invalid_cases=8,actual_user_fit_cancellation=True,cancelled_process_cleanup_complete=True,integration_credit=False));(out/'INCOMPLETE.json').unlink()
        print(json.dumps(dict(status='passed-complete-StringTie-recovery',retained_rehashed_rejections=24,new_actual_cancelled_user_fit=True)))
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='FAIL2_RECOVERY_CONTROLS_FAILED',error=str(error),required_action='Preserve this attempt and original partials; inspect logs, correct the cause and retry into NEW paths.'));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete recovery checks')
    main()
