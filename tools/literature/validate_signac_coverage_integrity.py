# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resealed numeric/SVG refusals and an explicit PNG pixel-replay limitation test."""
import argparse
import shutil
import struct
import zlib
from signac_coverage_validation import *


def edit(root,name,mutate):
    value=read(root/name);mutate(value);save(root/name,value)


def bind_input(root,name):
    edit(root,'manifest.json',lambda x:x['captured_input_sha256'].update({name:pin(root/name)['sha256']}))


def reseal(root,mutate_generic=None):
    generic=read(root/'manifest.json');generic['output_sha256']={n:v['sha256'] for n,v in files(root).items() if n not in ['manifest.yaml','capture-files.json']}
    if mutate_generic:mutate_generic(generic)
    save(root/'manifest.yaml',generic)
    save(root/'capture-files.json',dict(schema_version=1,authentication=False,files={n:v['sha256'] for n,v in files(root).items() if n!='capture-files.json'},
         directories=sorted(p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_dir())))


def white_png(width,height):
    def chunk(name,data):return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\x00'+b'\xff\xff\xff'*width)*height))+chunk(b'IEND',b'')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);original=ROOT/'build/signac-coverage-gene-demo-NEW';before=files(original)
    assert read(original/'analysis-status.json')['results_admitted'];w,h=struct.unpack('>II',(original/'coverage.png').read_bytes()[16:24])
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/signac_coverage_validation.py']}
    def mutate_text(root,name,old,new):
        p=root/name;text=p.read_text(encoding='utf8');assert old in text;p.write_text(text.replace(old,new,1),encoding='utf8',newline='\n')
    def metadata(root):mutate_text(root,'inputs/cells.tsv','\t100\n','\t101\n');bind_input(root,'inputs/cells.tsv')
    def fragment(root):mutate_text(root,'inputs/s1.fragments','chr1\t98\t103','chr2\t98\t103');bind_input(root,'inputs/s1.fragments')
    def duplicate_generic(root):pass
    changes=[
        ('published-normalization',lambda d:mutate_text(d,'coverage.tsv','\t0.0\t','\t999.0\t')),
        ('complete-cut-table',lambda d:mutate_text(d,'group-cuts.tsv','B\t100\t0','B\t100\t999')),
        ('object-count-input-rebound',metadata),('fragment-input-rebound',fragment),
        ('captured-settings',lambda d:edit(d,'request.json',lambda v:v.update(window=2))),
        ('effective-settings',lambda d:edit(d,'effective-request.json',lambda v:v.update(window=2))),
        ('bundled-source',lambda d:(d/'source/R4.6.1-RNG.c').write_text('altered original source')),
        ('SVG-label',lambda d:mutate_text(d,'coverage.svg','GeneA','Altered')),
        ('SVG-geometry',lambda d:mutate_text(d,'coverage.svg','x1="150.0"','x1="151.0"')),
        ('PNG-dimensions',lambda d:(d/'coverage.png').write_bytes(white_png(w+1,h))),
        ('gene-model',lambda d:mutate_text(d,'annotation-labels.tsv','GeneA','Altered')),
        ('source-NA-scaling-slot',lambda d:mutate_text(d,'group-scaling.tsv','\ttrue\t0\t0.0','\ttrue\t0\t1.0')),
        ('removed-warnings',lambda d:(d/'WARNINGS.md').write_text('No warnings.\n')),
        ('diagnostics',lambda d:edit(d,'diagnostics.json',lambda v:v.update(sampled_positions=999))),
        ('execution-authentication-claim',lambda d:edit(d,'manifest.json',lambda v:v.update(execution_authentication=True))),
        ('PNG-replay-claim',lambda d:edit(d,'COMPLETED.json',lambda v:v.update(raster_font_replay=True))),
        ('unlisted-member',lambda d:(d/'source/unlisted.txt').write_text('unlisted')),
        ('unlisted-empty-directory',lambda d:(d/'source/unlisted').mkdir()),
        ('generic-path-alias',duplicate_generic),
    ]
    runs=[]
    try:
        for name,mutate in changes:
            target=out/(name+'-TEST-ONLY');shutil.copytree(original,target);mutate(target)
            reseal(target,(lambda v:v['output_sha256'].update({'inputs\\cells.tsv':v['output_sha256']['inputs/cells.tsv']})) if name=='generic-path-alias' else None)
            request=out/(name+'-verify.json');save(request,dict(schema_version=1,mode='VERIFY',analysis=target.name));invoke(out,name,request,2)
            runs.append(dict(control=name,expected_exit_code=2,files=files(target)))
        target=out/'white-PNG-TEST-ONLY-NOT-A-SCIENTIFIC-FIGURE';shutil.copytree(original,target);(target/'coverage.png').write_bytes(white_png(w,h));reseal(target);verify(out,'white-PNG-limit',target)
        (out/'PNG-PIXEL-LIMIT-WARNING.md').write_text(
            '# TEST ONLY: WHITE PNG IS NOT A SCIENTIFIC FIGURE\n\n'
            'This fixture replaces the PNG preview by a valid blank white image with identical dimensions, '
            'then rebuilds transaction hashes and the capture seal. All three Java verifiers accept it '
            'because numeric/model/SVG data is unchanged and PNG pixels are not independently rerendered '
            'across platform fonts. Use the replayed SVG for audited geometry. Never interpret this TEST-ONLY '
            'blank PNG as measured accessibility. Hashes and consistency do not authenticate execution or biology.\n',encoding='utf8')
        assert pin(JAR)==artifact and files(original)==before and all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-resealed-native-coverage-refusals-with-demonstrated-PNG-pixel-limit',
             artifact=artifact,source_pins=sources,semantic_refusals=19,runs=runs,original_preserved=True,
             unchanged_dimension_PNG_pixel_forgery_can_pass=True,forgery_verifications=3,forged_PNG_is_scientific_figure=False,
             forged_files=files(target)))
        print('19 resealed numeric/model/SVG/capture corruptions refused; PNG pixel limitation demonstrated on3 JVMs')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_NATIVE_COVERAGE_INTEGRITY',error=str(e)));raise


if __name__=='__main__':main()
