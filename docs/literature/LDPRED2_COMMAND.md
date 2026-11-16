# LDpred2 normal command

**Source build required.** Published v0.4.0 downloads lack this development
command. The command uses the qualified prepared-input v2 workflow and preserves
its input, failure and replay rules. It adds no new catalog credit by itself.

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --request request.properties --out new-analysis
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --verify new-analysis --out new-verification
```

The explicit class `org.jlinalg.cli.LdPred2WorkflowCli` remains usable. Both
entry points invoke the same numerical/worker implementation and support the
same captured-input recomputation. The normal command runs before generic
project configuration, preserving the workflow's explicit request semantics.
No extra generic configuration sidecar is written.

Use [the v2 workflow guide](LDPRED2_WORKFLOWS_V2.md) for exact table/binary schemas,
model settings, runtime adoption and resource limits. That guide is a frozen
checkpoint predating this alias; its statement that no main-command alias exists
is historical. Native Java supports inf and the complete serial grid. Auto
requires an explicitly adopted original bigsnpr runtime in Linux/WSL. For
original execution, invoke Java inside the permitted Linux environment.

**SKIP3:** a required permitted runtime/helper is unavailable. Skip dependent
analysis, supply the named prerequisite only where installation is allowed, and
retry new output. No method/edition substitution or installation occurs during
analysis. If installation is prohibited, keep the analysis skipped.

**FAIL2:** analysis, validation, capacities or recording failed. Retain all
captured inputs, original objects, consoles and provisional outputs. Correct
the named cause and retry new paths. Source-rejected candidates have no accepted
weights; never replace their missing values or average rejected chains. Zero
accepted candidates fails the collection. A completion marker alone is not an
authenticity signature; require genuine recomputation.

The underlying frozen v2 controls cover both selected original editions on
eight variants and native inf/grid on 1.1 million synthetic variants. Further
original-auto scale controls and cumulative catalog admission are separate
work. No whole-paper replication or author-environment identity is claimed.
