#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render every original/reference plot page and retain exact pixel comparisons."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess
from PIL import Image,ImageChops,ImageDraw
from pypdf import PdfReader

def pin(path):
    raw=Path(path).read_bytes()
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--batch',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    args=p.parse_args();batch=args.batch.resolve();out=args.out.absolute()
    names=['constellation-prepared-sqrt','constellation-prepared-log10','constellation-original-neighbours']
    assert all((batch/name/'EXECUTED.json').is_file() for name in names),'Wait for the same live batch to finish the three plot cases'
    poppler=shutil.which('pdftoppm');assert poppler,'A permitted Poppler renderer is required; skip PDF validation if unavailable'
    assert not out.exists() and not out.is_relative_to(batch)
    out.mkdir(parents=True)
    version=subprocess.run([poppler,'-v'],capture_output=True,text=True)
    (out/'renderer-version.txt').write_text(version.stdout+version.stderr,encoding='utf-8')
    result=[];previews=[]
    for name in names:
        case=batch/name
        left={x.relative_to(case/'reference-workspace').as_posix():x for x in (case/'reference-workspace').rglob('*.pdf')}
        right={x.relative_to(case/'workspace').as_posix():x for x in (case/'workspace').rglob('*.pdf')}
        assert left and left.keys()==right.keys(),name
        for number,relative in enumerate(sorted(left),1):
            record=dict(case=name,relative_path=relative,source_pdfs={},metadata={},renders={})
            images={}
            for side,pdf in [('reference',left[relative]),('adapter',right[relative])]:
                original=pin(pdf);reader=PdfReader(pdf)
                record['source_pdfs'][side]=dict(path=str(pdf),**original)
                record['metadata'][side]={str(k):str(v) for k,v in (reader.metadata or {}).items()}
                target=out/name/('%02d'%number)/side;target.mkdir(parents=True)
                command=[poppler,'-r','110','-png',str(pdf),str(target/'page')]
                child=subprocess.run(command,capture_output=True)
                (target/'renderer.stdout').write_bytes(child.stdout);(target/'renderer.stderr').write_bytes(child.stderr)
                assert child.returncode==0,(name,relative,side,child.returncode)
                files=sorted(target.glob('page-*.png'),key=lambda x:int(x.stem.split('-')[-1]))
                assert len(files)==len(reader.pages)
                images[side]=files;record['renders'][side]=[dict(path=str(x),**pin(x)) for x in files]
                assert pin(pdf)==original,'Original PDF changed during read-only rendering'
            assert len(images['reference'])==len(images['adapter'])
            pages=[]
            for page,(lp,rp) in enumerate(zip(images['reference'],images['adapter']),1):
                with Image.open(lp) as li,Image.open(rp) as ri:
                    li=li.convert('RGB');ri=ri.convert('RGB')
                    equal=li.size==ri.size and li.tobytes()==ri.tobytes()
                    pages.append(dict(page=page,reference_size=li.size,adapter_size=ri.size,
                      complete_RGB_pixels_identical=equal,reference_pixel_sha256=hashlib.sha256(li.tobytes()).hexdigest(),
                      adapter_pixel_sha256=hashlib.sha256(ri.tobytes()).hexdigest(),
                      differing_bbox=None if equal or li.size!=ri.size else ImageChops.difference(li,ri).getbbox()))
                previews.append((name+' / '+relative+' / '+str(page),rp))
            record['pages']=pages;record['all_pages_identical']=all(x['complete_RGB_pixels_identical'] for x in pages)
            result.append(record)
    width,height=360,390;cols=5;rows=(len(previews)+cols-1)//cols
    sheet=Image.new('RGB',(width*cols,height*rows),'#eeeeee');draw=ImageDraw.Draw(sheet)
    for index,(label,path) in enumerate(previews):
        x=index%cols*width;y=index//cols*height
        with Image.open(path) as page:
            page=page.convert('RGB');page.thumbnail((width-12,height-54));sheet.paste(page,(x+6,y+48))
        draw.text((x+6,y+4),label[:48],fill='black');draw.text((x+6,y+20),label[48:96],fill='black')
    sheet.save(out/'all-adapter-pages-contact-sheet.png')
    report=dict(status='PASS_ALL_ORIGINAL_AND_ADAPTER_PDF_PAGE_PIXELS_IDENTICAL' if all(r['all_pages_identical'] for r in result)
      else 'FAIL_PDF_RENDER_DIFFERENCES_REQUIRE_REVIEW',renderer=str(poppler),dpi=110,pdf_pairs=len(result),
      pages_per_side=sum(len(r['pages']) for r in result),comparisons=result,source_pdfs_modified=False,
      original_metadata_differences_retained=True,new_family_credit=0)
    (out/'PDF-COMPARISONS.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ['status','pdf_pairs','pages_per_side']}))
    return 0 if all(r['all_pages_identical'] for r in result) else 2

if __name__=='__main__':raise SystemExit(main())
