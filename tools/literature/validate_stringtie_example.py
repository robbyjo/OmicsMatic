# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the documented standalone exporter, example preparer and three actual user fits."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, linux, pin
from validate_stringtie_gtf import save

def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--jar', type=Path, required=True); parser.add_argument('--out', type=Path, required=True); parser.add_argument('--linux-root', required=True)
    args = parser.parse_args(); jar, out = args.jar.resolve(), args.out.resolve(); base = ROOT / 'build/literature-review/stringtie-runtime'
    assert jar.is_relative_to(base.resolve()) and jar.is_file() and out.is_relative_to(base.resolve()) and not out.exists()
    assert args.linux_root.startswith('/var/tmp/omicsmatic-stringtie-example-') and len(args.linux_root.split('/')) == 4
    out.mkdir(); save(out / 'INCOMPLETE.json', dict(status='EXAMPLE_VALIDATION_IN_PROGRESS')); artifact = pin(jar)
    names = ['examples/stringtie/prepare_example.py', 'examples/stringtie/README.md', 'tools/literature/validate_stringtie_example.py']; sources = {n: pin(ROOT / n) for n in names}
    try:
        driver = out / 'native-driver.py'; driver.write_text(NATIVE, encoding='utf-8', newline='\n')
        argv = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-I', '-B', linux(driver), args.linux_root, linux(jar), linux(out), linux(ROOT / 'examples/stringtie/prepare_example.py')]
        with (out / 'native.stdout').open('xb') as stdout, (out / 'native.stderr').open('xb') as stderr: process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=900, check=False)
        save(out / 'native.process.json', dict(command=argv, exit_code=process.returncode)); assert process.returncode == 0
        for label in ['assembly', 'estimation', 'merged']:
            argv = [str(JAVA21 / 'java.exe'), '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', str(jar), 'org.jlinalg.cli.StringTieToolsCli', '--verify', str(out / 'bundles' / label)]
            with (out / (label + '-windows-verify.stdout')).open('xb') as stdout, (out / (label + '-windows-verify.stderr')).open('xb') as stderr: process = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=90, check=False)
            save(out / (label + '-windows-verify.process.json'), dict(command=argv, exit_code=process.returncode)); assert process.returncode == 0
        assert artifact == pin(jar) and all(pin(ROOT / n) == p for n,p in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-documented-standalone-kit-export-example-preparation-three-user-fits-and-copied-verification', artifact=artifact, sources=sources, actual_example_fits=3, independent_fresh_original_merge_comparison=1, copied_windows_verifications=3, native=json.loads((out / 'NATIVE.json').read_bytes()), integration_credit=False)); (out / 'INCOMPLETE.json').unlink()
        print(json.dumps(dict(status='passed-documented-StringTie-example', user_fits=3, independent_original_merge=1, copied_windows_verifications=3)))
    except BaseException as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_DOCUMENTED_EXAMPLE_VALIDATION_FAILED', error=str(error), required_action='Preserve this attempt and private native work. Read original logs, correct the cause and retry into NEW paths.')); raise

NATIVE = r'''
import hashlib,json,shutil,subprocess,sys
from pathlib import Path
base,jar,out,prepare=map(Path,sys.argv[1:]);assert not base.exists();base.mkdir(mode=0o700);work=base/'standalone-work';work.mkdir();records=[]
def pin(path):
 with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def run(label,command,expected=0,cwd=work):
 with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:process=subprocess.run(list(map(str,command)),cwd=cwd,env={'PATH':'/usr/bin:/bin','LANG':'C','LC_ALL':'C'},stdout=stdout,stderr=stderr,timeout=180,check=False)
 row=dict(label=label,command=list(map(str,command)),exit_code=process.returncode,expected_exit=expected);records.append(row);(out/(label+'.process.json')).write_text(json.dumps(row,indent=2)+'\n');assert process.returncode==expected,(label,process.returncode)
java=['/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-cp',jar,'org.jlinalg.cli.StringTieToolsCli'];kit=base/'kit';example=base/'example';home=Path('/var/tmp/omicsmatic-stringtie-setup-20261103-1/home-3.0.3');setup=home.parent/'install-3.0.3'
run('export-runtime-kit',[*java,'--export-runtime-setup',kit]);run('prepare-example',['/usr/bin/python3','-I','-B',prepare,'--kit',kit,'--out',example,'--profile','linux-x86_64-stringtie-3.0.3']);proof=json.loads((example/'EXAMPLE.json').read_bytes());assert not proof['user_analysis_performed'] and not(example/'INCOMPLETE.json').exists()
assert all(pin(example/'inputs'/n)==v for n,v in proof['inputs'].items());initial=pin(example/'EXAMPLE.json')
for recipe,label in [('assemble','assembly'),('estimate','estimation'),('merge','merged')]:
 bundle=example/label;run(label,[*java,'--recipe',example/(recipe+'.json'),'--home',home,'--setup-report',setup,'--out',bundle]);run(label+'-verify',java+['--verify',bundle]);shutil.copytree(bundle,out/'bundles'/label)
assert initial==pin(example/'EXAMPLE.json') and all(pin(example/'inputs'/n)==v for n,v in proof['inputs'].items())
# An unmodified original invocation independently checks this example's one-input
# default merge protocol; the earlier retained control used two inputs and -m150.
reference=base/'original-one-input-default-merge';reference.mkdir();binary=home/'bin/stringtie';before=pin(binary)
run('original-default-merge',[binary,'--merge','-G',example/'inputs/guide.gtf','-o','transcripts.gtf',example/'assembly/fit/transcripts.gtf'],cwd=reference)
science=lambda path:b''.join(line for line in path.read_bytes().splitlines(keepends=True) if not line.startswith(b'#'))
assert science(reference/'transcripts.gtf')==science(example/'merged/fit/transcripts.gtf') and before==pin(binary)
shutil.copytree(reference,out/'independent-original-default-merge');shutil.copytree(kit,out/'exported-kit')
for name in ['EXAMPLE.json','assemble.json','estimate.json','merge.json']:
 shutil.copyfile(example/name,out/name)
(out/'NATIVE.json').write_text(json.dumps(dict(status='passed-actual-documented-example-from-standalone-private-working-directory',linux_root=str(base),working_directory=str(work),user_fits=3,independent_original_merge=1,records=records),indent=2)+'\n')
'''

if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete example checks')
    main()
