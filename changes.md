## Changes from regular version (1.13+)
* Added NBTEditor dependency for NBT tags https://www.spigotmc.org/threads/1-8-1-15-v7-13-maven-single-class-nbt-editor-for-items-skulls-mobs-and-tile-entities.269621/
* WE BlockVector3 -> BlockVector (some may be Vector)
* WE BlockVector2 -> BlockVector2D
* WG Flags -> DefaultFlag
* Removed head support
* Removed chorus fruit teleportation check
* Removed support for protection stones being used as a crafting recipe ingredient
* Removed adding WG profiles on startup (random chance of an error) 
* Remove SpongePhysicsEvent listener since BlockPhysicsEvent should do it (and it doesn't work for some reason) 
* Removed WG profiles for UUID cache (no squirrelID)
* Removed javadoc plugin
* Added several utility classes using reflection for /ps view particles, and NBT data manipulation

## Bugs dealt with
* /ps view particles only show up at a certain range from where the player originally called /ps view
* /ps flag clickable text GUI causes client to crash
    * Fixed by adding "" in the TextComponent for flagLine constructor (requires a String to start with)
    * Added an extra ChatColor.WHITE before the flag group (colour didn't inherit for some reason?)
