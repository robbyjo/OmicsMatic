$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath('D:\projects\JLinAlg\build\fu-1')
$receiptRoot='D:\projects\JLinAlg\build\git-publish\20261122-squidpy-workflow-handover-136'
$plan=Get-Content -LiteralPath (Join-Path $receiptRoot 'RELOCATION-PLAN.json') -Raw | ConvertFrom-Json
$entries=@($plan.files.PSObject.Properties)
if($entries.Count -ne 14) { throw 'Unexpected draft relocation count' }
$checked=@()
foreach($entry in $entries) {
    $origin=[IO.Path]::GetFullPath($entry.Name)
    $target=[IO.Path]::GetFullPath($entry.Value.destination)
    if(-not $origin.StartsWith($taskRoot+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Origin escapes task checkout' }
    if(-not $target.StartsWith($taskRoot+'\docs\literature\squidpy-workflow-handover-20261122\drafts\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Destination escapes saved draft packet' }
    foreach($file in @($origin,$target)) {
        $node=Get-Item -LiteralPath $file -Force
        if($node.PSIsContainer) { throw 'Only exact regular files may be removed' }
        while($null -ne $node -and $node.FullName.Length -ge $taskRoot.Length) {
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse point in relocation path' }
            if($node -is [IO.FileInfo]) { $node=$node.Directory } else { $node=$node.Parent }
        }
    }
    foreach($file in @($origin,$target)) {
        $bytes=[IO.File]::ReadAllBytes($file)
        $algorithm=[Security.Cryptography.SHA256]::Create()
        try { $digest=[BitConverter]::ToString($algorithm.ComputeHash($bytes)).Replace('-','').ToLowerInvariant() } finally { $algorithm.Dispose() }
        if($bytes.LongLength -ne $entry.Value.bytes -or $digest -ne $entry.Value.sha256) { throw 'Draft bytes differ from saved exact copy' }
    }
    $checked+=@{origin=$origin;recovery=$target;bytes=[long]$entry.Value.bytes;sha256=$entry.Value.sha256}
}
$logicalBytes=0L
foreach($entry in $checked) { $logicalBytes+=$entry.bytes }
$before=[IO.DriveInfo]::new('D:\').AvailableFreeSpace
foreach($entry in $checked) { Remove-Item -LiteralPath $entry.origin -Force }
$after=[IO.DriveInfo]::new('D:\').AvailableFreeSpace
@{status='EXACT_UNTRACKED_DRAFTS_RELOCATED_TO_NONCOMPILED_HANDOVER';files_removed=$checked.Count;logical_bytes_removed=$logicalBytes;free_bytes_before=$before;free_bytes_after=$after;measured_free_space_change=$after-$before;removed_files=$checked;recursive_deletion=$false;active_frozen_contexts_and_runtime_evidence_preserved=$true} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $receiptRoot 'RELOCATION-RESULT.json') -Encoding UTF8
