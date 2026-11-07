# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve only new MEME proof/example bytes across Git checkouts; retain an exact attribute-file inverse."""
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from wire_signac_catalog import delta


def main():
    data=ROOT/'docs/literature/omics-2023-2026';record=data/'meme-git-bytes-shared-source-compatibility.json'
    out=ROOT/'build/literature-review/meme-runtime/git-byte-intake-1'
    assert not record.exists() and not out.exists()
    manifest=data/'SHA256SUMS';lines=manifest.read_text(encoding='utf8').splitlines()
    assert all(pin(data/name)['sha256']==digest for digest,name in (line.split('  ',1) for line in lines))
    path=ROOT/'.gitattributes';before=path.read_bytes();newline=b'\r\n' if b'\r\n' in before else b'\n'
    rules=[b'docs/literature/meme-discovery-workflow-development/** -text whitespace=cr-at-eol',b'examples/meme-discovery/** -text whitespace=cr-at-eol']
    assert all(rule.split(b' ')[0] not in before for rule in rules)
    after=before+newline+b'# Preserve the new byte-pinned MEME workflow proof and example sources.'+newline+newline.join(rules)+newline
    out.mkdir();(out/'attributes-before').write_bytes(before);(out/'SHA256SUMS-before').write_bytes(manifest.read_bytes())
    save(record,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='meme-checkout-byte-preservation',
        scope='Only two new MEME proof/example namespaces disable newline conversion. Every prior attribute byte reconstructs exactly; no scientific code or historical evidence is changed.',
        paths=[delta('.gitattributes',before,after)]))
    assert path.read_bytes()==before;path.write_bytes(after)
    manifest.write_text('\n'.join(lines)+'\n'+pin(record)['sha256']+'  '+record.name+'\n',encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',dict(status='scoped-new-MEME-checkout-byte-protection',before=pin(out/'attributes-before'),after=pin(path),compatibility=pin(record),catalog_checksums=len(lines)+1,
        no_scientific_source_change=True))
    print('Protected two new MEME namespaces from newline conversion; prior attributes and all scientific bytes retained')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/checksum preservation checks')
    main()
