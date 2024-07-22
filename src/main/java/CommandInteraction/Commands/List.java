package CommandInteraction.Commands;

import CommandInteraction.Command;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class List implements Command {
    @Override
    public void execute(SlashCommandInteractionEvent event) {

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Player Controlled Teams");
        embedBuilder.setColor(Color.BLUE);

        StringBuilder playerNames = new StringBuilder();
        StringBuilder teamNames = new StringBuilder();
        StringBuilder conferenceNames = new StringBuilder();


        try (Connection conn = Database.getConnection()) {
            if (conn == null) {
                event.reply("Database is offline. Please contact the admin.").queue();
                return;
            }

            String query = "SELECT p.name AS player_name, t.name AS team_name, c.name AS conference_name " +
                    "FROM players p " +
                    "LEFT JOIN teams t ON p.team_id = t.team_id " +
                    "LEFT JOIN conferences c ON t.conference_id = c.conference_id " +
                    "WHERE p.team_id IS NOT NULL";
            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                embedBuilder.setDescription("No player controlled teams found.");
            } else {
                do {
                    String playerName = rs.getString("player_name");
                    String teamName = rs.getString("team_name");
                    String conferenceName = rs.getString("conference_name");

                    playerNames.append(truncate(playerName, 24)).append("\n");
                    teamNames.append(teamName != null ? truncate(teamName, 24) : "Not Registered").append("\n");
                    conferenceNames.append(conferenceName != null ? truncate(conferenceName, 24) : "Not Registered").append("\n");
                } while (rs.next());

                embedBuilder.addField("Player", playerNames.toString(), true);
                embedBuilder.addField("Team", teamNames.toString(), true);
                embedBuilder.addField("Conference", conferenceNames.toString(), true);
            }

            event.replyEmbeds(embedBuilder.build()).queue();
        } catch (SQLException e) {
            LOGGER.severe("SQL error: " + e.getMessage());
            event.reply("An error occurred while retrieving the list of player-controlled teams.").queue();
        }


    }

    private String truncate(String value, int length) {
        if (value.length() > length) {
            return value.substring(0, length - 3) + "...";
        }
        return value;

    }
}
