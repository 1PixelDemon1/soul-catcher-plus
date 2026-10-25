# SPDX-License-Identifier: MIT
# Package approved imagegen artwork; System.Drawing is available on the Windows authoring host.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$artRoot = Join-Path $projectRoot 'art/scythes'
$textureRoot = Join-Path $projectRoot 'src/main/resources/assets/soulcatcherplus/textures/item'
$materials = @('soul_scythe', 'diamond_soul_scythe', 'netherite_soul_scythe')
$textureSize = 128

foreach ($material in $materials) {
    $source = [System.Drawing.Bitmap]::new((Join-Path $artRoot "$material.png"))
    $texture = [System.Drawing.Bitmap]::new($textureSize, $textureSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        if ($source.Width -ne $source.Height) { throw "Expected square scythe artwork: $material" }
        for ($y = 0; $y -lt $textureSize; $y++) {
            for ($x = 0; $x -lt $textureSize; $x++) {
                $sourceX = [int][Math]::Floor(($x + 0.5) * $source.Width / $textureSize)
                $sourceY = [int][Math]::Floor(($y + 0.5) * $source.Height / $textureSize)
                $color = $source.GetPixel($sourceX, $sourceY)
                # Nearest sampling and binary alpha retain crisp item silhouettes at the smaller atlas size.
                if ($color.A -ge 128) {
                    $texture.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $color.R, $color.G, $color.B))
                }
            }
        }
        $texture.Save((Join-Path $textureRoot "${material}_held.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output "Packaged $material held texture: ${textureSize}x${textureSize}"
    } finally {
        $source.Dispose()
        $texture.Dispose()
    }
}
