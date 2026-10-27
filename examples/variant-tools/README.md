# Managed variant software setup

JLinAlg downloads and installs the supported tools for users. The same CLI can be called by an application frontend; no repository checkout or separate installer script is needed.

```text
java -jar jlinalg.jar variant-tools install alphagenome
java -jar jlinalg.jar variant-tools status alphagenome
```

Python 3.10 or newer bootstraps a private environment. Use `--python /path/to/python` when Python is not on PATH, or `--home NEW_DIRECTORY` to select a private installation directory. Existing complete homes are verified and reused; incomplete or changed homes are retained with an error rather than overwritten. The default home is `~/.jlinalg/tools/alphagenome-0.10.0` (`annovar` for ANNOVAR), shared by the setup command and study execution.

The application obtains the official AlphaGenome 0.10.0 wheel from PyPI, checks its pinned SHA-256, downloads binary dependency wheels and writes a complete version/hash lock before installing from those local wheels. It verifies imports and dependencies. The installed software is the official Python client. API credentials, prediction requests and model outputs are separate; installing it requires no AlphaGenome key and submits no study data.

## ANNOVAR registration and download

The [official ANNOVAR download](https://annovar.openbioinformatics.org/en/latest/user-guide/download/) requires personal registration and licence acknowledgement. A frontend should present the registration URL from `variant-tools status annovar`, then accept the user's issued download link or registered local archive. It must not invent a registration or bundle someone else's licensed copy.

Put the issued URL in an environment variable, then let JLinAlg download and install it:

```text
java -jar jlinalg.jar variant-tools install annovar --source-url-env ANNOVAR_DOWNLOAD_URL --accept-license true
```

Alternatively import a previously registered archive:

```text
java -jar jlinalg.jar variant-tools install annovar --source-file annovar.latest.tar.gz --accept-license true --downloads false
```

`--expected-sha256 SHA256` optionally pins a known archive. JLinAlg records the actual archive hash, refuses unsafe archive paths/links and limits extraction. The issued URL itself is excluded from manifests and error messages. Perl and the chosen annotation databases remain explicit runtime/data prerequisites; installing the scripts does not silently download every genomic database.

## Automatic study setup

With permitted automatic downloads, a native AlphaGenome study route prepares the shared SDK home before making its first authenticated model request. If no API credential is configured, it reports that prerequisite without downloading an unusable dependency. Users can install the software independently with the setup command above.

```yaml
analysis:
  variant_followup:
    alphagenome:
      enabled: true
      engine: atlas
      installation:
        mode: automatic
        # home: /optional/private/tool/home
        # python: /optional/bootstrap/python
```

Explicit existing `alphagenome.python` paths keep their external-environment behavior unless `installation.mode: automatic` is requested. `installation.mode: external` disables managed setup. ANNOVAR automatically binds a managed home when no external executable was supplied; a first installation additionally needs its registered archive or issued-link environment variable and licence acknowledgement:

```yaml
analysis:
  variant_followup:
    consequences:
      engine: annovar
      release: YOUR_ANNOTATION_RELEASE
      database: /path/to/humandb
      protocol: refGene
      operation: g
      installation:
        mode: automatic
        source_url_env: ANNOVAR_DOWNLOAD_URL
        accept_license: true
```

`resources.network: offline` or a nonautomatic `resources.downloads` policy prevents missing software from being downloaded. A complete managed home can still be reused; a local licensed ANNOVAR archive can be imported without a software network request. Managed runtime hashes participate in study cache identities. A changed/missing/extra installed file invalidates the profile and cached scientific reuse; choose a new home after reviewing retained diagnostics.

The JSON status endpoint lets a frontend show installed, absent or registration-required states. Setup failures retain logs in/alongside the private home. A successful installation establishes software availability and provenance, not validated pathogenicity or scientific model quality.
