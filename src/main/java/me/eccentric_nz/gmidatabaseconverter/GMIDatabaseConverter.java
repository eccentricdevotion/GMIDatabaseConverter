package me.eccentric_nz.gmidatabaseconverter;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import net.minecraft.server.v1_6_R3.NBTBase;
import net.minecraft.server.v1_6_R3.NBTTagCompound;
import net.minecraft.server.v1_6_R3.NBTTagList;
import org.bukkit.craftbukkit.v1_6_R3.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.v1_6_R3.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

public class GMIDatabaseConverter extends JavaPlugin {

    GameModeInventoriesDatabase service = GameModeInventoriesDatabase.getInstance();

    @Override
    public void onDisable() {
        // TODO: Place any custom disable code here.
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginManager pm = getServer().getPluginManager();
        Plugin gmi = pm.getPlugin("GameModeInventories");
        if (gmi == null) {
            System.err.println("[GMIDatabaseConverter] This plugin requires GameModeInventories!");
            pm.disablePlugin(this);
            return;
        }
        String v = gmi.getDescription().getVersion();
        Version gmiversion = new Version(v);
        Version notneededversion = new Version("2.0");
        if (gmiversion.compareTo(notneededversion) >= 0) {
            System.err.println("[GMIDatabaseConverter] You do not need to run this with version " + v + " of GameModeInventories!");
            pm.disablePlugin(this);
            return;
        }
        if (getConfig().getBoolean("conversion_done")) {
            System.err.println("[GMIDatabaseConverter] The GameModeInventories database has already been converted!");
            pm.disablePlugin(this);
            return;
        }
        File old_file = new File(gmi.getDataFolder() + File.separator + "GMI.db");
        if (!old_file.exists()) {
            System.err.println("[GMIDatabaseConverter] Could not find GameModeInventories database file!");
            pm.disablePlugin(this);
            return;
        }
        File backup_file = new File(gmi.getDataFolder() + File.separator + "GMI_backup.db");
        try {
            copyFile(old_file, backup_file);
        } catch (IOException io) {
            System.err.println("[GMIDatabaseConverter] Could backup GameModeInventories database file!");
            pm.disablePlugin(this);
            return;
        }
        System.out.println("[GMIDatabaseConverter] The GameModeInventories database file was backed up successfully!");
        try {
            String path = gmi.getDataFolder() + File.separator + "GMI.db";
            service.setConnection(path);
        } catch (Exception e) {
            System.err.println("[GMIDatabaseConverter] Database connection error: " + e);
        }
        if (!convertInventories()) {
            System.err.println("[GMIDatabaseConverter] Inventory conversion failed!");
            pm.disablePlugin(this);
        } else {
            getConfig().set("conversion_done", true);
        }
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    private boolean convertInventories() {
        System.out.println("[GMIDatabaseConverter] Beginning conversion...");
        // get all the records
        try {
            Connection connection = service.getConnection();
            Statement statement = connection.createStatement();
            String getQuery = "SELECT id, inventory, armour, enderchest FROM inventories";
            ResultSet rsInv = statement.executeQuery(getQuery);
            int count = 0;
            while (rsInv.next()) {
                // set their inventory to the saved one
                String base64 = rsInv.getString("inventory");
                Inventory i = fromBase64(base64);
                ItemStack[] iis = i.getContents();
                String i_string = GameModeInventoriesSerialization.toString(iis);
                String savedarmour = rsInv.getString("armour");
                Inventory a = fromBase64(savedarmour);
                ItemStack[] ais = a.getContents();
                String a_string = GameModeInventoriesSerialization.toString(ais);
                String savedender = rsInv.getString("enderchest");
                if (savedender == null || savedender.equals("[Null]") || savedender.equals("") || savedender.isEmpty()) {
                    // empty inventory
                    savedender = "[\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\"]";
                }
                Inventory e = fromBase64(savedender);
                ItemStack[] eis = e.getContents();
                String e_string = GameModeInventoriesSerialization.toString(eis);

                // update
                String setQuery = "UPDATE inventories SET inventory = ?, armour = ?, enderchest = ? WHERE id = ?";
                PreparedStatement ps = connection.prepareStatement(setQuery);
                ps.setString(1, i_string);
                ps.setString(2, a_string);
                ps.setString(3, e_string);
                ps.setInt(4, rsInv.getInt("id"));
                ps.executeUpdate();
                count++;
            }
            System.out.println("[GMIDatabaseConverter] Conversion complete - " + count + " records updated successfully.");
            rsInv.close();
            statement.close();
        } catch (SQLException e) {
            System.err.println("Could not save inventory on gamemode change, " + e);
            return false;
        }
        return true;
    }

    public static Inventory fromBase64(String data) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
        NBTTagList itemList = (NBTTagList) NBTBase.a(new DataInputStream(inputStream));
        Inventory inventory = new CraftInventoryCustom(null, itemList.size());
        for (int i = 0; i < itemList.size(); i++) {
            NBTTagCompound inputObject = (NBTTagCompound) itemList.get(i);
            if (!inputObject.isEmpty()) {
                inventory.setItem(i, CraftItemStack.asCraftMirror(net.minecraft.server.v1_6_R3.ItemStack.createStack(inputObject)));
            }
        }
        return inventory;
    }
}
