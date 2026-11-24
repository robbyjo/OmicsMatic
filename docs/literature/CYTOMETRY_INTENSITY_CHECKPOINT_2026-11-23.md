# Native cytometry intensity checkpoint, 2026-11-23

The new `flowjo-tools SUMMARIZE` operation computes explicit unbinned summaries
after freshly reproducing every membership/count of a sealed native gate parent.
The [command guide](../CYTOMETRY_INTENSITY.md) includes copyable requests, exact
definitions, measurement domains, undefined statuses and failure remedies.

**This is a component milestone. Canonical catalog admission remains pending.**
Totals remain **157 scoped families (29 native / 128 upstream), 1,714 reviewed /
1,010 pending uses, 2,724 observations and 250 papers**. No additional vendor
family or paper-tool review is credited by this checkpoint.

The initial build passed 1,910 selected tests; the final producer passed 76 targeted
tests. All 23 packaged sample cases on each host pass summarize, verify and replay;
each scientific pass freshly checks 53,019,143 event/gate pairs. Windows and Linux
export identical statistics and all 16,312,934 selected measurement values per pass.
Independent FlowKit preprocessing/compensation and saved reference populations
check those values; R checks 855 statistics both on independent values and on the
exact native values. All native counts, nonpositive counts and statuses match the
latter R check. Twenty-five Windows controls and five Linux controls pass, including
checksum-consistent forged parents that pass byte verification but fail fresh gate
or saved-count checks. The final integrity extension preserves all mathematical and
gate class payloads and reproduces all previously qualified scientific exports.

**Numerical differences are retained.** Among 910 artificial numerical rows, 908
agree with raw R within the recorded tolerance. Raw R 4.6.1 SD and CV underflow at
the `1e-200` scale; Java's nonzero results agree with a separate scaled R calculation.
Native SVD versus FlowKit solver roundoff yields 513 selected sign-boundary
comparisons, all within `4.59e-13` of zero, with original values/counts preserved.
The failed prototype's percentile-index rounding and the first control driver's
existing-output assertion are retained. No historical source hashes are refreshed.

**Vendor statistics remain unqualified.** Unbinned measurement summaries do not
establish agreement with FlowJo's transform-dependent binned statistics or its
off-scale Robust SD handling. Geometric mean requires every native value to be
strictly positive; no event is dropped, clipped, replaced or snapped to zero.
Saved vendor-count disagreement requires an explicit native-population policy.
Missing inputs/unsupported methods produce prominent SKIP3 with the required action;
integrity, membership and capacity failures produce FAIL2 and no complete result.

The [44 prepared source-role records](cytometry-source-roles-2026-11-23.json) cover
27 FlowJo and 17 FACSDiva paper/tool pairs, based on 48 paragraphs in 33 papers
after checking all 250 frozen XML/DOI identities. They distinguish vendor editions,
instrument acquisition/sorting, jointly attributed analysis, intensity statistics,
plots, cell-cycle fits, plugins and software-only declarations. They are not yet
applied to the canonical review ledger.

Read the packet without rerunning analysis:

```powershell
python tools/literature/verify_cytometry_intensity_checkpoint.py
```

Optional `--jar`, `--private-root` and `--cache` check the exact producer, retained
private evidence and fresh frozen XML. Private root is
`build/fu-1/build/cytometry-intensity-629` in the main checkout. Keep the final JAR
at `normal-build-3/project-build/cli/omicsmatic-0.4.0.jar`, SHA-256
`afa06f981e84a0cfc9ec20d14304f770a9f716c65f4be13b00ac7bd53cb13405`.
The previous exact gate producer and its physical pre-extension source context
remain retained; use that historical context for its unchanged reader.

Next work is guarded source-role/catalog admission, with explicit partial coverage
and unresolved operations, followed by rendered report checks and higher-impact
remaining families. Do not credit FlowJo 9, Dean-Jett-Fox/Watson models, FlowSOM/
UMAP/ClusterExplorer plugins, acquisition or vendor figures through this component.
Keep literature Pages publication on hold. Preserve original/failed scientific
evidence, exact JARs, permitted runtimes, other owners/worktrees and restricted refs.
The full 250-paper objective remains active.
