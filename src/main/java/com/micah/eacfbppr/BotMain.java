package com.micah.eacfbppr;

import Utilities.AppLogger;
import CommandInteraction.CommandHandler;
import Utilities.GoogleSheetsHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;


import javax.xml.crypto.Data;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.logging.Logger;

public class BotMain {
    private static final Logger LOGGER = AppLogger.getLogger();
    private static String BOT_TOKEN = "";







    public static void main(String[] args) {
        try {
            JDA jda = JDABuilder.createDefault(BOT_TOKEN)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .setActivity(Activity.customStatus("GKCO!"))
                    .enableCache(CacheFlag.VOICE_STATE)
                    .addEventListeners(new CommandHandler())
                    .build();


            jda.awaitReady();
            registerSlashCommands(jda);

            Database.getConnection();
            GoogleSheetsHandler.updateConferenceData();
            GoogleSheetsHandler.updateTop25Rankings();


            LOGGER.info("Bot Loaded!");



        } catch (InterruptedException e) {
            LOGGER.severe(e.getMessage());
        } catch (SQLException | GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void registerSlashCommands(JDA jda) {
        LOGGER.fine("Registering Commands");
        LOGGER.fine("Adding slash commands");




        jda.updateCommands().addCommands(
                Commands.slash("ping", "Test the bot's response time!"),
                Commands.slash("register", "Register yourself or tag someone else to register them to a team.")
                        .addOption(OptionType.STRING, "team", "The name of the team to register", true, true)
                        .addOption(OptionType.USER, "user", "The user to register to the team", false),
                Commands.slash("list", "List taken dynasty teams and their conferences."),
                Commands.slash("report_scrimmage", "Record the final score of a friendly match.")
                        .addOption(OptionType.STRING, "first_team","The first player/team in the match.", true)
                        .addOption(OptionType.STRING, "first_team_score", "Score of the first player/team", true)
                        .addOption(OptionType.STRING, "second_team", "The second player/team in the match.",true)
                        .addOption(OptionType.STRING, "second_team_score", "Score of the second player/team", true)

        ).queue();
    }

    private static void grabCurrentTop25()   {

    }

}