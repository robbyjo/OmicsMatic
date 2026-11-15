# Original iDEP counts in a genuine Shiny session

**Development implementation; zero catalog credit.** The separate source-baseline
integration in [the main merge warning](MAIN_MERGE_20261115_STATUS.md) remains
unfinished. This profile calls original functions in an actual Shiny session;
full iDEP GUI coverage, author defaults, biological accuracy and paper
reproduction remain unqualified. The preserved [headless profile](IDEP244_COUNTS_DEVELOPMENT.md)
and its original failures remain separate and unchanged.

The selected original source is **iDEP 2.4.4**, commit
`60479c4375ea07bc930dc40b3c85230856f4ad50`, with **Windows R 4.6.1**, **DESeq2
1.52.0** and **Shiny 1.14.0**. These are project-selected versions; they do not
identify the unreported deployment behind P050's literal **iDEP.96**. Original
source and installed iDEP binaries are private prerequisites under the
[preserved noncommercial assessment](IDEP_CURRENT_DEVELOPMENT.md). They are not
redistributed by this adapter or its own-source evidence archive.

## Prepare the exact runtime and transport

First audit an existing permitted runtime using
[the headless profile's runtime-adoption command](IDEP244_COUNTS_DEVELOPMENT.md#prepare-an-existing-permitted-runtime).
The new profile additionally checks that INSPECTED, READY, actual package
DESCRIPTION files, library priority and the complete Depends/Imports plus loaded
namespace closure agree. The runtime index must contain exactly every selected
package directory and R's `bin`, `etc` and `modules` directories, with nonempty
file inventories and a consistent total. These checks establish consistency;
saved hashes do not authenticate an upstream binary. DESCRIPTION versions such
as `1.4-8` are compared with the native R `packageVersion()` representation
`1.4.8` without modifying either record.

The session route also needs the Apache-2.0 **websocket-client 1.9.2** Python
package. Use the same Python executable for installation and execution. The
installer downloads the pinned official wheel and its PyPI metadata, checks the
selected SHA-256, and invokes pip with `--no-index --no-deps --no-compile` into
the NEW directory's `python-site`. It changes no global Python installation.

From the project root:

~~~powershell
python -B tools/literature/idep244_session/install_transport.py `
  --out "C:/analysis/NEW-idep-websocket-transport"
~~~

The selected wheel is `websocket_client-1.9.2-py3-none-any.whl`, SHA-256
`e1a673830a9c7bfa47b1cd3d5e4178f4c9651d80a4eab02c9c23a1c3ec6250ce`.
Original download metadata and complete installer logs remain in the setup
directory. Its `COMPLETED.json` admits dependency setup, not analysis. The
installed Python executable/version and the complete transport file inventory
must still match when running the session profile.

**If the permitted exact runtime, source, transport, database or required
installation is unavailable, skip the dependent analysis.** Read `FAILURE.json`
and `WARNINGS.md` and supply the named permitted prerequisite before retrying
NEW paths. Do not replace another version's saved hashes or overwrite an old
provider. The current entry point audits an existing iDEP runtime; it does not
provide a new automatic installer for all original iDEP dependencies.

## Run the original counts workflow

Copy [the complete schema2 request](../../examples/idep244-session-workflow/request.json)
and set `expression` to your original counts file. Every cutoff, transform,
mapping option, model factor, locale, RNG kind, seed and limit is explicit.
These example choices are not recovered paper defaults. Request paths resolve
from the directory where Python is invoked.

~~~powershell
python -B tools/literature/idep244_session/run.py `
  --runtime "C:/analysis/NEW-idep-provider" `
  --transport "C:/analysis/NEW-idep-websocket-transport" `
  --request "C:/analysis/request.json" `
  --out "C:/analysis/NEW-idep-session-analysis"
~~~

The schema is `2` and the profile is
`original-idep244-shiny-r461-windows`. Original/effective request checks allow
only the documented relocation into captured input paths. All scientific
options must remain exactly equal. A captured expression/design basename keeps
the caller's literal extension even when a top-level file symlink resolves to a
differently suffixed target. This matters because the original loader selects
some formats using that extension. Recursive reference links remain refused.

The original stages are `input_data`, optionally `get_idep_data` and `convert_id`,
then `convert_data`, `pre_process` and `limma_value` with its DESeq2 dispatcher.
The complete original return from each stage is saved before its admission
checks. Original identifier transformations, duplicate handling, missing-value
methods, mapping aggregators, transformations, comparison caps and failures
remain original behavior. Returned generated R script text is saved and **never
executed**. Captured database prerequisites prevent the original loader's
missing-reference automatic-download branch.

An owned Rscript process starts an actual `shiny::runApp` on `127.0.0.1` with
`launch.browser=FALSE`. Its real `ShinySession` supplies the default reactive
domain. A local WebSocket client sends the one invocation token, captures full
server messages, acknowledges the completion receipt, and lets R record that
acknowledgement before stopping. No namespace bindings, notification functions
or scientific methods are replaced. This is a count-workflow caller, not the
complete original iDEP web application. See the upstream
[runApp reference](https://shiny.posit.co/r/reference/shiny/latest/runapp.html)
and [Shiny client source](https://github.com/rstudio/shiny/blob/main/srcts/src/shiny/shinyapp.ts).

The profile fixes the explicit Windows UTF-8 locale, `LC_TIME=C`, UTC and the
requested R RNG kinds/seed before scientific calls. Complete before/after RNG,
conditions, caller context and source audits remain in the original RDS files.
The post-call namespace audit also runs after an original error, preserving
that error and partial values first. All 259 active original function names
must match their pinned definitions; the original duplicate definition is
retained. No default is silently substituted after an error.

## Read warnings and incomplete outcomes first

**Only the final root `COMPLETED.json` admits a complete recorded development
run.** `SESSION-COMPLETED.json` is a transport receipt; a worker exit status,
native RDS value or session acknowledgement alone admits no result.

* `SKIP3` names an unavailable prerequisite and skips the dependent analysis
  before the scientific R process starts. Supply the named permitted component
  and retry NEW paths.
* `FAIL2` admits no complete result. It includes native errors, original
  character/NULL error returns, broken sessions, recording failures and resource
  refusals. Read `FAILURE.json`, retain all originals and partials, resolve the
  named cause, and retry NEW paths.
* `WARNINGS.md` presents original Shiny notifications and R condition messages.
  `received-notifications.json` retains complete notification objects, including
  original HTML, IDs, duration and type. `websocket-messages.jsonl` retains exact
  message payloads as base64 with direction, opcode and byte count. The raw
  `worker.stdout` and `worker.stderr` remain complete; structured conditions do
  not count every possible native diagnostic.
* For a zero-count sample, the original loader may delete the sample and send
  `zero_expression_file`. That notification must remain visible. The checked
  all-zero input instead raises the original `argument is of length zero`
  error after a warning; it admits no downstream DESeq2 result. Do not turn it
  into an invented successful empty matrix or label it merely a missing tool.

Limits preserve complete records instead of truncating inputs or results.
Wall time, output files and bytes are monitored and may overshoot between
observations; they are not OS disk quotas. The R vector-heap limit must be
accepted at its requested value. An owned Windows Job Object closes the created
Rscript/Rterm group on refusal. Networking and the general filesystem are not
isolated by this adapter. The session binds to loopback and opens no browser.

## Replay complete captured inputs

~~~powershell
python -B tools/literature/idep244_session/verify.py `
  --bundle "C:/analysis/NEW-idep-session-analysis" `
  --out "C:/analysis/NEW-idep-session-replay"
~~~

Verification checks complete bundle membership, the exact installed/captured
engine, request coherence, runtime/provider records, typed status, process
limits and the ordered invocation/completion/acknowledgement messages. It copies
every captured input and repeats original calls in a NEW genuine session.
Strict R comparisons include complete original stage values and attributes,
NA/NaN/Inf/signed zero, conditions, RNG and caller context. Only the validated
first generated-script timestamp line differs in local comparison copies; raw
script text remains unchanged. The session's transport token may differ while
both exact session classes, default domains and genuine-session flags must
agree. Original notification objects must be checked separately when comparing
independent literal controls. Replay never changes the saved original bundle.

The independently checked native-error signature has a narrower scope than a
successful full-value comparison: surrounding Shiny failure stacks differ by
caller. Both complete raw stacks remain private; the comparison claims only the
named error class/message/call, full partial stage values, conditions' declared
signatures and complete RNG identity. It does not claim full failure-stack
equivalence. Source-bearing RDS traces remain private under the original terms.

## Preserved controls and remaining qualification

The [separate development checkpoint](idep244-session-counts-development/README.md)
preserves the following actual controls. These are tests of the declared
synthetic inputs and caller profile, not additional paper or catalog credit.

| Control | Observed original outcome |
| --- | --- |
| Baseline CSV | 200 genes, 9 samples; complete 200 × 3 DESeq2 matrix |
| One zero-count sample | Original warning and sample removal; 200 genes, 8 samples; complete 200 × 3 matrix |
| Literal CSV file alias to a `.xlsx`-named target containing unchanged CSV bytes | Literal CSV selection preserved; complete original outputs and conditions agree; this is not Excel-format qualification |
| All-zero input | Original warning followed by `argument is of length zero`; FAIL2, no admitted result |
| Resealed request/provider/input-extension contradictions | 12 refusals, including scientific-option changes and incomplete runtime membership |
| Missing runtime, transport and reference | 3 SKIP3 outcomes before scientific R starts, with warnings and required actions |
| Actual session wall limit | Owned Rscript and Rterm stopped; another actual R test job stayed alive and finished normally |

All three successful science cases have independent complete original-call
comparisons and complete captured-input replays. Their stage values, original
conditions, RNG and caller context agree under the stated timestamp/session
policies. The all-zero comparison claims the narrower error-signature scope
described above. The record-freeze check additionally compares complete saved
notification objects for the baseline and one-zero replays; adding that parity
check as a generic production replay gate remains pending.

The first full control suite failed because the wrapper had not written its
post-error namespace audit; that failed suite remains retained. The first alias
comparison put a synthetic link inside an output tree and correctly triggered
the output monitor's link refusal. Its retained successor reads the actual
original alias from separate caller inputs. The earliest loader probe also
exposed a parent failure-reporting bug; its original R consoles, values and
conditions remain preserved, while its parent traceback is in the tool
transcript. None of these failed attempts was silently overwritten or promoted.

To check the public metadata and own-source archive only:

~~~powershell
python -B tools/literature/idep244_session_records.py
~~~

Add `--private-base "C:/private/monocle-runtime"` to check the complete saved
private payload inventory and declared synthetic file aliases. These modes do
not repeat a scientific fit or re-audit the currently installed runtime. A
future implementation change is reported as a historical source snapshot;
saved hashes are not silently refreshed.

Broader VST/rlog, aggregation, missing-value, identifier, interaction/model,
format and source-cap controls remain necessary before wider option claims.
Other operating systems, automatic full iDEP provisioning, full GUI behavior
and the main-source catalog-baseline integration remain open. The complete
250-paper implementation objective remains active.
