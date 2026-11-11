# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Link an observing wrapper to unchanged original hifiasm objects and compare the six source equation families."""
import argparse,hashlib,json,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def function(source,name):
    clean=re.sub(r'/\*.*?\*/|//[^\n]*','',source,flags=re.S);match=re.search(r'\b'+re.escape(name)+r'\s*\([^;{}]*\)\s*(?:const\s*)?\{',clean);assert match,name;start=match.start();pos=match.end();depth=1
    while depth:
        if clean[pos]=='{':depth+=1
        elif clean[pos]=='}':depth-=1
        pos+=1
    normalized=re.sub(r'\s+','',clean[start:pos]);return dict(comment_whitespace_normalized_sha256=hashlib.sha256(normalized.encode()).hexdigest(),normalized_body=normalized)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original-builds',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_PARENTAL_SOURCE_OBSERVER_BUILD',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/hifiasm-source/profiles.json').read_bytes());relationships={}
        for edition,definition in profile['editions'].items():
            source=a.original_builds.resolve()/edition/'source';assert all(pin(source/n)==v for n,v in definition['members'].items());relationships[edition]={name:function((source/file).read_text(),name) for file,name in [('htab.h','yak_hash64'),('htab.h','yak_hash64_64'),('htab.h','yak_hash_long'),('Trio.cpp','tb_classify')]}
        save(out/'source-function-relationships.json',relationships);source=a.original_builds.resolve()/'0.19.5-r587/source';process=json.loads((a.original_builds/'0.19.5-r587/receipts/compile/process.json').read_bytes());assert process['exit_code']==0 and not process['owned_live_after_cleanup'];helper=ROOT/'tools/literature/hifiasm_parental_reference.cpp';(out/'reference-source.cpp').write_bytes(helper.read_bytes());objects=sorted(p for p in source.glob('*.o') if p.name not in ['main.o','Trio.o']);assert len(objects)>20;before={p.name:pin(p) for p in objects};command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/g++','-O2','-msse4.2','-mpopcnt','-I',linux(source),linux(helper),*map(linux,objects),'-lz','-lpthread','-lm','-o',linux(out/'reference')];run=subprocess.run(command,capture_output=True,timeout=300);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read retained compile.stderr';assert all(pin(source/n)==v for n,v in before.items());groups={name:len({edition[name]['comment_whitespace_normalized_sha256'] for edition in relationships.values()}) for name in ['yak_hash64','yak_hash64_64','yak_hash_long','tb_classify']};assert all(n==1 for n in groups.values()),groups;state.update(status='passed-compiled-original-parental-source-observer-and-six-edition-equations-only',binary=pin(out/'reference'),original_objects=before,source_function_relationships=pin(out/'source-function-relationships.json'),distinct_function_families=groups,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [helper,Path(__file__).resolve()]},observed_original_edition='0.19.5-r587',original_assembler_source_members_unchanged=True)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source identity checks')
    raise SystemExit(main())
