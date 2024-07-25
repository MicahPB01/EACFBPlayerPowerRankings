package CommandInteraction.Commands.Report;

import CommandInteraction.Command;
import Utilities.AppLogger;
import java.sql.Date;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class ReportBase implements Command {
    private static final Logger LOGGER = AppLogger.getLogger();

    protected void getUserFromEntity(SlashCommandInteractionEvent event, String entityName, Consumer<User> callback) {
        LOGGER.fine("Checking entity: " + entityName);

        // Check if entityName is a role mention
        if (entityName.startsWith("<@&") && entityName.endsWith(">")) {
            String roleId = entityName.substring(3, entityName.length() - 1);
            Role role = Objects.requireNonNull(event.getGuild()).getRoleById(roleId);

            if (role != null) {
                LOGGER.fine("Tag provided is a role");

                // Ensure members are loaded asynchronously
                event.getGuild().loadMembers().onSuccess(members -> {
                    List<Member> membersWithRole = members.stream()
                            .filter(member -> member.getRoles().contains(role))
                            .collect(Collectors.toList());

                    LOGGER.fine("Found members for role: " + role.getName() + " " + membersWithRole);
                    if (!membersWithRole.isEmpty()) {
                        LOGGER.fine("Found a username from the role: " + role.getName() + " - " + membersWithRole.get(0).getUser());
                        callback.accept(membersWithRole.get(0).getUser());
                    } else {
                        LOGGER.fine("No members found with role: " + role.getName());
                        callback.accept(null);
                    }
                }).onError(throwable -> {
                    LOGGER.severe("Error fetching members: " + throwable.getMessage());
                    callback.accept(null);
                });
                return;
            }
        }

        // Check if entityName is a user mention
        if (entityName.startsWith("<@") && entityName.endsWith(">")) {
            LOGGER.fine("Looking for direct users");
            String userId = entityName.substring(2, entityName.length() - 1);
            LOGGER.fine("Looking for user id: " + userId);

            // Use retrieveMemberById to ensure the member is fetched if not cached
            Objects.requireNonNull(event.getGuild()).retrieveMemberById(userId).queue(member -> {
                if (member != null) {
                    LOGGER.fine("Got user: " + member.getUser().getName());
                    LOGGER.fine("Found a user from the mention: " + member.getUser().getName());
                    callback.accept(member.getUser());
                } else {
                    LOGGER.severe("No user found for mention: " + entityName);
                    callback.accept(null);
                }
            }, throwable -> {
                LOGGER.severe("Error retrieving user: " + throwable.getMessage());
                callback.accept(null);
            });
            return;
        }

        // Otherwise, assume entityName is a username and fetch the user
        List<Member> members = Objects.requireNonNull(event.getGuild()).getMembersByName(entityName, true);
        LOGGER.fine("Tag provided is a user");
        if (!members.isEmpty()) {
            callback.accept(members.get(0).getUser());
        } else {
            LOGGER.severe("No user found for name: " + entityName);
            callback.accept(null); // Return null if no user or role was found
        }
    }

    protected void ensurePlayerExists(Connection conn, User user) throws SQLException {
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT COUNT(*) FROM players WHERE discord_id = ?");
        checkPlayerStmt.setString(1, user.getId());
        ResultSet rs = checkPlayerStmt.executeQuery();
        if (!rs.next() || rs.getInt(1) == 0) {
            // Player does not exist, insert new player
            PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO players (name, discord_id, Friendly_Points) VALUES (?, ?, 500)");
            insertStmt.setString(1, user.getName());
            insertStmt.setString(2, user.getId());
            insertStmt.executeUpdate();
        }
    }

    protected void adjustPlayerRank(Connection conn, String discordId, int rankAdjustment, String pointsColumn) throws SQLException {
        LOGGER.fine("Adjusting rank for discordID: " + discordId);
        PreparedStatement updateRankStmt = conn.prepareStatement(
                "UPDATE players SET " + pointsColumn + " = " + pointsColumn + " + ? WHERE discord_id = ?"
        );
        updateRankStmt.setInt(1, rankAdjustment);
        updateRankStmt.setString(2, discordId);
        updateRankStmt.executeUpdate();
    }

    protected void addMatchToDatabase(Connection conn, String firstEntity, User firstUser, String secondEntity, User secondUser, int team1Score, int team2Score, int firstTeamRankChange, int secondTeamRankChange, int firstTeamCurrentRank, int secondTeamCurrentRank) throws SQLException {
        LOGGER.fine("Adding match to database.");

        int firstTeamId = getTeamId(conn, firstUser != null ? firstUser.getId() : null, firstEntity);
        int secondTeamId = getTeamId(conn, secondUser != null ? secondUser.getId() : null, secondEntity);



        String insertQuery = "INSERT INTO matches (team1_name, team2_name, team1_id, team2_id, team1_score, team2_score, team1_rank_change, team2_rank_change, team1_rank_before_match, team2_rank_before_match, date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement addMatch = conn.prepareStatement(insertQuery)) {
            addMatch.setString(1, firstUser != null ? firstUser.getName() : firstEntity);
            addMatch.setString(2, secondUser != null ? secondUser.getName() : secondEntity);
            addMatch.setInt(3, firstTeamId);
            addMatch.setInt(4, secondTeamId);
            addMatch.setInt(5, team1Score);
            addMatch.setInt(6, team2Score);
            addMatch.setInt(7, firstTeamRankChange);
            addMatch.setInt(8, secondTeamRankChange);
            addMatch.setInt(9, firstTeamCurrentRank);
            addMatch.setInt(10, secondTeamCurrentRank);
            addMatch.setDate(11, Date.valueOf(LocalDate.now()));

            addMatch.executeUpdate();
        }
    }

    protected int getPoints(Connection conn, String discordId, String pointsColumn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT " + pointsColumn + " FROM players WHERE discord_id = ?");
        stmt.setString(1, discordId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getInt(pointsColumn);
        }
        return 0; // default if not found
    }



    protected PointsResult calculatePoints(int rankWinning, int rankLosing, int scoreDifferential) {
        LOGGER.fine("Calculating points");
        // Calculate rank differential
        int rankDifferential = rankLosing - rankWinning;
        LOGGER.fine("Rank Difference: " + rankDifferential);

        // Calculate base points
        int basePointsWinning;
        int basePointsLosing;
        if (rankDifferential < 0) {
            // Higher ranked team wins
            LOGGER.fine("Higher ranked team won");
            basePointsWinning = Math.max(1, 10 + (rankDifferential / 100));
            basePointsLosing = Math.max(1, 8 + (rankDifferential / 100));
        } else {
            // Lower ranked team wins
            LOGGER.fine("Lower ranked team won");
            basePointsWinning = 10 + (rankDifferential / 100);
            basePointsLosing = 8 + (rankDifferential / 100);
        }

        // Calculate score multiplier
        double scoreMultiplier = 1 + (scoreDifferential / 10.0);

        // Calculate total points
        int pointsGained = (int) (basePointsWinning * scoreMultiplier);
        int pointsLost = (int) (basePointsLosing * scoreMultiplier);

        // Adjust points to ensure winning has a slightly higher impact
        pointsGained = Math.max(pointsGained, basePointsWinning);
        pointsLost = Math.max(pointsLost, basePointsLosing);

        LOGGER.fine("Points won/lost: " + pointsGained + " " + pointsLost);

        return new PointsResult(pointsGained, pointsLost);
    }

    protected String normalizeEntity(String entity) {
        // If the entity is a mention, strip the mention syntax to get the ID or name
        if (entity.startsWith("<@&") && entity.endsWith(">")) {
            return entity.substring(3, entity.length() - 1); // Role mention
        } else if (entity.startsWith("<@") && entity.endsWith(">")) {
            return entity.substring(2, entity.length() - 1); // User mention
        }
        return entity;
    }

    protected boolean isTeam(Connection conn, String entityName) throws SQLException {
        LOGGER.fine("Checking if entity is a team: " + entityName);
        PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT COUNT(*) FROM Teams WHERE name = ?");
        checkTeamStmt.setString(1, entityName);
        ResultSet rs = checkTeamStmt.executeQuery();
        boolean isTeam = rs.next() && rs.getInt(1) > 0;
        LOGGER.fine("Is team: " + isTeam);
        return !isTeam;
    }

    protected boolean isPlayer(Connection conn, String entityName) throws SQLException {
        LOGGER.fine("Checking if entity is a player: " + entityName);
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT COUNT(*) FROM players WHERE name = ?");
        checkPlayerStmt.setString(1, entityName);
        ResultSet rs = checkPlayerStmt.executeQuery();
        boolean isPlayer = rs.next() && rs.getInt(1) > 0;
        LOGGER.fine("Is player: " + isPlayer);
        return !isPlayer;
    }

    protected boolean isUserId(String entity) {
        // Check if the entity is a user mention (starts with <@ and ends with >)
        return !entity.startsWith("<@") || !entity.endsWith(">");
    }

    private int getTeamId(Connection conn, String userId, String entityName) throws SQLException {
        if (userId == null) {
            // If userId is null, assume it's an NPC team and retrieve the team_id based on the entity name
            String query = "SELECT team_id FROM teams WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, entityName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("team_id");
                }
            }
        } else {
            // Retrieve the team_id based on the user's team association
            String query = "SELECT team_id FROM players WHERE discord_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("team_id");
                }
            }
        }
        return 135; // NPC team id
    }
}
