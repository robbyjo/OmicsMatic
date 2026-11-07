# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Expose validated native Salmon-format work while retaining the missing tool badge."""
import hashlib
import json
from pathlib import PurePosixPath
import zipfile
from catalog_source_integrity import verify_source
from catalog_fixed_cohort import verify_fixed_cohort


def sha(raw):return hashlib.sha256(raw).hexdigest()


def salmon_metadata(root,data):
    folder=root/'docs/literature/salmon-native-development';proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['status']=='passed-native-Salmon-inferential-decoder-and-original-development-controls-public-workflow-open'
    checks=dict(line.split('  ',1)[::-1] for line in (folder/'SHA256SUMS').read_text().splitlines());assert set(checks)=={'controls.zip','VALIDATION.json'}
    for name,digest in checks.items():assert sha((folder/name).read_bytes())==digest
    archive=folder/proof['archive']['path'];assert archive.name=='controls.zip' and archive.stat().st_size==proof['archive']['bytes'] and sha(archive.read_bytes())==proof['archive']['sha256']
    with zipfile.ZipFile(archive) as z:
        assert len(z.infolist())==len(set(z.namelist()))==len(proof['archive_files'])==proof['archive']['entries'] and z.testzip() is None
        for name,row in proof['archive_files'].items():
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
            raw=z.read(name);assert len(raw)==row['bytes'] and sha(raw)==row['sha256']
        for path,row in proof['source_pins'].items():
            raw=z.read('source/'+path);assert len(raw)==row['bytes'] and sha(raw)==row['sha256'];verify_source(root,data,path,row['sha256'])
    native=proof['native_validation'];assert len(native['runs'])==3 and all(r['tests']==r['passed']==27 and r['original_values']==31600 for r in native['runs'])
    assert native['unchanged_parent_classes']>100 and len(native['new_classes'])==4
    assert proof['original_reference_validation']['original_quantifications']==25 and proof['original_reference_validation']['complete_quant_rows']==1000
    assert all(row['complete_payload_unchanged'] for row in proof['post_control_runtime_payload_checks']) and len(proof['post_control_runtime_payload_checks'])==5
    roles=json.loads((data/'salmon-role-coverage.json').read_bytes());assert roles==proof['source_role_validation'] and roles['applied'] and roles['reviewed_roles']==8 and roles['total_reviews']==793 and roles['supporting_pointers']==241
    verify_source(root,data,'tools/literature/reviewed-uses.json',roles['reviews_sha256'])
    assert sha((root/'tools/literature/curate_salmon_uses.py').read_bytes())==roles['curator_sha256'] and sha((root/'tools/literature/salmon-use-reviews.json').read_bytes())==roles['draft_sha256']
    papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(root,data,papers)
    by_id={p['paper_id']:p for p in papers}
    for row in roles['source_proof']:
        paper=by_id[row['paper_id']];use=next(t for t in paper['tools'] if t['tool_id']=='salmon')
        assert paper['xml_sha256']==row['xml_sha256'] and use['evidence_paragraph_sha256']==row['paragraph_sha256']
    assert proof['claims']['native_complete_replicate_decoder_available'] and not any(proof['claims'][k] for k in ['typed_public_quantification_command_available','scientific_runtime_admitted','complete_quantification_output_audit_available','Salmon_catalog_promoted','whole_paper_reproduced','biological_accuracy_certified','whole_goal_complete'])
    artifact=root/proof['artifact']['path']
    if artifact.exists():assert artifact.stat().st_size==proof['artifact']['bytes'] and sha(artifact.read_bytes())==proof['artifact']['sha256']
    return dict(implementation_status='missing',implementation_source='src/main/java/org/jlinalg/sequence/SalmonInferentialReplicates.java',
        coverage_note='Eight reviewed Salmon uses and a validated native inferential-count decoder; public quantification integration remains missing.',
        implementation_scope='Native Java SalmonInferentialReplicates streams complete original bulk bootstrap binary64 values with exact UTF-8 targets/order, declared samples, CRC/size/end-of-file, capacity and cancellation checks. Counts are estimates; consumer output must remain staged until the whole receipt succeeds. Exact original Linux 1.0.0, 1.1.0, 1.5.2, 1.8.0, 1.9.0 sources/startup and synthetic reference fits are retained. A typed public quantification command, managed runtime/index/reference/read admission and full quantification output auditing remain unfinished: skip dependent project quantification. Mapping, abundance/bias inference, single-cell alevin, downstream models and whole-paper reproduction are not implemented by this decoder. Read SALMON_DEVELOPMENT.md.',
        validation_note='27 actual-JAR integrity/capacity/cancellation tests pass without skips on Windows Java 21/25 and Linux Java 21; every one of 31,600 original replicate values across 20 complete bootstrap-bearing protocols matches bit for bit on each JVM. All prior packaged Java classes are unchanged. Twenty-five original quantifications retain 1,000 quant.sf rows across five editions and one 40-target synthetic population; this is not native certification of the full quantification or biological accuracy. A four-target positional-bias fixture crashes in four tested original editions; omitting position bias avoids the 1.8.0 crash, while the 40-target case succeeds with that flag unchanged. Exact root cause is unproven. Raw failures, burn-in/fallback warnings, diagnostics and the optional missing-GCC trace are retained. Runtime startup does not admit a user analysis.',
        project_adapter_available=False,canonical_adapter_available=False,native_inferential_decoder_API_available=True,
        public_quantification_CLI_available=False,scientific_runtime_admitted=False,complete_quantification_output_audit_available=False,
        native_development_candidate=proof['artifact']['path'],native_development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../salmon-native-development/VALIDATION.json',source_role_coverage='salmon-role-coverage.json',source_roles_reviewed=8,
        runtime_setup_export_available=False,download_install_available=False,whole_paper_reproduced=False,biological_accuracy_certified=False,paper_exact_unknown_settings_available=False)


SALMON_WARNING='''
## Salmon: native decoder available; project quantification still missing

**SKIP DEPENDENT PROJECT QUANTIFICATION** until a typed public command, managed runtime/index/reference/read admission and complete output auditing pass. Read **SALMON_DEVELOPMENT.md**, **salmon-native-development/VALIDATION.json** and **salmon-role-coverage.json**. Official historical startup checks and 25 synthetic original fits do not admit a user analysis. Older released executables lack the native decoder.

Native **SalmonInferentialReplicates** checks complete original little-endian binary64 bootstrap streams, every exact target/value, gzip CRC/size/end, capacity and cancellation. **Stage consumer callbacks until the complete receipt succeeds.** A prefix or partial quant.sf is not a result. Replicate values are estimates, not observed integer reads. All 27 focused checks and 31,600 original values pass on three JVMs; one 40-target synthetic population is reused, without biological/model/full-paper certification.

**Observed upstream failure:** a four-target positional-bias fixture crashes in 1.0.0, 1.1.0, 1.5.2, 1.8.0. A 1.8.0 diagnostic still fails with 8/32 GiB or one/two threads and without bootstraps; omitting position bias succeeds. The 40-target control succeeds with the same scientific flags. Exact root cause remains unproven. Preserve all failed outputs; do not silently disable a requested bias correction. Original low-fragment burn-in/effective-length fallback warnings remain visible. An optional signal trace was skipped because Linux GCC is absent; that compiler is not required by the decoder or original quantifier.

**Required action:** preserve exact reference/decoy/library/inference choices, all inferential samples and raw logs. **SKIP3:** missing/forbidden runtime, resource or capability means skip dependent analysis and complete permitted setup. **FAIL2:** input, integrity, process, numerical, capacity, cancellation or finalization failure admits no result. Use NEW paths after correction; download/install only where permitted. Do not equate TPM with integer counts (P226) or replace P227's ambiguous percentage denominator. The captured P227 author module specifies ISR, sequence/GC/position bias, VB and 100 bootstraps, but does not authenticate every historic run. Hashes prove integrity, not authenticity. Cell Ranger remains excluded.

'''

SALMON_ACTIONS='''<section id="salmon-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Salmon: quantification remains missing; native decoder and required recovery</h2>
<p><strong>SKIP DEPENDENT PROJECT QUANTIFICATION</strong> until typed execution, managed runtime/index/reference/read admission and complete output auditing pass. Read <code>SALMON_DEVELOPMENT.md</code>, <code>salmon-native-development/VALIDATION.json</code> and <code>salmon-role-coverage.json</code>. Startup checks and 25 synthetic original fits are development evidence, not an admission ticket. Older released executables lack the new decoder.</p>
<p><strong>Native Java decoder:</strong> <code>SalmonInferentialReplicates</code> reads complete original little-endian binary64 bootstrap streams. Every exact name/value, CRC/size/end, capacity and cancellation is checked. <strong>Stage callback output until the complete receipt succeeds.</strong> Truncated families and partial <code>quant.sf</code> files are not results. Values are estimated fragment counts, not observed integer reads. All 27 cases and 31,600 original values pass on each of three JVMs; prior packaged Java classes are unchanged. One 40-target population is reused.</p>
<p><strong>Observed original positional-bias failure:</strong> the four-target fixture crashes in 1.0.0, 1.1.0, 1.5.2 and 1.8.0. The 1.8.0 failure persists with 8/32 GiB and one/two threads and without bootstraps. Removing position bias succeeds; the 40-target control succeeds with that flag unchanged. <strong>Exact root cause is unproven.</strong> Preserve all failed outputs and do not silently disable requested bias correction. Original low-fragment burn-in/effective-length fallback warnings are retained. An optional signal trace was skipped because GCC is absent; it is not a quantifier/decoder dependency.</p>
<p><strong>Required action:</strong> preserve exact references/decoys, library type, inference options, complete samples and raw logs. <strong>SKIP3:</strong> unavailable/forbidden runtime, resource or capability means skip dependent analysis and complete permitted setup. <strong>FAIL2:</strong> input, integrity, process, numerical, capacity, cancellation or finalization failure admits no result. Keep partials and use NEW paths after correction; install/download only where permitted.</p>
<p><strong>Keep units and source uncertainty:</strong> TPM is not raw integer counts; P226's DESeq2 input is unresolved. P227's percentage denominator is ambiguous. Its captured author module declares paired ISR, sequence/GC/position bias, VB and 100 bootstraps, without authenticating every historical run. No biological, model-calibration or whole-paper certificate is claimed. Hashes prove integrity, not authenticity. Cell Ranger remains excluded.</p></section>'''
