# Soul Transfer Altar

The physical table fits a 16 × 16 × 16 block. Its front faces north (negative Z).
All four legs rest on rectangular, stepped gold ingot feet. The tabletop has an
offset cage socket and a shallow writing tray joined by two cyan channels. The
channels leave different sides of the socket and reach different edges of the tray.

`soul_transfer_altar.bbmodel` is the empty block model. The companion `_loaded`
project shows the actual iron cage model at 60% scale, a sheet with a seal, and
static guide particles above the conduit. These preview objects are separate
from the table and show where later game rendering can place the real items.

Run `python modeling/soul-transfer-altar/generate_model.py` to regenerate both
projects and the empty block plan. Both projects embed their textures.
