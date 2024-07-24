package CommandInteraction;

import CommandInteraction.Commands.*;
import CommandInteraction.Commands.Report.ReportRanked;
import CommandInteraction.Commands.Report.ReportScrimmage;
import Utilities.AppLogger;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.checkerframework.checker.units.qual.C;
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
            case "register" -> new Register().execute(event);
            case "list" -> new CommandInteraction.Commands.List<C>().execute(event);
            case "report_scrimmage" -> new ReportScrimmage().execute(event);
            case "friendly_rankings" -> new FriendlyRanks().execute(event);
            case "report_ranked" -> new ReportRanked().execute(event);
            case "power_rankings" -> new CompetitiveRank().execute(event);
            case "conference_rankings" -> new ConferenceRanking().execute(event);

            default -> event.reply("Unknown command").setEphemeral(true).queue();
        }
        LOGGER.info("Command successfully executed: " + commandName);
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        LOGGER.fine("Setting up autocomplete options");
        if (event.getCommandString().startsWith("/register")) {
            String input = event.getFocusedOption().getValue();
            List<Command.Choice> choices = getTeamChoices(input);
            event.replyChoices(choices).queue();
        }
    }

    private List<Command.Choice> getTeamChoices(String input)   {
        List<String> teamNames = fetchTeamNames(input);
        return teamNames.stream()
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList());
    }

    private List<String> fetchTeamNames(String input) {
        LOGGER.fine("Fetching team names");
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM Teams WHERE name LIKE ? LIMIT 25";

        try (Connection conn = Database.getConnection()) {
            assert conn != null;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, input + "%");

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Could net fill autocomplete: " + e.getMessage());
        }

        LOGGER.fine("Got team names");
        return names;
    }









}
