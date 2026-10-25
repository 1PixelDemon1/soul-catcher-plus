# SPDX-License-Identifier: MIT
# Package approved imagegen artwork; System.Drawing is available on the Windows authoring host.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path -Parent $PSScriptRoot
$artRoot = Join-Path $projectRoot 'art/scythes'
$textureRoot = Join-Path $projectRoot 'src/main/resources/assets/soulcatcherplus/textures/item'
$modelRoot = Join-Path $projectRoot 'src/main/resources/assets/soulcatcherplus/models/item'
$materials = @('soul_scythe', 'diamond_soul_scythe', 'netherite_soul_scythe')
$textureSize = 128

function Write-HeldMesh([System.Drawing.Bitmap]$texture, [string]$material) {
    # Merge identical opaque runs in consecutive rows into disjoint, fully opaque rectangles.
    $rectangles = [System.Collections.Generic.List[object]]::new()
    $previousRuns = @{}
    for ($y = 0; $y -lt $texture.Height; $y++) {
        $currentRuns = @{}
        $x = 0
        while ($x -lt $texture.Width) {
            if ($texture.GetPixel($x, $y).A -eq 0) { $x++; continue }
            $left = $x
            while ($x -lt $texture.Width -and $texture.GetPixel($x, $y).A -gt 0) { $x++ }
            $key = "$left,$x"
            if ($previousRuns.ContainsKey($key)) {
                $rectangle = $previousRuns[$key]
                $rectangle.Bottom = $y + 1
            } else {
                $rectangle = [pscustomobject]@{ Left = $left; Right = $x; Top = $y; Bottom = $y + 1 }
                $rectangles.Add($rectangle)
            }
            $currentRuns[$key] = $rectangle
        }
        $previousRuns = $currentRuns
    }

    $unit = 16.0 / $texture.Width
    $elements = foreach ($rectangle in $rectangles) {
        # All UV limits are texel centers inside opaque regions, including the four edge faces.
        $u0 = ($rectangle.Left + 0.5) * $unit
        $u1 = ($rectangle.Right - 0.5) * $unit
        $v0 = ($rectangle.Top + 0.5) * $unit
        $v1 = ($rectangle.Bottom - 0.5) * $unit
        $element = [ordered]@{
            from = @(($rectangle.Left * $unit), (($texture.Height - $rectangle.Bottom) * $unit), 7.5)
            to = @(($rectangle.Right * $unit), (($texture.Height - $rectangle.Top) * $unit), 8.5)
            faces = [ordered]@{
                north = [ordered]@{ uv = @($u1, $v0, $u0, $v1); texture = '#layer0' }
                south = [ordered]@{ uv = @($u0, $v0, $u1, $v1); texture = '#layer0' }
                west = [ordered]@{ uv = @($u0, $v0, $u0, $v1); texture = '#layer0' }
                east = [ordered]@{ uv = @($u1, $v0, $u1, $v1); texture = '#layer0' }
                up = [ordered]@{ uv = @($u0, $v0, $u1, $v0); texture = '#layer0' }
                down = [ordered]@{ uv = @($u0, $v1, $u1, $v1); texture = '#layer0' }
            }
        }
        $element | ConvertTo-Json -Depth 5 -Compress
    }
    $json = "{`n  `"parent`": `"soulcatcherplus:item/soul_scythe_held`",`n  `"elements`": [`n"
    $json += ($elements -join ",`n") + "`n  ]`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $modelRoot "${material}_held_mesh.json"), $json,
        [System.Text.UTF8Encoding]::new($false))
    Write-Output "Authored $material closed mesh: $($rectangles.Count) rectangles"
}

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
        Write-HeldMesh $texture $material
        Write-Output "Packaged $material held texture: ${textureSize}x${textureSize}"
    } finally {
        $source.Dispose()
        $texture.Dispose()
    }
}
