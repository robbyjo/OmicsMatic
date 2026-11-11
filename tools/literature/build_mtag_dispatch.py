# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an isolated MTAG root supplement with a staged two-edit dispatch delta; leave live root and predecessor science unchanged."""
import argparse
import json
import subprocess
import struct
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta


def without_line_numbers(raw):
    """Comparison only: remove Code/LineNumberTable debug attributes, preserving every other class byte."""
    at=0
    def take(n):
        nonlocal at
        assert n>=0 and at+n<=len(raw);v=raw[at:at+n];at+=n;return v
    def u2():return struct.unpack('>H',take(2))[0]
    def u4():return struct.unpack('>I',take(4))[0]
    assert take(4)==b'\xca\xfe\xba\xbe';take(4);count=u2();names={};index=1
    while index<count:
        tag=take(1)[0]
        if tag==1:names[index]=take(u2()).decode('utf8')
        else:
            lengths={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
            take(lengths[tag])
            if tag in (5,6):index+=1
        index+=1
    take(6);take(2*u2());prefix=raw[:at]
    def attributes():
        kept=[]
        for unused in range(u2()):
            name=u2();size=u4();end=at+size
            if names[name]=='Code':
                start=at;take(4);take(u4());take(8*u2());body=raw[start:at]+attributes();assert at==end
            else:body=take(size)
            if names[name]!='LineNumberTable':kept.append(struct.pack('>HI',name,len(body))+body)
        return struct.pack('>H',len(kept))+b''.join(kept)
    def members():
        n=u2();values=[]
        for unused in range(n):values.append(take(6)+attributes())
        return struct.pack('>H',n)+b''.join(values)
    answer=prefix+members()+members()+attributes();assert at==len(raw);return answer


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',type=Path,required=True);p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_STAGED_ROOT_BUILD',new_catalog_credit=False,live_root_modified=False,whole_goal_complete=False)
    code=2
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';prior_home=ROOT/'docs/literature/genesis-tools-development';prior=prior_home/('genesis-root-'+a.compiler+'.jar')
        build=a.build.resolve();standalone=build/'mtag-workflow-development.jar'
        tool=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath')/'javac.exe'
        if not all(path.is_file() for path in (canonical,prior,standalone,tool)):raise FileNotFoundError('Selected exact compiler, canonical dependency or development artifact unavailable')
        old=json.loads((prior_home/'VALIDATION.json').read_bytes());proof=json.loads((build/'COMPLETED.json').read_bytes())
        assert pin(prior)==old['artifacts'][prior.name] and pin(standalone)==proof['artifact'] and pin(canonical)==proof['canonical_dependency']==old['canonical_executable_unchanged']
        assert proof['compiler']==a.compiler
        for name,wanted in proof['source_pins'].items():assert pin(ROOT/name)==wanted,name
        dependencies={path.relative_to(ROOT).as_posix():pin(path) for path in (prior,standalone,canonical)}
        source=ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';before=source.read_bytes();nl=b'\r\n' if b'\r\n' in before else b'\n'
        def block(s):return s.replace('\n',nl.decode()).encode('utf8')
        anchor=block('        if (arguments.length > 0 && arguments[0].equals("genesis-tools"))\n')
        insertion=block('        if (arguments.length > 0 && arguments[0].equals("mtag-tools"))\n            return MtagToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n')
        help_anchor=b'              java -jar omicsmatic-<version>.jar genesis-tools --help'
        assert before.count(anchor)==before.count(help_anchor)==1 and b'arguments[0].equals("mtag-tools")' not in before
        after=before.replace(anchor,insertion+anchor).replace(help_anchor,b'              java -jar omicsmatic-<version>.jar mtag-tools --help'+nl+help_anchor)
        delta=inverse(before,after);delta['path']=source.relative_to(ROOT).as_posix();assert len(delta['edits'])==2 and reverse_delta(after,delta)==before
        save(out/'staged-root-delta.json',dict(schema_version=1,status='STAGED_DISPATCH_AND_HELP_ONLY_NOT_APPLIED',paths=[delta]))
        source_pin=pin(source);(out/'prior-root-source.java').write_bytes(before)
        staged=out/'source-snapshot/src/main/java/org/jlinalg/cli/JLinAlgCli.java';staged.parent.mkdir(parents=True);staged.write_bytes(after)
        with zipfile.ZipFile(prior) as z:payload={n:z.read(n) for n in z.namelist()}
        previous=dict(payload);added={};shared_debug_deltas=[]
        with zipfile.ZipFile(standalone) as z:
            for name in z.namelist():
                raw=z.read(name)
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/mtag/'):
                    if name in payload and payload[name]!=raw:
                        assert name=='org/jlinalg/cli/SimpleJson.class' and without_line_numbers(payload[name])==without_line_numbers(raw),('Scientific/common overlap differs',name)
                        # Keep the predecessor byte for byte. The independent
                        # workflow builder included the canonical JSON decoder;
                        # javac25 differs only in its line-number debug table.
                        from signac_scan_evidence import byte_pin
                        shared_debug_deltas.append(dict(path=name,predecessor=byte_pin(payload[name]),standalone=byte_pin(raw),without_line_number_tables=byte_pin(without_line_numbers(raw)),only_line_number_debug_metadata_differs=True,predecessor_retained_byte_identical=True))
                        continue
                    payload[name]=raw;added[name]=raw
                elif name.startswith('META-INF/'):payload['META-INF/mtag/'+name[len('META-INF/'):]]=raw
        classes=out/'classes';classes.mkdir();command=[str(tool),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-sourcepath','','-cp',';'.join(map(str,(standalone,prior,canonical))),'-d',str(classes),str(staged)]
        result=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(result.stdout);(out/'compile.stderr').write_bytes(result.stderr);save(out/'compile.process.json',dict(command=command,exit_code=result.returncode));assert result.returncode==0,result.stderr.decode(errors='replace')
        compiled={path.relative_to(classes).as_posix():path.read_bytes() for path in classes.rglob('*.class')};assert set(compiled)=={'org/jlinalg/cli/JLinAlgCli.class'};payload.update(compiled)
        payload['META-INF/mtag/DEPENDENCIES.json']=(json.dumps(dependencies,indent=2)+'\n').encode()
        artifact=out/'mtag-root-supplement.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(payload.items()):
                item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
        with zipfile.ZipFile(artifact) as z:
            assert all(z.read(n)==raw for n,raw in previous.items() if n!='org/jlinalg/cli/JLinAlgCli.class')
            assert all(z.read(n)==raw for n,raw in added.items())
        assert pin(source)==source_pin and all(pin(ROOT/n)==wanted for n,wanted in dependencies.items())
        state.update(status='PASS_MTAG_STAGED_ROOT_PREDECESSOR_SCIENTIFIC_BYTES_UNCHANGED',compiler=a.compiler,artifact=pin(artifact),dependencies=dependencies,source_before=source_pin,staged_source=pin(staged),
                     predecessor_entries=len(previous),new_scientific_entries=len(set(added)-set(previous)),overlapping_scientific_entries=len(set(added)&set(previous)),all_overlapping_class_bytes_identical=True,
                     retained_shared_json_debug_deltas=shared_debug_deltas,builder=pin(Path(__file__)),canonical_executable_unchanged=pin(canonical));code=0
    except FileNotFoundError as error:state.update(status='SKIP3_MTAG_STAGED_ROOT_PREREQUISITE_UNAVAILABLE',cause=repr(error));code=3
    except Exception as error:state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''))
    return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable predecessor byte preservation and exact staged dispatch checks')
    raise SystemExit(main())
