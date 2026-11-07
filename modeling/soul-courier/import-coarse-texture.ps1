# Convert the generated 16x16 logical pixel atlas to native pixels and six flat colors.
param([Parameter(Mandatory = $true)][string]$SourcePath)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$textureDirectory = Join-Path $PSScriptRoot 'textures'
Copy-Item -LiteralPath $SourcePath -Destination (Join-Path $PSScriptRoot 'texture-source-coarse.png')
$source = [System.Drawing.Bitmap]::new($SourcePath)
$result = [System.Drawing.Bitmap]::new(16, 16)
$palette = @('192B33', '253D43', '3A5860', '147EAA', '2DBCD7', '8DE3EB') | ForEach-Object {
    [System.Drawing.ColorTranslator]::FromHtml('#' + $_)
}
try {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            # Sample the centre of each logical texel, avoiding generated edge antialiasing.
            $color = $source.GetPixel([int][Math]::Floor(($x + 0.5) * $source.Width / 16),
                                     [int][Math]::Floor(($y + 0.5) * $source.Height / 16))
            $nearest = $palette[0]
            $bestDistance = [double]::PositiveInfinity
            foreach ($candidate in $palette) {
                $distance = [Math]::Pow($color.R - $candidate.R, 2) +
                            [Math]::Pow($color.G - $candidate.G, 2) +
                            [Math]::Pow($color.B - $candidate.B, 2)
                if ($distance -lt $bestDistance) { $bestDistance = $distance; $nearest = $candidate }
            }
            $result.SetPixel($x, $y, $nearest)
        }
    }
    $result.Save((Join-Path $textureDirectory 'cloak_runic.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $result.Dispose()
    $source.Dispose()
}
