DAI QUICKPATCH — STARTUP WOOD + TABLE CONTINUATION/HOME RETRY

Drag the decisions_and_impulses folder over the existing datapack folder and replace files when prompted.

Updated objective files only. No Java, recognition, groups, recipes, menus, or unrelated blueprint files are included.

Fixes:
1. Startup wood acquisition now lets exploration own execution until it finds a log or times out.
2. A log found by exploration is approached/mined directly instead of being discarded and re-searched by mine_nearest_block.
3. Long exploration continues from the newly explored position if wood is still absent, so arbitrary spawns do not silently stop.
4. Successful first crafting-table crafting always returns to fp_cycle/fp_decide.
5. Table clear attempt 3 rejects/relocates the home site instead of fp_cycle rediscovering the same impossible cell.
6. Exact table-placement retries are mutually exclusive; success no longer leaves retry siblings queued.
7. Third exact placement failure relocates the home candidate.
