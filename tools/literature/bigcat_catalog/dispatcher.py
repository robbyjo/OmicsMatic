# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Review exactly six literal help lines; never overlay executable scientific code."""
from contextlib import contextmanager
from pathlib import Path
import hashlib,json,os,stat,uuid
from catalog_source_integrity import reverse_delta,verify_source
from run_mrmega_catalog_checks import ArchivedCatalogView

PATH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
DECLARATION='tools/literature/bigcat_catalog/cli-help-review.json'
COMPATIBILITY='docs/literature/omics-2023-2026/bigcat-cli-help-shared-source-compatibility.json'
BEFORE='cb14367c079da29ce25c4416f37dc9c5f06b690033eda1a34bfa3349864ae47a'
AFTER='9b719bfee9a611903fc02a51be53647772e0195547631d88c736c5cd760c5687'
INSERTION=(b'              --adjust-omics NAME=FILE      Mapped adjustment matrix; repeat for multiple sources\n'
 b'              --adjust-map FILE             CSV/TSV: feature_id,source,adjustment_id\n'
 b'              --adjust-transform NAME=SPEC  Transform one adjustment source; repeat for stages\n'
 b'              --omics-response              Fit omics ~ phenotype + covariates + mapped features\n'
 b'                                            Formula still names phenotype on the left; Gaussian only\n'
 b'                                            Adjusted scans refit OLS/GLM/LMM/GLMM on complete cases\n')

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def review(raw,document):
    if document.get('schema_version')!=1 or document.get('status')!='passed-dispatch-catalog-only-source-deltas' or len(document.get('paths',[]))!=1:
        raise ValueError('Unreviewed help-only source relationship')
    row=document['paths'][0]
    if (row['path'],row['before_sha256'],row['after_sha256'],row['before_bytes'],row['after_bytes'])!=(PATH,BEFORE,AFTER,50011,50587) or len(row['edits'])!=1:
        raise ValueError('Only the exact six-line help addition is reviewed')
    if pin(raw)!=dict(bytes=50587,sha256=AFTER):raise ValueError('Live CLI differs from the exact reviewed help state')
    edit=row['edits'][0];lines=raw.splitlines(keepends=True)
    if (edit['after_start_line'],edit['after_end_line'],edit['before_bytes'])!=(675,681,0) or b''.join(lines[675:681])!=INSERTION:
        raise ValueError('Source change is not exactly the reviewed literal help insertion')
    restored=reverse_delta(raw,row)
    if pin(restored)!=dict(bytes=50011,sha256=BEFORE) or restored[:len(b''.join(lines[:675]))]!=b''.join(lines[:675]):
        raise ValueError('The full pre-help dispatcher did not reconstruct exactly')
    return restored

def declaration(root):return json.loads((root/DECLARATION).read_bytes())

def reviewed_root(root):
    document=declaration(root)
    if json.loads((root/COMPATIBILITY).read_bytes())!=document:raise ValueError('Exact help-only compatibility declaration differs')
    raw=(root/PATH).read_bytes();restored=review(raw,document)
    # verify_source follows exact approved dispatcher inverses from the real
    # live file, independently of this read-only literal-help view.
    verify_source(root,root/'docs/literature/omics-2023-2026',PATH,BEFORE)
    return ArchivedCatalogView(root,{PATH:restored}),dict(path=PATH,current=pin(raw),pre_help=pin(restored),
      removed_literal_help_lines=6,existing_executable_bytes_unchanged=True,
      scientific_sources_overridden=False,live_source_file_written=False)

@contextmanager
def private_help_fixture(root):
    """Use exact historical literal help only in an isolated linked worktree.

    Some old modules reopen their root rather than preserving an input view.
    Both complete files and restoration receipts stay retained. Scientific
    executable bytes, shared checkout, old verifiers and hashes are unchanged.
    """
    actual=Path(str(root)).resolve()
    if not (actual/'.git').is_file():
        raise OSError('Cumulative legacy checks require an isolated linked Git worktree with the qualified legacy CLI; never change help text in the shared checkout')
    _,report=reviewed_root(root);path=actual/PATH
    if path.is_symlink() or path.resolve()!=path:raise ValueError('Private help fixture source must be a canonical regular file')
    original=path.read_bytes();historical=review(original,declaration(root));mode=stat.S_IMODE(path.stat().st_mode)
    out=actual/'build/literature-review/monocle-runtime'/('bigcat-private-help-fixture-'+uuid.uuid4().hex)
    out.mkdir(parents=True,exist_ok=False)
    for name,raw in [('current-source.bin',original),('historical-help-input.bin',historical),
      ('historical-for-active.java',historical),('current-for-restore.java',original)]:
        with (out/name).open('xb') as stream:stream.write(raw)
        os.chmod(out/name,mode)
    def record(name,value):
        with (out/name).open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
    record('STARTED.json',dict(status='EXACT_SIX_LITERAL_HELP_LINES_PRIVATE_AUDIT_FIXTURE',
      current=pin(original),historical=pin(historical),path=PATH,scientific_executable_bytes_changed=False))
    os.replace(out/'historical-for-active.java',path)
    try:
        yield root,dict({k:v for k,v in report.items() if k!='live_source_file_written'},
          private_help_fixture_used=True,shared_checkout_written=False,
          private_help_source_temporarily_written=True,restoration_required=True)
    finally:
        if path.read_bytes()!=historical:
            record('RESTORATION-CONFLICT.json',dict(status='FAIL2_PRIVATE_HELP_FIXTURE_CHANGED_CONCURRENTLY',
              current=pin(path.read_bytes()),required_action='Preserve the concurrent file and both complete source inputs; review before restoring anything. No catalog result admitted.'))
            raise ValueError('Private help fixture changed concurrently; both complete inputs retained, no overwrite or catalog result admitted')
        os.replace(out/'current-for-restore.java',path)
        if path.read_bytes()!=original:raise ValueError('Exact private help source restoration failed; no catalog result admitted')
        record('RESTORED.json',dict(status='PASS_EXACT_PRIVATE_HELP_SOURCE_RESTORED',current=pin(original),
          scientific_executable_bytes_changed=False,shared_checkout_written=False))
