package muCkk.DeathAndRebirth;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import muCkk.DeathAndRebirth.config.DARProperties;
import muCkk.DeathAndRebirth.messages.DARErrors;
import muCkk.DeathAndRebirth.messages.DARMessages;
import muCkk.DeathAndRebirth.messages.Messages;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.config.Configuration;

public class DARHandler {

	private String dir;
	private HashSet<String> flyingPlayers;
	private File ghostsFile;
	private boolean permissions;
	private HashMap<String, ItemStack[]> dropManager;
		
	private DARProperties config;
	private DARGraves graves;
	private DARSpout spout;
	private DARMessages message;
	
	private Configuration yml;
	
	public DARHandler(String dir, String fileName, DARProperties config, DARGraves graves, boolean permissions, DARSpout spout, DARMessages message) {
		this.dir = dir;
		this.ghostsFile = new File(fileName);
		this.config = config;
		this.graves = graves;
		this.permissions = permissions;
		this.spout = spout;
		this.message = message;
		flyingPlayers = new HashSet<String>();
		dropManager = new HashMap<String, ItemStack[]>();
	}
	
	
	/**
	 * Loads saved data from a file
	 */
	public void load() {
		if(!ghostsFile.exists()){
            try {
            	new File(dir).mkdir();
                ghostsFile.createNewFile(); 
            } catch (Exception e) {
            	DARErrors.couldNotReadGhostFile();
            	e.printStackTrace();
            }
        } else {
        	DARErrors.gravesLoaded();
        }
		try {
            yml = new Configuration(ghostsFile);
            yml.load();
        } catch (Exception e) {
        	DARErrors.couldNotReadGhostFile();
        	e.printStackTrace();
        }
	}

	/**
	 * Saves the current information to a file
	 */
	public void save() {
		yml.save();
	}
	
	/**
	 * Adds a new player to the ghost file
	 * @param player that will be added
	 */
	public void newPlayer(Player player) {
		if(existsPlayer(player)) {
			return;
		}
		String pname = player.getName();
		String world = player.getWorld().getName();
				
		yml.setProperty("players." +pname +"."+world +".dead", false);
		yml.setProperty("players." +pname +"."+world +".location.x", player.getLocation().getBlockX());
		yml.setProperty("players." +pname +"."+world +".location.y", player.getLocation().getBlockY());
		yml.setProperty("players." +pname +"."+world +".location.z", player.getLocation().getBlockZ());
		yml.setProperty("players." +pname +"."+world +".world", world);
		
		yml.save();
	}
	
	/**
	 * Checks if the player is already listed in the ghosts file	
	 * @param player to be checked
	 * @return boolean
	 */
	public boolean existsPlayer(Player player) {
		List<String> names = yml.getKeys("players");
		String pname = player.getName();
		
		try {
			for (String name : names) {
				if(name.equalsIgnoreCase(pname)) {
					return true;
				}
			}
		}catch (NullPointerException e) {
			return false;
		}
		return false;
	}
	
	/**
	 * Checks if a player is dead
	 * @param player which is checked
	 * @return The state of the player (dead/alive).
	 */
	public boolean isGhost(Player player) {
		if(player == null) {
			return false;
		}
		String pname = player.getName();
		String world = player.getWorld().getName();
		
		try {
			return yml.getBoolean("players."+pname +"."+world +".dead", false);	
		}catch (NullPointerException e) {
			return false;
		}		
	}
	
	/**
	 * Manages the death of players.
	 * @param player which died
	 */
	public void died(Player player, ItemStack [] drops) {
		String pname = player.getName();
		String world = player.getWorld().getName();
		
		yml.setProperty("players."+pname +"."+world +".dead", true);
		
		Block block = player.getWorld().getBlockAt(player.getLocation());
		
		if (config.isLightningDEnabled()) {
			player.getWorld().strikeLightningEffect(player.getLocation());
		}
		
		yml.setProperty("players."+pname +"."+world +".location.x", block.getX());
		yml.setProperty("players."+pname +"."+world +".location.y", block.getY());
		yml.setProperty("players."+pname +"."+world +".location.z", block.getZ());		
		save();
		
		if (!config.isDroppingEnabled()) {
			dropManager.put(pname, drops);
		}
		
	// *** change the displayname **************************************
		String [] ghostName = config.getGhostName().split("%");
		if(ghostName.length == 3) {
			player.setDisplayName(ghostName[0] + player.getName() + ghostName[2]);
		}
		if(ghostName.length == 2) {
			player.setDisplayName(ghostName[0] + player.getName());
		}
		if(ghostName.length == 1) {
			player.setDisplayName(ghostName[0]);
		}
		else DARErrors.ghostNameWrong();
		
	// *** spout stuff *******************************************
		if (config.isSpoutEnabled()) {
			spout.playerDied(player, config.getDeathSound());
		}
	// *******************
		
	// *** nocheat stuff *****************************************
		if(config.isNoCheatEnabled() && permissions) {
			if (!DAR.permissionHandler.has(player, "nocheat.flying")) {
				DAR.permissionHandler.addUserPermission(world, pname, "nocheat.flying");
				DAR.permissionHandler.addUserPermission(world, pname, "nocheat.moving");
				DAR.permissionHandler.saveAll();
				DAR.permissionHandler.reload();
			}
			else {
				flyingPlayers.add(pname);
			}
		}
	// *** grave stuff ******************************************************************
		Location location = player.getLocation();
		location.getBlock().setType(Material.SIGN_POST);
		Sign sign = (Sign) location.getBlock().getState();
		String l1 = "R.I.P";
		sign.setLine(1, l1);
		sign.setLine(2, pname);
		sign.update(true);
		graves.addGrave(pname,block.getX(), block.getY(), block.getZ(), l1, pname, world);
	}
	
	/**
	 * Brings players back to life
	 * @param player to be resurrected
	 */
	public void resurrect(Player player) {
		String pname = player.getName();
		String world = player.getWorld().getName();
		
		yml.setProperty("players."+pname +"."+world +".dead", false);
		player.getWorld().getBlockAt(getLocation(player)).setType(Material.AIR);
		graves.deleteGrave(pname, world);
		message.send(player, Messages.reborn);
		
		if (config.isLightningREnabled()) {
			player.getWorld().strikeLightningEffect(player.getLocation());
		}
		
	// *** spout stuff ***********************************************
		if (config.isSpoutEnabled()) {
			spout.playerRes(player, config.getResSound());
		}
	// *** nocheat stuff **********************************************
		if(config.isNoCheatEnabled() && permissions) {
			if(!flyingPlayers.contains(pname)) {
				DAR.permissionHandler.removeUserPermission(world, pname, "nocheat.flying");
				DAR.permissionHandler.removeUserPermission(world, pname, "nocheat.moving");
				DAR.permissionHandler.saveAll();
				DAR.permissionHandler.reload();
			}
		}
	// *******************************************************************
		player.setDisplayName(pname);
		if(!config.isDroppingEnabled()) {
			PlayerInventory inv = player.getInventory();
			try {
				for (ItemStack item : dropManager.get(pname)) {
					if (item == null) continue;
					inv.addItem(item);
				}
			}catch(NullPointerException e) {
				// empty inventory on death
			}
		}
		save();
	}
	
	/**
	 * Called when a player tries to resurrect someone.
	 * @param player who tries to resurrect someone
	 * @param target player to be resurrected
	 */
	public void resurrect(Player player, Player target) {
		// *** check distance ***
		double distance = player.getLocation().distance(target.getLocation());
		if(distance > config.getInteger("distance")) {
			message.send(player, Messages.tooFarAway);
			return;
		}
		
		// *** check items ***
		if (config.getBoolean("needItem")) {
			int itemID = config.getInteger("itemID");
			int amount = config.getInteger("amount");
			
			ItemStack costStack = new ItemStack(itemID);
			costStack.setAmount(amount);
			
			if(!ConsumeItems(player, costStack)) {
				player.sendMessage("You need "+amount +" "+Material.getMaterial(itemID).name() +" to resurrect someone.");
				return;
			}
		}		
		resurrect(target);
		message.send(player, Messages.resurrected);
		target.teleport(getLocation(target));
		
		// *** Spout stuff ***
		if (config.isSpoutEnabled()) {
			spout.playResSound(player, config.getResSound());
		}		
	}
	
	/**
	 * The location of death
	 * @param player which is checked
	 * @return Location of death
	 */
	public Location getLocation(Player player) {
		String pname = player.getName();
		World world = player.getWorld();
		String worldName = world.getName();
		
		double x = yml.getDouble("players."+pname +"."+worldName +".location.x", 0);
		double y = yml.getDouble("players."+pname +"."+worldName +".location.y", 64);
		double z = yml.getDouble("players."+pname +"."+worldName +".location.z", 0);
		Location loc = new Location(world, x, y, z);
		return loc;
	}

	

    /**
     * binds the players soul to a shrine
     * @param player
     */
	public void bindSoul(Player player) {
		String pname = player.getName();
		String world = player.getWorld().getName();
		Location loc = player.getLocation();
		
		yml.setProperty("players." +pname +"."+world +".shrine.x", loc.getX());
		yml.setProperty("players." +pname +"."+world +".shrine.y", loc.getY());
		yml.setProperty("players." +pname +"."+world +".shrine.z", loc.getZ());
		save();
	}
	
	/**
	 * Gets the shrine the player clicked
	 * @param player to be checked
	 * @return Location of the place the player was standing when he clicked the shrine
	 */
	public Location getBoundShrine(Player player) {
		String pname = player.getName();
		World world = player.getWorld();
		String worldName = world.getName();
		
		// *** check if a shrine is saved ***
		Object doesShrineExists = yml.getProperty("players."+pname +"."+worldName +".shrine.x");
		if (doesShrineExists == null) {
			return null;
		}
		
		double x = yml.getDouble("players."+pname +"."+worldName +".shrine.x", 0);
		double y = yml.getDouble("players."+pname +"."+worldName +".shrine.y", 64);
		double z = yml.getDouble("players."+pname +"."+worldName +".shrine.z", 0);
		Location loc = new Location(world, x, y, z);	
		return loc;
	}

	/**
	 * Called when a player teleports or enters a portal.
	 * Checks if the player enters a world where he has never been before.
	 * @param player who teleported or used a portal
	 */
	public void worldChange(Player player) {
		String worldName = player.getWorld().getName();
		String name = player.getName();
		List<String> worlds = yml.getKeys("players."+name);
		
		try {
			for (String world : worlds) {
				if(world.equalsIgnoreCase(worldName)) {
					return;
				}
			}
		}catch (NullPointerException e) {
			worldChangeHelper(name, worldName, player.getLocation());
			return;
		}
		worldChangeHelper(name, worldName, player.getLocation());
	}
	
	public String getGrave(Player player) {
		String name = player.getName();
		String world = player.getWorld().getName();
		String x,y,z;
		try {
			x = yml.getString("players." +name +"."+world +".location.x");
			y = yml.getString("players." +name +"."+world +".location.y");
			z = yml.getString("players." +name +"."+world +".location.z");
		}catch(NullPointerException e) {
			return Messages.youHaveNoGrave.msg();
		}
		return Messages.yourGraveIsHere +": "+x +", "+y+", "+z;
	}
	
// *** private methods ************************************************************************************************************
	private void worldChangeHelper(String playerName, String worldName, Location location) {
		yml.setProperty("players." +playerName +"."+worldName +".dead", false);
		yml.setProperty("players." +playerName +"."+worldName +".location.x", location.getBlockX());
		yml.setProperty("players." +playerName +"."+worldName +".location.y", location.getBlockY());
		yml.setProperty("players." +playerName +"."+worldName +".location.z", location.getBlockZ());
		yml.setProperty("players." +playerName +"."+worldName +".world", worldName);
		
		yml.save();
	}
		
	// **************************************************************
	// *** code from DwarfCraft, found on the bukkit forum - THX! ***
	// **************************************************************	
	private boolean CheckItems(Player player, ItemStack costStack)
	{
	    //make sure we have enough
	    int cost = costStack.getAmount();
	    boolean hasEnough=false;
	    for (ItemStack invStack : player.getInventory().getContents())
	    {
	        if(invStack == null)
	            continue;
	        if (invStack.getTypeId() == costStack.getTypeId()) {
	
	            int inv = invStack.getAmount();
	            if (cost - inv >= 0) {
	                cost = cost - inv;
	            } else {
	                hasEnough=true;
	                break;
	            }
	        }
	    }
	    return hasEnough;
	}
	private boolean ConsumeItems(Player player, ItemStack costStack)
	{
	    if (!CheckItems(player,costStack)) return false;
	    //Loop though each item and consume as needed. We should of already
	    //checked to make sure we had enough with CheckItems.
	    for (ItemStack invStack : player.getInventory().getContents())
	    {
	        if(invStack == null)
	            continue;
	
	        if (invStack.getTypeId() == costStack.getTypeId()) {
	            int inv = invStack.getAmount();
	            int cost = costStack.getAmount();
	            if (cost - inv >= 0) {
	                costStack.setAmount(cost - inv);
	                player.getInventory().remove(invStack);
	            } else {
	                costStack.setAmount(0);
	                invStack.setAmount(inv - cost);
	                break;
	            }
	        }
	    }
	    return true;
	}
}
