<# sample usage of the script
  $ powershell.exe -File .\importAttributeStore.ps1
 #>

Param(
)

Add-ADFSAttributeStore -TypeQualifiedName "RoleCatalogueAttributeStore.MainClass, RoleCatalogueAttributeStore" -Configuration @{"Debug"="false";"ApiKey"="973a2d3a-df26-4afa-9e1d-7ee8a34ee6ff";"RoleCatalogueUrl"="https://www.rollekatalog.dk/favrskov"} -Name RoleCatalogueAttributeStore

