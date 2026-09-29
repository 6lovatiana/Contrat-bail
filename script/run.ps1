<#
    Script de lancement du projet Contrat-bail en developpement.

    Usage :
        .\script\run.ps1                  -> lance spring-boot:run
        .\script\run.ps1 -PauseEnCasDeSucces

    Le script se place a la racine du projet avant de demarrer Maven, car :
      - Maven doit trouver le pom.xml ;
      - spring.thymeleaf.prefix vaut "file:./template/", relatif au repertoire courant.

    En cas d'echec, le terminal reste ouvert (Read-Host) afin que le message
    d'erreur reste lisible et que la fenetre ne se ferme pas.
#>
[CmdletBinding()]
param(
    # Utile en cas de double-clic : met aussi en pause apres un lancement reussi.
    [switch]$PauseEnCasDeSucces
)

# Emplacement de Maven impose par le projet (et non le "mvn" du PATH).
$MavenCmd = 'C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd'
$racine   = Split-Path -Parent $PSScriptRoot

function Suspendre-Terminal {
    param([string]$Message)
    Write-Host ''
    Write-Host $Message -ForegroundColor Yellow
    [void](Read-Host '  Appuyez sur Entree pour fermer cette fenetre')
}

# --- 1. Verification de la presence de Maven --------------------------------
if (-not (Test-Path -LiteralPath $MavenCmd)) {
    Suspendre-Terminal "ERREUR : Maven est introuvable.`n  Chemin attendu : $MavenCmd"
    exit 1
}

# --- 2. Verification de la version de Java (17+ requis par Spring Boot 3.5) ---
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $jdkParDefaut = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
    if (Test-Path -LiteralPath $jdkParDefaut) {
        $env:JAVA_HOME = $jdkParDefaut
        Write-Host "JAVA_HOME etait vide : utilisation de $jdkParDefaut" -ForegroundColor DarkGray
    }
}

$javaExe = if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $null }
           else { Join-Path $env:JAVA_HOME 'bin\java.exe' }

if ($javaExe -and (Test-Path -LiteralPath $javaExe)) {
    try {
        $versionJava = (& $javaExe -version 2>&1 | Select-Object -First 1) -replace '.*"([\d._]+)".*', '$1'
        Write-Host "Java detecte : $versionJava ($javaExe)" -ForegroundColor DarkGray
        if ($versionJava -and $versionJava -notmatch '^(1\.[89]|9|1[0-6])(\.|$)') {
            # ok : 17 ou superieur
        }
        elseif ($versionJava) {
            Write-Warning "Java $versionJava est trop ancien : Spring Boot 3.5 exige Java 17 ou superieur."
        }
    } catch {
        Write-Warning "Impossible de lire la version de Java : $($_.Exception.Message)"
    }
} else {
    Write-Warning "JAVA_HOME n'est pas defini ou invalide. Maven risque d'echouer au demarrage."
}

# --- 3. Lancement ----------------------------------------------------------
Push-Location $racine

Write-Host ''
Write-Host "Racine du projet : $racine"
Write-Host "Commande        : & `"$MavenCmd`" spring-boot:run" -ForegroundColor DarkGray
Write-Host 'Arreter l''application avec Ctrl+C.' -ForegroundColor DarkGray
Write-Host ''

$codeSortie = 1
try {
    & $MavenCmd spring-boot:run
    $codeSortie = $LASTEXITCODE
} catch {
    Write-Host ''
    Write-Host "Echec du lancement de Maven :" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    $codeSortie = 1
} finally {
    Pop-Location
}

# --- 4. Pause en cas d'echec ----------------------------------------------
if ($codeSortie -ne 0) {
    Suspendre-Terminal "ECHEC : spring-boot:run s'est termine avec le code $codeSortie.`n  Le message ci-dessus indique la cause (base injoignable, compilation, port 8080 occupe...)."
    exit $codeSortie
}

if ($PauseEnCasDeSucces) {
    Suspendre-Terminal 'Lancement termine sans erreur.'
}

exit 0
