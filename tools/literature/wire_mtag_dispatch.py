# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply the exact already compiled/tested two-edit MTAG root dispatch delta; no scientific class or released executable changes."""
import argparse
import json
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from verify_mtag_dimensions import inspect
from verify_mtag_workflow_evidence import HOME,PREFIX
from signac_scan_evidence import byte_pin
from catalog_source_integrity import reverse_delta,verify_source


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    dimensions,files=inspect();proof=json.loads((HOME/'VALIDATION.json').read_bytes());record=proof['archives']['controls.zip'];assert pin(HOME/'controls.zip')==record['archive']
    def read(name):
        wanted=record['logical_file_pins'][PREFIX+name]
        with zipfile.ZipFile(HOME/'controls.zip') as archive:raw=archive.read('objects/'+wanted['sha256'])
        assert byte_pin(raw)==wanted;return raw
    group='dispatch-build21-3/';delta=json.loads(read(group+'staged-root-delta.json'))['paths'][0];name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';source=ROOT/name
    before=source.read_bytes();after=read(group+'source-snapshot/'+name)
    assert len(delta['edits'])==2 and delta['path']==name and reverse_delta(after,delta)==before and byte_pin(before)['sha256']==delta['before_sha256']
    data=ROOT/'docs/literature/omics-2023-2026';descriptor=data/'mtag-dispatch-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mtag',scope='Only the already compiled/tested mtag-tools dispatch and help insertion. All predecessor scientific class bytes, Java scientific sources and canonical executable remain unchanged.',paths=[delta]))
    (out/'root.before.java').write_bytes(before);(out/'root.after.java').write_bytes(after);assert source.read_bytes()==before;source.write_bytes(after)
    verify_source(ROOT,data,name,delta['before_sha256'])
    save(out/'COMPLETED.json',dict(status='PASS_MTAG_LIVE_ROOT_EXACT_ALREADY_TESTED_DISPATCH_DELTA',source=pin(source),descriptor=pin(descriptor),dimension_evidence=pin(ROOT/'docs/literature/mtag-dimensions-development/VALIDATION.json'),
        unchanged_canonical_dependency=proof['canonical_executable_unchanged'],new_catalog_credit=False,whole_goal_complete=False))
    print('PASS_MTAG_LIVE_ROOT_EXACT_ALREADY_TESTED_DISPATCH_DELTA')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact tested root inverse and unchanged science checks')
    main()
