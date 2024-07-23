package CommandInteraction.Commands;

import CommandInteraction.Command;
import com.micah.eacfbppr.Database;
import com.mysql.cj.util.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

        String firstEntity = firstOption.getAsString();
        String firstScoreString = firstScoreOption.getAsString();
        String secondEntity = secondOption.getAsString();
        String secondScoreString = secondScoreOption.getAsString();

        int firstScore;
        int secondScore;
        try {
            firstScore = Integer.parseInt(firstScoreString);
            secondScore = Integer.parseInt(secondScoreString);
        } catch (NumberFormatException e) {
            event.reply("Invalid score format. Scores must be numbers.").queue();
            return;
        }

        // Fetch users asynchronously
        getUserFromEntity(event, firstEntity, firstUser -> {
            getUserFromEntity(event, secondEntity, secondUser -> {
                if (firstUser == null || secondUser == null) {
                    event.reply("Unable to find users associated with the provided entities.").queue();
                    return;
                }

                try (Connection conn = Database.getConnection()) {
                    if (conn == null) {
                        event.reply("Database is offline. Please contact the admin.").queue();
                        return;
                    }

                    int scoreDifferential = Math.abs(firstScore - secondScore);
                    User winningUser = firstScore > secondScore ? firstUser : secondUser;
                    User losingUser = firstScore > secondScore ? secondUser : firstUser;

                    updateRankings(conn, winningUser, losingUser, scoreDifferential);

                    String winningEntity = firstScore > secondScore ? firstEntity : secondEntity;
                    String losingEntity = firstScore > secondScore ? secondEntity : firstEntity;

                    int winningScore = Math.max(firstScore, secondScore);
                    int losingScore = Math.min(firstScore, secondScore);

                    // Retrieve updated points
                    int winningUserPoints = getFriendlyPoints(conn, winningUser.getId());
                    int losingUserPoints = getFriendlyPoints(conn, losingUser.getId());
                    int winningPointsGained = 20 + scoreDifferential + FRIENDLY_DEFAULT;
                    int losingPointsGained = 1 + scoreDifferential + FRIENDLY_DEFAULT;

                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("Scrimmage Report")
                            .setColor(Color.GREEN)
                            .addField("Winning Team", winningEntity, true)
                            .addField("Losing Team", losingEntity, true)
                            .addField("Score", winningScore + " - " + losingScore, false)
                            .addField(winningUser.getName() + " Points", "+" + winningPointsGained + " (Total: " + winningUserPoints + ")", false)
                            .addField(losingUser.getName() + " Points", "+" + losingPointsGained + " (Total: " + losingUserPoints + ")", false);

                    event.replyEmbeds(embedBuilder.build()).queue();

                } catch (SQLException e) {
                    LOGGER.severe("SQL error: " + e.getMessage());
                    event.reply("An error occurred while reporting the scrimmage.").queue();
                }
            });
        });
    }

    private int getFriendlyPoints(Connection conn, String discordId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT Friendly_Points FROM players WHERE discord_id = ?");
        stmt.setString(1, discordId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getInt("Friendly_Points");
        }
        return 0; // default if not found
    }


    private void updateRankings(Connection conn, User winningUser, User losingUser, int scoreDifferential) throws SQLException {
        LOGGER.fine("Updating Rankings");

        // Ensure both users exist in the players table
        ensurePlayerExists(conn, winningUser);
        ensurePlayerExists(conn, losingUser);

        // Adjust ranks
        adjustPlayerRank(conn, winningUser.getId(), 20 + scoreDifferential + FRIENDLY_DEFAULT);
        adjustPlayerRank(conn, losingUser.getId(), 1 + scoreDifferential + FRIENDLY_DEFAULT);
    }

    private String normalizeEntity(String entity) {
        // If the entity is a mention, strip the mention syntax to get the ID or name
        if (entity.startsWith("<@&") && entity.endsWith(">")) {
            return entity.substring(3, entity.length() - 1); // Role mention
        } else if (entity.startsWith("<@") && entity.endsWith(">")) {
            return entity.substring(2, entity.length() - 1); // User mention
        }
        return entity;
    }

    private boolean isTeam(Connection conn, String entityName) throws SQLException {
        LOGGER.fine("Checking if entity is a team: " + entityName);
        PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT COUNT(*) FROM Teams WHERE name = ?");
        checkTeamStmt.setString(1, entityName);
        ResultSet rs = checkTeamStmt.executeQuery();
        boolean isTeam = rs.next() && rs.getInt(1) > 0;
        LOGGER.fine("Is team: " + isTeam);
        return isTeam;
    }

    private boolean isPlayer(Connection conn, String entityName) throws SQLException {
        LOGGER.fine("Checking if entity is a player: " + entityName);
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT COUNT(*) FROM players WHERE name = ?");
        checkPlayerStmt.setString(1, entityName);
        ResultSet rs = checkPlayerStmt.executeQuery();
        boolean isPlayer = rs.next() && rs.getInt(1) > 0;
        LOGGER.fine("Is player: " + isPlayer);
        return isPlayer;
    }

    private boolean isRoleId(String entity) {
        // Check if the entity is a role mention (starts with <@& and ends with >)
        return entity.startsWith("<@&") && entity.endsWith(">");
    }

    private boolean isUserId(String entity) {
        // Check if the entity is a user mention (starts with <@ and ends with >)
        return entity.startsWith("<@") && entity.endsWith(">");
    }

    private void adjustPlayerRank(Connection conn, String discordId, int rankAdjustment) throws SQLException {
        LOGGER.fine("Adjusting rank for discordID: " + discordId);
        PreparedStatement updateFriendlyRankStmt = conn.prepareStatement(
                "UPDATE players SET Friendly_Points = Friendly_Points + ? WHERE discord_id = ?"
        );
        updateFriendlyRankStmt.setInt(1, rankAdjustment);
        updateFriendlyRankStmt.setString(2, discordId);
        updateFriendlyRankStmt.executeUpdate();
    }

    private boolean isTeam(Connection conn, String entityId, String entityName) throws SQLException {
        LOGGER.fine("Checking if entity is a team: " + entityId + " or " + entityName);
        PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT COUNT(*) FROM Teams WHERE name = ? OR team_id = ?");
        checkTeamStmt.setString(1, entityName);
        checkTeamStmt.setString(2, entityId);
        ResultSet rs = checkTeamStmt.executeQuery();
        boolean isTeam = rs.next() && rs.getInt(1) > 0;
        LOGGER.fine("Is team: " + isTeam);
        return isTeam;
    }

    private boolean isPlayer(Connection conn, String entityId, String entityName) throws SQLException {
        LOGGER.fine("Checking if entity is a player: " + entityId + " or " + entityName);
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT COUNT(*) FROM players WHERE name = ? OR discord_id = ?");
        checkPlayerStmt.setString(1, entityName);
        checkPlayerStmt.setString(2, entityId);
        ResultSet rs = checkPlayerStmt.executeQuery();
        boolean isPlayer = rs.next() && rs.getInt(1) > 0;
        LOGGER.fine("Is player: " + isPlayer);
        return isPlayer;
    }





    private void ensurePlayerExists(Connection conn, User user) throws SQLException {
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


    private void getUserFromEntity(SlashCommandInteractionEvent event, String entityName, Consumer<User> callback) {
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





}

