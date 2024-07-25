package CommandInteraction.Commands.Report;

import Utilities.AppLogger;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public class ReportRanked extends ReportBase {
    private static final int POWER_DEFAULT = 10;
    private static final double NPC_WEIGHT_FACTOR = 0.60;
    private static final double NPC_TOP_25_WEIGHT_FACTOR = 1;
    private static final Logger LOGGER = AppLogger.getLogger();

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
                if (firstUser == null && secondUser == null) {
                    event.reply("Unable to find users associated with the provided entities.").queue();
                    return;
                }

                try (Connection conn = Database.getConnection()) {
                    if (conn == null) {
                        event.reply("Database is offline. Please contact the admin.").queue();
                        return;
                    }

                    // Determine who is the winner and who is the loser
                    boolean isFirstUserWinner = firstScore > secondScore;
                    User winningUser = isFirstUserWinner ? firstUser : secondUser;
                    User losingUser = isFirstUserWinner ? secondUser : firstUser;

                    //get winning/losing scores
                    int winningScore = isFirstUserWinner ? firstScore : secondScore;
                    int losingScore = isFirstUserWinner ? secondScore : firstScore;


                    // Check if the entities are NPCs
                    boolean isWinningEntityNpc = isTeam(conn, normalizeEntity(firstEntity)) && isPlayer(conn, normalizeEntity(firstEntity)) && isUserId(firstEntity);
                    boolean isLosingEntityNpc = isTeam(conn, normalizeEntity(secondEntity)) && isPlayer(conn, normalizeEntity(secondEntity)) && isUserId(secondEntity);
                    double weightFactor = getWeightFactor(conn, winningUser, losingUser, isWinningEntityNpc, isLosingEntityNpc);




                    // Calculate current ranks
                    int rankWinningUser = winningUser != null ? getPoints(conn, winningUser.getId(), "Power_Points") : 0;
                    int rankLosingUser = losingUser != null ? getPoints(conn, losingUser.getId(), "Power_Points") : 0;

                    LOGGER.fine("Winning Rank: " + rankWinningUser + " : Losing Rank: " + rankLosingUser);

                    // Calculate score differential
                    int scoreDifferential = Math.abs(firstScore - secondScore);

                    // Calculate points gained and lost
                    PointsResult pointsResult = calculatePoints(rankWinningUser, rankLosingUser, scoreDifferential);
                    LOGGER.fine("Got point results: " + pointsResult.pointsGained + " " + pointsResult.pointsLost);

                    // Adjust points with weight factor
                    int adjustedPointsGained = (int) (pointsResult.pointsGained * weightFactor);
                    int adjustedPointsLost = (int) (pointsResult.pointsLost * weightFactor);

                    //get ranking points before inserting match
                    int winningUserPointsBefore = getPoints(conn, winningUser != null ? winningUser.getId() : null, "Power_Points");
                    int losingUserPointsBefore = getPoints(conn, losingUser != null ? losingUser.getId() : null, "Power_Points");


                    String winningEntity = isFirstUserWinner ? firstEntity : secondEntity;
                    String losingEntity = isFirstUserWinner ? secondEntity : firstEntity;

                    // Update rankings
                    updateRankings(conn, winningEntity, winningUser, losingEntity, losingUser, adjustedPointsGained, adjustedPointsLost, winningScore, losingScore, adjustedPointsGained, -adjustedPointsLost, winningUserPointsBefore, losingUserPointsBefore);



                    int winningUserPoints = getPoints(conn, winningUser != null ? winningUser.getId() : null, "Power_Points");
                    int losingUserPoints = getPoints(conn, losingUser != null ? losingUser.getId() : null, "Power_Points");



                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("Ranked Match Report")
                            .setColor(Color.GREEN)
                            .addField("Winning Team", winningEntity, true)
                            .addField("Losing Team", losingEntity, true)
                            .addField("Score", winningScore + " - " + losingScore, false)
                            .addField(winningUser != null ? winningUser.getName() + " Points" : "Winning Team Points", "+" + adjustedPointsGained + " (Total: " + winningUserPoints + ")", false)
                            .addField(losingUser != null ? losingUser.getName() + " Points" : "Losing Team Points", "-" + adjustedPointsLost + " (Total: " + losingUserPoints + ")", false);

                    event.replyEmbeds(embedBuilder.build()).queue();

                } catch (SQLException e) {
                    LOGGER.severe("SQL error: " + e.getMessage());
                    event.reply("An error occurred while reporting the matchup.").queue();
                }
            });
        });
    }

    private void updateRankings(Connection conn, String firstEntity, User winningUser, String secondEntity, User losingUser, int pointsGained, int pointsLost, int firstScore, int secondScore, int adjustedPointsGained, int adjustedPointsLost, int winningUserPointsBefore, int losingUserPointsBefore) throws SQLException {
        LOGGER.fine("Updating Rankings");

        // Ensure both users exist in the players table
        if (winningUser != null) {
            ensurePlayerExists(conn, winningUser);
            adjustPlayerRank(conn, winningUser.getId(), pointsGained, "Power_Points");
        }

        if (losingUser != null) {
            ensurePlayerExists(conn, losingUser);
            adjustPlayerRank(conn, losingUser.getId(), -pointsLost, "Power_Points");
        }

        // Add the match to the database
        addMatchToDatabase(conn, firstEntity, winningUser, secondEntity, losingUser, firstScore, secondScore, adjustedPointsGained, adjustedPointsLost, winningUserPointsBefore, losingUserPointsBefore);
    }


    private boolean isNpcTop25(Connection conn, User user) throws SQLException {
        if (user == null) {
            return false;
        }

        String query = "SELECT COUNT(*) AS rank_position " +
                "FROM top25_teams t25 " +
                "JOIN players p ON t25.team_id = p.team_id " +
                "WHERE p.discord_id = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, user.getId());
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            int rankPosition = rs.getInt("rank_position");
            return rankPosition > 0;
        }

        return false;
    }

    private double getWeightFactor(Connection conn, User winningUser, User losingUser, boolean isWinningEntityNpc, boolean isLosingEntityNpc) throws SQLException {
        double weightFactor = 1.0;

        if (isWinningEntityNpc || isLosingEntityNpc) {
            // Check if the NPCs are in the top 25
            boolean isWinningNpcTop25 = isNpcTop25(conn, winningUser);
            boolean isLosingNpcTop25 = isNpcTop25(conn, losingUser);

            // Determine the higher weight factor if any NPC is in the top 25
            if (isWinningNpcTop25 || isLosingNpcTop25) {
                weightFactor = NPC_TOP_25_WEIGHT_FACTOR;
            } else {
                weightFactor = NPC_WEIGHT_FACTOR;
            }
        }

        return weightFactor;
    }
}
