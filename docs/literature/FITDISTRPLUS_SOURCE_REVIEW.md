# fitdistrplus source role and Cicero integration update

The fixed 250-paper cohort now contains **390 named tools, 2,450 paper–tool observations, 918 reviewed uses and309 supporting paragraph pointers**. All917 prior reviews and2,449 observations are preserved. One newly registered tool, fitdistrplus, was recovered in P043. Every XML hash, DOI, primary observation pointer and reviewed support pointer is audited; paper-wide semantic completeness remains open.

There are **76 new scoped integrations: 17 native subsets and59 upstream adapters**, or79 including three existing adapters. The missing-tool count is255. This update credits the native normal-fitting subset and Cicero's validated original fitting/native prediction-reuse workflows. These counts describe the stated supported operations, not whole packages, historical paper execution, all author-specific pipeline steps or biology. The full implementation goal remains active.

## Primary fitdistrplus role

P043, Nature ([DOI](https://doi.org/10.1038/s41586-023-06824-9), PMC10719105), Methods paragraph51, SHA-256 `41db6565b9c28d56ef328ce411a63865d3f6a88dfa070f1c94457546ac40598e`, fits a normal distribution to shuffled co-accessibility scores to form subclass backgrounds before the reported BH/FDR procedure. The function, estimator, package version and shuffle implementation are unknown. The source's sentence says scores significantly larger than background were filtered out; no reversed retention rule is inferred.

The first exploratory raw-text name search missed citation94 attached to the software name. The failed attempt remains retained. The corrected scan checks citation-stripped and literal forms across all250 hash/DOI-verified primary XML texts and finds this one role. The registry extension is separate; all425 base entries and the two earlier Cicero/py-monocle entries remain byte-identical.

The [native normal guide](NORMAL_FIT_WORKFLOWS.md) describes unweighted analytical normal-mle and optional explicit normal-tail/BH testing of complete supplied vectors. Java runs it without installing dependencies. It retains every row and reports a declared cutoff flag without filtering. Original fitdistrplus1.2-6 controls include86 actual MLE/MME calls and36 tail/BH families; five original source bodies/formals are checked before and after execution, with25,602 existing library files unchanged. All original errors and warnings remain retained.

**Estimator differences are explicit.** Original default MLE fails on tiny-scale and nearly constant samples, while the analytical method has a finite positive-variance solution. Original all-zero MLE returns SD0.1 with warnings and moment matching returns zero SD/infinite likelihood. Native fitting refuses zero variance. This is a supported analytical normal-fitting subset, not original optimizer/Hessian/covariance, weighted/censored, bootstrap, other distributions or full-package equivalence. No paper estimator is inferred.

## Cicero's two reported tool roles

The preserved reviews distinguish P043's co-accessibility fitting from P153's supplied-prediction reuse ([DOI](https://doi.org/10.1038/s41588-026-02695-8), PMC13447104, Methods paragraph54, SHA-256 `7127ad8496e4253a67c941a3959d37f382490c354b00b13ed0ba61e663d1cf88`). P153 applies accessibility-only subsetting for Cicero; its detected-gene rule for other predictors is not imposed on Cicero. Prediction release, exact coordinate/overlap and endpoint choices remain unknown and explicit in user requests.

The [original fitting adapter](CICERO_COACCESSIBILITY_DEVELOPMENT.md) executes Cicero-Monocle3 1.3.9 with explicit raw counts, prepared coordinates and genome lengths. Complete aggregation/calibration/model/connection objects match independent original controls, and native audits cover every connection. The [Java prediction subsetter](CICERO_PREDICTION_SUBSET_DEVELOPMENT.md) preserves every selected original row byte and independently checks complete accessibility memberships. This completes scoped availability for those two reported tool operations. Author-specific shuffle construction, threshold interpretation, TSS annotations, historical environments, CCANs and gene activity remain separate. Older sealed guides retain their checkpoint counts; use this current source review and summary.json for current availability.

**SKIP3:** original fitting needs the exact permitted Windows/R profile. If unavailable, forbidden or mismatched, skip the dependent analysis, retain the warning and provision only where permitted. Java prediction reuse and analytical normal fitting require no R runtime. Analysis installs nothing and does not silently substitute editions or methods.

**FAIL2:** invalid inputs, failed original execution, failed audits, capacities or heap/storage failures admit no atomic result. Preserve status, WARNINGS, complete original objects and unadmitted partials; correct the named cause and retry NEW paths. Native normal fitting does not omit original Cicero NA values. Original unresolved connections, precision asymmetry, opaque prediction scores and linear probability underflow remain visible with their limitations. Do not repair identifiers, recode values, silently remove rows or interpret partial/empty output as biological absence.

## Verification and retained boundaries

```powershell
python tools/literature/normal_fit_evidence.py
python tools/literature/audit_fitdistrplus_current_catalog.py
python tools/literature/verify_fitdistrplus_review_context.py
python tools/literature/verify_fitdistrplus_catalog.py
```

The primary-pointer audit requires the original local XML/extraction cache. The current-context entry point runs unchanged MEME and ChIPseeker scientific gates with only their metadata review callback bound to the exact917 preserved reviews plus one specified addition. It rechecks the earlier898→905→917 derivations, source guards and approved byte inverses. Frozen verifiers, scientific functions, fixtures and the base registry remain unchanged. Older standalone catalog entry points retain their historical contracts; use this update's entry point for current metadata.

The final gate verifies the sealed catalog derivation, two exact availability rows, every other preserved tool row, primary roles, component proofs, warnings and desktop/mobile browser evidence. The canonical executable and18 unrelated local files remain unchanged. There is no release or website deployment; the unfinished literature pages remain excluded from Pages. Cell Ranger stays excluded. The py-monocle license preference remains pending and no restricted runtime was installed or executed. Hashes establish consistency, not authorship, execution authenticity or biological validity.
