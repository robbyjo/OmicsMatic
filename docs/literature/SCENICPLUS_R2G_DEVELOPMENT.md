# SCENIC+ region-to-gene development component

`org.jlinalg.cli.ScenicPlusToolsCli` is a separate Java entry point for original
SCENIC+ v1.0a2 GBM region-to-gene inference on prepared paired matrices and an
explicit search space. The project supplies its own controller and API caller;
it includes no original SCENIC+ source or runtime. This is one component of the
unfinished full eGRN integration, with **zero family or paper-review credit**.
The global dispatcher and canonical catalog are unchanged. Qualification of
normal builds and packaged original comparisons is tracked in private development
evidence and the separate component checkpoint; the presence of this source alone
does not establish those outcomes.

**Known original masked-call failure:** the artificial masked Pearson case fails
with a boolean-index shape error in unchanged SCENIC+ v1.0a2, independently in the
original API and through this controller. The failed capture is FAIL2, not a
qualified masked analysis. Preserve it and obtain a vendor-supported correction
or separately qualify another permitted upstream edition before retrying that
operation. Do not patch the licensed source, reshape data to hide the error or
silently disable the requested masking. Unmasked Spearman and Pearson are
separate tested cases.

Start with [the artificial example](../../examples/scenicplus-r2g/README.md)
and [the full-goal handover](HANDOVER_SCENICPLUS_2026-11-23.md). All 250 papers
are collected; current admitted totals remain 155 scoped families, 1,702
reviewed and 1,020 pending uses. Source-bound paper editions remain separate
from this available v1.0a2 runtime profile.

## Required environment and private captures

The user already accepted the [v1.0a2 license](https://github.com/aertslab/scenicplus/blob/v1.0a2/LICENCE.txt)
for an unmodified private academic runtime. Preserve its copying, transfer,
modification, citation and indemnification terms. Do not publish the original
tool, runtime/cache, notebooks or source-bearing traces. **All captures from this
component are private**, including failures that may embed vendor source lines.
Public source contains only our caller, synthetic data, parameter metadata and
original-source SHA-256 identities. Do not treat older whole-WSL transfer advice
as permission to transfer this licensed runtime to another recipient.

Use a complete source-built JAR and the exact permitted Linux x86_64 Python
3.11.8 environment. `SOURCE-PROFILE.json` pins the dependency versions and
41 original Python source members at SCENIC+ commit
`86fa25e6819919e2a824c14f7fd0d6e01e257ae9`. Its 228 installed distributions and
231 metadata bindings after selected imports are distinct: Ray exposes bundled
`setproctitle`, `colorama` and `psutil` metadata during import. There are 229
unique post-import names; duplicate names retain separate origins and metadata
identities. Both full populations and 73 Python source files in the four pinned
Git dependencies are checked. No extra package installation is requested.

Runtime and references are explicit local inputs. The command never downloads
references, installs software, infers an unreported paper edition or silently
changes the method. If required installation is forbidden or unavailable, the
dependent analysis remains **SKIP3** with the missing prerequisite and remedy.
No new approval is needed for terms already accepted in this session.

## Inputs and original behavior

Schema1 RUN requires `mode`, `profile=scenicplus-1.0a2-r2g-v1`,
`operation=REGION_TO_GENE`, `python`, `out`, the three `inputs` below and explicit
`options` containing `correlation` (`SR` or `PR`) and Boolean `mask_expr_dropout`.
Relative paths resolve against the request file. `out` must be NEW under an
existing physical directory. Unknown request fields are rejected.

| Input | Contract |
| --- | --- |
| `expression` | Literal UTF-8 cell-by-gene TSV; first header `cell`; unique nonempty cells and gene labels; at least two cells and one gene; finite float64 values. |
| `accessibility` | Same format, with region columns. Cell labels and their order must exactly match expression. No implicit pairing, sorting, normalization or downsampling. |
| `search_space` | Exact `Name`, `Gene`, `Distance` TSV header. Unique region/gene pairs naming supplied features; canonical signed int64 distances. No automatic genome lookup or distance construction. |

Control characters, duplicate labels, ragged rows, unknown candidate features,
nonfinite prepared inputs and capacity overruns fail explicitly. Literal `NA`
and leading-zero labels are preserved. The finite-input contract does not make
constant-vector output correlations defined.

The caller uses the original public `calculate_regions_to_genes_relationships`
API with GBM's original default importance options, the requested original
correlation/dropout settings, one worker and distance output enabled. It does
not infer an author seed or expose a synthetic replacement regression. The
actual resolved importance parameters are captured in `invocation.json`.

`adjacencies.tsv` retains the original seven fields and row order.
`scientific.json` additionally retains dtypes and hexadecimal float64 values,
including nonfinite values and signed zero, for complete fresh comparisons.
`field-ledger.json` flags each numerical field's finiteness and lists unreturned
candidates. A successful whole call does not prove each target was fitted;
the public API does not expose that per-target status. No invented fit status,
silent zero substitution or reportability inference is applied.

## Execution, verification and limitations

Java captures input bytes and the packaged caller/profile. The Python worker
checks original-source membership and runtime versions, then waits for Java's
ACK before the original scientific call. Java fingerprints recorded distribution
locations, the resolved interpreter and stdlib, checks loaded-module coverage,
and repeats the runtime fingerprint after execution. Existing owned-process
guards track root/observed-child identities, deadlines and capture capacities.
The closure excludes `__pycache__` and uses a private bytecode-cache prefix.
It does not enumerate OS loader dependencies, enforce a hard aggregate RAM/job
quota, authenticate the host, or prove absence of changes restored between
observations. The profile is not an author-environment reconstruction.

The source-built JAR's complete hash binds captures to their producer. Loose or
mixed controller classes are refused. Saved manifests bind full capture file
and directory populations; `--inspect` checks that closure and current producer.
It does not repeat upstream science. Both VERIFY and REPLAY require a new,
disjoint witness and actually repeat the original R2G call, then compare complete
scientific frames, raw result TSV, field ledger, invocation, summary and runtime
fingerprint with the read-only parent. SHA-256 supplies integrity, not execution
authentication against a coordinated forgery.

**Read warnings and act:** SKIP3 means the dependent analysis was skipped;
restore the named permitted prerequisite or retain the skip. FAIL2 means no
complete result was admitted; preserve raw/partial/failed evidence, resolve the
specific input, runtime, numerical, deadline or integrity cause and retry fresh
paths. Undefined correlations and derived fields remain in the raw output and
ledger even when the original call returns. Never refresh historical hashes,
repair identifiers or replace NaN/Inf to make a capture pass.

Remaining family work includes TF-to-gene and full eGRN inference, motif/cistrome
references, activity scoring under this dependency profile, original unpaired
metacell construction and resource/Cluster-Buster roles. Guarded global dispatch,
full qualification and six draft paper-role reviews remain separate requirements.
P153's direct-call source evidence is retained but its missing observation has
not yet been added; Supplementary Note 2 remains unread. The 33 pre-existing
website citation-panel failures remain open and both literature reports remain
withheld from Pages. Cell Ranger remains excluded by user instruction.
