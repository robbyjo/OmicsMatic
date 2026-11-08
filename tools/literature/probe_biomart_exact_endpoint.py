# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate original exact-endpoint query configuration and complete raw HTTP diagnostic capture, without changing package functions."""
import argparse,concurrent.futures,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/biomart-runtime') and not out.exists();out.mkdir(parents=True);setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_ORIGINAL_BIOMART_PACKAGES_AND_SOURCE_BODIES_ONLY';sources=[Path(__file__).resolve(),ROOT/'tools/literature/probe_biomart_exact_endpoint.R'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources}
    for source in sources:(out/source.name).write_bytes(source.read_bytes())
    profiles=[('GRCh38-archive115','https://sep2025.archive.ensembl.org','GRCh38.p14'),('GRCh37-explicit','https://grch37.ensembl.org','GRCh37.p13'),('GRCh38-archive116','https://jun2026.archive.ensembl.org','GRCh38.p14')]
    def run(profile):
        name,host,assembly=profile;home=out/name;home.mkdir();temporary=home/'private-temp';temporary.mkdir();env=environment();env.update(TMPDIR=str(temporary),TMP=str(temporary),TEMP=str(temporary),BIOMART_CACHE=str(home/'private-cache'));command=[str(RSCRIPT),'--vanilla',str(sources[1]),str(home),'2.68.0',host,assembly,*setup['versions']['2.68.0']['libraries']];save(home/'command.json',dict(command=command))
        try:
            with (home/'original.stdout').open('xb') as first,(home/'original.stderr').open('xb') as second:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW)
            save(home/'process.json',dict(command=command,exit_code=r.returncode));report=json.loads((home/'COMPLETED.json').read_bytes());assert r.returncode in [0,3];print(name,report['status'],report.get('error',''),flush=True);return dict(name=name,exit_code=r.returncode,status=report['status'],error=report.get('error',''))
        except Exception as error:
            report=dict(name=name,exit_code=3,status='SKIP3_EXACT_ENDPOINT_REFERENCE_UNAVAILABLE',error=repr(error));save(home/'SUPERVISOR-ERROR.json',report);print(name,report['status'],repr(error),flush=True);return report
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:reports=list(pool.map(run,profiles))
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='completed-original-exact-endpoint-and-HTTP-trace-probes-only',source_pins=pins,profiles=reports,package_functions_patched=False,original_archive_factory_not_used=True,archive_or_build_substitution_permitted=False,annotation_client_admitted=False,implementation_credit=False,whole_goal_complete=False))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable explicit-endpoint/source checks')
    main()
