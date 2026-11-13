# Complete original ImageJ 1.54t evidence

Read [the workflow guide](../IMAGEJ154T_WORKFLOW_DEVELOPMENT.md). Three platforms compare every byte of14 outputs from five independent original macros. All original inputs, outputs, decoded pixels, macros, process logs, setup refusals, synthetic plugin checks and the complete520-file original source archive are retained through content-addressed objects in controls.zip. No biological data, external scientific plugins or Java runtime are included.

SKIP3 skips dependent analysis; FAIL2 retains diagnostics and admits no partial result. Original code is unchanged; project diagnostic shims are separate. The evidence qualifies the stated synthetic native Java core workflows, not a recovered Fiji distribution, every ImageJ command or paper reproduction. No catalog credit is added.

Run `python tools/literature/imagej154t/verify.py --java-bin /permitted/full/jdk/bin --allow-build --allow-analysis --out /new/output` to rebuild from complete retained original source and rerun actual original controls offline with an available AWT display. Without both permission flags, dependent analysis is skipped.
