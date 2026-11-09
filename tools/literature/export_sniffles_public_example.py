# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export a relocated toy CRAM/FASTA request and exact artifact; never run/install software.

Run inside the permitted matching Linux environment. Uses only published frozen
repository data, not ignored fixture/reference directories. Original sources,
bindings, artifacts and input bytes remain unchanged.
"""
import argparse,json,re,shlex,sys,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from sniffles_installation_evidence import verify,byte_pin


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--binding',type=Path,required=True);parser.add_argument('--artifact',type=Path,required=True);a=parser.parse_args()
    out=a.out;assert sys.platform=='linux','Export this Linux request inside the permitted Linux environment'
    assert out.is_absolute() and out==out.resolve() and not out.exists() and out.parent==out.parent.resolve() and out.parent.is_dir()
    assert out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime') and re.fullmatch(r'/[A-Za-z0-9_./+\-]+',str(out))
    binding=a.binding.resolve(strict=True);artifact=a.artifact.resolve(strict=True);record=json.loads(binding.read_bytes())
    assert record['status']=='PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY' and not record['analysis_admitted'] and not record['owned_live_after_cleanup']
    before={path.relative_to(ROOT).as_posix():pin(path) for path in [binding,artifact,Path(__file__).resolve()]}
    with zipfile.ZipFile(artifact) as opened:
        assert byte_pin(opened.read('org/jlinalg/omics/sniffles-execution/profiles.json'))==record['profile_pin']
        assert byte_pin(opened.read('org/jlinalg/omics/sniffles-execution/bootstrap.py'))==record['bootstrap_pin']
    _,files=verify(True);prefix='build/literature-review/sniffles-runtime/extended-fixtures-1/'
    out.mkdir();data=out/'data';data.mkdir();application=out/'application';application.mkdir();copied=application/'sniffles-workflow.jar';copied.write_bytes(artifact.read_bytes())
    inputs=[]
    for id,kind,source,target in [('reference','fasta','reference.fa','reference.fa'),('reads','cram','sampleA.cram','reads.cram')]:
        raw=files[prefix+source];path=data/target;path.write_bytes(raw);row=dict(id=id,kind=kind,path=str(path),pin=pin(path))
        if kind=='cram':row['reference']='reference'
        inputs.append(row)
    request=dict(schema_version=1,out=str(out/'analysis'),runtime=dict(path=str(binding),pin=pin(binding)),inputs=inputs,
        analyses=[dict(id='phased-cram',edition='2.4',input='reads',threads=1,minsvlen=80,minsupport=10,mapq=22,minimum_alignment_length=500,phase=True,sample_id='--literal sample24')],
        limits=dict(maximum_input_bytes=16777216,maximum_alignment_records=10000,maximum_read_bases=100000,maximum_reference_bases=1000000,
            maximum_vcf_bytes=16777216,maximum_vcf_records=1000,wall_millis=120000,address_space_bytes=8589934592,cpu_seconds=120,
            file_bytes=33554432,workspace_bytes=536870912,file_count=20000,processes=32,threads=256,sampled_rss_bytes=2147483648,monitor_millis=100))
    save(out/'request.json',request)
    commands=dict(call=['/usr/bin/java','-cp',str(copied),'org.jlinalg.cli.SnifflesWorkflowCli','--request',str(out/'request.json')],
        verify=['/usr/bin/java','-cp',str(copied),'org.jlinalg.cli.SnifflesVerifyCli','--result',str(out/'analysis'),'--out',str(out/'verification')])
    save(out/'commands.json',commands)
    (out/'README.md').write_text('# Exported Sniffles toy example\n\n**Data export only: no scientific result has run or been admitted.** This is a relocated40-read constructed CRAM/reference control, not a biological dataset or paper reproduction. It deliberately selects minsvlen80 and explicit phasing/numeric settings; the corresponding direct original control returns2 SV records. The leading dashes/spaces in the sample label are literal JSON data.\n\nRun inside the permitted matching Linux environment with Linux Java21. Where prerequisites are unavailable/forbidden, skip calling and read the named action; do not substitute another host/source edition or refresh saved scientific hashes. The exported data contain no supplied CRAM index; the workflow creates a NEW owned index and a local declared-reference cache.\n\n```sh\n'+shlex.join(commands['call'])+'\n'+shlex.join(commands['verify'])+'\n```\n\nRequire exit0 AND analysis/COMPLETED.json analysis_admitted=true for the entire call batch. Read analysis/WARNINGS.md and complete original logs/VCF/native values. Failure, skip, cancellation and earlier provisional data admit no result. Verification checks frozen integrity/consistency, not a new caller run, current runtime health, authenticity or biological accuracy. Preserve failed attempts; retry NEW export/output paths after correcting the named problem.\n',encoding='utf8',newline='\n')
    assert pin(copied)==pin(artifact) and before=={name:pin(ROOT/name) for name in before}
    save(out/'EXPORTED.json',dict(status='PASS_FROZEN_TOY_SNIFFLES_EXAMPLE_EXPORT_ONLY',analysis_admitted=False,analysis_executed=False,software_installed=False,implementation_credit=False,
        exported_request=pin(out/'request.json'),exported_artifact=pin(copied),exported_input_files={row['id']:row['pin'] for row in inputs},
        original_inputs_unchanged=before,expected_original_records=2,whole_paper_reproduced=False,whole_goal_complete=False))
    print('Exported a relocated toy CRAM/FASTA request and exact artifact; nothing executed or admitted')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen example/profile/source preservation checks')
    try:main()
    except Exception as failure:
        print('FAIL2_SNIFFLES_EXAMPLE_EXPORT: '+repr(failure)+'\nACTION: preserve all original bindings, artifacts and data. Correct the named problem where permitted, then use a NEW canonical Linux export path. This helper installs/runs nothing; skip dependent calling when its permitted runtime is unavailable.',file=sys.stderr)
        raise SystemExit(2)
