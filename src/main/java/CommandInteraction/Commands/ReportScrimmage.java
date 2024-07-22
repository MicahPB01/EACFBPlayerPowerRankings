package CommandInteraction.Commands;

import CommandInteraction.Command;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class ReportScrimmage implements Command {
    private static final int FRIENDLY_DEFAULT = 10;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping firstOption = event.getOption("first_team");
        OptionMapping firstScoreOption = event.getOption("first_team_score");
        OptionMapping secondOption = event.getOption("second_team");
        OptionMapping secondScoreOption = event.getOption("second_team_score");



        if (firstOption == null || firstScoreOption == null || secondOption == null || secondScoreOption == null) {
            event.reply("Please provide both teams or players and their scores.").queue();
            return;
        }


        String firstTeam = firstOption.getAsString();
        String firstScoreString = firstScoreOption.getAsString();
        String secondTeam = secondOption.getAsString();
        String secondScoreString = secondScoreOption.getAsString();

        System.out.println(firstScoreString);
        System.out.println(secondScoreString);

        int firstScore;
        int secondScore;
        try {
            firstScore = Integer.parseInt(firstScoreString);
            secondScore = Integer.parseInt(secondScoreString);
        } catch (NumberFormatException e) {
            event.reply("Invalid score format. Scores must be numbers.").queue();
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (conn == null) {
                event.reply("Database is offline. Please contact the admin.").queue();
                return;
            }

            String winningEntity = "";
            String losingEntity = "";
            int scoreDifferential = Math.abs(firstScore - secondScore);

            if (firstScore > secondScore) {
                winningEntity = firstTeam;
                losingEntity = secondTeam;
            } else {
                winningEntity = secondTeam;
                losingEntity = firstTeam;
                scoreDifferential = -scoreDifferential; // Negative differential for the loser
            }

            User winningUser = getUserFromEntity(event, winningEntity);
            User losingUser = getUserFromEntity(event, losingEntity);

            assert winningUser != null;
            ensurePlayerExists(conn, winningUser);
            assert losingUser != null;
            ensurePlayerExists(conn, losingUser);


            updateRankings(conn, winningEntity, losingEntity, scoreDifferential, winningUser, losingUser);

            System.out.println(winningEntity + " " + losingEntity);
            event.reply("Scrimmage reported. " + winningEntity + " won against " + losingEntity + " by a score of " + firstScore + " - " + secondScore + "." ).queue();

        } catch (SQLException e) {
            LOGGER.severe("SQL error: " + e.getMessage());
            event.reply("An error occurred while reporting the scrimmage.").queue();
        }


    }

    private void updateRankings(Connection conn, String winningEntity, String losingEntity, int scoreDifferential, User winningUser, User losingUser) throws SQLException {
        // Update winning team/player
        if (isTeam(conn, winningEntity)) {
            int teamId = getTeamId(conn, winningEntity);
            adjustTeamRank(conn, teamId, 20 + scoreDifferential + FRIENDLY_DEFAULT);
        } else if (isPlayer(conn, winningEntity)) {
            ensurePlayerExists(conn, winningUser);
            adjustPlayerRank(conn, winningUser.getId(), 20 + scoreDifferential + FRIENDLY_DEFAULT);
        }

        // Update losing team/player
        if (isTeam(conn, losingEntity)) {
            int teamId = getTeamId(conn, losingEntity);
            adjustTeamRank(conn, teamId, 1 + scoreDifferential + FRIENDLY_DEFAULT);
        } else if (isPlayer(conn, losingEntity)) {
            ensurePlayerExists(conn, losingUser);
            adjustPlayerRank(conn, losingUser.getId(), 1 + scoreDifferential + FRIENDLY_DEFAULT);
        }
    }

    private void adjustTeamRank(Connection conn, int teamId, int rankAdjustment) throws SQLException {
        PreparedStatement updateFriendlyRankStmt = conn.prepareStatement(
                "UPDATE Teams SET friendly_rank = friendly_rank + ? WHERE team_id = ?"
        );
        updateFriendlyRankStmt.setInt(1, rankAdjustment);
        updateFriendlyRankStmt.setInt(2, teamId);
        updateFriendlyRankStmt.executeUpdate();
    }

    private void adjustPlayerRank(Connection conn, String discordId, int rankAdjustment) throws SQLException {
        PreparedStatement updateFriendlyRankStmt = conn.prepareStatement(
                "UPDATE players SET Friendly_Points = Friendly_Points + ? WHERE discord_id = ?"
        );
        updateFriendlyRankStmt.setInt(1, rankAdjustment);
        updateFriendlyRankStmt.setString(2, discordId);
        updateFriendlyRankStmt.executeUpdate();
    }

    private boolean isTeam(Connection conn, String entityName) throws SQLException {
        PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT COUNT(*) FROM Teams WHERE name = ?");
        checkTeamStmt.setString(1, entityName);
        ResultSet rs = checkTeamStmt.executeQuery();
        return rs.next() && rs.getInt(1) > 0;
    }

    private boolean isPlayer(Connection conn, String entityName) throws SQLException {
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT COUNT(*) FROM players WHERE name = ?");
        checkPlayerStmt.setString(1, entityName);
        ResultSet rs = checkPlayerStmt.executeQuery();
        return rs.next() && rs.getInt(1) > 0;
    }

    private int getTeamId(Connection conn, String teamName) throws SQLException {
        PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT team_id FROM Teams WHERE name = ?");
        checkTeamStmt.setString(1, teamName);
        ResultSet rs = checkTeamStmt.executeQuery();
        return rs.next() ? rs.getInt("team_id") : -1;
    }

    private int getPlayerTeamId(Connection conn, String playerName) throws SQLException {
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT team_id FROM players WHERE name = ?");
        checkPlayerStmt.setString(1, playerName);
        ResultSet rs = checkPlayerStmt.executeQuery();
        return rs.next() ? rs.getInt("team_id") : -1;
    }

    private void ensurePlayerExists(Connection conn, User user) throws SQLException {
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT COUNT(*) FROM players WHERE discord_id = ?");
        checkPlayerStmt.setString(1, user.getId());
        ResultSet rs = checkPlayerStmt.executeQuery();
        if (rs.next() && rs.getInt(1) == 0) {
            PreparedStatement insertPlayerStmt = conn.prepareStatement(
                    "INSERT INTO players (name, discord_id, Power_Points, Friendly_Points) VALUES (?, ?, 0, 500)"
            );
            insertPlayerStmt.setString(1, user.getName());
            insertPlayerStmt.setString(2, user.getId());
            insertPlayerStmt.executeUpdate();
        }
    }

    private User getUserFromEntity(SlashCommandInteractionEvent event, String entityName) {
        // Check if entityName is a role
        java.util.List<Role> roles = Objects.requireNonNull(event.getGuild()).getRolesByName(entityName, true);
        if (!roles.isEmpty()) {
            Role role = roles.get(0);
            java.util.List<Member> members = event.getGuild().getMembersWithRoles(role);
            if (!members.isEmpty()) {
                LOGGER.fine("Found a username from the role: " + members.get(0).getUser());
                return members.get(0).getUser();
            }
        }

        // Otherwise, assume entityName is a username and fetch the user
        List<Member> members = event.getGuild().getMembersByName(entityName, true);
        if (!members.isEmpty()) {
            return members.get(0).getUser();
        }

        return null; // Return null if no user or role was found
    }





}

