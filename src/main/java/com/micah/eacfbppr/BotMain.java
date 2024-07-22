package com.micah.eacfbppr;

import Utilities.AppLogger;
import CommandInteraction.CommandHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;


import java.util.logging.Logger;

public class BotMain {
    private static final Logger LOGGER = AppLogger.getLogger();
    private static String BOT_TOKEN = "";






    public static void main(String[] args) {
        try {
            JDA jda = JDABuilder.createDefault(BOT_TOKEN)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setActivity(Activity.customStatus("GKCO!"))
                    .enableCache(CacheFlag.VOICE_STATE)
                    .addEventListeners(new CommandHandler())
                    .build();

            jda.awaitReady();
            registerSlashCommands(jda);


            LOGGER.info("Bot Loaded!");



        } catch (InterruptedException e) {
            LOGGER.severe(e.getMessage());
        }
    }

    private static void registerSlashCommands(JDA jda) {
        LOGGER.fine("Registering Commands");


        LOGGER.fine("Adding slash commands");


        jda.updateCommands().addCommands(
                Commands.slash("ping", "Test the bot's response time!")

        ).queue();
    }

}