param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)
$ErrorActionPreference='Stop'

# A file over 800 lines is allowed only while it stands in this budget at its current size. Growth is red; a file
# that got shorter is reported so its number is lowered by hand (the ratchet must not creep back up); a new file
# over the limit and a stale entry are red too.
$budget=[ordered]@{
    'CompanionEntity.java'=1216
    'CompanionWork.java'=1005
}
$limit=800
$dir=Join-Path $Root 'src/main/java/dev/aicompanion/entity'
if(!(Test-Path -LiteralPath $dir)){ Write-Output "FAIL no such directory: $dir"; exit 1 }
$files=@(Get-ChildItem -LiteralPath $dir -Filter '*.java')
if($files.Count -eq 0){ Write-Output "FAIL no Java files under $dir"; exit 1 }
$failed=$false
foreach($file in $files){
    $lines=@(Get-Content -LiteralPath $file.FullName).Count
    if($budget.Contains($file.Name)){
        if($lines -gt $budget[$file.Name]){
            Write-Output "FAIL $($file.Name) has $lines lines, over its budget of $($budget[$file.Name])"
            $failed=$true
        } elseif($lines -lt $budget[$file.Name]){
            Write-Output "NOTE $($file.Name) is $lines lines, below its budget of $($budget[$file.Name]): lower the number in scripts/memory-budget.ps1"
        }
    } elseif($lines -gt $limit){
        Write-Output "FAIL $($file.Name) has $lines lines > $limit and is not in the budget"
        $failed=$true
    }
}
foreach($name in @($budget.Keys)){
    if($files.Name -notcontains $name){
        Write-Output "FAIL $name stands in the budget but is no longer in entity/: remove it from scripts/memory-budget.ps1"
        $failed=$true
    }
}
if($failed){ exit 1 }
Write-Output "PASS entity/*.java: nothing grew past its budget, no new file is over $limit lines"
exit 0
