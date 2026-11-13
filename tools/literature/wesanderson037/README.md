# wesanderson 0.3.7: native colors and original R package

**Use a NEW output directory. SKIP3 means the dependent work was skipped; FAIL2 admits no result.** Read the generated `WARNINGS.md` or `WARNINGS_FAILURE.md` and process logs. Supply missing prerequisites only where installation and execution are permitted, then retry with a different output path. Native palette generation requires no R installation.

The standalone Java executable lists all 24 original palettes, generates discrete or continuous colors and replays complete saved results. Its public Java API is `org.jlinalg.plot.WesAnderson037`. The separate provider installs the unchanged original R package into a new private library for original R functions, datasets and plotting. The native implementation does not implement R graphical devices or R-language vector/coercion rules.

See the [complete build, use, evidence and recovery guide](../../../docs/literature/WESANDERSON037_WORKFLOW_DEVELOPMENT.md). This component remains separate from the canonical OmicsMatic executable. Its qualification does not by itself change catalog badges or establish paper reproduction.

```powershell
python tools/literature/wesanderson037/build.py --java-bin "C:/path/to/jdk-21/bin" --out build/my-new-palette-build
java -jar build/my-new-palette-build/wesanderson037-native-development.jar palettes --out build/my-new-palette-list
java -jar build/my-new-palette-build/wesanderson037-native-development.jar generate --name Zissou1 --n 256 --type continuous --out build/my-new-palette-colors
java -jar build/my-new-palette-build/wesanderson037-native-development.jar verify --bundle build/my-new-palette-colors --out build/my-new-palette-replay
```

The palette and number above are examples, not recovered author settings.
