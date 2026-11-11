"""Source-reviewed exact CPU scvi-tools workflows and explicit recovery boundaries."""
import json
from catalog_source_integrity import verify_source


def scvi_metadata(root, data):
    proof = json.loads((data / 'scvi-validation.json').read_text(encoding='utf-8'))
    roles = json.loads((data / 'scvi-role-coverage.json').read_text(encoding='utf-8'))
    assert proof['status'] == 'passed-scoped-scvi-original-CPU-public-workflows-and-source-roles'
    assert proof['tests_per_JVM'] == 146 and proof['JVM_profiles'] == ['Windows21', 'Windows25', 'Linux21']
    assert proof['public_model_workflow_invocations'] == 9 and proof['original_model_stages'] == 13
    assert proof['latent_rows'] == 272 and proof['latent_values'] == 816 and proof['classification_rows'] == 104
    assert proof['fresh_hash_locked_runtime_admitted'] and proof['source_role_reviews_applied']
    assert len(roles['roles']) == 11 and roles['historical_release_substitution_forbidden']
    assert not proof['whole_paper_reproduced'] and not proof['biological_accuracy_certified']
    for item in proof['source_evidence']:
        verify_source(root, data, item['path'], item['sha256'])
    return dict(
        implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/ScviToolsCli.java',
        implementation_scope=SCVI_SCOPE,
        validation_note=SCVI_VALIDATION,
        coverage_note='The pre-expansion inventory lacked this workflow. Current source supplies a validated scoped original1.3.0 CPU adapter with native admission/audits;historical environments and complete paper reconstruction remain explicit restrictions.',
        project_adapter_available=True,
        canonical_adapter_available=False,
        runtime_admission_available=True,
        development_adapter_cli='scvi-tools',
        development_candidate=proof['candidate'],
        development_candidate_sha256=proof['candidate_sha256'],
        workflow_validation='scvi-validation.json',
        source_role_coverage='scvi-role-coverage.json',
        source_roles_reviewed=11,
        runtime_profiles=['scvi-tools-1.3.0-linux-x86_64-py3123-cpu'],
        whole_paper_reproduced=False,
        native_helpers=['src/main/java/org/jlinalg/singlecell/ScviGeneProjection.java', 'src/main/java/org/jlinalg/singlecell/ScviLikelihood.java', 'src/main/java/org/jlinalg/cli/ScviMatrix.java', 'src/main/java/org/jlinalg/cli/ScviCovariates.java', 'src/main/java/org/jlinalg/cli/ScviModelMetadata.java', 'src/main/java/org/jlinalg/cli/ScviOutputAudit.java'])


SCVI_SCOPE = (
    'Original scvi-tools1.3.0 Linux x86_64 Python3.12.3 CPU CHECK/FIT/TRANSFER/QUERY through scvi-tools. '
    'Raw-count SCVI and SCANVI reference/query annotation remain distinct from signed-expression SysVI with normal or Vamp priors. '
    'Native Java admits complete matrices/covariates,applies shared genes before reference fitting or explicit fixed-reference padding/removal, '
    'preserves every cell and typed learned category,checks all latent/probability/label/split/history outputs and complete saved tensor state, '
    'and requires original strict reloads. Explicit source/SHA256 review is required before pickle loading;no third-party model was opened by validation. '
    'Whole sibling-stage workspace accounting,owned process cleanup,source/executable fingerprints and prominent SKIP3/FAIL2 recovery are implemented. '
    'A fresh private install of82hash-checked official artifacts passes pip check and live health. '
    'Historical1.0.3/1.2.0 or Python3.10 runtimes,GPU,automatic preprocessing/HVG/orthotype conversion,other scvi-tools models,popV as a whole, '
    'biological accuracy,convergence and full-paper parity remain open. Read SCVI_DEVELOPMENT.md and examples/scvi-workflow/README.md.')

SCVI_VALIDATION = (
    '146exact-package tests per WindowsJava21/25 and LinuxJava21,0skips,strictJava17/Javadoc;9actual original public model workflow invocations '
    'cover13stages/272latentrows/816float32values/104classificationrows,including two exact complete-output/history/split replays. '
    'Numeric references preserve integer class order2,1 and four explicit field renames;known-label NB/logistic-normal SCVI and a custom SCANVI classifier pass. '
    'Fresh82artifact runtime health and actual combined-sibling capacity failure/cleanup pass. '
    'The initial controller source-guard failure is retained;all five frozen-JAR analyses passed,then803members and all output values were independently audited '
    'after exact source restoration. Eleven source roles retain version/layer/model ambiguity. No release or whole-paper reproduction is claimed.')

SCVI_WARNING = (
    '**SCVI INPUT,MODEL TRUST AND VERSION REQUIRE ACTION:** use the exact admitted1.3.0 Linux x86_64 CPU profile and Linux Java inside WSL on Windows. '
    'Supply reviewed raw counts for SCVI/SCANVI or signed/transformed expression for SysVI;no automatic normalization,HVG selection or filtering occurs. '
    'Review every gene/cell/covariate conversion and original warning. Saved pickle models require genuine-source review and an exact SHA256;hashes are integrity,not authenticity. '
    'Historical1.0.3/1.2.0,Python3.10,GPU and unsupported model capabilities are not substituted. Missing,forbidden,unsupported or untrusted requirements leave dependent analysis skipped3. '
    'Input,integrity,capacity and original execution failures return2,retain diagnostics and have no successful root manifest. '
    'Original SysVI automatic VampPrior uses gene count for its cell-index bound;retain failures or explicitly review different in-range indices,never silently clamp. '
    'SysVI disables KL warmup;declare NONE. Tiny one-epoch examples do not establish convergence or biological validity. Read SCVI_DEVELOPMENT.md and examples/scvi-workflow/README.md.\n\n')

SCVI_ACTIONS = (
    '<section id="scvi-actions" class="callout" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere;scroll-margin-top:10rem"><h2>scVI: setup,source trust and required action</h2>'
    '<p><strong>Use the current source JAR and exact admitted1.3.0 Linux x86_64 CPU profile.</strong> '
    'The <a href="https://github.com/robbyjo/OmicsMatic/blob/codex/analysis-pipeline/examples/scvi-workflow/README.md">documented82artifact private setup and recipes</a> require permitted download/install. '
    'Set the exact Python path and run <code>scvi-tools --recipe FILE.json --out NEW_DIRECTORY</code>. '
    'When installation/execution is forbidden,supply a permitted compatible runtime or keep the affected analysis <strong>skipped3</strong>.</p>'
    '<p>Review raw counts versus signed SysVI expression,gene/cell/covariate ledgers and genuine saved-model source before permitting pickle loading. '
    'Historical1.0.3/1.2.0,Python3.10 and GPU are not substituted. Automatic SysVI VampPrior retains the original gene-count cell-index bound; '
    'do not silently clamp or repair it. SysVI requires explicit NONE warmup.</p>'
    '<p><strong>Read root WARNINGS.md and analysis-status.json.</strong> CHECK performs no user study analysis. '
    'Failure2 retains diagnostics and has no successful root manifest. Correct the named cause and use a new output path. '
    'See <a href="https://github.com/robbyjo/OmicsMatic/blob/codex/analysis-pipeline/docs/literature/SCVI_DEVELOPMENT.md">scope and validation</a>. One-epoch examples do not establish convergence or biological accuracy.</p></section>')
