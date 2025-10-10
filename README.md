# Synchronised Blockstates
Modded multiserver setups can be... complicated.

When you have multiple connected servers that all have different modlists, it's not uncommon for different servers to have mismatched blocks, leaving you with sights like this:
<img width="3440" height="1369" alt="An image of a minecraft world (seed 4443874797342797) but many of the blocks are incorrect, having been corrupted by the inconsistent network order of blockstates between client/server. Sandstone is replaced by waterlogged cherry leaves. Dead bushes are replaced by noteblocks. Brown, light grey, white, red, brown and orange terracotta are replaced by birch buttons in multiple rotations. Uncoloured terracotta appears as waterlogged spruce stairs. Dripstone us replaced by various forms of copper. Etc" src="https://github.com/user-attachments/assets/afb850d4-c39d-45ac-b9ff-c1752d79713b" />


Obviously this isn't ideal, so this mod takes the approach of synchronising all of the blockstates from the server to the client side when the player joins, allowing the client to compensate.

For example, that same image with synchronised blockstates enabled:
<img width="3440" height="1369" alt="The above image, but the world looks as it is meant to, no blocks were incorrectly replaced" src="https://github.com/user-attachments/assets/3bb43663-03a2-4139-bb67-01d2ade71623" />


Note: if the client doesn't receive blockstate info from the server, it'll fall back to using vanilla states.

This mod is still a W.I.P. and there are plenty of improvements yet to be made, but it *should* function for the time being.

## Configuration

In lieu of a proper config at this point in time, Synchronised Blockstates has several behaviours that can be enabled or customised using JVM arguments or environment variables:

* `-Dmod.synchronisedblockstates.dumpAllStates`
  * Client-only.
  * Accepted values: `true`, `1`, `yes`, `on`.
  * On startup, encodes all blockstates (using the same format as if it were sending a single, unchunked, state registry packet) and writes it to `<minecraft run directory>/synchronized_blockstates/vanillablockstates.dat` (note: not necessarily only vanilla blockstates, will also include modded blockstates if there are any modded blocks).

* `-Dmod.synchronisedblockstates.ouputMissingStates`
  * Client-only.
  * Accepted values: `true`, `1`, `yes`, `on`.
  * When the client recieves a packet and remaps its local registry, outputs any mismatches between the client and server registries to `<minecraft run directory>/synchronized_blockstates/missing-states-<current time>.dat`

* `-Dmod.synchronisedblockstates.propertyChunkingThreshold`
  * Logical Server only.
  * Accepted values: any integer, default `1024`.
  * Max limit on properties before the server starts chunking data before sending it to the client. Additionally, the maximum number of properties that can be sent in a single property chunk.

* `-Dmod.synchronisedblockstates.blockChunkingThreshold`
  * Logical Server only.
  * Accepted values: any integer, default `4096`.
  * Max limit on blocks before the server starts chunking data before sending it to the client. Additionally, the maximum number of blocks there can be sent in a single blockstate chunk.

* `-Dmod.synchronisedblockstates.stateChunkingThreshold`
  * Logical Server only.
  * Accepted values: any integer, default `32768`.
  * Max limit on blockstates before the server starts chunking data before sending it to the client.
