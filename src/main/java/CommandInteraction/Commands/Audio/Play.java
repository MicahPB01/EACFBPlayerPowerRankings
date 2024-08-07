package CommandInteraction.Commands.Audio;

import CommandInteraction.Command;
import CommandInteraction.Commands.Audio.LavaPlayer.PlayerManager;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class Play implements Command {
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        GuildVoiceState memberVoiceState = member.getVoiceState();

        if(!memberVoiceState.inAudioChannel())   {
            event.reply("Please join a voice channel before using this command.").queue();
            return;
        }


        Member self = event.getGuild().getSelfMember();
        GuildVoiceState selfVoiceState = self.getVoiceState();

        if(!selfVoiceState.inAudioChannel())   {
            event.getGuild().getAudioManager().openAudioConnection(memberVoiceState.getChannel());
        }
        else   {
            if(selfVoiceState.getChannel() != memberVoiceState.getChannel())   {
                event.reply("Please be in the same channel as the bot.").queue();
                return;
            }
        }


        PlayerManager playerManager = PlayerManager.get();
        playerManager.play(event.getGuild(), event.getOption("url").getAsString());



    }
}
