# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Probe real original biomaRt annotation using explicit archive/assembly and public gene controls; preserve every external-service skip."""
import argparse,concurrent.futures,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/biomart-runtime') and not out.exists();out.mkdir(parents=True);setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_ORIGINAL_BIOMART_PACKAGES_AND_SOURCE_BODIES_ONLY';sources=[Path(__file__).resolve(),ROOT/'tools/literature/probe_biomart_live.R'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources}
    for source in sources:(out/source.name).write_bytes(source.read_bytes())
    profiles=[('GRCh38-archive115','2.68.0','https://sep2025.archive.ensembl.org','GRCh38.p14'),('GRCh37-explicit','2.68.0','https://grch37.ensembl.org','GRCh37.p13')]
    def run(profile):
        name,version,host,assembly=profile;home=out/name;home.mkdir();temporary=home/'private-temp';temporary.mkdir();env=environment();env.update(TMPDIR=str(temporary),TMP=str(temporary),TEMP=str(temporary));command=[str(RSCRIPT),'--vanilla',str(sources[1]),str(home),version,host,assembly,*setup['versions'][version]['libraries']];save(home/'command.json',dict(command=command))
        try:
            with (home/'original.stdout').open('xb') as first,(home/'original.stderr').open('xb') as second:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=240,creationflags=subprocess.CREATE_NO_WINDOW)
            save(home/'process.json',dict(command=command,exit_code=r.returncode));report=json.loads((home/'COMPLETED.json').read_bytes());assert r.returncode in [0,3];print(name,report['status'],flush=True);return dict(name=name,exit_code=r.returncode,status=report['status'],error=report.get('error',''),completed_queries=report['completed_queries'])
        except Exception as error:
            report=dict(name=name,exit_code=3,status='SKIP3_ORIGINAL_LIVE_BIOMART_PROBE_UNAVAILABLE',error=repr(error),dependent_Java_analysis_admitted=False);save(home/'SUPERVISOR-ERROR.json',report);print(name,report['status'],repr(error),flush=True);return report
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:reports=list(pool.map(run,profiles))
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='completed-original-live-BioMart-probes-not-Java-client-admission',source_pins=pins,profiles=reports,public_gene_controls_only=True,unavailable_or_forbidden_analysis_must_skip=True,archive_or_build_fallback_allowed=False,implementation_credit=False,whole_goal_complete=False))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable explicit archive/assembly binding')
    main()
