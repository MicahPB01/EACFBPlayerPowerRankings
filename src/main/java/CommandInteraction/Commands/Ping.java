package CommandInteraction.Commands;

import CommandInteraction.Command;
import Utilities.AppLogger;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;


import java.util.logging.Logger;

/**
 * simple ping command
 * Helps get the status of the bot
 */

public class Ping implements Command {
    private static final Logger LOGGER = AppLogger.getLogger();



    @Override
    public void execute(SlashCommandInteractionEvent event) {
        LOGGER.fine("Pinging");
        event.reply("Pong!").queue();
    }
}
