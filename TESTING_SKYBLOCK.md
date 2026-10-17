# Testing SkyBlock Mode on Hypixel

A checklist for your first SkyBlock sessions with Hollow Client 2.x. Everything here was built and tested without
Hypixel (unit tests with realistic text, the singleplayer simulator, screenshots), so this is where the real server
gets checked. Go top to bottom; each area builds on the ones before it.

**If something is wrong:** turn on the debug recorder (Hub → Record session, or `/hollow sbrec on`), do the thing
again, stop it (`/hollow sbrec off`) and send the file from `hollow-debug/` (in the 26.2 profile's game folder). For a
wrong item, hover it in a menu and press the "save hovered item" key (set it in Controls → Hollow Client), or hold it
and use `/hollow sbdump`. Other players' chat is never recorded and names are replaced.

## 0. Setup checker (do this first)

1. Launch the **Hollow 26.2** profile (or press **Join Hypixel**), join SkyBlock (`/skyblock`).
2. You should see a **SkyBlock Mode** toast with the SkyBlock badge.
3. Press **H** → **Setup checker**. Expected: Hypixel Mod API ✓, SkyBlock Mode on ✓, SkyBlock detected ✓ ("from the
   Hypixel Mod API"), Profile ✓ (your profile name; Ironman/Stranded/Bingo shown if you play one), Prices ✓ after a
   minute, Item repository ✓ after a few minutes (one ~30 MB download).
4. **Tab list widgets:** if it says something is missing, find Hypixel's tab widget settings and turn it on.
   - [ ] Confirm where those settings are (the checker says: SkyBlock Menu → Settings → player list / tab widgets).
     If the path is different, note the real one.
   - [ ] Some widgets are only checked on their island: Visitors in the Garden; Commissions, Powders and Forges in
     the Dwarven Mines; Commissions, Powders and Crystals in the Crystal Hollows. Run the checker there too.
5. `/hollow sbstate` prints what SkyBlock Mode knows: island, area, profile, purse, bits.

## 1. Foundation

- [ ] Changing islands (warp around) updates the island in `/hollow sbstate` and the SkyBlock Info HUD.
- [ ] SkyBlock Info HUD (top left): island · area, SkyBlock date and time, purse (gold), bits (aqua); motes in the
  Rift, copper on the Garden.
- [ ] SkyBlock HUD layout: move a HUD element while on SkyBlock (Right Shift → Edit HUD), leave to the lobby: your
  normal layout comes back; rejoin SkyBlock: the SkyBlock layout comes back.
- [ ] Session HUD (switch it on in Right Shift → SkyBlock): purse change, bits, skill XP and rare drops add up.
- [ ] The launcher's SkyBlock page shows "Last session" after you've played a few minutes.
- [ ] Alerts: a rare drop shows the "RARE DROP!" title with a sound; a pet drop the pet one. Right Shift → H → Alerts:
  switch one off, change a sound, add your own (e.g. pattern `Fairy Soul`, title `Soul!`) and trigger it.
- [ ] Hub (H): session, coming up (compare with the in-game calendar), held item value (hold an item you know the price
  of), mayor, latest events.
- [ ] Feature health: every switched-on feature shows ✓ where its data should be.
- [ ] Compatibility (only if you also use Skyblocker, SkyHanni or Firmament): their names are listed.
- [ ] Chat from other players never fires an alert (ask a friend to type "RARE DROP! test").

## 2. General & HUD (Right Shift → SkyBlock → General)

Switch the features on in the menu first (they start off, except SkyBlock Info).

- [ ] **Status Bars**: health/mana/defense bars match the action bar; overheal shows gold; overflow mana shows "+N";
  EHP looks sensible. With "Take these out of the action bar" on, the action bar only shows the rest (skill XP etc.).
- [ ] **Hide Vanilla Bars**: hearts, armor and food are gone on SkyBlock and come back in lobbies.
- [ ] **Pet**: name and level match your pet; XP progress (or MAX LEVEL). Needs the tab list's Pet widget.
- [ ] **Next Events**: compare with the in-game calendar (Dark Auction, Jacob's contest, festivals).
- [ ] **Deployables**: place a power orb (or stand near someone's): its name and seconds show and count down.
  If nothing shows, record a few seconds next to an orb and send the file (the name format is a guess).
- [ ] **Compact Damage**: big damage numbers show as 1.2M / 45.7k; crits keep their stars. If not, record a fight.
- [ ] **Clutter Hider**: in the Hub other players disappear, NPCs stay, party members stay; lightning is gone.
- [ ] **Fairy Souls**: beams lead to souls; after you click one ("SOUL! You found a Fairy Soul!") its beam goes away.
  `/hollow sbsouls found` marks every soul on the island as found (if you got them before Hollow).

## 3. Items, inventory & GUI (Right Shift → SkyBlock → Items)

- [ ] **Price tooltips**: hover a bazaar item (bazaar buy/sell), an auction item (lowest BIN), a pet (lowest BIN for its
  rarity and level), a craftable item (craft cost). Hold Shift on an upgraded weapon: the breakdown adds up to
  "Estimated value". Donated items say "Donated to the Museum".
- [ ] **Slot info**: rarity backgrounds in your inventory, chests and menus; pet levels on pets, ✪ on starred items,
  short names on enchanted books (e.g. UWV), roman tiers on minions.
- [ ] **Item protection**: set the lock key (Controls → Hollow Client → "SkyBlock: lock / unlock item"), lock a
  weapon (gold padlock appears). Try: Q in hand, Q in the inventory, dropping it outside the inventory, selling it to
  an NPC, salvaging it, putting it up for auction. All should be blocked with a red message. Unlock and it works again.
  - [ ] If a sell/salvage menu isn't protected, record opening it (`/hollow sbrec on`) so its title can be added.
- [ ] **Menu tools**: the search box under a chest dims non-matching items; the buttons above your inventory run
  /pets, /storage, /wardrobe, /ah, /bz, /craft (change the list in the module settings).
- [ ] **Experiment helpers** (Experimentation Table): Ultrasequencer numbers stay on the tiles after they hide, with
  the next one outlined; Chronomatron outlines the color to press next; Superpairs writes the items you've seen on
  covered tiles. If nothing shows, record one game (`/hollow sbrec on`) and send it.

## 4. Economy (Right Shift → SkyBlock → Economy)

- [ ] **Bazaar Orders**: set up a buy order and a sell offer: both appear in the HUD. When one fills you get the
  "filled" title + toast and the HUD marks it ✔; claiming it removes it. If the HUD stays empty, record placing an
  order (`/hollow sbrec on`) and send it (the chat format is a guess).
- [ ] Bazaar tooltips: after a few hours the 24h sparkline appears; the flip margin looks right (buy × 0.9875 − sell).
- [ ] **Auctions**: in the auction browser, cheap BIN listings get a green corner, overpriced ones a red corner.
  When one of your auctions sells you get an alert and the HUD shows it with the coins to collect.
- [ ] **Coins per Hour**: grows while you earn; `/hollow sbprofit reset` starts it over.

## 5. Garden (Right Shift → SkyBlock → Garden)

- [ ] **Garden HUD** on the Garden: level and copper right; visitors match the tab list ("full" at 5); when pests
  spawn the count and plot appear and the "PESTS" title plays; after killing them it goes back to none (needs the
  tab list's pest widget). Record a pest spawn if the plot is wrong.
- [ ] **Visitor Offer**: talk to a visitor: the HUD lists what they want, the bazaar cost and coins per copper. If it
  doesn't show, record opening a visitor (the menu format is a guess).
- [ ] **Jacob's Contests**: times and crops match the in-game Jacob's calendar.
- [ ] **Farming HUD**: blocks/s while farming (about 20 at max speed), your tool's counter going up with a rate per
  hour, yaw/pitch.
- [ ] **Composter**: shows the Composter widget (turn it on in the tab widget settings if missing).

## 6. Dungeons (Right Shift → SkyBlock → Dungeons)

- [ ] **Dungeon HUD** in a run: floor right; secrets/crypts/deaths match the tab list; puzzles count down; keys show
  after someone picks one up and go away when the door opens; split times appear (blood open, Watcher, boss, end);
  the "S reached" / "S+ reached" titles come near the end score Hypixel gives. Note the HUD's score next to the
  real one after a few runs so the estimate can be tuned.
- [ ] **Dungeon Map**: the map from your hotbar on the HUD with the markers it has.
- [ ] **Terminal Helper** (F7/M7): each terminal outlines the right slots; "same color" writes click counts. If a
  terminal shows nothing, record opening it (the title may differ).
- [ ] **Blaze Puzzle**: lowest and highest health match what you see.
- [ ] **Spirit Leap Classes**: class letters on the heads in the leap menu.
- [ ] **Reward Chest Value**: in a reward chest, value vs cost.

## 7. Slayers (Right Shift → SkyBlock → Slayers)

- [ ] **Slayer HUD**: start a quest at Maddox (or the Batphone). The HUD shows the boss and tier and the progress with
  a percent that matches the scoreboard. When the boss spawns, its name/health tag and timer show under it, and
  the fight time counts up.
- [ ] After a kill: bosses, average time and (after a minute) bosses per hour. A rare drop right after the kill adds
  to "Drops" (needs prices loaded). A drop outside slayers (dungeons, fishing) must not be added.
- [ ] **Boss Highlight** (off by default): a soft box around your boss only, never someone else's; walls hide it.
  If nothing is found, record a boss fight with the debug recorder: the "Spawned by" tag may sit somewhere else.
- [ ] Fail a quest (let the timer run out): the fight time stops and no kill is counted.

## 8. Mining (Right Shift → SkyBlock → Mining)

Enable the **Commissions**, **Powders**, **Forges** and **Crystals** widgets in `/tablist` first (the setup checker
lists them).

- [ ] **Mining HUD** in the Dwarven Mines: commissions match the tab list; finishing one shows "Commission done" once
  (arriving with one already done doesn't). A mining event shows with its time left.
- [ ] Glacite Tunnels: Cold matches the scoreboard; a warning title at -75. Crystal Hollows Magma Fields: Heat shows.
- [ ] **Sky Mall**: the next morning's buff line shows on the HUD.
- [ ] **Powder HUD**: totals match; mining adds to "+gained" and after a minute shows per hour. Spending powder in the
  HotM tree doesn't lower the gain.
- [ ] **Pickaxe Ability**: using Mining Speed Boost starts a "~2m" countdown; it switches to "ready" when Hypixel says
  "is now available!".
- [ ] **Forge HUD**: put something in the Forge; the HUD shows it with the time left, keeps counting after a relog, and
  "Forge … is done" pops up when it finishes.
- [ ] **Crystals HUD** in the Crystal Hollows: matches the Crystals widget.
- [ ] **Metal Detector** in the Mines of Divan: hold the detector and walk to four or five spots a few blocks apart.
  The HUD says "Walk around a little" until then, then gives a guess with a ± and a soft column on the floor. Dig
  there; note how far off it was. If the HUD never shows, record the action bar with the debug recorder (the
  TREASURE format may differ).

## 9. Fishing (Right Shift → SkyBlock → Fishing)

- [ ] **Fishing HUD**: cast anywhere. The HUD shows how long the bobber has been out; when "!!!" shows over the
  bobber, "Fish on!" appears (you still reel in). If it never shows, record a cast with the debug recorder.
- [ ] "Sea creatures near you" goes up as you catch them and down as they die; it turns yellow at 10.
- [ ] Catch a Legendary or Mythic sea creature: a title, sound and toast, and it's listed with "×1 (… ago)". Note any
  that don't trigger (the message may differ from the wiki).
- [ ] Hit the sea creature cap: one "Sea creature cap" title, not repeated for 30 seconds.
- [ ] A double hook adds to "Double hooks" (if not, record the line).
- [ ] **Trophy Fish HUD** on the Crimson Isle: each trophy fish adds to this session's counts with the right tier.

## 10. Hunting (Right Shift → SkyBlock → Hunting)

- [ ] **Huntraps HUD** on Galatea: place a Huntrap; the HUD shows "Huntraps 1/5" and how long ago it went down. Place
  more; the count follows Hypixel's "(n/5 traps placed)".
- [ ] When Hypixel says "HUNT - n of your Hunting Traps caught something!", an alert pops up and the HUD says how many
  are waiting. Picking a trap up lowers both counts. Relog: the traps are still listed.

## 11. Crimson Isle and Kuudra (Right Shift → SkyBlock → Crimson Isle)

- [ ] **Vanquisher Alert**: when a Vanquisher spawns near you, one title, sound and toast (not repeated for the same
  one). If nothing happens, record its name tag with the debug recorder.
- [ ] **Reward Chest Value** in a Kuudra Paid Chest: value against cost, with the key's price included (needs the key
  on the auction house). Free Chest: value only.
- [ ] **Tab Widgets HUD** (General, off by default): type "Faction Quests, Reputation" (or any widget titles from
  your tab list) in its settings; those widgets show on screen as they are.

## 12. The Rift (Right Shift → SkyBlock → Rift)

- [ ] **Rift HUD**: the time left matches the action bar and counts down smoothly; it turns yellow under a minute and
  red under 20 seconds, with a warning title at each.
- [ ] Motes: picking up orbs or selling to the Motes Grubber adds to "+gained"; after a minute, per hour shows.

## 13. Events (Right Shift → SkyBlock → Events)

- [ ] **Diana HUD** (during the Mythological Ritual, in the Hub): digging a burrow shows "Burrow n/total" with your
  spade's chain length. A Minos Inquisitor, Sphinx, Manticore or King Minos near you gives one alert each.
- [ ] **Chocolate Factory Helper**: open /cf. One rabbit employee has a green outline: the one with the most chocolate
  per second for its cost. Check it against the costs shown (if no outline, record the menu: the cost line may differ).

## 14. Progression (Right Shift → SkyBlock → Progression)

- [ ] **Skill Progress HUD**: farm, mine or fight; the HUD shows the skill, its percent, XP gained and (after a minute)
  per hour and time to the next level.
- [ ] **Reminders**: `/hollow remind 1m test`; after a minute a "Reminder" title and toast show. `/hollow remind list`
  and `/hollow remind clear` work. A reminder set before quitting fires after you come back if its time has passed.
- [ ] **Booster Cookie Reminder** (off by default): with no cookie active, joining SkyBlock shows a toast.

## 15. Chat and party (Right Shift → SkyBlock → Chat)

- [ ] **Chat Cleanup** (off by default): with it on, ability damage lines, "There are blocks in the way!", mana and
  cooldown lines and Watchdog announcements stop showing; each category has its own switch. A friend typing the same
  words in chat still shows.
- [ ] **Party HUD** (needs the Hypixel Mod API): join a party; the HUD lists the others by name.
- [ ] **Share location**: bind "SkyBlock: share my location in party chat" in Controls; one press sends one
  "x: …, y: …, z: … (area)" line to party chat; pressing again within 3 seconds sends nothing.

