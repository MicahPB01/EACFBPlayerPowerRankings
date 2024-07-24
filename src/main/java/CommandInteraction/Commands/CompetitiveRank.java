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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CompetitiveRank implements Command {
    private static final Logger LOGGER = Logger.getLogger(FriendlyRanks.class.getName());

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Competitive Rankings");
        embedBuilder.setColor(Color.BLUE);

        try (Connection conn = Database.getConnection()) {
            if (conn == null) {
                event.reply("Database is offline. Please contact the admin.").queue();
                return;
            }

            List<PlayerRank> rankedPlayers = getRankedPlayers(conn);

            StringBuilder rankings = new StringBuilder("```\n");
            rankings.append(String.format("%-3s %-25s %-18s %-6s\n", "#", "Username", "Team", "Points"));

            for (int i = 0; i < rankedPlayers.size(); i++) {
                PlayerRank player = rankedPlayers.get(i);
                rankings.append(String.format("%-3d %-25s %-18s %-6d\n", i + 1, player.playerName, player.teamName, player.friendlyPoints));
            }
            rankings.append("```");

            embedBuilder.setDescription(rankings.toString());
            event.replyEmbeds(embedBuilder.build()).queue();
        } catch (SQLException e) {
            LOGGER.severe("SQL error: " + e.getMessage());
            event.reply("An error occurred while retrieving the competitive rankings.").queue();
        }
    }

    private List<PlayerRank> getRankedPlayers(Connection conn) throws SQLException {
        String query = "SELECT p.name AS player_name, p.discord_id, p.Power_Points, t.name AS team_name " +
                "FROM players p " +
                "LEFT JOIN teams t ON p.team_id = t.team_id " +
                "WHERE p.team_id IS NOT NULL " +
                "ORDER BY p.Power_Points DESC";
        PreparedStatement stmt = conn.prepareStatement(query);
        ResultSet rs = stmt.executeQuery();

        List<PlayerRank> rankedPlayers = new ArrayList<>();
        while (rs.next()) {
            String playerName = rs.getString("player_name");
            String discordId = rs.getString("discord_id");
            int friendlyPoints = rs.getInt("Power_Points");
            String teamName = rs.getString("team_name");

            rankedPlayers.add(new PlayerRank(playerName, discordId, friendlyPoints, teamName));
        }
        return rankedPlayers;
    }

    private static class PlayerRank {
        String playerName;
        String discordId;
        int friendlyPoints;
        String teamName;

        public PlayerRank(String playerName, String discordId, int friendlyPoints, String teamName) {
            this.playerName = playerName;
            this.discordId = discordId;
            this.friendlyPoints = friendlyPoints;
            this.teamName = teamName != null ? teamName : "Unassigned";
        }
    }
}
