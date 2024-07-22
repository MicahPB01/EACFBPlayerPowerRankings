package CommandInteraction;

import Utilities.AppLogger;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.logging.Logger;

public interface Command {
    static final Logger LOGGER = AppLogger.getLogger();

    void execute(SlashCommandInteractionEvent event);

}
