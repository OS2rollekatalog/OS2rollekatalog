<# sample usage of the script
  $ powershell.exe -File .\importRules.ps1 -rp KOMBIT
 #>

Param(
        [Parameter(Mandatory=$True)][string]$rp
)

Set-AdfsRelyingPartyTrust -TargetName $rp -IssuanceTransformRulesFile ./rules.txt
