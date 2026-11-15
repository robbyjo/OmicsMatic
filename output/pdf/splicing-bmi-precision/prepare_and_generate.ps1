$ErrorActionPreference = 'Stop'
$stage = $PSScriptRoot
$project = 'D:\Research\topmed\splicing-bmi\new'
$previous = Join-Path $project 'outputs\2026-11-14T214405-0500-persuasive-typeset-manuscript'
$stamp = (Get-Date).ToString('yyyy-MM-ddTHHmmsszzz').Replace(':', '')
$bundle = Join-Path $project ('outputs\' + $stamp + '-manuscript-concise-decimals')
if (Test-Path -LiteralPath $bundle) { throw 'Refusing to overwrite an existing output bundle.' }
foreach ($folder in @('artifacts','scripts','metadata','logs','previews')) { New-Item -ItemType Directory -Path (Join-Path $bundle $folder) | Out-Null }
Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $bundle 'scripts\prepare_and_generate.ps1')
Copy-Item -LiteralPath (Join-Path $stage 'revise_precision.py') -Destination (Join-Path $bundle 'scripts\revise_precision.py')
Copy-Item -LiteralPath 'D:\projects\JLinAlg\output\pdf\splicing-bmi-current-v5\ManuscriptCurrentBridge.java' -Destination (Join-Path $bundle 'scripts\ManuscriptCurrentBridge.java')
foreach ($name in @('manuscript.css','export_pdf.cjs','update_index_scoped_v3.py')) { Copy-Item -LiteralPath (Join-Path $previous ('scripts\' + $name)) -Destination (Join-Path $bundle ('scripts\' + $name)) }
Copy-Item -LiteralPath 'D:\projects\JLinAlg\build\cli\omicsmatic-0.3.7.jar' -Destination (Join-Path $bundle 'scripts\omicsmatic-0.3.7.jar')
Copy-Item -LiteralPath 'D:\projects\JLinAlg\src\main\resources\org\jlinalg\study\manuscript-writing-v1.txt' -Destination (Join-Path $bundle 'scripts\manuscript-writing-v1.txt')
New-Item -ItemType Directory -Path (Join-Path $bundle 'scripts\software') | Out-Null
foreach ($name in @('src/main/java/org/jlinalg/cli/StudyReportNumbers.java','src/main/java/org/jlinalg/cli/StudyMathTypography.java','src/main/java/org/jlinalg/study/StudyManuscriptPolicy.java','src/test/java/org/jlinalg/cli/StudyMathTypographyTest.java','src/test/java/org/jlinalg/cli/StudyManuscriptWritingTest.java','docs/manuscript-writing-template.md')) {
    Copy-Item -LiteralPath (Join-Path 'D:\projects\JLinAlg' $name) -Destination (Join-Path $bundle 'scripts\software')
}
Copy-Item -LiteralPath (Join-Path $stage 'verification-build.log') -Destination (Join-Path $bundle 'logs\verification-build.log')
Copy-Item -LiteralPath (Join-Path $stage 'initial-verification') -Destination (Join-Path $bundle 'logs\initial-verification') -Recurse
foreach ($name in @('StudyMathTypographyTest','StudyManuscriptWritingTest','StudyPublicationTest','StudyManuscriptRevisionTest')) {
    Copy-Item -LiteralPath ('D:\projects\JLinAlg\build\test-results\test\TEST-org.jlinalg.cli.' + $name + '.xml') -Destination (Join-Path $bundle 'logs')
}
[System.IO.File]::WriteAllText((Join-Path $stage 'bundle-path.txt'), $bundle, [System.Text.UTF8Encoding]::new($false))
$env:PYTHONIOENCODING = 'utf-8'
& 'C:\Program Files\WindowsApps\OpenAI.CodexPrimaryRuntime.v26-1112-611-0_26.1112.611.0_x64__3k8sg7r9htsxt\dependencies\python\python.exe' (Join-Path $bundle 'scripts\revise_precision.py') generate *> (Join-Path $bundle 'logs\generation-console.log')
$code = $LASTEXITCODE
Get-Content -LiteralPath (Join-Path $bundle 'logs\generation-console.log') -Encoding utf8 -Tail 60
Write-Output $bundle
exit $code
