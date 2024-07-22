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

public class Register implements Command {
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

        try (Connection conn = Database.getConnection()) {
            if (conn == null) {
                event.reply("Database is offline. Please contact Pitchfork263.").queue();
                return;
            }

            PreparedStatement checkTeamStmt = conn.prepareStatement("SELECT team_id FROM Teams WHERE name = ?");
            checkTeamStmt.setString(1, teamName);
            ResultSet rs = checkTeamStmt.executeQuery();
            if (!rs.next()) {
                event.reply("Team not found.").queue();
                return;
            }

            int teamId = rs.getInt("team_id");

            //check for registered user
            PreparedStatement checkPlayerStmt = conn.prepareStatement("SELECT team_id FROM players WHERE discord_id = ?");
            checkPlayerStmt.setString(1, userId);
            ResultSet playerRs = checkPlayerStmt.executeQuery();
            if (playerRs.next() && playerRs.getInt("team_id") != 0) {
                event.reply("You are already registered to a team!").queue();
                return;
            }

            //register to a new team
            PreparedStatement updateStmt = conn.prepareStatement("UPDATE players SET team_id = ? WHERE discord_id = ?");
            updateStmt.setInt(1, teamId);
            updateStmt.setString(2, userId);
            updateStmt.executeUpdate();

            event.reply("Team " + teamName + " successfully registered to user " + (userOption != null ? userOption.getAsUser().getAsMention() : event.getUser().getAsMention()) + ".").queue();

        } catch (SQLException e) {
            LOGGER.warning(e.getMessage());
            event.reply("An error occurred while registering the team. Contact Pitchfork263").queue();
        }

        //assign roles
        Member member = userOption != null ? event.getGuild().retrieveMemberById(userOption.getAsUser().getId()).complete() : event.getMember();
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
                success -> event.reply("Team " + teamName + " successfully registered to user " + (userOption != null ? userOption.getAsUser().getAsMention() : event.getUser().getAsMention()) + " and role assigned.").queue(),
                failure -> event.reply("Failed to assign the role.").queue()
        );


    }

}
