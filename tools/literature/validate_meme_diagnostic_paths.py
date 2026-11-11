# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Require exact user file paths in MEME failures; component names must never rewrite identifiers."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/meme-runtime') and not out.exists();out.mkdir()
    build=json.loads((args.artifact_build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
    rows=[]
    for label,java in [('windows-java21',JAVA21/'java.exe'),('windows-java25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')]:
        missing=out/(label+'-RSEM-Tomtom-original-name.json');assert not missing.exists()
        argv=[str(java),'-Dfile.encoding=UTF-8','-cp',str(jar),'org.jlinalg.cli.MemeToolsCli','--request',str(missing)]
        r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=60)
        (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr)
        text=r.stderr.decode('utf8',errors='replace');row=dict(JVM=label,command=argv,exit_code=r.returncode,exact_path_preserved=str(missing) in text,
            prominent_failure='FAIL2: MEME NO RESULT ADMITTED' in text,required_action='REQUIRED ACTION:' in text);rows.append(row)
    passed=all(r['exit_code']==2 and r['exact_path_preserved'] and r['prominent_failure'] and r['required_action'] for r in rows)
    record=dict(status='passed-exact-MEME-diagnostic-path-preservation' if passed else 'failed-MEME-diagnostic-path-preservation',artifact=build['artifact'],records=rows,
        source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},analysis_run=False)
    (out/('COMPLETED.json' if passed else 'FAILURE.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(status=record['status'],paths_preserved=[r['exact_path_preserved'] for r in rows])))
    return 0 if passed else 2


if __name__=='__main__':sys.exit(main())
