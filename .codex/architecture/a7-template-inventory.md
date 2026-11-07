# A7 vanilla template inventory — research draft

Planning evidence only. No production classifier is installed. Counts are unique resources, not generated building instances. Classification uses resource identity/pool membership and still needs content review.

Source: `build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar`
SHA-256: `8e9cf6f4b6eafb1c6bf745a67a61dd1736f31865cad409e5b2bec918667dab5e`

Count selected house/workplace instances after original layout selection. Do not count weights or empty entries. Farms, animal pens and stables are approved exclusions; accessory pieces and meeting points are candidate exclusions as decorations. Approved scope excludes abandoned villages entirely: they receive no A7 content. Abandoned-start totals below support detection, not bank planning.

| Style | House/workplace candidates | Exclusion candidates | Unresolved | Empty entries | Normal / abandoned starts |
| --- | ---: | ---: | ---: | ---: | ---: |
| plains | 26 | 10 | 0 | 1 | 4 / 4 |
| desert | 23 | 5 | 0 | 1 | 3 / 3 |
| savanna | 25 | 6 | 0 | 1 | 4 / 4 |
| snowy | 26 | 4 | 0 | 1 | 3 / 3 |
| taiga | 23 | 4 | 0 | 1 | 2 / 2 |

Roads, terminators, decorations, entity spawns and town-center-only pieces are outside the candidate inclusion set. Unknown/list/feature elements need explicit handling. Never classify solely by folder name or count sub-elements without checking whether they represent a single building.


## plains

Source pools: `minecraft:village/plains/houses`, `minecraft:village/plains/town_centers`.

### Candidate house/workplace

- `minecraft:village/plains/houses/plains_armorer_house_1`
- `minecraft:village/plains/houses/plains_big_house_1`
- `minecraft:village/plains/houses/plains_butcher_shop_1`
- `minecraft:village/plains/houses/plains_butcher_shop_2`
- `minecraft:village/plains/houses/plains_cartographer_1`
- `minecraft:village/plains/houses/plains_fisher_cottage_1`
- `minecraft:village/plains/houses/plains_fletcher_house_1`
- `minecraft:village/plains/houses/plains_library_1`
- `minecraft:village/plains/houses/plains_library_2`
- `minecraft:village/plains/houses/plains_masons_house_1`
- `minecraft:village/plains/houses/plains_medium_house_1`
- `minecraft:village/plains/houses/plains_medium_house_2`
- `minecraft:village/plains/houses/plains_shepherds_house_1`
- `minecraft:village/plains/houses/plains_small_house_1`
- `minecraft:village/plains/houses/plains_small_house_2`
- `minecraft:village/plains/houses/plains_small_house_3`
- `minecraft:village/plains/houses/plains_small_house_4`
- `minecraft:village/plains/houses/plains_small_house_5`
- `minecraft:village/plains/houses/plains_small_house_6`
- `minecraft:village/plains/houses/plains_small_house_7`
- `minecraft:village/plains/houses/plains_small_house_8`
- `minecraft:village/plains/houses/plains_tannery_1`
- `minecraft:village/plains/houses/plains_temple_3`
- `minecraft:village/plains/houses/plains_temple_4`
- `minecraft:village/plains/houses/plains_tool_smith_1`
- `minecraft:village/plains/houses/plains_weaponsmith_1`

### Candidate exclusion

- `minecraft:village/plains/houses/plains_accessory_1`
- `minecraft:village/plains/houses/plains_animal_pen_1`
- `minecraft:village/plains/houses/plains_animal_pen_2`
- `minecraft:village/plains/houses/plains_animal_pen_3`
- `minecraft:village/plains/houses/plains_large_farm_1`
- `minecraft:village/plains/houses/plains_meeting_point_4`
- `minecraft:village/plains/houses/plains_meeting_point_5`
- `minecraft:village/plains/houses/plains_small_farm_1`

- `minecraft:village/plains/houses/plains_stable_1`
- `minecraft:village/plains/houses/plains_stable_2`


## desert

Source pools: `minecraft:village/desert/houses`, `minecraft:village/desert/town_centers`.

### Candidate house/workplace

- `minecraft:village/desert/houses/desert_armorer_1`
- `minecraft:village/desert/houses/desert_butcher_shop_1`
- `minecraft:village/desert/houses/desert_cartographer_house_1`
- `minecraft:village/desert/houses/desert_fisher_1`
- `minecraft:village/desert/houses/desert_fletcher_house_1`
- `minecraft:village/desert/houses/desert_library_1`
- `minecraft:village/desert/houses/desert_mason_1`
- `minecraft:village/desert/houses/desert_medium_house_1`
- `minecraft:village/desert/houses/desert_medium_house_2`
- `minecraft:village/desert/houses/desert_shepherd_house_1`
- `minecraft:village/desert/houses/desert_small_house_1`
- `minecraft:village/desert/houses/desert_small_house_2`
- `minecraft:village/desert/houses/desert_small_house_3`
- `minecraft:village/desert/houses/desert_small_house_4`
- `minecraft:village/desert/houses/desert_small_house_5`
- `minecraft:village/desert/houses/desert_small_house_6`
- `minecraft:village/desert/houses/desert_small_house_7`
- `minecraft:village/desert/houses/desert_small_house_8`
- `minecraft:village/desert/houses/desert_tannery_1`
- `minecraft:village/desert/houses/desert_temple_1`
- `minecraft:village/desert/houses/desert_temple_2`
- `minecraft:village/desert/houses/desert_tool_smith_1`
- `minecraft:village/desert/houses/desert_weaponsmith_1`

### Candidate exclusion

- `minecraft:village/desert/houses/desert_animal_pen_1`
- `minecraft:village/desert/houses/desert_animal_pen_2`
- `minecraft:village/desert/houses/desert_farm_1`
- `minecraft:village/desert/houses/desert_farm_2`
- `minecraft:village/desert/houses/desert_large_farm_1`


## savanna

Source pools: `minecraft:village/savanna/houses`, `minecraft:village/savanna/town_centers`.

### Candidate house/workplace

- `minecraft:village/savanna/houses/savanna_armorer_1`
- `minecraft:village/savanna/houses/savanna_butchers_shop_1`
- `minecraft:village/savanna/houses/savanna_butchers_shop_2`
- `minecraft:village/savanna/houses/savanna_cartographer_1`
- `minecraft:village/savanna/houses/savanna_fisher_cottage_1`
- `minecraft:village/savanna/houses/savanna_fletcher_house_1`
- `minecraft:village/savanna/houses/savanna_library_1`
- `minecraft:village/savanna/houses/savanna_mason_1`
- `minecraft:village/savanna/houses/savanna_medium_house_1`
- `minecraft:village/savanna/houses/savanna_medium_house_2`
- `minecraft:village/savanna/houses/savanna_shepherd_1`
- `minecraft:village/savanna/houses/savanna_small_house_1`
- `minecraft:village/savanna/houses/savanna_small_house_2`
- `minecraft:village/savanna/houses/savanna_small_house_3`
- `minecraft:village/savanna/houses/savanna_small_house_4`
- `minecraft:village/savanna/houses/savanna_small_house_5`
- `minecraft:village/savanna/houses/savanna_small_house_6`
- `minecraft:village/savanna/houses/savanna_small_house_7`
- `minecraft:village/savanna/houses/savanna_small_house_8`
- `minecraft:village/savanna/houses/savanna_tannery_1`
- `minecraft:village/savanna/houses/savanna_temple_1`
- `minecraft:village/savanna/houses/savanna_temple_2`
- `minecraft:village/savanna/houses/savanna_tool_smith_1`
- `minecraft:village/savanna/houses/savanna_weaponsmith_1`
- `minecraft:village/savanna/houses/savanna_weaponsmith_2`

### Candidate exclusion

- `minecraft:village/savanna/houses/savanna_animal_pen_1`
- `minecraft:village/savanna/houses/savanna_animal_pen_2`
- `minecraft:village/savanna/houses/savanna_animal_pen_3`
- `minecraft:village/savanna/houses/savanna_large_farm_1`
- `minecraft:village/savanna/houses/savanna_large_farm_2`
- `minecraft:village/savanna/houses/savanna_small_farm`


## snowy

Source pools: `minecraft:village/snowy/houses`, `minecraft:village/snowy/town_centers`.

### Candidate house/workplace

- `minecraft:village/snowy/houses/snowy_armorer_house_1`
- `minecraft:village/snowy/houses/snowy_armorer_house_2`
- `minecraft:village/snowy/houses/snowy_butchers_shop_1`
- `minecraft:village/snowy/houses/snowy_butchers_shop_2`
- `minecraft:village/snowy/houses/snowy_cartographer_house_1`
- `minecraft:village/snowy/houses/snowy_fisher_cottage`
- `minecraft:village/snowy/houses/snowy_fletcher_house_1`
- `minecraft:village/snowy/houses/snowy_library_1`
- `minecraft:village/snowy/houses/snowy_masons_house_1`
- `minecraft:village/snowy/houses/snowy_masons_house_2`
- `minecraft:village/snowy/houses/snowy_medium_house_1`
- `minecraft:village/snowy/houses/snowy_medium_house_2`
- `minecraft:village/snowy/houses/snowy_medium_house_3`
- `minecraft:village/snowy/houses/snowy_shepherds_house_1`
- `minecraft:village/snowy/houses/snowy_small_house_1`
- `minecraft:village/snowy/houses/snowy_small_house_2`
- `minecraft:village/snowy/houses/snowy_small_house_3`
- `minecraft:village/snowy/houses/snowy_small_house_4`
- `minecraft:village/snowy/houses/snowy_small_house_5`
- `minecraft:village/snowy/houses/snowy_small_house_6`
- `minecraft:village/snowy/houses/snowy_small_house_7`
- `minecraft:village/snowy/houses/snowy_small_house_8`
- `minecraft:village/snowy/houses/snowy_tannery_1`
- `minecraft:village/snowy/houses/snowy_temple_1`
- `minecraft:village/snowy/houses/snowy_tool_smith_1`
- `minecraft:village/snowy/houses/snowy_weapon_smith_1`

### Candidate exclusion

- `minecraft:village/snowy/houses/snowy_animal_pen_1`
- `minecraft:village/snowy/houses/snowy_animal_pen_2`
- `minecraft:village/snowy/houses/snowy_farm_1`
- `minecraft:village/snowy/houses/snowy_farm_2`


## taiga

Source pools: `minecraft:village/taiga/houses`, `minecraft:village/taiga/town_centers`.

### Candidate house/workplace

- `minecraft:village/taiga/houses/taiga_armorer_2`
- `minecraft:village/taiga/houses/taiga_armorer_house_1`
- `minecraft:village/taiga/houses/taiga_butcher_shop_1`
- `minecraft:village/taiga/houses/taiga_cartographer_house_1`
- `minecraft:village/taiga/houses/taiga_fisher_cottage_1`
- `minecraft:village/taiga/houses/taiga_fletcher_house_1`
- `minecraft:village/taiga/houses/taiga_library_1`
- `minecraft:village/taiga/houses/taiga_masons_house_1`
- `minecraft:village/taiga/houses/taiga_medium_house_1`
- `minecraft:village/taiga/houses/taiga_medium_house_2`
- `minecraft:village/taiga/houses/taiga_medium_house_3`
- `minecraft:village/taiga/houses/taiga_medium_house_4`
- `minecraft:village/taiga/houses/taiga_shepherds_house_1`
- `minecraft:village/taiga/houses/taiga_small_house_1`
- `minecraft:village/taiga/houses/taiga_small_house_2`
- `minecraft:village/taiga/houses/taiga_small_house_3`
- `minecraft:village/taiga/houses/taiga_small_house_4`
- `minecraft:village/taiga/houses/taiga_small_house_5`
- `minecraft:village/taiga/houses/taiga_tannery_1`
- `minecraft:village/taiga/houses/taiga_temple_1`
- `minecraft:village/taiga/houses/taiga_tool_smith_1`
- `minecraft:village/taiga/houses/taiga_weaponsmith_1`
- `minecraft:village/taiga/houses/taiga_weaponsmith_2`

### Candidate exclusion

- `minecraft:village/taiga/houses/taiga_animal_pen_1`
- `minecraft:village/taiga/houses/taiga_large_farm_1`
- `minecraft:village/taiga/houses/taiga_large_farm_2`
- `minecraft:village/taiga/houses/taiga_small_farm_1`

