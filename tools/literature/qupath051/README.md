# Original QuPath 0.5.1 development runtime

**Development workflow: no catalog badge or paper reproduction.** Setup installs the complete pinned original Windows/Linux release and all 1,097 original source files into a new private directory. The unchanged release includes its own Java 21.0.2 runtime. The standalone Java SCRIPT adapter uses that original engine. See the [complete build, example, source-scope and recovery guide](../../../docs/literature/QUPATH051_WORKFLOW_DEVELOPMENT.md).

The [12 individual primary-source reviews](paper-use-intake.json) distinguish QuPath image analysis from stitching, acquisition, manual ROIs/classifiers and downstream statistics. The selected 0.5.1 edition is reported in P009 and P040. Other papers report 0.6.0, 0.4.1, 0.4.3, a literal 0.2.3-to-0.4.3 range, or no edition. Do not substitute those identities. External extensions, author classifiers/models, stain vectors, image-series choices and manual decisions are separate prerequisites.

## Install where permitted

From the repository root, use native Python on the selected Windows/Linux x86_64 platform:

```powershell
python tools/literature/qupath051/provider.py --allow-install --allow-download --out build/my-new-qupath051-provider
```

To use previously downloaded exact archives without network access, supply their directory:

```powershell
python tools/literature/qupath051/provider.py --source-cache path/to/exact-archives --allow-install --out build/my-new-offline-qupath051-provider
```

The cache must contain the platform's original binary archive and the commit-pinned source archive named in `source-profile.json`. Cache inputs are retained unchanged. Installation verifies complete archive bytes, extracts every original regular file with path checks, verifies the complete source/runtime populations, and probes the original version and script help. It makes no scientific image-analysis calls. All original bundled license files are retained.

`READY.json` identifies the exact original launcher and platform. Read `WARNINGS.md`, `PREFERENCES.json` and the complete process logs. QuPath uses a fresh named preference node and an owned home/cache directory for these probes. On Windows, Java Preferences may store that new node in the current-user registry. Existing QuPath settings are not selected. This is configuration isolation, not an OS security sandbox.

**SKIP3 skips dependent installation/analysis** when a required permitted archive, supported platform, executable filesystem or native runtime prerequisite is unavailable. Supply the named prerequisite only where permitted and retry a NEW output directory. **FAIL2 admits no provider:** retain the full downloads, source, runtime, errors and original process logs; resolve the stated contradiction and retry NEW paths. Incomplete installations remain evidence and must not be relabelled ready.

## Analysis admission remains separate

**An original exit code of zero alone does not prove complete image analysis.** The source can log command-line parse failures and return normally, and project batches can log a per-image exception and continue. The adapter checks selected-format original ERROR headers, an explicit complete-population return marker and every declared output, retaining full original logs and files. It refuses missing/extra results and never accepts a prefix. Trusted Groovy scripts run with the user's account permissions and must propagate failures rather than hide them.

This runtime provides the original full script engine and original Java APIs. Original source and binary payloads are unchanged. No source build or binary/source reproducibility claim is made. The upstream release publishes no digest for these older assets; recorded hashes pin complete official downloads and establish integrity, not producer authentication. Follow the [official command-line reference](https://qupath.readthedocs.io/en/0.5/docs/advanced/command_line.html) for the selected edition. Broad paper reconstruction and the overall implementation goal remain open.
