package CommandInteraction.Commands;

import Utilities.AppLogger;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public class PreviousMatches {
    private static final Logger LOGGER = AppLogger.getLogger();

    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping userOption = event.getOption("user");
        User targetUser = userOption != null ? userOption.getAsUser() : event.getUser();
        String userId = targetUser.getId();

        try (Connection conn = Database.getConnection()) {
            if (conn == null) {
                event.reply("Database is offline. Please contact the admin.").queue();
                return;
            }

            // SQL query to fetch the 15 most recent matches
            String query = "SELECT m.date, " +
                    "IF(m.team1_id = p.team_id, m.team2_name, m.team1_name) AS opponent, " +
                    "IF(m.team1_id = p.team_id, m.team1_score, m.team2_score) AS user_score, " +
                    "IF(m.team1_id = p.team_id, m.team2_score, m.team1_score) AS opponent_score, " +
                    "IF(m.team1_id = p.team_id, m.team1_rank_change, m.team2_rank_change) AS points_change " +
                    "FROM matches m " +
                    "JOIN players p ON (m.team1_id = p.team_id OR m.team2_id = p.team_id) " +
                    "WHERE p.discord_id = ? " +
                    "ORDER BY m.date DESC " +
                    "LIMIT 15";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, userId);

                ResultSet rs = stmt.executeQuery();
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle(targetUser.getName() + "'s Recent Matches");
                embedBuilder.setColor(Color.BLUE);

                while (rs.next()) {
                    String date = rs.getString("date");
                    String opponent = rs.getString("opponent");
                    int userScore = rs.getInt("user_score");
                    int opponentScore = rs.getInt("opponent_score");
                    int pointsChange = rs.getInt("points_change");

                    String result = String.format("vs %s - %d - %d (%+d points)", opponent, userScore, opponentScore, pointsChange);
                    embedBuilder.addField(result, date, false);
                }

                event.replyEmbeds(embedBuilder.build()).queue();
            }

        } catch (SQLException e) {
            LOGGER.severe("SQL error: " + e.getMessage());
            event.reply("An error occurred while fetching the matches.").queue();
        }
    }
}
