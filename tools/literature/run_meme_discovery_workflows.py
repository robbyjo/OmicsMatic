# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual typed MEME discovery and portable VERIFY; one WSL launch avoids DNS-link rewrites during host census."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import pin, linux, JAVA21

PROFILES = {'MEME_5_5_9': 'linux-build-3', 'MEME_5_5_4': 'historical-build-5.5.4-1',
            'MEME_4_9_1_BASE': 'historical-recovered-4.9.1-base-1', 'MEME_4_9_1_PATCH2': 'historical-recovered-4.9.1-patch2-1'}


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True)
    parser.add_argument('--profiles', choices=list(PROFILES), nargs='+', default=['MEME_5_5_9'])
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--empty', action='store_true', help='Use an explicit 1e-300 E-value cutoff and assert zero accepted motifs')
    args = parser.parse_args(); base = ROOT / 'build/literature-review/meme-runtime'; out = args.out.resolve()
    assert out.is_relative_to(base) and not out.exists(); out.mkdir()
    collector = pin(Path(__file__))
    (out / 'collector-source.py').write_bytes(Path(__file__).read_bytes())
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
    original = base / 'sites-original-5.5.9-1/captured/inputs'
    inputs = out / 'inputs Δ research'; inputs.mkdir()
    for name in ['weighted.fa', 'asymmetric.bg']: shutil.copyfile(original / name, inputs / name)
    plan = []
    for profile in args.profiles:
        folder = out / profile; folder.mkdir()
        source = json.loads((base / PROFILES[profile] / 'COMPLETED.json').read_bytes())
        recipe = dict(schema_version=1, profile=profile, primary=linux(inputs / 'weighted.fa'), background=linux(inputs / 'asymmetric.bg'),
            occurrence='ZOOPS', both_strands=True, motifs=2, minimum_width=10, maximum_width=10,
            seed=None if profile.startswith('MEME_4_') else 17, search_size=None if profile.startswith('MEME_4_') else 0,
            dirichlet_beta=0.5, maximum_iterations=50, convergence_distance=0.00001,
            maximum_motif_evalue=None, minimum_sites=None, maximum_sites=None,
            limits=dict(wall_seconds=120, cpu_seconds=120, address_space_bytes=2147483648, sampled_rss_bytes=1073741824))
        if args.empty:
            recipe['maximum_motif_evalue'] = 1e-300
            if not profile.startswith('MEME_4_'): recipe['motifs'] = 1000
        save(folder / 'recipe.json', recipe)
        save(folder / 'discover.json', dict(schema_version=1, operation='DISCOVER', recipe=linux(folder / 'recipe.json'),
            runtime_home=source['prefix'], build_evidence=linux(base / PROFILES[profile]), out=linux(folder / 'result')))
        save(folder / 'verify-linux.json', dict(schema_version=1, operation='VERIFY', bundle=linux(folder / 'result')))
        save(folder / 'verify-windows.json', dict(schema_version=1, operation='VERIFY', bundle=str(folder / 'result')))
        plan.append(dict(profile=profile, folder=linux(folder), discover=linux(folder / 'discover.json'), verify=linux(folder / 'verify-linux.json')))
    save(out / 'plan.json', dict(jar=linux(jar), rows=plan))
    script = '''import json,subprocess,sys,time,os
from pathlib import Path
plan=json.loads(Path(sys.argv[1]).read_text());records=[]
for row in plan['rows']:
 folder=Path(row['folder']);(folder/'LINUX_STARTED.json').write_text(json.dumps(dict(controller_pid=os.getpid(),profile=row['profile'],started=time.time())))
 for mode,request in [('discover',row['discover']),('verify-linux',row['verify'])]:
  argv=['/usr/bin/java','-Xmx768m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-cp',plan['jar'],'org.jlinalg.cli.MemeToolsCli','--request',request]
  with (folder/(mode+'.stdout')).open('xb') as a,(folder/(mode+'.stderr')).open('xb') as b:
   result=subprocess.run(argv,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=2400)
  record=dict(profile=row['profile'],operation=mode,command=argv,exit_code=result.returncode)
  (folder/(mode+'.process.json')).write_text(json.dumps(record,indent=2)+'\\n');records.append(record)
  if result.returncode:
   Path(sys.argv[2]).write_text(json.dumps(dict(status='stopped-with-retained-failure',records=records),indent=2)+'\\n');sys.exit(result.returncode)
Path(sys.argv[2]).write_text(json.dumps(dict(status='completed-original-discovery-and-portable-verification',records=records),indent=2)+'\\n')
'''
    command = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/python3', '-c', script, linux(out / 'plan.json'), linux(out / 'LINUX_TERMINAL.json')]
    save(out / 'STARTED.json', dict(command=command, artifact=build['artifact'], profiles=args.profiles, collector=collector,
        note='Do not launch additional wsl.exe processes while host census runs: this host rewrites /etc/resolv.conf link metadata on each WSL launch. The strict host census is unchanged.'))
    with (out / 'controller.stdout').open('xb') as a, (out / 'controller.stderr').open('xb') as b:
        result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=a, stderr=b, timeout=2500 * len(plan))
    save(out / 'TERMINAL.json', dict(command=command, exit_code=result.returncode, artifact=build['artifact']))
    if result.returncode:
        for row in plan:
            p = out / row['profile'] / 'discover.stderr'
            if p.exists(): print(p.read_text(encoding='utf8', errors='replace')[-5000:], flush=True)
        raise SystemExit(result.returncode)
    records = []
    for profile in args.profiles:
        folder = out / profile
        audit = json.loads((folder / 'result/OUTPUT-AUDIT.json').read_bytes())
        assert audit['motifs'] == (0 if args.empty else 2), audit
        for label, java in [('windows-java21', JAVA21 / 'java.exe'), ('windows-java25', 'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')]:
            argv = [str(java), '-Xmx768m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8', '-cp', str(jar), 'org.jlinalg.cli.MemeToolsCli', '--request', str(folder / 'verify-windows.json')]
            r = subprocess.run(argv, cwd=ROOT, capture_output=True, stdin=subprocess.DEVNULL, timeout=240)
            (folder / (label + '.stdout')).write_bytes(r.stdout); (folder / (label + '.stderr')).write_bytes(r.stderr)
            records.append(dict(profile=profile, JVM=label, command=argv, exit_code=r.returncode))
            assert r.returncode == 0, label + ' failed: ' + r.stderr.decode('utf8', errors='replace')
    assert pin(Path(__file__)) == collector and pin(jar) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
    save(out / 'COMPLETED.json', dict(status='passed-actual-typed-MEME-discovery-and-three-platform-portable-VERIFY', artifact=build['artifact'], collector=collector,
        profiles=args.profiles, original_analyses=len(args.profiles), portable_verifications=3 * len(args.profiles), Windows_records=records,
        expected_zero_motifs=args.empty,
        original_input_paths_contain_Unicode_and_spaces=True, source_and_native_worker_retained=True, implementation_credit=False))
    print('Passed original discovery and three-platform VERIFY for ' + ', '.join(args.profiles), flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable original scientific checks')
    main()
