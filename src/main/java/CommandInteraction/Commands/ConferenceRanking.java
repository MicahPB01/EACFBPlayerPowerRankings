package CommandInteraction.Commands;

import CommandInteraction.Commands.Report.ReportBase;
import Utilities.AppLogger;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;

public class ConferenceRanking extends ReportBase {
    private static final Logger LOGGER = AppLogger.getLogger();

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        try (Connection conn = Database.getConnection()) {
            if (conn == null) {
                event.reply("Database is offline. Please contact the admin.").queue();
                return;
            }

            // Retrieve conference rankings
            List<ConferenceRank> conferenceRanks = getConferenceRanks(conn);

            // Build and send the embed
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Conference Rankings")
                    .setColor(Color.GREEN);

            for (ConferenceRank rank : conferenceRanks) {
                embedBuilder.addField(rank.conferenceName, "Average Rank: " + rank.averageRank, false);
            }

            event.replyEmbeds(embedBuilder.build()).queue();

        } catch (SQLException e) {
            LOGGER.severe("SQL error: " + e.getMessage());
            event.reply("An error occurred while retrieving the conference rankings.").queue();
        }
    }

    private List<ConferenceRank> getConferenceRanks(Connection conn) throws SQLException {
        String query = "SELECT t.conference_id, c.name AS conference_name, AVG(p.Power_Points) AS average_power_points " +
                "FROM players p " +
                "JOIN teams t ON p.team_id = t.team_id " +
                "JOIN conferences c ON t.conference_id = c.conference_id " +
                "WHERE t.is_user_controlled = 1 " +
                "GROUP BY t.conference_id, c.name " +
                "ORDER BY average_power_points DESC";
        PreparedStatement stmt = conn.prepareStatement(query);
        ResultSet rs = stmt.executeQuery();

        List<ConferenceRank> conferenceRanks = new ArrayList<>();
        while (rs.next()) {
            int conferenceId = rs.getInt("conference_id");
            String conferenceName = rs.getString("conference_name");
            double averageRank = rs.getDouble("average_power_points");

            conferenceRanks.add(new ConferenceRank(conferenceId, conferenceName, averageRank));
        }
        return conferenceRanks;
    }

    private static class ConferenceRank {
        int conferenceId;
        String conferenceName;
        double averageRank;

        ConferenceRank(int conferenceId, String conferenceName, double averageRank) {
            this.conferenceId = conferenceId;
            this.conferenceName = conferenceName;
            this.averageRank = averageRank;
        }
    }
}
