$ErrorActionPreference = 'Stop'
$taskBase = (Resolve-Path -LiteralPath 'D:/projects/JLinAlg/build/fu-1/build/scenic-1').Path.TrimEnd('\')
$packet = 'D:/projects/JLinAlg/build/fu-1/docs/literature/pyscenic-aucell-component-checkpoint'
$publicationRoot = 'D:/projects/JLinAlg/build/git-publish/20261123-scenic-270'
$readback = Get-Content -LiteralPath ($publicationRoot + '/GITHUB-READBACK.json') -Raw | ConvertFrom-Json
if ($readback.status -ne 'PASS' -or $readback.files.Count -ne 57) { throw 'Published recovery has not been verified' }
$plan = Get-Content -LiteralPath ($packet + '/CLEANUP-PLAN.json') -Raw | ConvertFrom-Json
$archive = Get-Item -LiteralPath ($packet + '/evidence.zip')
if ($archive.Length -ne $plan.recovery_archive.bytes -or (Get-FileHash -LiteralPath $archive.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $plan.recovery_archive.sha256) { throw 'Recovery archive changed' }
$allowed = @()
foreach ($stageName in @('component-check-1','component-check-2','packaged-check-1','packaged-check-2')) {
 foreach ($hostName in @('windows21','windows25','linux21')) {
  $relativeName = $stageName + '\' + $hostName + '\classes'
  $allowed += (Join-Path -Path $taskBase -ChildPath $relativeName)
 }
}
if ($plan.roots.Count -ne 12 -or $allowed.Count -ne 12) { throw 'Unexpected cleanup root count' }
$ancestor = Get-Item -LiteralPath $taskBase -Force
while ($null -ne $ancestor -and $ancestor.FullName.StartsWith('D:\projects\JLinAlg',[StringComparison]::OrdinalIgnoreCase)) {
 if ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw ('Reparse point in task ancestry: ' + $ancestor.FullName) }
 $ancestor = $ancestor.Parent
}
$processes = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(java|javac|python|wsl)(\.exe)?$' -and $_.CommandLine -like '*build*scenic-1*' } | Select-Object ProcessId,Name,CommandLine)
if ($processes.Count -ne 0) { throw 'Owned scientific/check process may still be active' }
$linuxProcesses = @(wsl.exe -d JLinAlg-Omics-SAIGE-20261026 -- ps -eo pid,args)
if ($LASTEXITCODE -ne 0) { throw 'Could not establish Linux process ownership' }
if (@($linuxProcesses | Where-Object { $_ -like '*/mnt/d/projects/JLinAlg/build/fu-1/build/scenic-1/*' }).Count -ne 0) { throw 'A Linux task process may still be active' }
$validated = @(); $removedFiles = @(); $logicalBytes = [int64]0
foreach ($entry in $plan.roots) {
 $resolved = (Resolve-Path -LiteralPath $entry.root).Path.TrimEnd('\')
 if (-not $resolved.StartsWith($taskBase + '\',[StringComparison]::OrdinalIgnoreCase) -or $resolved -notin $allowed) { throw ('Unexpected absolute cleanup target: ' + $resolved) }
 $nodes = @((Get-Item -LiteralPath $resolved -Force)) + @(Get-ChildItem -LiteralPath $resolved -Recurse -Force)
 if (@($nodes | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -ne 0) { throw ('Reparse point in cleanup target: ' + $resolved) }
 $files = @($nodes | Where-Object { -not $_.PSIsContainer })
 if ($files.Count -ne $entry.files.Count) { throw ('Cleanup namespace changed: ' + $resolved) }
 foreach ($expected in $entry.files) {
  $item = Get-Item -LiteralPath (Join-Path -Path $resolved -ChildPath $expected.path)
  if ($item.Length -ne $expected.identity.bytes -or (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected.identity.sha256) { throw ('Cleanup bytes changed: ' + $item.FullName) }
  $removedFiles += [ordered]@{path=$item.FullName;bytes=$item.Length;sha256=$expected.identity.sha256;recovery_archive='evidence.zip';member=$expected.member;logical_evidence=$expected.logical_evidence}
  $logicalBytes += $item.Length
 }
 $validated += $resolved
}
$before = [IO.DriveInfo]::new('D:\').AvailableFreeSpace
foreach ($resolved in $validated) { Remove-Item -LiteralPath $resolved -Recurse -Force }
foreach ($resolved in $validated) { if (Test-Path -LiteralPath $resolved) { throw ('Cleanup target still exists: ' + $resolved) } }
$after = [IO.DriveInfo]::new('D:\').AvailableFreeSpace
$receipt = [ordered]@{status='PASS_EXACT_REDUNDANT_COMPONENT_COMPILER_COPY_CLEANUP';publication_commit=$readback.commit;published_recovery_verified=$true;removed_roots=$validated;removed_files=$removedFiles;logical_file_bytes=$logicalBytes;available_space_before=$before;available_space_after=$after;measured_available_space_delta=($after-$before);measurement_can_include_other_filesystem_activity=$true;other_worktree_owners_preserved=18;unrelated_processes_stopped=0;failed_scientific_evidence_retained=$true;original_inputs_outputs_producers_runtimes_retained=$true;owned_scientific_processes_before=$processes}
[IO.File]::WriteAllText(($publicationRoot + '/CLEANUP.json'),(($receipt | ConvertTo-Json -Depth 12) + [Environment]::NewLine),[Text.UTF8Encoding]::new($false))
Write-Output ('Removed ' + $removedFiles.Count + ' redundant compiled files (' + $logicalBytes + ' logical bytes) from12 verified roots; measured available-space delta: ' + ($after-$before))
