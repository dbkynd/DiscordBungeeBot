package com.dbkynd.DiscordBungeeBot;

import com.dbkynd.DiscordBungeeBot.command.ReloadCommand;
import com.dbkynd.DiscordBungeeBot.sql.UserRecord;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.dbkynd.DiscordBungeeBot.bot.ServerBot;
import com.dbkynd.DiscordBungeeBot.sql.MySQLConnection;
import com.dbkynd.DiscordBungeeBot.listener.PostLoginListener;
import org.bstats.bungeecord.Metrics;

public class DiscordBungeeBot extends Plugin {

    /* CONFIG.YML VARIABLES */

    private String sqlhost;
    private String sqlport;
    private String sqluser;
    private String sqldatabase;
    private String sqlpassword;
    private String sqltable;
    private String bottoken;
    private String commandprefix;
    private String addmecommand;
    private boolean checkrole = false;
    private String requiredroleid;
    private String kickmsg;

    /* END OF CONFIG.YML VARIABLES */

    private MySQLConnection sql;

    ServerBot bot;

    public static Logger log = Logger.getLogger("DBBungeeBot");

    @Override
    public void onEnable() {
        Metrics metrics = new Metrics(this);

        // Load config.yml
        loadConfig();

        // Register Events
        getProxy().getPluginManager().registerListener(this, new PostLoginListener(this));
        // Register Commands
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));

        // Get sql instance and connect
        sql = new MySQLConnection(this, sqlhost, sqlport, sqluser, sqlpassword, sqldatabase);
        sql.connect();
        // Create table if does not exist
        if (!sql.tableExists(sqltable)) {
            log(Level.INFO, "Table not found. Creating new table...");
            sql.update("CREATE TABLE " + sqltable + " (DiscordID CHAR(18), MinecraftName VARCHAR(16), UUID CHAR(36), PRIMARY KEY (DiscordID));");
            // Ensure table was created before saying so
            if (sql.tableExists(sqltable)) {
                log(Level.INFO, "Table created!");
            }
        }
        // Reconnect to the MySQL database every 20 minutes
        getProxy().getScheduler().schedule(this, new Runnable() {
            @Override
            public void run() {
                sql.reconnect();
            }
        }, 1, 20, TimeUnit.MINUTES);

        // Instantiate and run the Discord Bot
        bot = new ServerBot(this);
        bot.runBot();
    }

    public void loadConfig() {
        // Create the data folder if missing
        if (!getDataFolder().exists())
            getDataFolder().mkdir();

        // Set the file path
        File file = new File(getDataFolder(), "config.yml");

        // If the file does not exist save out our resource config.yml
        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            // Attempt to load the config file
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

            // Load config.yml into class variables
            sqlhost = config.getString("Host");
            sqlport = config.getString("Port");
            sqluser = config.getString("User");
            sqlpassword = config.getString("Password");
            sqldatabase = config.getString("Database");
            sqltable = config.getString("TableName");
            bottoken = config.getString("BotToken");
            checkrole = config.getBoolean("CheckRole");
            requiredroleid = config.getString("RequiredRoleId");
            kickmsg = config.getString("KickMessage");
            addmecommand = config.getString("AddMeCommand");
            commandprefix = config.getString("CommandPrefix");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void log(Level level, String msg) {
        log.log(level, msg.replaceAll("�[0-9A-FK-OR]", ""));
    }

    public String getBotToken() {
        return bottoken;
    }

    public MySQLConnection getSQL() {
        return sql;
    }

    public ServerBot getBot() {
        return bot;
    }

    public String getTableName() {
        return sqltable;
    }

    public String getCommandPrefix() {
        return commandprefix;
    }

    public String getAddMeCommand() {
        return addmecommand;
    }

    public String getKickMessage() {
        return kickmsg;
    }

    public boolean checkRole() {
        return checkrole;
    }

    public String getRequiredRoleId() {
        return requiredroleid;
    }

    public UserRecord getRegistered(String name) {
        ResultSet rs;

        if (sql.itemExists("MinecraftName", name, sqltable)) {
            rs = sql.query("SELECT * FROM " + sqltable + " HAVING MinecraftName = " + "\'" + name.toLowerCase() + "\';");
            try {
                rs.next();
                String discordId = rs.getString("DiscordID");
                String minecraftName = rs.getString("MinecraftName");
                String uuid = rs.getString("UUID");
                return new UserRecord(discordId, minecraftName, uuid);
            } catch (SQLException e) {
                log(Level.SEVERE, "Error getting user data from database!");
                e.printStackTrace();
            }
        }
        return null;
    }
}
