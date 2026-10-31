# SPDX-License-Identifier: MIT
# Palette variants of the approved iron master, with optional native pixel ornaments.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$artRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'art/scythes'
$spec = Get-Content -LiteralPath (Join-Path $artRoot 'held-palettes.json') -Raw | ConvertFrom-Json
$master = [Drawing.Bitmap]::new((Join-Path $artRoot 'soul_scythe.png'))
try {
    foreach ($material in $spec.materials.PSObject.Properties) {
        $ramp = @($material.Value | ForEach-Object { [Drawing.ColorTranslator]::FromHtml("#$_") })
        if ($ramp.Count -ne $spec.tone_levels.Count) { throw 'Palette tones and colors must have equal lengths' }
        $output = [Drawing.Bitmap]::new($master.Width, $master.Height)
        try {
            for ($y = 0; $y -lt $master.Height; $y++) {
                for ($x = 0; $x -lt $master.Width; $x++) {
                    $color = $master.GetPixel($x, $y)
                    $blade = $y -le $spec.blade.max_row -and ($x -le $spec.blade.main_max_column -or
                        ($x -le $spec.blade.root_max_column -and $y -le $spec.blade.root_max_row))
                    $pommelMetal = $y -ge $spec.pommel_min_row -and $color.GetSaturation() -le 0.25
                    if ($color.A -gt 0 -and ($blade -or $pommelMetal)) {
                        $tone = 0.2126 * $color.R + 0.7152 * $color.G + 0.0722 * $color.B
                        $index = 0
                        while ($index -lt $ramp.Count - 2 -and $tone -gt $spec.tone_levels[$index + 1]) { $index++ }
                        $fraction = ($tone - $spec.tone_levels[$index]) / ($spec.tone_levels[$index + 1] - $spec.tone_levels[$index])
                        $channels = foreach ($channel in @('R', 'G', 'B')) {
                            [int][Math]::Round($ramp[$index].$channel * (1 - $fraction) + $ramp[$index + 1].$channel * $fraction)
                        }
                        $color = [Drawing.Color]::FromArgb($color.A, $channels[0], $channels[1], $channels[2])
                    }
                    $output.SetPixel($x, $y, $color)
                }
            }
            $overlay = $spec.overlays.($material.Name)
            if ($overlay) {
                for ($y = 0; $y -lt $overlay.pixels.Count; $y++) {
                    $row = $overlay.pixels[$y]
                    for ($x = 0; $x -lt $row.Length; $x++) {
                        $key = [string]$row[$x]
                        $color = if ($key -eq '.') { [Drawing.Color]::Transparent }
                            else { [Drawing.ColorTranslator]::FromHtml('#' + $overlay.palette.$key) }
                        $output.SetPixel($overlay.origin[0] + $x, $overlay.origin[1] + $y, $color)
                    }
                }
            }
            $output.Save((Join-Path $artRoot ($material.Name + '.png')), [Drawing.Imaging.ImageFormat]::Png)
            Write-Output "Recolored blade and pommel: $($material.Name)"
        } finally { $output.Dispose() }
    }
} finally { $master.Dispose() }
