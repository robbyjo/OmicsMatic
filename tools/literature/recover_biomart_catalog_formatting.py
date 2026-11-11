# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Restore only original presentation newline styles and refresh complete catalog hashes; retain the failed stale-manifest gate."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';names=['docs/literature/omics-2023-2026/'+n for n in ['tool-inventory.json','summary.json','catalog.md','implementation-plan.md','README.md','WARNINGS.md']]+['site/literature-review.html','site/literature-warnings.html'];reports=[]
    for name in names:
        path=ROOT/name;raw=path.read_bytes();baseline=subprocess.run(['git','show','HEAD:'+name],cwd=ROOT,capture_output=True,check=True).stdout;assert baseline.count(b'\r\n') in [0,baseline.count(b'\n')];style='CRLF' if baseline.count(b'\r\n') else 'LF';normalized=raw.replace(b'\r\n',b'\n');updated=normalized.replace(b'\n',b'\r\n') if style=='CRLF' else normalized;assert updated.replace(b'\r\n',b'\n')==normalized;before=pin(path);snapshot=out/'before'/name;snapshot.parent.mkdir(parents=True,exist_ok=True);snapshot.write_bytes(raw);path.write_bytes(updated);reports.append(dict(path=name,baseline_style=style,before=before,after=pin(path),presentation_only=True))
    raw=(data/'SHA256SUMS').read_bytes();(out/'before-SHA256SUMS').write_bytes(raw);baseline=subprocess.run(['git','show','HEAD:docs/literature/omics-2023-2026/SHA256SUMS'],cwd=ROOT,capture_output=True,check=True).stdout.decode('utf8');hashes={p.name:pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'};order=[r.split('  ',1)[1] for r in baseline.splitlines() if r.split('  ',1)[1] in hashes];assert len(order)==len(set(order));order += [n for n in hashes if n not in set(order)];(data/'SHA256SUMS').write_bytes(''.join(hashes[n]+'  '+n+'\n' for n in order).encode('utf8'));assert all(pin(data/n)['sha256']==v for n,v in hashes.items());save(out/'COMPLETED.json',dict(status='passed-presentation-only-newline-restoration-and-complete-catalog-checksums',files=reports,checksum_manifest=pin(data/'SHA256SUMS'),catalog_files=len(hashes),source_or_semantic_data_changed=False,failed_gate_preserved='catalog-scientific-gates-1',whole_goal_complete=False));print('Restored original presentation newline styles; refreshed',len(hashes),'catalog hashes; no semantic data/source changes')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact presentation-only recovery checks')
    main()
