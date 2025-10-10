# Synchronised Blockstates
Modded multiserver setups can be... complicated.

When you have multiple connected servers that all have different modlists, it's not uncommon for different servers to have mismatched blocks, leaving you with sights like this:
<img width="3440" height="1369" alt="An image of a minecraft world (seed 4443874797342797) but many of the blocks are incorrect, having been corrupted by the inconsistent network order of blockstates between client/server. Sandstone is replaced by waterlogged cherry leaves. Dead bushes are replaced by noteblocks. Brown, light grey, white, red, brown and orange terracotta are replaced by birch buttons in multiple rotations. Uncoloured terracotta appears as waterlogged spruce stairs. Dripstone us replaced by various forms of copper. Etc" src="https://github.com/user-attachments/assets/afb850d4-c39d-45ac-b9ff-c1752d79713b" />


Obviously this isn't ideal, so this mod takes the approach of synchronising all of the blockstates from the server to the client side when the player joins, allowing the client to compensate.

For example, that same image with synchronised blockstates enabled:
<img width="3440" height="1369" alt="The above image, but the world looks as it is meant to, no blocks were incorrectly replaced" src="https://github.com/user-attachments/assets/3bb43663-03a2-4139-bb67-01d2ade71623" />


Note: if the client doesn't receive blockstate info from the server, it'll fall back to using vanilla states.

This mod is still a W.I.P. and there are plenty of improvements yet to be made, but it *should* function for the time being.
