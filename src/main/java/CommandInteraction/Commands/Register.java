package CommandInteraction.Commands;

import CommandInteraction.Command;
import com.micah.eacfbppr.Database;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;

public class Register implements Command {
    private static final Logger LOGGER = Logger.getLogger(Register.class.getName());

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping userOption = event.getOption("user");
        OptionMapping teamOption = event.getOption("team");

        if (teamOption == null) {
            event.reply("You must specify a team name.").queue();
            return;
        }

        String teamName = teamOption.getAsString();
        String userId = userOption != null ? userOption.getAsUser().getId() : event.getUser().getId();
        String userName = userOption != null ? userOption.getAsUser().getName() : event.getUser().getName();

        try (Connection conn = Database.getConnection()) {
            if (conn == null) {
                event.reply("Database is offline. Please contact the Pitchfork263.").queue();
                return;
            }

            int teamId = getTeamId(conn, teamName);
            if (teamId == -1) {
                event.reply("Team not found.").queue();
                return;
            }

            if (isUserAlreadyRegistered(conn, userId)) {
                event.reply("This user is already registered to a team.").queue();
                return;
            }

            upsertPlayer(conn, userId, userName, teamId);
            updateTeamControlStatus(conn, teamId);

            assignRoleToUser(event, userOption, teamName);

            event.reply("Team " + teamName + " successfully registered to user " +
                    (userOption != null ? userOption.getAsUser().getAsMention() : event.getUser().getAsMention()) +
                    " and role assigned.").queue();

        } catch (SQLException e) {
            LOGGER.severe("SQL error: " + e.getMessage());
            event.reply("An error occurred while registering the team.").queue();
        }
    }

    private int getTeamId(Connection conn, String teamName) throws SQLException {
        PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT team_id FROM Teams WHERE name = ?");
        checkTeamStmt.setString(1, teamName);
        ResultSet rs = checkTeamStmt.executeQuery();
        if (rs.next()) {
            return rs.getInt("team_id");
        }
        return -1;
    }

    private boolean isUserAlreadyRegistered(Connection conn, String userId) throws SQLException {
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT team_id FROM players WHERE discord_id = ?");
        checkPlayerStmt.setString(1, userId);
        ResultSet playerRs = checkPlayerStmt.executeQuery();
        return playerRs.next() && playerRs.getInt("team_id") != 0;
    }

    private void upsertPlayer(Connection conn, String userId, String userName, int teamId) throws SQLException {
        PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT team_id FROM players WHERE discord_id = ?");
        checkPlayerStmt.setString(1, userId);
        ResultSet playerRs = checkPlayerStmt.executeQuery();

        if (playerRs.next()) {
            PreparedStatement updateStmt = conn.prepareStatement("UPDATE players SET team_id = ? WHERE discord_id = ?");
            updateStmt.setInt(1, teamId);
            updateStmt.setString(2, userId);
            updateStmt.executeUpdate();
        } else {
            PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO players (name, discord_id, team_id) VALUES (?, ?, ?)");
            insertStmt.setString(1, userName);
            insertStmt.setString(2, userId);
            insertStmt.setInt(3, teamId);
            insertStmt.executeUpdate();
        }
    }

    private void updateTeamControlStatus(Connection conn, int teamId) throws SQLException {
        PreparedStatement updateTeamStmt = conn.prepareStatement("UPDATE Teams SET is_user_controlled = ? WHERE team_id = ?");
        updateTeamStmt.setInt(1, 1);
        updateTeamStmt.setInt(2, teamId);
        updateTeamStmt.executeUpdate();
    }

    private void assignRoleToUser(SlashCommandInteractionEvent event, OptionMapping userOption, String teamName) {
        Member member = userOption != null ? Objects.requireNonNull(event.getGuild()).retrieveMemberById(userOption.getAsUser().getId()).complete() : event.getMember();
        if (member == null) {
            event.reply("Unable to find the user in the guild.").queue();
            return;
        }

        Role teamRole = Objects.requireNonNull(event.getGuild()).getRolesByName(teamName, true).stream().findFirst().orElse(null);
        if (teamRole == null) {
            event.reply("Role for the team not found. Please ensure the role exists.").queue();
            return;
        }

        event.getGuild().addRoleToMember(member, teamRole).queue(
                success -> LOGGER.info("Role assigned successfully."),
                failure -> event.reply("Failed to assign the role.").queue()
        );
    }
}
