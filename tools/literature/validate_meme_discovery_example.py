# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented synthetic example through the actual root JAR, then verify on three JVMs."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, linux, pin


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); base = ROOT/'build/literature-review/meme-runtime'
    assert out.is_relative_to(base) and not out.exists(); out.mkdir()
    proof = json.loads((args.artifact_build/'COMPLETED.json').read_bytes()); jar = ROOT/proof['artifact']['path']
    assert pin(jar) == {k: proof['artifact'][k] for k in ['bytes','sha256']}
    example = ROOT/'examples/meme-discovery/create_example.py'
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [example, Path(__file__)]}
    for name in source_pins:
        target = out/'source-snapshot'/name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes((ROOT/name).read_bytes())
    prepared = out/'example Δ research'
    plan = dict(example=linux(example), evidence=linux(base/'linux-build-3'), prepared=linux(prepared), jar=linux(jar))
    save(out/'plan.json', plan)
    script = '''import json,subprocess,sys
from pathlib import Path
out=Path(sys.argv[2]); plan=json.loads(Path(sys.argv[1]).read_bytes()); records=[]
prepare=['/usr/bin/python3',plan['example'],'--profile','MEME_5_5_9','--build-evidence',plan['evidence'],'--out',plan['prepared']]
for label,command in [('prepare',prepare)]+[(mode,['/usr/bin/java','-Xmx768m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-jar',plan['jar'],'meme-tools','--request',str(Path(plan['prepared'])/(mode+'.json'))]) for mode in ['discover','verify']]:
 with (out/(label+'.stdout')).open('xb') as a,(out/(label+'.stderr')).open('xb') as b:
  r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=2400)
 record=dict(label=label,command=command,exit_code=r.returncode);records.append(record)
 (out/(label+'.process.json')).write_text(json.dumps(record,indent=2)+'\\n')
 if r.returncode:sys.exit(r.returncode)
(out/'LINUX_COMPLETED.json').write_text(json.dumps(dict(records=records),indent=2)+'\\n')
'''
    command = ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-c',script,linux(out/'plan.json'),linux(out)]
    save(out/'STARTED.json', dict(command=command, artifact=proof['artifact'], source_pins=source_pins))
    with (out/'controller.stdout').open('xb') as a, (out/'controller.stderr').open('xb') as b:
        r = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=a, stderr=b, timeout=3000)
    save(out/'TERMINAL.json', dict(exit_code=r.returncode)); assert r.returncode == 0, 'Read retained example process stderr; no result admitted'
    assert json.loads((prepared/'result/OUTPUT-AUDIT.json').read_bytes())['motifs'] == 2
    save(out/'verify-windows.json', dict(schema_version=1, operation='VERIFY', bundle=str(prepared/'result')))
    records = []
    for label,java in [('windows-java21',JAVA21/'java.exe'),('windows-java25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')]:
        command = [str(java),'-Xmx768m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-jar',str(jar),'meme-tools','--request',str(out/'verify-windows.json')]
        r = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=240)
        (out/(label+'.stdout')).write_bytes(r.stdout); (out/(label+'.stderr')).write_bytes(r.stderr)
        row = dict(JVM=label, command=command, exit_code=r.returncode); records.append(row); save(out/(label+'.process.json'),row)
        assert r.returncode == 0, r.stderr.decode('utf8',errors='replace')
    assert all(pin(ROOT/n) == wanted for n,wanted in source_pins.items()) and pin(jar) == {k:proof['artifact'][k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json', dict(status='passed-documented-MEME-example-through-root-CLI-and-three-platform-VERIFY', artifact=proof['artifact'], source_pins=source_pins,
        original_analyses=1, portable_verifications=3, Windows_records=records, synthetic_health_population_reused=True, paper_reconstruction=False))
    print('Passed documented MEME example: actual root discovery and three-platform VERIFY')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source/artifact bindings')
    main()
