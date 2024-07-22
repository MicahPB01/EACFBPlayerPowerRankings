package CommandInteraction;

import CommandInteraction.Commands.Ping;
import Utilities.AppLogger;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.jetbrains.annotations.NotNull;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CommandHandler extends ListenerAdapter {
    private static final Logger LOGGER = AppLogger.getLogger();

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        LOGGER.fine("Received command: " + commandName);

        switch (commandName) {
            case "ping" -> new Ping().execute(event);

            default -> event.reply("Unknown command").setEphemeral(true).queue();
        }
        LOGGER.info("Command successfully executed: " + commandName);
    }







}
