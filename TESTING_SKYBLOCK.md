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
