package CommandInteraction.Commands.Report;

import Utilities.AppLogger;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public class ReportScrimmage extends ReportBase {
    private static final int FRIENDLY_DEFAULT = 10;
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
                if (firstUser == null || secondUser == null) {
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

                    // Calculate current ranks
                    int rankWinningUser = getPoints(conn, winningUser.getId(), "Friendly_Points");
                    int rankLosingUser = getPoints(conn, losingUser.getId(), "Friendly_Points");

                    LOGGER.fine("Winning Rank: " + rankWinningUser + " : Losing Rank: " + rankLosingUser);

                    // Calculate score differential
                    int scoreDifferential = Math.abs(firstScore - secondScore);

                    // Calculate points gained and lost
                    PointsResult pointsResult = calculatePoints(rankWinningUser, rankLosingUser, scoreDifferential);
                    LOGGER.fine("Got point results: " + pointsResult.pointsGained + " " + pointsResult.pointsLost);

                    // Update rankings
                    updateRankings(conn, winningUser, losingUser, pointsResult);

                    String winningEntity = isFirstUserWinner ? firstEntity : secondEntity;
                    String losingEntity = isFirstUserWinner ? secondEntity : firstEntity;

                    int winningScore = Math.max(firstScore, secondScore);
                    int losingScore = Math.min(firstScore, secondScore);

                    int winningUserPoints = getPoints(conn, winningUser.getId(), "Friendly_Points");
                    int losingUserPoints = getPoints(conn, losingUser.getId(), "Friendly_Points");

                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("Scrimmage Report")
                            .setColor(Color.GREEN)
                            .addField("Winning Team", winningEntity, true)
                            .addField("Losing Team", losingEntity, true)
                            .addField("Score", winningScore + " - " + losingScore, false)
                            .addField(winningUser.getName() + " Points", "+" + pointsResult.pointsGained + " (Total: " + winningUserPoints + ")", false)
                            .addField(losingUser.getName() + " Points", "-" + pointsResult.pointsLost + " (Total: " + losingUserPoints + ")", false);

                    event.replyEmbeds(embedBuilder.build()).queue();

                } catch (SQLException e) {
                    LOGGER.severe("SQL error: " + e.getMessage());
                    event.reply("An error occurred while reporting the scrimmage.").queue();
                }
            });
        });
    }

    private void updateRankings(Connection conn, User winningUser, User losingUser, PointsResult pointsResult) throws SQLException {
        LOGGER.fine("Updating Rankings");

        // Ensure both users exist in the players table
        ensurePlayerExists(conn, winningUser);
        ensurePlayerExists(conn, losingUser);

        // Adjust ranks
        adjustPlayerRank(conn, winningUser.getId(), pointsResult.pointsGained, "Friendly_Points");
        adjustPlayerRank(conn, losingUser.getId(), -pointsResult.pointsLost, "Friendly_Points");
    }
}
