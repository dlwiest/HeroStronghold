package multitallented.redcastlemedia.bukkit.herostronghold;
/**
 *
 * @author Multitallented
 */
import multitallented.redcastlemedia.bukkit.herostronghold.checkregiontask.CheckRegionTask;
import multitallented.redcastlemedia.bukkit.herostronghold.region.RegionManager;
import multitallented.redcastlemedia.bukkit.herostronghold.region.Region;
import multitallented.redcastlemedia.bukkit.herostronghold.region.RegionType;
import multitallented.redcastlemedia.bukkit.herostronghold.region.SuperRegionType;
import multitallented.redcastlemedia.bukkit.herostronghold.region.SuperRegion;
import multitallented.redcastlemedia.bukkit.herostronghold.effect.EffectManager;
import com.herocraftonline.dev.heroes.Heroes;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import multitallented.redcastlemedia.bukkit.herostronghold.listeners.*;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class HeroStronghold extends JavaPlugin {
    private PluginServerListener serverListener;
    private Logger log;
    protected FileConfiguration config;
    private RegionManager regionManager;
    private RegionBlockListener blockListener;
    public static Economy econ;
    public static Permission perms;
    private RegionEntityListener regionEntityListener;
    private RegionPlayerInteractListener dpeListener;
    private Map<String, String> pendingInvites = new HashMap<String, String>();
    private ConfigManager configManager;
    private Map<String, List<String>> pendingCharters = new HashMap<String, List<String>>();
    
    @Override
    public void onDisable() {
        log = Logger.getLogger("Minecraft");
        log.info("[HeroStronghold] is now disabled!");
    }

    @Override
    public void onEnable() {
        //setup configs
        config = getConfig();
        config.options().copyDefaults(true);
        saveConfig();
        
        //Setup RegionManager
        regionManager = new RegionManager(this, config);
        
        setupPermissions();
        setupEconomy();
        
        //Register Listeners Here
        serverListener = new PluginServerListener(this);
        blockListener = new RegionBlockListener(this);
        dpeListener = new RegionPlayerInteractListener(regionManager);
        regionEntityListener = new RegionEntityListener(regionManager);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Type.BLOCK_BREAK, blockListener, Priority.Highest, this);
        pm.registerEvent(Type.BLOCK_PLACE, blockListener, Priority.High, this);
        pm.registerEvent(Type.BLOCK_DAMAGE, blockListener, Priority.High, this);
        pm.registerEvent(Type.BLOCK_FROMTO, blockListener, Priority.Normal, this);
        pm.registerEvent(Type.BLOCK_IGNITE, blockListener, Priority.High, this);
        pm.registerEvent(Type.BLOCK_BURN, blockListener, Priority.High, this);
        pm.registerEvent(Type.SIGN_CHANGE, blockListener, Priority.High, this);
        pm.registerEvent(Type.BLOCK_PISTON_EXTEND, blockListener, Priority.High, this);
        pm.registerEvent(Type.BLOCK_PISTON_RETRACT, blockListener, Priority.High, this);
        
        pm.registerEvent(Type.PLUGIN_ENABLE, serverListener, Priority.Monitor, this);
        pm.registerEvent(Type.PLUGIN_DISABLE, serverListener, Priority.Monitor, this);
        
        pm.registerEvent(Type.PAINTING_PLACE, regionEntityListener, Priority.High, this);
        pm.registerEvent(Type.ENDERMAN_PLACE, regionEntityListener, Priority.High, this);
        pm.registerEvent(Type.PAINTING_BREAK, regionEntityListener, Priority.High, this);
        pm.registerEvent(Type.EXPLOSION_PRIME, regionEntityListener, Priority.High, this);
        pm.registerEvent(Type.ENTITY_EXPLODE, regionEntityListener, Priority.High, this);
        pm.registerEvent(Type.ENDERMAN_PICKUP, regionEntityListener, Priority.High, this);
        pm.registerEvent(Type.ENTITY_DAMAGE, regionEntityListener, Priority.Monitor, this);
        pm.registerEvent(Type.ENTITY_DEATH, regionEntityListener, Priority.Monitor, this);
        
        pm.registerEvent(Type.PLAYER_INTERACT, dpeListener, Priority.High, this);
        pm.registerEvent(Type.PLAYER_BED_ENTER, dpeListener, Priority.High, this);
        pm.registerEvent(Type.PLAYER_BUCKET_FILL, dpeListener, Priority.High, this);
        pm.registerEvent(Type.PLAYER_BUCKET_EMPTY, dpeListener, Priority.High, this);
        pm.registerEvent(Type.PLAYER_CHAT, dpeListener, Priority.Normal, this);
        
        pm.registerEvent(Type.CUSTOM_EVENT, new CustomListener(regionManager), Priority.Normal, this);
        log = Logger.getLogger("Minecraft");
        
        //Check for Heroes
        log.info("[HeroStronghold] is looking for Heroes...");
        Plugin currentPlugin = pm.getPlugin("Heroes");
        if (currentPlugin != null) {
            log.info("[HeroStronghold] found Heroes!");
            serverListener.setupHeroes((Heroes) currentPlugin);
        } else {
            log.info("[HeroStronghold] didnt find Heroes, waiting for Heroes to be enabled.");
        }
        
        //TODO fix message lists by size
        
        new EffectManager(this);
        
        //Setup repeating sync task for checking regions
        CheckRegionTask theSender = new CheckRegionTask(getServer(), regionManager);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, theSender, 10L, 10L);
        
        System.currentTimeMillis();
        Date date = new Date();
        date.setSeconds(0);
        date.setMinutes(0);
        date.setHours(0);
        long timeUntilDay = (86400000 + date.getTime() - System.currentTimeMillis()) / 50;
        System.out.println("[HeroStronghold] " + timeUntilDay + " ticks until 00:00");
        DailyTimerTask dtt = new DailyTimerTask(this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, dtt, timeUntilDay, 1728000);
        
        log.info("[HeroStronghold] is now enabled!");
    }
    
    
    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[HeroStronghold] doesn't recognize non-player commands.");
            return true;
        }
        Player player = (Player) sender;
        
        if (args.length > 2 && args[0].equalsIgnoreCase("charter")) {
            
            //Check if valid super region
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            SuperRegionType currentRegionType = regionManager.getSuperRegionType(sr.getType());
            if (currentRegionType == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[1] + " isnt a valid region type");
                int j=0;
                String message = ChatColor.GOLD + "";
                for (String s : regionManager.getSuperRegionTypes()) {
                    if (perms == null || (perms.has(player, "herostronghold.create.all") ||
                            perms.has(player, "herostronghold.create." + s))) {
                        message += s + ", ";
                        if (j >= 2) {
                            player.sendMessage(message.substring(0, message.length() - 3));
                            message = ChatColor.GOLD + "";
                            j=-1;
                        }
                        j++;
                    }
                }
                if (j!= 0)
                    player.sendMessage(message.substring(0, message.length() - 3));
                return true;
            }
            
            String regionTypeName = args[1];
            //Permission Check
            if (perms != null && !perms.has(player, "herostronghold.create.all") &&
                    !perms.has(player, "herostronghold.create." + regionTypeName)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] you dont have permission to create a " + regionTypeName);
                return true;
            }
            
            //Make sure the super-region requires a Charter
            if (currentRegionType.getCharter() <= 0) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[1] + " doesnt require a charter. /hs create " + args[1]);
                return true;
            }
            
            //Make sure the name isn't too long
            if (args[2].length() > 25) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Sorry but that name is too long. (25 max)");
                return true;
            }
            
            //Check if valid name
            if (pendingCharters.containsKey(args[2])) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is already a charter or region with that name.");
                return true;
            }
            if (getServer().getPlayerExact(args[2]) != null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Dont name a super-region after a player");
                return true;
            }
            
            //Check if allowed super-region
            if (regionManager.getSuperRegion(args[2]) != null || !regionManager.getSuperRegion(args[2]).hasOwner(player.getName())
                    || regionManager.getSuperRegion(args[2]).getType().equalsIgnoreCase(args[1])) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] That exact super-region already exists.");
                return true;
            }
            
            //Add the charter
            List<String> tempList = new ArrayList<String>();
            tempList.add(args[1]);
            tempList.add(player.getName());
            pendingCharters.put(args[2], tempList);
            configManager.writeToCharter(args[2], tempList);
            player.sendMessage(ChatColor.GOLD + "[HeroStronghold] Youve successfully created a charter for " + args[2]);
            player.sendMessage(ChatColor.GOLD + "[HeroStronghold] Get other people to type /hs sign " + args[2] + " to get started.");
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("signcharter")) {
            //Check if valid name
            if (!pendingCharters.containsKey(args[1])) {
                player.sendMessage(ChatColor.GRAY + "[Herostronghold] There is no charter for " + args[1]);
                return true;
            }
            
            //Check permission
            if (perms != null && perms.has(player, "herostronghold.join")) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You dont have permission to sign a charter.");
                return true;
            }
            
            //Sign Charter
            List<String> charter = pendingCharters.get(args[1]);
            charter.add(player.getName());
            pendingCharters.put(args[1], charter);
            player.sendMessage(ChatColor.GOLD + "[HeroStronghold] You just signed the charter for " + args[1]);
            Player owner = getServer().getPlayer(charter.get(1));
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(ChatColor.GOLD + "[HeroStronghold] " + player.getDisplayName() + " just signed your charter for " + args[1]);
            }
            return true;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            String regionName = args[1];
            
            //Permission Check
            boolean nullPerms = perms == null;
            boolean createAll = nullPerms || perms.has(player, "herostronghold.create.all");
            if (!(nullPerms || createAll || perms.has(player, "herostronghold.create." + regionName))) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] you dont have permission to create a " + regionName);
                return true;
            }
            
            Location currentLocation = player.getLocation();
            //Check if player is standing someplace where a chest can be placed.
            Block currentBlock = currentLocation.getBlock();
            if (currentBlock.getTypeId() != 0) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] please stand someplace where a chest can be placed.");
                return true;
            }
            RegionType currentRegionType = regionManager.getRegionType(regionName);
            if (currentRegionType == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + regionName + " isnt a valid region type");
                int j=0;
                String message = ChatColor.GOLD + "";
                for (String s : regionManager.getRegionTypes()) {
                    if (perms == null || (perms.has(player, "herostronghold.create.all") ||
                            perms.has(player, "herostronghold.create." + s))) {
                        message += s + ", ";
                        if (j > 4) {
                            player.sendMessage(message.substring(0, message.length() - 2));
                            message = ChatColor.GOLD + "";
                            j=-1;
                        }
                        j++;
                    }
                }
                if (j!=0)
                    player.sendMessage(message.substring(0, message.length() - 2));
                return true;
            }
            
            //Check if player can afford to create this herostronghold
            if (econ != null) {
                double cost = currentRegionType.getMoneyRequirement();
                if (econ.getBalance(player.getName()) < cost) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You need $" + cost + " to make this type of structure.");
                    return true;
                } else {
                    econ.withdrawPlayer(player.getName(), cost);
                }
                
            }
            
            //Check if too close to other HeroStrongholds
            for (Location loc : regionManager.getRegionLocations()) {
                try {
                    if (loc.distanceSquared(currentLocation) <= regionManager.getRegionType(regionManager.getRegion(loc).getType()).getRadius() + currentRegionType.getRadius()) {
                        player.sendMessage (ChatColor.GRAY + "[HeroStronghold] You are too close to another HeroStronghold");
                        return true;
                    }
                } catch (IllegalArgumentException iae) {

                }
            }
            
            //Check if in a super region and if has permission to make that region
            String playername = player.getName();
            String currentRegionName = currentRegionType.getName();
            
            Location loc = player.getLocation();
            double x1 = loc.getX();
            for (SuperRegion sr : regionManager.getSortedSuperRegions()) {
                int radius = regionManager.getRegionType(sr.getType()).getRadius();
                Location l = sr.getLocation();
                if (l.getX() + radius < x1) {
                    break;
                }
                try {
                    if (!(l.getX() - radius > x1) && l.distanceSquared(loc) < radius) {
                        if (!sr.hasOwner(playername)) {
                            if (!sr.hasMember(playername) || !sr.getMember(playername).contains(currentRegionName)) {
                                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You dont have permission from an owner of " + sr.getName()
                                        + " to create a " + currentRegionName + " here");
                                return true;
                            }
                        }
                    }
                } catch (IllegalArgumentException iae) {

                }
            }
            
            
            /*for (String s : regionManager.getSuperRegionNames()) {
                SuperRegion sr = regionManager.getSuperRegion(s);
                SuperRegionType srt = regionManager.getSuperRegionType(sr.getType());
                Location l = sr.getLocation();
                try {
                    if (Math.sqrt(l.distanceSquared(player.getLocation())) < srt.getRadius()) {
                        if (!sr.hasOwner(playername)) {
                            if (!sr.hasMember(playername) || !sr.getMember(playername).contains(currentRegionName)) {
                                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You dont have permission from an owner of " + s + " to create a " + currentRegionName + " here");
                                return true;
                            }
                        }
                    }
                } catch (IllegalArgumentException iae) {
                    
                }
            }*/
            
            //Prepare a requirements checklist
            ArrayList<ItemStack> requirements = currentRegionType.getRequirements();
            Map<Integer, Integer> reqMap = null;
            if (!requirements.isEmpty()) {
                reqMap = new HashMap<Integer, Integer>();
                for (ItemStack currentIS : requirements) {
                    reqMap.put(currentIS.getTypeId(), currentIS.getAmount());
                }
                //Check the area for required blocks
                //TODO Fix this!! It's slow, and it probably doesn't work

                int radius = currentRegionType.getRadius();

                int lowerLeftX = (int) currentLocation.getX() - radius;
                int lowerLeftY = (int) currentLocation.getY() - radius;
                lowerLeftY = lowerLeftY < 0 ? 0 : lowerLeftY;
                int lowerLeftZ = (int) currentLocation.getZ() - radius;

                int upperRightX = (int) currentLocation.getX() + radius;
                int upperRightY = (int) currentLocation.getY() + radius;
                upperRightY = upperRightY > 128 ? 128 : upperRightY;
                int upperRightZ = (int) currentLocation.getZ() + radius;

                World world = currentLocation.getWorld();
                
                outer: for (int x=lowerLeftX; x<upperRightX; x++) {
                    
                    for (int z=lowerLeftZ; z<upperRightZ; z++) {
                        
                        for (int y=lowerLeftY; y<upperRightY; y++) {
                            int type = world.getBlockTypeIdAt(x, y, z);
                            if (reqMap.containsKey(type)) {
                                if (reqMap.get(type) <= 1) {
                                    reqMap.remove(type);
                                    if (reqMap.isEmpty()) {
                                        break outer;
                                    }
                                } else {
                                    reqMap.put(type, reqMap.get(type) - 1);
                                }
                            }
                        }
                        
                    }
                    
                }
            }
            
            
            
            /*if (!requirements.isEmpty()) {
                outer: for (int x= (int) (currentLocation.getX()-radius); x< radius + currentLocation.getX(); x++) {
                    for (int y = currentLocation.getY() - radius > 1 ? (int) (currentLocation.getY() - radius) : 1; y < radius + currentLocation.getY() && y < 128; y++) {
                        for (int z = ((int) currentLocation.getZ() - radius); z<Math.abs(radius + currentLocation.getZ()); z++) {
                            if (currentLocation.getWorld().getBlockAt(x, y, z).getTypeId() != 0) {
                                for (Iterator<Material> iter = reqMap.keySet().iterator(); iter.hasNext(); ) {
                                    Material mat = iter.next();
                                    if (currentLocation.getWorld().getBlockAt(x, y, z).getType().equals(mat)) {
                                        if (reqMap.get(mat) <= 1) {
                                            reqMap.remove(mat);
                                            if (reqMap.isEmpty())
                                                break outer;
                                        } else {
                                            reqMap.put(mat, reqMap.get(mat) - 1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }*/
            if (reqMap != null && !reqMap.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] you don't have all of the required blocks in this structure.");
                String message = ChatColor.GOLD + "";
                int j=0;
                for (int type : reqMap.keySet()) {
                    message += reqMap.get(type) + " " + Material.getMaterial(type).name() + ", ";
                    if (j > 5) {
                        player.sendMessage(message.substring(0, message.length() - 2));
                        message = ChatColor.GOLD + "";
                        j=-1;
                    }
                    j++;
                }
                if (j!=0) {
                    player.sendMessage(message.substring(0, message.length() - 2));
                }
                return true;
            }
            
            //Create chest at players feet for tracking reagents and removing upkeep items
            currentBlock.setType(Material.CHEST);
            
            ArrayList<String> owners = new ArrayList<String>();
            owners.add(player.getName());
            regionManager.addRegion(currentLocation, regionName, owners);
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + ChatColor.WHITE + "You successfully create a " + ChatColor.RED + regionName);
            
            //Tell the player what reagents are required for it to work
            String message = ChatColor.GOLD + "Reagents: ";
            int j=0;
            for (ItemStack is : currentRegionType.getReagents()) {
                message += is.getAmount() + ":" + is.getType().name() + ", ";
                if (j >= 2) {
                    player.sendMessage(message.substring(0, message.length()-2));
                    message = ChatColor.GOLD + "";
                    j=-1;
                }
                j++;
            }
            if (currentRegionType.getReagents().isEmpty()) {
                message += "None";
                player.sendMessage(message);
            } else if (j!= 0)
                player.sendMessage(message.substring(0, message.length()-2));
            
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("create")) {
            //Check if valid name (further name checking later)
            if (args[2].length() > 25) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] That name is too long.");
                return true;
            }
            if (getServer().getPlayerExact(args[2]) != null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Dont name a super-region after a player.");
                return true;
            }
            
            String regionTypeName = args[1];
            //Permission Check
            if (perms != null && !perms.has(player, "herostronghold.create.all") &&
                    !perms.has(player, "herostronghold.create." + regionTypeName)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] you dont have permission to create a " + regionTypeName);
                return true;
            }
            
            //Check if valid super region
            Location currentLocation = player.getLocation();
            SuperRegionType currentRegionType = regionManager.getSuperRegionType(regionTypeName);
            if (currentRegionType == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + regionTypeName + " isnt a valid region type");
                int j=0;
                String message = ChatColor.GOLD + "";
                for (String s : regionManager.getSuperRegionTypes()) {
                    if (perms == null || (perms.has(player, "herostronghold.create.all") ||
                            perms.has(player, "herostronghold.create." + s))) {
                        message += s + ", ";
                        if (j >= 2) {
                            player.sendMessage(message.substring(0, message.length() - 3));
                            message = ChatColor.GOLD + "";
                            j=-1;
                        }
                        j++;
                    }
                }
                if (j!= 0)
                    player.sendMessage(message.substring(0, message.length() - 3));
                return true;
            }
            
            //Check if player can afford to create this herostronghold
            if (econ != null) {
                double cost = currentRegionType.getMoneyRequirement();
                if (econ.getBalance(player.getName()) < cost) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You need $" + cost + " to make this type of region.");
                    return true;
                } else {
                    econ.withdrawPlayer(player.getName(), cost);
                }
                
            }
            
            //Make sure the super-region has a valid charter
            int currentCharter = currentRegionType.getCharter();
            Map<String, List<String>> members = new HashMap<String, List<String>>();
            try {
                if (currentCharter > 0 && !pendingCharters.containsKey(args[2])) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You need to start a charter first. /hs charter " + args[1] + " " + args[2]);
                    return true;
                } else if (currentCharter > 0 && pendingCharters.get(args[2]).size() <= currentCharter) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You need " + currentCharter + " signature(s). /hs signcharter " + args[2]);
                    return true;
                } else if (currentCharter > 0 && (!pendingCharters.get(args[2]).get(0).equalsIgnoreCase(args[1]) ||
                        !pendingCharters.get(args[2]).get(1).equalsIgnoreCase(player.getName()))) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] The charter for this name is for a different region type or owner.");
                    player.sendMessage(ChatColor.GRAY + "Owner: " + pendingCharters.get(args[2]).get(1) + ", Type: " + pendingCharters.get(args[2]).get(0));
                    return true;
                } else if (currentCharter > 0) {
                    int i =0;
                    for (String s : pendingCharters.get(args[2])) {
                        if (i != 0) {
                            members.put(s, new ArrayList<String>());
                        } else {
                            i++;
                        }
                    }
                }
            } catch (Exception e) {
                warning("Possible failure to find correct charter for " + args[2]);
            }
            
            Map<String, Integer> requirements = (HashMap<String, Integer>) ((HashMap<String, Integer>) currentRegionType.getRequirements()).clone();
            
            //Check for required regions
            List<String> children = (ArrayList<String>) ((ArrayList<String>) currentRegionType.getChildren()).clone();
            for (String s : children) {
                if (!requirements.containsKey(s))
                    requirements.put(s, 1);
            }
            
            //Check if there already is a super-region by that name, but not if it's one of the child regions
            if (regionManager.getSuperRegion(args[2]) != null && !children.contains(args[2])) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is already a super-region by that name.");
                return true;
            }
            List<String> quietDestroy = new ArrayList<String>();
            if (!requirements.isEmpty()) {
                
                double x1 = currentLocation.getX();
                for (SuperRegion sr : regionManager.getSortedSuperRegions()) {
                    int radius1 = regionManager.getRegionType(sr.getType()).getRadius();
                    Location l = sr.getLocation();
                    if (l.getX() + radius1 < x1) {
                        break;
                    }
                    try {
                        if (!(l.getX() - radius1 > x1) && l.distanceSquared(currentLocation) < radius1) {
                            if (children.contains(sr.getType()) && sr.hasOwner(player.getName())) {
                                quietDestroy.add(sr.getName());
                            }
                            String rType = sr.getType();
                            if (requirements.containsKey(rType)) {
                                int amount = requirements.get(rType);
                                if (amount <= 1) {
                                    requirements.remove(rType);
                                } else {
                                    requirements.put(rType, amount - 1);
                                }
                            }
                        }
                    } catch (IllegalArgumentException iae) {

                    }
                }
                
                /*for (String s : regionManager.getSuperRegionNames()) {
                    SuperRegion sr = regionManager.getSuperRegion(s);
                    Location l = sr.getLocation();
                    try {
                        if (Math.sqrt(l.distanceSquared(currentLocation)) < radius) {
                            if (children.contains(sr.getType()) && sr.hasOwner(player.getName())) {
                                quietDestroy.add(s);
                            }
                            String rType = sr.getType();
                            if (requirements.containsKey(rType)) {
                                int amount = requirements.get(rType);
                                if (amount <= 1) {
                                    requirements.remove(rType);
                                } else {
                                    requirements.put(rType, amount - 1);
                                }
                            }
                        }
                    } catch (IllegalArgumentException e) {

                    }
                }*/
                
                if (!requirements.isEmpty()) {
                    double x = currentLocation.getX();
                    for (Region r : regionManager.getSortedRegions()) {
                        int radius1 = regionManager.getRegionType(r.getType()).getRadius();
                        Location l = r.getLocation();
                        if (l.getX() + radius1 < x) {
                            break;
                        }
                        try {
                            if (!(l.getX() - radius1 > x) && l.distanceSquared(currentLocation) < radius1) {
                                String rType = regionManager.getRegion(l).getType();
                                if (requirements.containsKey(rType)) {
                                    int amount = requirements.get(rType);
                                    if (amount <= 1) {
                                        requirements.remove(rType);
                                    } else {
                                        requirements.put(rType, amount - 1);
                                    }
                                }
                            }
                        } catch (IllegalArgumentException iae) {

                        }
                    }
                    
                    
                    /*for (Location l : regionManager.getRegionLocations()) {
                        try {
                            if (Math.sqrt(l.distanceSquared(currentLocation)) < radius) {
                                String rType = regionManager.getRegion(l).getType();
                                if (requirements.containsKey(rType)) {
                                    int amount = requirements.get(rType);
                                    if (amount <= 1) {
                                        requirements.remove(rType);
                                    } else {
                                        requirements.put(rType, amount - 1);
                                    }
                                }
                            }
                        } catch (IllegalArgumentException e) {

                        }
                    }*/
                }
            }
            if (!requirements.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] This area doesnt have all of the required regions.");
                int j=0;
                String message = ChatColor.GOLD + "";
                for (String s : requirements.keySet()) {
                    message += requirements.get(s) + " " + s + ", ";
                    if (j>=2) {
                        player.sendMessage(message.substring(0, message.length() -3));
                        message = ChatColor.GOLD + "";
                        j=-1;
                    }
                    j++;
                }
                if (j!=0)
                    player.sendMessage(message.substring(0, message.length() -3));
                return true;
            }
            
            //Assimulate any child super regions
            List<String> owners = new ArrayList<String>();
            for (String s : quietDestroy) {
                SuperRegion sr = regionManager.getSuperRegion(s);
                for (String so : sr.getOwners()) {
                    if (!owners.contains(so))
                        owners.add(so);
                }
                for (String sm : sr.getMembers().keySet()) {
                    if (!members.containsKey(sm))
                        members.put(sm, sr.getMember(sm));
                }
                regionManager.destroySuperRegion(s, false);
            }
            if (currentCharter > 0) {
                pendingCharters.remove(args[2]);
            }
            String playername = player.getName();
            if (!owners.contains(playername))
                owners.add(playername);
            regionManager.addSuperRegion(args[2], currentLocation, regionTypeName, owners, members, currentRegionType.getMaxPower());
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("withdraw")) {
            if (econ == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] No econ plugin recognized");
                return true;
            }
            double amount = 0;
            try {
                amount = Double.parseDouble(args[1]);
                if (amount < 0) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Withdraw a positive amount only.");
                    return true;
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Invalid amount /hs withdraw <amount> <superregionname>");
                return true;
            }
            
            //Check if valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[2] + " is not a super-region");
                return true;
            }
            
            //Check if owner or permitted member
            if ((!sr.hasMember(player.getName()) || !sr.getMember(player.getName()).contains("withdraw")) && !sr.hasOwner(player.getName())) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You are not a member or dont have permission to withdraw");
                return true;
            }
            
            //Check if bank has that money
            double output = regionManager.getSuperRegionType(sr.getType()).getOutput();
            if (output < 0 && sr.getBalance() - amount < -output) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You cant withdraw below the the minimum required.");
                return true;
            } else if (output >= 0 && sr.getBalance() - amount < 0) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + sr.getName() + " doesnt have that much money.");
                return true;
            }
            
            //Withdraw the money
            econ.depositPlayer(player.getName(), amount);
            regionManager.addBalance(sr, -amount);
            player.sendMessage(ChatColor.GOLD + "[HeroStronghold] You withdrew " + amount + " in the bank of " + args[2]);
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("deposit")) {
            if (econ == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] No econ plugin recognized");
                return true;
            }
            double amount = 0;
            try {
                amount = Double.parseDouble(args[1]);
                if (amount < 0) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Nice try. Deposit a positive amount.");
                    return true;
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Invalid amount /hs deposit <amount> <superregionname>");
                return true;
            }
            
            //Check if player has that money
            if (!econ.has(player.getName(), amount)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You dont have that much money.");
                return true;
            }
            
            //Check if valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[2] + " is not a super-region");
                return true;
            }
            
            //Check if owner or member
            if (!sr.hasMember(player.getName()) && !sr.hasOwner(player.getName())) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You are not a member of " + args[2]);
                return true;
            }
            
            //Deposit the money
            econ.withdrawPlayer(player.getName(), amount);
            regionManager.addBalance(sr, amount);
            player.sendMessage(ChatColor.GOLD + "[HeroStronghold] You deposited " + amount + " in the bank of " + args[2]);
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("settaxes")) {
            String playername = player.getName();
            //Check if the player is a owner or member of the super region
            SuperRegion sr = regionManager.getSuperRegion(args[3]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no region called " + args[2]);
                return true;
            }
            if (!sr.hasOwner(playername) && !sr.hasMember(playername)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You dont have permission to set taxes for " + args[2] + ".");
                return true;
            }
            
            //Check if member has permission
            if (sr.hasMember(playername) && !sr.getMember(playername).contains("settaxes")) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You dont have permission to set taxes for " + args[2] + ".");
                return true;
            }
            
            //Check if valid amount
            double taxes = 0;
            try {
                taxes = Double.parseDouble(args[1]);
                if (taxes < 0) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You cant set negative taxes.");
                    return true;
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Use /hs settaxes <amount> <superregionname>.");
                return true;
            }
            
            //Set the taxes
            regionManager.setTaxes(sr, taxes);
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("listperms")) {
            //Get target player
            String playername = "";
            if (args.length > 3) {
                Player currentPlayer = getServer().getPlayer(args[2]);
                if (currentPlayer == null) {
                    OfflinePlayer op = getServer().getOfflinePlayer(args[2]);
                    if (op != null)
                        playername = op.getName();
                    else {
                        player.sendMessage(ChatColor.GOLD + "[HeroStronghold] Could not find " + args[2]);
                        return true;
                    }
                } else {
                    playername = currentPlayer.getName();
                }
            } else {
                playername = player.getName();
            }
            
            String message = ChatColor.GRAY + "[HeroStronghold] " + playername + " perms for " + args[3] + ":";
            String message2 = ChatColor.GOLD + "";
            //Check if the player is a owner or member of the super region
            SuperRegion sr = regionManager.getSuperRegion(args[3]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no region called " + args[3]);
                return true;
            }
            if (sr.hasOwner(playername)) {
                player.sendMessage(message);
                player.sendMessage(message2 + "All Permissions");
                return true;
            } else if (sr.hasMember(playername)) {
                player.sendMessage(message);
                int j=0;
                for (String s : sr.getMember(label)) {
                    message2 += s + ", ";
                    if (j >= 3) {
                        player.sendMessage(message2.substring(0, message2.length() - 3));
                        message2 = ChatColor.GOLD + "";
                        j = -1;
                    }
                    j++;
                }
                if (j != 0)
                    player.sendMessage(message2.substring(0, message2.length() - 3));
                return true;
            }
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + playername + " doesn't belong to that region.");
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("ch")) {
            //Check if valid super region
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no super-region by that name (" + args[1] + ").");
                return true;
            }
            
            //Check if player is a member or owner of that super-region
            String playername = player.getName();
            if (!sr.hasMember(playername) && !sr.hasOwner(playername)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You must be a member of " + args[1] + " before joining thier channel");
                return true;
            }
            
            //Set the player as being in that channel
            dpeListener.setPlayerChannel(player, args[1]);
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("addmember")) {
            //Check if valid super region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no super-region by that name (" + args[2] + ").");
                return true;
            }
            
            //Check if player is a member or owner of that super-region
            String playername = player.getName();
            boolean isOwner = sr.hasOwner(playername);
            if (!sr.hasMember(playername) && !isOwner) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You arent a member of " + args[2]);
                return true;
            }
            
            //Check if player has permission to invite players
            if (!isOwner && !sr.getMember(playername).contains("addmember")) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You need permission addmember from an owner of " + args[2]);
                return true;
            }
            
            //Check if valid player
            Player invitee = getServer().getPlayer(args[1]);
            if (invitee == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[1] + " is not online.");
                return true;
            }
            
            //Check permission herostronghold.join
            if (!perms.has(invitee, "herostronghold.join")) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[1] + " doesnt have permission to join a super-region.");
                return true;
            }
            
            //Send an invite
            pendingInvites.put(args[1], args[2]);
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You have invited " + ChatColor.GOLD + args[1] + ChatColor.GRAY + " to join " + ChatColor.GOLD + args[2]);
            if (invitee != null)
                invitee.sendMessage(ChatColor.GOLD + "[HeroStronghold] You have been invited to join " + args[2]);
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("accept")) {
            //Check if player has a pending invite to that super-region
            if (!pendingInvites.containsKey(player.getName()) || !pendingInvites.get(player.getName()).equals(args[1])) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't have an invite to " + args[1]);
                return true;
            }
            
            //Check if valid super region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no super-region by that name (" + args[1] + ").");
                return true;
            }
            
            //Check if player is a member or owner of that super-region
            String playername = player.getName();
            if (sr.hasMember(playername) || sr.hasOwner(playername)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You are already a member of " + args[1]);
                return true;
            }
            
            //Add the player to the super region
            sr.addMember(player.getName(), new ArrayList<String>());
            pendingInvites.remove(player.getName());
            player.sendMessage(ChatColor.GOLD + "[HeroStronghold] Welcome to " + args[1]);
            for (String s : sr.getMembers().keySet()) {
                Player p = getServer().getPlayer(s);
                if (p != null) {
                    p.sendMessage(ChatColor.GOLD + playername + " has joined " + args[1]);
                }
            }
            for (String s : sr.getOwners()) {
                Player p = getServer().getPlayer(s);
                if (p != null) {
                    p.sendMessage(ChatColor.GOLD + playername + " has joined " + args[1]);
                }
            }
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("addowner")) {
            Player p = getServer().getPlayer(args[1]);
            OfflinePlayer op = getServer().getOfflinePlayer(args[1]);
            String playername = "";
            
            //Check valid player
            if (p == null) {
                if (op == null) {
                    player.sendMessage(ChatColor.GRAY + "[Herostronghold] There is no player named: " + args[1]);
                    return true;
                } else {
                    playername = op.getName();
                }
            } else {
                playername = p.getName();
            }
            
            //Check valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no super-region named " + args[2]);
                return true;
            }
            
            //Check if player is member of super-region
            if (!sr.hasMember(playername)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[1] + " is not a member of " + args[2]);
                return true;
            }
            
            sr.remove(playername);
            if (p != null)
                p.sendMessage(ChatColor.GOLD + "[HeroStronghold] You are now an owner of " + args[2]);
            for (String s : sr.getMembers().keySet()) {
                Player pl = getServer().getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + " is now an owner of " + args[2]);
                }
            }
            for (String s : sr.getOwners()) {
                Player pl = getServer().getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + " is now an owner of " + args[2]);
                }
            }
            sr.addOwner(playername);
            return true;
        } else if (args.length > 2 && args[0].equalsIgnoreCase("remove")) {
            Player p = getServer().getPlayer(args[1]);
            OfflinePlayer op = getServer().getOfflinePlayer(args[1]);
            String playername = "";
            
            //Check valid player
            if (p == null) {
                if (op == null) {
                    player.sendMessage(ChatColor.GRAY + "[Herostronghold] There is no player named: " + args[1]);
                    return true;
                } else {
                    playername = op.getName();
                }
            } else {
                playername = p.getName();
            }
            
            //Check valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no super-region named " + args[2]);
                return true;
            }
            
            //Check if player is member or owner of super-region
            if (!sr.hasMember(playername) && !sr.hasOwner(playername)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[1] + " is not a member of " + args[2]);
                return true;
            }
            
            sr.remove(playername);
            if (p != null)
                p.sendMessage(ChatColor.GRAY + "[HeroStronghold] You are no longer a member of " + args[2]);
            
            for (String s : sr.getMembers().keySet()) {
                Player pl = getServer().getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + " was removed from " + args[2]);
                }
            }
            for (String s : sr.getOwners()) {
                Player pl = getServer().getPlayer(s);
                if (pl != null) {
                    pl.sendMessage(ChatColor.GOLD + playername + " was removed from " + args[2]);
                }
            }
            return true;
        } else if (args.length > 3 && args[0].equalsIgnoreCase("toggleperm")) {
            Player p = getServer().getPlayer(args[1]);
            OfflinePlayer op = getServer().getOfflinePlayer(args[1]);
            String playername = "";
            
            //Check valid player
            if (p == null) {
                if (op == null) {
                    player.sendMessage(ChatColor.GRAY + "[Herostronghold] There is no player named: " + args[1]);
                    return true;
                } else {
                    playername = op.getName();
                }
            } else {
                playername = p.getName();
            }
            
            //Check valid super-region
            SuperRegion sr = regionManager.getSuperRegion(args[2]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no super-region named " + args[3]);
                return true;
            }
            
            //Check if player is member and not owner of super-region
            if (!sr.hasMember(playername)) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + args[1] + " either owns, or is not a member of " + args[3]);
                return true;
            }
            
            if (sr.togglePerm(playername, args[2])) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Removed perm " + args[2] + " for " + args[1] + " in " + args[3]);
                if (p != null)
                    p.sendMessage(ChatColor.GRAY + "[HeroStronghold] Your perm " + args[2] + " was revoked in " + args[3]);
                return true;
            } else {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Added perm " + args[2] + " for " + args[1] + " in " + args[3]);
                if (p != null)
                    p.sendMessage(ChatColor.GRAY + "[HeroStronghold] You were granted permission " + args[2] + " in " + args[3]);
                return true;
            }
        } else if (args.length > 1 && args[0].equalsIgnoreCase("addowner")) {
            String playername = args[1];
            Player aPlayer = getServer().getPlayer(playername);
            if (aPlayer == null) {
                OfflinePlayer op = getServer().getOfflinePlayer(playername);
                if (op == null) {
                    player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no player by the name of " + args[1]);
                    return true;
                } else {
                    playername = op.getName();
                }
            } else {
                playername = aPlayer.getName();
            }
            
            Location loc = player.getLocation();
            double x = loc.getX();
            for (Region r : regionManager.getSortedRegions()) {
                int radius = regionManager.getRegionType(r.getType()).getRadius();
                Location l = r.getLocation();
                if (l.getX() + radius < x) {
                    break;
                }
                try {
                    if (!(l.getX() - radius > x) && l.distanceSquared(loc) < radius) {
                        if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                            if (r.isOwner(playername)) {
                                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + playername + " is already an owner of this region.");
                                return true;
                            }
                            if (r.isMember(playername))
                                r.remove(playername);
                            r.addOwner(playername);
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + ChatColor.WHITE + "Added " + playername + " to the region.");
                            return true;
                        } else {
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                            return true;
                        }
                    }
                } catch (IllegalArgumentException iae) {

                }
            }
            
            
            /*for (Location l : regionManager.getRegionLocations()) {
                Region r = regionManager.getRegion(l);
                if (Math.sqrt(l.distanceSquared(loc)) < regionManager.getRegionType(r.getType()).getRadius()) {
                    if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                        if (r.isOwner(playername)) {
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + playername + " is already an owner of this region.");
                            return true;
                        }
                        if (r.isMember(playername))
                            r.remove(playername);
                        r.addOwner(playername);
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + ChatColor.WHITE + "Added " + playername + " to the region.");
                        return true;
                    } else {
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                        return true;
                    }
                }
            }*/
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You're not standing in a region.");
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("addmember")) {
            String playername = args[1];
            boolean isSuperRegion = false;
            Player aPlayer = getServer().getPlayer(playername);
            if (aPlayer == null) {
                OfflinePlayer op = getServer().getOfflinePlayer(playername);
                if (op == null) {
                    SuperRegion sr = regionManager.getSuperRegion(args[1]);
                    if (sr == null) {
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no player by the name of " + args[1]);
                        return true;
                    } else {
                        isSuperRegion = true;
                        playername = args[1];
                    }
                } else {
                    playername = op.getName();
                }
            } else {
                playername = aPlayer.getName();
            }
            Location loc = player.getLocation();
            double x = loc.getX();
            for (Region r : regionManager.getSortedRegions()) {
                int radius = regionManager.getRegionType(r.getType()).getRadius();
                Location l = r.getLocation();
                if (l.getX() + radius < x) {
                    break;
                }
                try {
                    if (!(l.getX() - radius > x) && l.distanceSquared(loc) < radius) {
                        if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                            if (r.isMember(playername)) {
                                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + playername + " is already a member of this region.");
                                return true;
                            }
                            if (r.isOwner(playername))
                                r.remove(playername);
                            r.addMember(playername);
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + ChatColor.WHITE + "Added " + playername + " to the region.");
                            return true;
                        } else {
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                            return true;
                        }
                    }
                } catch (IllegalArgumentException iae) {

                }
            }
            
            
            /*for (Location l : regionManager.getRegionLocations()) {
                Region r = regionManager.getRegion(l);
                if (Math.sqrt(l.distanceSquared(loc)) < regionManager.getRegionType(r.getType()).getRadius()) {
                    if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                        if (r.isMember(playername)) {
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + playername + " is already a member of this region.");
                            return true;
                        }
                        if (r.isOwner(playername))
                            r.remove(playername);
                        r.addMember(playername);
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + ChatColor.WHITE + "Added " + playername + " to the region.");
                        return true;
                    } else {
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                        return true;
                    }
                }
            }*/
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You're not standing in a region.");
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("remove")) {
            String playername = args[1];
            Player aPlayer = getServer().getPlayer(playername);
            if (aPlayer != null)
                playername = aPlayer.getName();
            Location loc = player.getLocation();
            double x = loc.getX();
            for (Region r : regionManager.getSortedRegions()) {
                int radius = regionManager.getRegionType(r.getType()).getRadius();
                Location l = r.getLocation();
                if (l.getX() + radius < x) {
                    break;
                }
                try {
                    if (!(l.getX() - radius > x) && l.distanceSquared(loc) < radius) {
                        if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                            if (!r.isMember(playername) && !r.isOwner(playername)) {
                                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + playername + " doesn't belong to this region");
                                return true;
                            }
                            r.remove(playername);
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + ChatColor.WHITE + "Removed " + playername + " from the region.");
                            return true;
                        } else {
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                            return true;
                        }
                    }
                } catch (IllegalArgumentException iae) {

                }
            }
            
            /*for (Location l : regionManager.getRegionLocations()) {
                Region r = regionManager.getRegion(l);
                if (Math.sqrt(l.distanceSquared(loc)) < regionManager.getRegionType(r.getType()).getRadius()) {
                    if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                        if (!r.isMember(playername) && !r.isOwner(playername)) {
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + playername + " doesn't belong to this region");
                            return true;
                        }
                        r.remove(playername);
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + ChatColor.WHITE + "Removed " + playername + " from the region.");
                        return true;
                    } else {
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                        return true;
                    }
                }
            }*/
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You're not standing in a region.");
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("destroy")) {
            Location loc = player.getLocation();
            Location locationToDestroy = null;
            
            double x = loc.getX();
            for (Region r : regionManager.getSortedRegions()) {
                int radius = regionManager.getRegionType(r.getType()).getRadius();
                Location l = r.getLocation();
                if (l.getX() + radius < x) {
                    break;
                }
                try {
                    if (!(l.getX() - radius > x) && l.distanceSquared(loc) < radius) {
                        if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                            regionManager.destroyRegion(l);
                            locationToDestroy = l;
                            break;
                        } else {
                            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                            return true;
                        }
                    }
                } catch (IllegalArgumentException iae) {

                }
            }
            
            /*for (Iterator<Location> iter = locations.iterator(); iter.hasNext();) {
                Location l = iter.next();
                Region r = regionManager.getRegion(l);
                if (Math.sqrt(l.distanceSquared(loc)) < regionManager.getRegionType(r.getType()).getRadius()) {
                    if (r.isOwner(player.getName()) || (perms != null && perms.has(player, "herostronghold.admin"))) {
                        
                        regionManager.destroyRegion(l);
                        locationToDestroy = l;
                        return true;
                    } else {
                        player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You don't own this region.");
                        return true;
                    }
                }
            }*/
            if (locationToDestroy != null) {
                regionManager.removeRegion(locationToDestroy);
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Region destroyed.");
            } else {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You're not standing in a region.");
            }
            return true;
        } else if (args.length > 1 && args[0].equalsIgnoreCase("destroy")) {
            //Check if valid region
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            if (sr == null) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] There is no region named " + args[1]);
                return true;
            }
            
            //Check if owner or admin of that region
            if (!(perms != null && perms.has(player, "herostronghold.admin") &&
                    !sr.getOwners().get(0).equalsIgnoreCase(player.getName()))) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] You are not the owner of that region.");
                return true;
            }
            
            regionManager.destroySuperRegion(args[1], true);
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            int j=0;
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] list of Region Types");
            String message = ChatColor.GOLD + "";
            boolean permNull = perms == null;
            boolean createAll = permNull || perms.has(player, "herostronghold.create.all");
            for (String s : regionManager.getRegionTypes()) {
                if (createAll || permNull || perms.has(player, "herostronghold.create." + s)) {
                    if (message.length() + s.length() + 2 > 55) {
                        player.sendMessage(message);
                        message += ChatColor.GOLD + "";
                        j++;
                    }
                    if (j==0) {
                        message += s;
                        j++;
                    } else if (j > 14) {
                        break;
                    } else {
                        message += ", " + s;
                    }
                }
            }
            for (String s : regionManager.getSuperRegionTypes()) {
                if (createAll || permNull || perms.has(player, "herostronghold.create." + s)) {
                    if (message.length() + s.length() + 2 > 55) {
                        player.sendMessage(message);
                        message += ChatColor.GOLD + "";
                        j++;
                    }
                    if (j==0) {
                        message += s;
                        j++;
                    } else if (j > 14) {
                        break;
                    } else {
                        message += ", " + s;
                    }
                }
            }
            player.sendMessage(message);
            return true;
        } else if (args.length > 1 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("who"))) {
            Player p = getServer().getPlayer(args[1]);
            if (p != null) {
                String playername = p.getName();
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] " + p.getDisplayName() + " is a member of:");
                String message = ChatColor.GOLD + "";
                int j = 0;
                for (SuperRegion sr : regionManager.getSortedSuperRegions()) {
                    if (sr.hasOwner(playername) || sr.hasMember(playername)) {
                        if (message.length() + sr.getName().length() + 2 > 55) {
                            player.sendMessage(message);
                            message = ChatColor.GOLD + "";
                            j++;
                        }
                        if (j==0) {
                            message += sr.getName();
                            j++;
                        } else if (j > 14) {
                            break;
                        } else {
                            message += ", " + sr.getName();
                        }
                    }
                }
                player.sendMessage(message);
                return true;
            }
            SuperRegion sr = regionManager.getSuperRegion(args[1]);
            
            if (sr != null) {
                //TODO make revenue include all owned regions within the super region
                SuperRegionType srt = regionManager.getSuperRegionType(sr.getType());
                int population = sr.getOwners().size() + sr.getMembers().size();
                double revenue = sr.getTaxes() * population + srt.getOutput();
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] ==:|" + ChatColor.GOLD + sr.getName() + " (" + sr.getType() + ") " + ChatColor.GRAY + "|:==");
                player.sendMessage(ChatColor.GRAY + "Population: " + ChatColor.GOLD + population + ChatColor.GRAY +
                        " Bank: " + (sr.getBalance() < srt.getOutput() ? ChatColor.RED : ChatColor.GOLD) + sr.getBalance() + ChatColor.GRAY +
                        " Power: " + (sr.getPower() < srt.getDailyPower() ? ChatColor.RED : ChatColor.GOLD) + sr.getPower() + 
                        " (+" + srt.getDailyPower() + ") / " + srt.getMaxPower());
                player.sendMessage(ChatColor.GRAY + "Taxes: " + ChatColor.GOLD + sr.getTaxes()
                        + ChatColor.GRAY + " Revenue: " + (revenue < 0 ? ChatColor.RED : ChatColor.GOLD) + revenue +
                        " Size: " + ChatColor.GOLD + ((int) (Math.pow(srt.getRadius(), 2) * 3.14)));
                if (sr.getTaxes() != 0) {
                    String message = ChatColor.GRAY + "Tax Revenue History: " + ChatColor.GOLD;
                    int j = 0;
                    for (double d : sr.getTaxRevenue()) {
                        if (j == 0)
                            message += d;
                        else
                            message += ", " + d;
                    }
                    player.sendMessage(message);
                }
                String message = ChatColor.GRAY + "Owners: ";
                int j = 0;
                for (String s : sr.getOwners()) {
                    if (j == 0)
                        message += s;
                    else
                        message += ", " + s;
                    if (j % 5 == 0 && j < 76) {
                        player.sendMessage(message);
                        message = ChatColor.GRAY + "";
                    }
                    j++;
                }
                if (j % 5 != 0 && j < 76) {
                    player.sendMessage(message);
                }
                int k = j;
                message = ChatColor.GRAY + "Members: ";
                for (String s : sr.getMembers().keySet()) {
                    if (j == k)
                        message += s;
                    else
                        message += ", " + s;
                    if (j % 5 == 0 && j < 76) {
                        player.sendMessage(message);
                        message = ChatColor.GRAY + "";
                    }
                    j++;
                }
                if (j % 5 != 0 && j < 76) {
                    player.sendMessage(message);
                }
                return true;
            }
            player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Could not find player or super-region by that name");
            return true;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("god")) {
            if (perms == null || !perms.has(player, "herostronghold.god")) {
                player.sendMessage(ChatColor.GRAY + "[HeroStronghold] Sorry you don't have permission to do that.");
                return true;
            }
            
            if (regionEntityListener.toggleGod(player)) {
                player.sendMessage(ChatColor.GOLD + "[HeroStronghold] Godmode Enabled.");
            } else {
                player.sendMessage(ChatColor.GOLD + "[HeroStronghold] Godmode Disabled.");
            }
            return true;
        } else {
            if (args.length > 0 && args[args.length - 1].equals("2")) {
                sender.sendMessage(ChatColor.GRAY + "[HeroStronghold] by " + ChatColor.GOLD + "Multitallented" + ChatColor.GRAY + ": <> = required, () = optional" +
                        ChatColor.GOLD + " Page 2");
                sender.sendMessage(ChatColor.GRAY + "/hs accept <name>");
                sender.sendMessage(ChatColor.GRAY + "/hs listperms <playername> <name>");
                sender.sendMessage(ChatColor.GRAY + "/hs toggleperm <playername> <perm> <name>");
                sender.sendMessage(ChatColor.GRAY + "/hs destroy (name)");
                sender.sendMessage(ChatColor.GRAY + "/hs ch <channel>");
                sender.sendMessage(ChatColor.GRAY + "Google 'HeroStronghold bukkit' for more info | " + ChatColor.GOLD + "Page 2");
            } else {
                sender.sendMessage(ChatColor.GRAY + "[HeroStronghold] by " + ChatColor.GOLD + "Multitallented" + ChatColor.GRAY + ": () = optional" +
                        ChatColor.GOLD + " Page 1");
                sender.sendMessage(ChatColor.GRAY + "/hs list");
                sender.sendMessage(ChatColor.GRAY + "/hs charter <superregiontype> <name>");
                sender.sendMessage(ChatColor.GRAY + "/hs signcharter <name>");
                sender.sendMessage(ChatColor.GRAY + "/hs create <regiontype> (name)");
                sender.sendMessage(ChatColor.GRAY + "/hs addowner|addmember|remove <playername> (name)");
                sender.sendMessage(ChatColor.GRAY + "/hs settaxes <amount> <name>");
                sender.sendMessage(ChatColor.GRAY + "Google 'HeroStronghold bukkit' for more info |" + ChatColor.GOLD + " Page 1");
            }
            
            return true;
        }
    }
    
    public boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            econ = rsp.getProvider();
            if (econ != null)
                System.out.println("[HeroStronghold] Hooked into " + econ.getName());
        }
        return econ != null;
    }
    private Boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            perms = permissionProvider.getProvider();
            if (perms != null)
                System.out.println("[HeroStronghold] Hooked into " + perms.getName());
        }
        return (perms != null);
    }
    
    public Heroes getHeroes() {
        if (serverListener == null)
            return null;
        return serverListener.getHeroes();
    }
    
    public RegionManager getRegionManager() {
        return regionManager;
    }
    
    public void warning(String s) {
        String warning = "[HeroStronghold] " + s;
        Logger.getLogger("Minecraft").warning(warning);
    }
    
    public void setConfigManager(ConfigManager cm) {
        this.configManager = cm;
    }
    
    public ConfigManager getConfigManager() {
        return this.configManager;
    }
    public void setCharters(Map<String, List<String>> input) {
        this.pendingCharters = input;
    }
    
}